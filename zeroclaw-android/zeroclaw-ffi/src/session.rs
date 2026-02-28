/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Live agent session management with streaming tool-call loop integration.
//!
//! A session represents a single multi-turn conversation with the `ZeroClaw`
//! agent loop. The lifecycle follows a strict state machine:
//!
//! 1. **Start** -- [`session_start`](crate::session_start) creates a new
//!    session, parsing daemon config and building the system prompt.
//! 2. **Seed** -- optional: inject prior context via
//!    [`session_seed_history`](crate::session_seed_history).
//! 3. **Send** -- [`session_send`](crate::session_send) runs the full
//!    tool-call loop, streaming progress deltas through an
//!    [`FfiSessionListener`] callback.
//! 4. **Cancel / Clear** -- abort the current send or wipe history.
//! 5. **History** -- [`session_history`](crate::session_history) returns
//!    the conversation transcript.
//! 6. **Destroy** -- [`session_destroy`](crate::session_destroy) tears
//!    down the session and releases all resources.
//!
//! Only one session exists at a time (guarded by the [`SESSION`] mutex).

// Foundation types and helpers consumed by session_start_inner,
// session_send_inner, and other functions landing in later tasks.
// Remove these allows once all session functions are wired up.
#![allow(dead_code, unused_imports)]

use std::sync::Mutex;

use crate::error::FfiError;

/// The global singleton session slot.
///
/// At most one [`Session`] is active at any time. Operations that require
/// a running session acquire this mutex and return
/// [`FfiError::StateError`] when the slot is `None`.
static SESSION: Mutex<Option<Session>> = Mutex::new(None);

/// Internal session state holding conversation history and provider config.
///
/// Not exposed across the FFI boundary -- Kotlin interacts exclusively
/// through exported free functions and the [`FfiSessionListener`] callback.
struct Session {
    /// Accumulated conversation messages (user + assistant turns).
    history: Vec<zeroclaw::providers::ChatMessage>,
    /// Parsed daemon configuration snapshot taken at session creation.
    config: zeroclaw::Config,
    /// Assembled system prompt (identity + workspace files).
    system_prompt: String,
    /// Model identifier passed to the provider (e.g. `"gpt-4o"`).
    model: String,
    /// Sampling temperature for the provider.
    temperature: f64,
    /// Provider name used to create the provider instance (e.g. `"openai"`).
    provider_name: String,
}

/// A single conversation message exchanged over the FFI boundary.
///
/// Mirrors [`zeroclaw::providers::ChatMessage`] but uses UniFFI-compatible
/// types. The `role` field is one of `"system"`, `"user"`, or `"assistant"`.
#[derive(uniffi::Record, Clone, Debug)]
pub struct SessionMessage {
    /// The message role: `"system"`, `"user"`, or `"assistant"`.
    pub role: String,
    /// The text content of the message.
    pub content: String,
}

/// Callback interface that Kotlin implements to receive live agent session events.
///
/// Events are dispatched from the tokio runtime thread during
/// [`session_send`](crate::session_send). Implementations must be
/// thread-safe (`Send + Sync`). Each callback corresponds to a distinct
/// phase of the agent's tool-call loop execution.
#[uniffi::export(callback_interface)]
pub trait FfiSessionListener: Send + Sync {
    /// The agent is producing internal reasoning (thinking/planning).
    ///
    /// Called with progressive text chunks as the agent reasons about
    /// which tools to invoke or how to answer.
    fn on_thinking(&self, text: String);

    /// A chunk of the agent's final response text has arrived.
    ///
    /// Called incrementally as the provider streams response tokens.
    /// Concatenating all chunks yields the full response.
    fn on_response_chunk(&self, text: String);

    /// The agent is about to invoke a tool.
    ///
    /// `name` is the tool identifier (e.g. `"read_file"`).
    /// `arguments_hint` is a short summary of the arguments, which may
    /// be empty if no hint is available.
    fn on_tool_start(&self, name: String, arguments_hint: String);

    /// A tool invocation has completed.
    ///
    /// `name` is the tool identifier, `success` indicates whether the
    /// tool returned a result or an error, and `duration_secs` is the
    /// wall-clock execution time rounded to whole seconds.
    fn on_tool_result(&self, name: String, success: bool, duration_secs: u64);

    /// Raw tool output text for display in a collapsible detail section.
    ///
    /// Called after [`on_tool_result`](FfiSessionListener::on_tool_result)
    /// with the full stdout/stderr captured from the tool execution.
    fn on_tool_output(&self, name: String, output: String);

    /// A progress status line from the agent loop.
    ///
    /// Used for miscellaneous status updates that do not fit the other
    /// callback categories (e.g. `"Searching memory..."`).
    fn on_progress(&self, message: String);

    /// The conversation history was compacted to fit the context window.
    ///
    /// `summary` contains the AI-generated summary that replaced older
    /// messages. The UI should display this as a fold/expansion point.
    fn on_compaction(&self, summary: String);

    /// The agent loop has finished and the full response is available.
    ///
    /// `full_response` contains the concatenated final answer. This is
    /// always the last callback for a successful send.
    fn on_complete(&self, full_response: String);

    /// An unrecoverable error occurred during the agent loop.
    ///
    /// `error` contains a human-readable description. The session
    /// remains valid and the caller may retry with a new send.
    fn on_error(&self, error: String);

    /// The current send was cancelled by the user.
    ///
    /// The session remains valid; the caller may issue a new send.
    fn on_cancelled(&self);
}

// ── Delta string parser ─────────────────────────────────────────────────
//
// Upstream `ZeroClaw`'s `run_tool_call_loop()` emits progress as plain
// strings with emoji prefixes. The parser below converts these strings
// into typed [`FfiSessionListener`] callbacks.

/// Sentinel value emitted by upstream to signal the transition from
/// tool-call progress lines to streamed response tokens.
///
/// After this sentinel, all subsequent deltas are response content
/// until the loop iteration ends.
const DRAFT_CLEAR_SENTINEL: &str = "\x00CLEAR\x00";

/// Dispatches a single progress delta string to the appropriate listener callback.
///
/// The upstream agent loop emits deltas in two phases:
///
/// 1. **Progress phase** -- emoji-prefixed status lines describing thinking,
///    tool starts, tool completions, and other progress.
/// 2. **Response phase** -- raw text chunks of the assistant's streamed reply,
///    entered after [`DRAFT_CLEAR_SENTINEL`] is received.
///
/// `streaming_response` tracks which phase we are in and is mutated when
/// the sentinel is encountered.
pub(crate) fn dispatch_delta(
    delta: &str,
    listener: &dyn FfiSessionListener,
    streaming_response: &mut bool,
) {
    if delta == DRAFT_CLEAR_SENTINEL {
        *streaming_response = true;
        return;
    }

    if *streaming_response {
        listener.on_response_chunk(delta.to_string());
        return;
    }

    let trimmed = delta.trim_end_matches('\n');
    if trimmed.is_empty() {
        return;
    }

    let mut chars = trimmed.chars();
    if let Some(first) = chars.next() {
        let rest = chars.as_str();
        match first {
            '\u{1f914}' => {
                // 🤔 Thinking / planning
                listener.on_thinking(rest.trim().to_string());
            }
            '\u{23f3}' => {
                // ⏳ Tool start — format: "⏳ tool_name: hint text"
                let rest = rest.trim();
                let (name, hint) = match rest.find(':') {
                    Some(pos) => (rest[..pos].trim(), rest[pos + 1..].trim()),
                    None => (rest, ""),
                };
                listener.on_tool_start(name.to_string(), hint.to_string());
            }
            '\u{2705}' => {
                // ✅ Tool success — format: "✅ tool_name (3s)"
                let (name, secs) = parse_tool_completion(rest.trim());
                listener.on_tool_result(name, true, secs);
            }
            '\u{274c}' => {
                // ❌ Tool failure — format: "❌ tool_name (2s)"
                let (name, secs) = parse_tool_completion(rest.trim());
                listener.on_tool_result(name, false, secs);
            }
            '\u{1f4ac}' => {
                // 💬 Informational progress
                listener.on_progress(rest.trim().to_string());
            }
            _ => {
                // Unrecognised prefix -- treat as generic progress
                listener.on_progress(trimmed.to_string());
            }
        }
    }
}

/// Parses a tool completion string into `(tool_name, duration_seconds)`.
///
/// Expected format: `"tool_name (Ns)"` where `N` is an integer.
/// If no parenthesised duration is found, returns `(input, 0)`.
///
/// # Examples
///
/// ```text
/// "read_file (3s)" → ("read_file", 3)
/// "read_file"      → ("read_file", 0)
/// ```
fn parse_tool_completion(s: &str) -> (String, u64) {
    if let Some(paren_start) = s.rfind('(') {
        let name = s[..paren_start].trim();
        let inside = &s[paren_start + 1..];
        let secs = inside
            .trim_end_matches(')')
            .trim()
            .trim_end_matches('s')
            .trim()
            .parse::<u64>()
            .unwrap_or(0);
        (name.to_string(), secs)
    } else {
        (s.to_string(), 0)
    }
}

#[cfg(test)]
#[allow(clippy::unwrap_used)]
mod tests {
    use super::*;
    use std::sync::Mutex;

    /// A test listener that records all callback invocations as strings.
    ///
    /// Each event is formatted as `"callback_name:payload"` and pushed
    /// onto the internal vector for later assertion.
    struct RecordingListener {
        /// Accumulated event strings.
        events: Mutex<Vec<String>>,
    }

    impl RecordingListener {
        /// Creates a new empty recording listener.
        fn new() -> Self {
            Self {
                events: Mutex::new(Vec::new()),
            }
        }

        /// Returns a snapshot of all recorded events.
        fn events(&self) -> Vec<String> {
            self.events.lock().unwrap().clone()
        }
    }

    impl FfiSessionListener for RecordingListener {
        fn on_thinking(&self, text: String) {
            self.events.lock().unwrap().push(format!("thinking:{text}"));
        }

        fn on_response_chunk(&self, text: String) {
            self.events
                .lock()
                .unwrap()
                .push(format!("response_chunk:{text}"));
        }

        fn on_tool_start(&self, name: String, arguments_hint: String) {
            self.events
                .lock()
                .unwrap()
                .push(format!("tool_start:{name}:{arguments_hint}"));
        }

        fn on_tool_result(&self, name: String, success: bool, duration_secs: u64) {
            self.events
                .lock()
                .unwrap()
                .push(format!("tool_result:{name}:{success}:{duration_secs}"));
        }

        fn on_tool_output(&self, name: String, output: String) {
            self.events
                .lock()
                .unwrap()
                .push(format!("tool_output:{name}:{output}"));
        }

        fn on_progress(&self, message: String) {
            self.events
                .lock()
                .unwrap()
                .push(format!("progress:{message}"));
        }

        fn on_compaction(&self, summary: String) {
            self.events
                .lock()
                .unwrap()
                .push(format!("compaction:{summary}"));
        }

        fn on_complete(&self, full_response: String) {
            self.events
                .lock()
                .unwrap()
                .push(format!("complete:{full_response}"));
        }

        fn on_error(&self, error: String) {
            self.events.lock().unwrap().push(format!("error:{error}"));
        }

        fn on_cancelled(&self) {
            self.events.lock().unwrap().push("cancelled".to_string());
        }
    }

    // ── dispatch_delta tests ────────────────────────────────────────

    #[test]
    fn test_dispatch_thinking_first_round() {
        let listener = RecordingListener::new();
        let mut streaming = false;
        dispatch_delta("\u{1f914} Planning next steps\n", &listener, &mut streaming);
        assert!(!streaming);
        assert_eq!(listener.events(), vec!["thinking:Planning next steps"]);
    }

    #[test]
    fn test_dispatch_thinking_round_n() {
        let listener = RecordingListener::new();
        let mut streaming = false;
        dispatch_delta(
            "\u{1f914} Re-evaluating approach\n",
            &listener,
            &mut streaming,
        );
        assert_eq!(listener.events(), vec!["thinking:Re-evaluating approach"]);
    }

    #[test]
    fn test_dispatch_tool_start_with_hint() {
        let listener = RecordingListener::new();
        let mut streaming = false;
        dispatch_delta(
            "\u{23f3} read_file: /src/main.rs\n",
            &listener,
            &mut streaming,
        );
        assert_eq!(listener.events(), vec!["tool_start:read_file:/src/main.rs"]);
    }

    #[test]
    fn test_dispatch_tool_start_no_hint() {
        let listener = RecordingListener::new();
        let mut streaming = false;
        dispatch_delta("\u{23f3} list_files\n", &listener, &mut streaming);
        assert_eq!(listener.events(), vec!["tool_start:list_files:"]);
    }

    #[test]
    fn test_dispatch_tool_success() {
        let listener = RecordingListener::new();
        let mut streaming = false;
        dispatch_delta("\u{2705} read_file (3s)\n", &listener, &mut streaming);
        assert_eq!(listener.events(), vec!["tool_result:read_file:true:3"]);
    }

    #[test]
    fn test_dispatch_tool_failure() {
        let listener = RecordingListener::new();
        let mut streaming = false;
        dispatch_delta(
            "\u{274c} execute_command (12s)\n",
            &listener,
            &mut streaming,
        );
        assert_eq!(
            listener.events(),
            vec!["tool_result:execute_command:false:12"]
        );
    }

    #[test]
    fn test_dispatch_got_tool_calls() {
        let listener = RecordingListener::new();
        let mut streaming = false;
        dispatch_delta("\u{1f4ac} Got 3 tool calls\n", &listener, &mut streaming);
        assert_eq!(listener.events(), vec!["progress:Got 3 tool calls"]);
    }

    #[test]
    fn test_dispatch_sentinel_switches_to_response() {
        let listener = RecordingListener::new();
        let mut streaming = false;
        dispatch_delta(DRAFT_CLEAR_SENTINEL, &listener, &mut streaming);
        assert!(streaming);
        assert!(listener.events().is_empty());
    }

    #[test]
    fn test_dispatch_response_chunks_after_sentinel() {
        let listener = RecordingListener::new();
        let mut streaming = false;

        dispatch_delta(DRAFT_CLEAR_SENTINEL, &listener, &mut streaming);
        assert!(streaming);

        dispatch_delta("Hello, ", &listener, &mut streaming);
        dispatch_delta("world!", &listener, &mut streaming);

        assert_eq!(
            listener.events(),
            vec!["response_chunk:Hello, ", "response_chunk:world!",]
        );
    }

    // ── parse_tool_completion tests ─────────────────────────────────

    #[test]
    fn test_parse_tool_completion_with_seconds() {
        let (name, secs) = parse_tool_completion("read_file (3s)");
        assert_eq!(name, "read_file");
        assert_eq!(secs, 3);
    }

    #[test]
    fn test_parse_tool_completion_no_parens() {
        let (name, secs) = parse_tool_completion("list_files");
        assert_eq!(name, "list_files");
        assert_eq!(secs, 0);
    }
}
