/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Observer event bridge with UniFFI callback interface.
//!
//! Implements the upstream [`Observer`] trait to capture events from the
//! `ZeroClaw` daemon and forward them to Kotlin via a registered callback.
//! A fixed-capacity ring buffer retains recent events for on-demand queries.
//!
//! ## Observer Wiring
//!
//! [`AndroidObserver`] is currently wired into the heartbeat worker via
//! [`MultiObserver`]. The gateway and agent runner create their own
//! observers internally from config, so only heartbeat-triggered events
//! flow through this bridge. Wiring the remaining components requires
//! upstream support for a global observer registry or dependency injection.

use std::collections::VecDeque;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};

use zeroclaw::observability::traits::{Observer, ObserverEvent, ObserverMetric};

use crate::error::FfiError;

/// Ring buffer capacity for event history.
const EVENT_BUFFER_CAPACITY: usize = 500;

/// Callback interface that Kotlin implements to receive events.
///
/// The generated Kotlin class calls back into Rust from the UI thread,
/// but [`on_event`](FfiEventListener::on_event) is invoked from a Rust
/// background thread, so implementations must be thread-safe.
#[uniffi::export(callback_interface)]
pub trait FfiEventListener: Send + Sync {
    /// Called from a Rust background thread with a JSON-encoded event.
    fn on_event(&self, event_json: String);
}

/// Global listener slot.
static LISTENER: OnceLock<Mutex<Option<Arc<dyn FfiEventListener>>>> = OnceLock::new();

/// Global event ring buffer.
static EVENT_BUFFER: OnceLock<Mutex<VecDeque<String>>> = OnceLock::new();

/// Thread-safe event counter for unique IDs.
static EVENT_COUNTER: AtomicU64 = AtomicU64::new(0);

/// Returns a reference to the listener mutex, initialising on first access.
fn listener_slot() -> &'static Mutex<Option<Arc<dyn FfiEventListener>>> {
    LISTENER.get_or_init(|| Mutex::new(None))
}

/// Returns a reference to the event buffer mutex, initialising on first access.
fn event_buffer() -> &'static Mutex<VecDeque<String>> {
    EVENT_BUFFER.get_or_init(|| Mutex::new(VecDeque::with_capacity(EVENT_BUFFER_CAPACITY)))
}

/// Observer implementation that forwards events to the registered callback.
///
/// Events are serialised to JSON strings, stored in the ring buffer, and
/// optionally forwarded to a Kotlin listener if one is registered.
/// Metrics are intentionally not forwarded to conserve battery.
pub(crate) struct AndroidObserver;

impl Observer for AndroidObserver {
    fn record_event(&self, event: &ObserverEvent) {
        let id = EVENT_COUNTER.fetch_add(1, Ordering::Relaxed);
        let json = format_event_json(id, event);

        // Buffer the event.
        if let Ok(mut buf) = event_buffer().lock() {
            if buf.len() >= EVENT_BUFFER_CAPACITY {
                buf.pop_front();
            }
            buf.push_back(json.clone());
        }

        // Clone the Arc outside the lock to avoid holding the mutex across
        // the foreign callback (which could re-enter register/unregister).
        let maybe_listener = listener_slot()
            .lock()
            .ok()
            .and_then(|guard| guard.as_ref().map(Arc::clone));
        if let Some(listener) = maybe_listener {
            listener.on_event(json);
        }
    }

    fn record_metric(&self, _metric: &ObserverMetric) {
        // Metrics are intentionally not forwarded to Android (battery concern).
    }

    // Upstream `Observer` trait returns `&str` with an implicit lifetime;
    // clippy suggests `&'static str` but we cannot change the trait signature.
    #[allow(clippy::unnecessary_literal_bound)]
    fn name(&self) -> &str {
        "android"
    }
}

/// Registers a Kotlin-side event listener.
///
/// Only one listener can be registered at a time. A new listener replaces
/// the previous one. Accepts an [`Arc`] so the caller can convert from the
/// UniFFI `Box<dyn FfiEventListener>` at the FFI boundary.
pub(crate) fn register_event_listener_inner(
    listener: Arc<dyn FfiEventListener>,
) -> Result<(), FfiError> {
    let mut slot = listener_slot()
        .lock()
        .map_err(|_| FfiError::StateCorrupted {
            detail: "event listener mutex poisoned".into(),
        })?;
    *slot = Some(listener);
    Ok(())
}

/// Unregisters the current event listener.
///
/// After this call, events are still buffered but no longer forwarded.
pub(crate) fn unregister_event_listener_inner() -> Result<(), FfiError> {
    let mut slot = listener_slot()
        .lock()
        .map_err(|_| FfiError::StateCorrupted {
            detail: "event listener mutex poisoned".into(),
        })?;
    *slot = None;
    Ok(())
}

/// Returns the most recent events as a JSON array string.
///
/// Events are ordered chronologically (oldest first). The `limit`
/// parameter caps how many events to return.
pub(crate) fn get_recent_events_inner(limit: u32) -> Result<String, FfiError> {
    let buf = event_buffer()
        .lock()
        .map_err(|_| FfiError::StateCorrupted {
            detail: "event buffer mutex poisoned".into(),
        })?;
    let start = buf.len().saturating_sub(limit as usize);
    let json = buf.range(start..).cloned().collect::<Vec<_>>().join(",");
    Ok(format!("[{json}]"))
}

/// Serialises an [`ObserverEvent`] to a JSON string with metadata.
///
/// Uses manual formatting rather than serde to avoid per-event allocation
/// overhead from the `serde_json` value tree.
fn format_event_json(id: u64, event: &ObserverEvent) -> String {
    let now_ms = chrono::Utc::now().timestamp_millis();
    let (kind, data) = event_to_kind_and_data(event);
    format!(r#"{{"id":{id},"timestamp_ms":{now_ms},"kind":"{kind}","data":{data}}}"#)
}

/// Converts an [`ObserverEvent`] to a `(kind, data_json)` pair.
#[allow(clippy::match_same_arms)]
fn event_to_kind_and_data(event: &ObserverEvent) -> (&'static str, String) {
    match event {
        ObserverEvent::LlmRequest {
            provider,
            model,
            messages_count,
        } => (
            "llm_request",
            format!(
                r#"{{"provider":"{}","model":"{}","messages":{messages_count}}}"#,
                escape_json_string(provider),
                escape_json_string(model)
            ),
        ),
        ObserverEvent::LlmResponse {
            provider,
            model,
            duration,
            success,
            error_message,
        } => {
            let error_json = error_message.as_ref().map_or_else(
                || "null".to_string(),
                |e| format!(r#""{}""#, escape_json_string(e)),
            );
            (
                "llm_response",
                format!(
                    r#"{{"provider":"{}","model":"{}","duration_ms":{},"success":{success},"error":{error_json}}}"#,
                    escape_json_string(provider),
                    escape_json_string(model),
                    duration.as_millis()
                ),
            )
        }
        ObserverEvent::ToolCall {
            tool,
            duration,
            success,
        } => (
            "tool_call",
            format!(
                r#"{{"tool":"{}","duration_ms":{},"success":{success}}}"#,
                escape_json_string(tool),
                duration.as_millis()
            ),
        ),
        ObserverEvent::ToolCallStart { tool } => (
            "tool_call_start",
            format!(r#"{{"tool":"{}"}}"#, escape_json_string(tool)),
        ),
        ObserverEvent::ChannelMessage { channel, direction } => (
            "channel_message",
            format!(
                r#"{{"channel":"{}","direction":"{}"}}"#,
                escape_json_string(channel),
                escape_json_string(direction)
            ),
        ),
        ObserverEvent::Error { component, message } => (
            "error",
            format!(
                r#"{{"component":"{}","message":"{}"}}"#,
                escape_json_string(component),
                escape_json_string(message)
            ),
        ),
        ObserverEvent::HeartbeatTick => ("heartbeat_tick", "{}".to_string()),
        ObserverEvent::TurnComplete => ("turn_complete", "{}".to_string()),
        ObserverEvent::AgentStart { provider, model } => (
            "agent_start",
            format!(
                r#"{{"provider":"{}","model":"{}"}}"#,
                escape_json_string(provider),
                escape_json_string(model)
            ),
        ),
        ObserverEvent::AgentEnd {
            duration,
            tokens_used,
        } => {
            let tokens_json = tokens_used.map_or_else(|| "null".to_string(), |t| t.to_string());
            (
                "agent_end",
                format!(
                    r#"{{"duration_ms":{},"tokens":{tokens_json}}}"#,
                    duration.as_millis()
                ),
            )
        }
    }
}

/// Escapes a string for safe embedding inside a JSON string literal.
///
/// Handles double quotes, backslashes, common whitespace escapes
/// (`\n`, `\r`, `\t`), and all remaining control characters (`\x00`-`\x1F`)
/// via `\uXXXX` notation.
fn escape_json_string(s: &str) -> String {
    use std::fmt::Write;
    let mut out = String::with_capacity(s.len() + 4);
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 => {
                let _ = write!(out, "\\u{:04X}", c as u32);
            }
            c => out.push(c),
        }
    }
    out
}

#[cfg(test)]
#[allow(clippy::unwrap_used)]
mod tests {
    use super::*;
    use std::sync::Arc;
    use std::sync::atomic::AtomicUsize;
    use std::time::Duration;

    /// Drains any leftover events from the shared ring buffer.
    ///
    /// Tests share process-global state, so each test drains first.
    fn drain_buffer() {
        if let Ok(mut buf) = event_buffer().lock() {
            buf.clear();
        }
    }

    /// A test listener that counts callback invocations.
    struct CountingListener {
        count: Arc<AtomicUsize>,
    }

    impl FfiEventListener for CountingListener {
        fn on_event(&self, _event_json: String) {
            self.count.fetch_add(1, Ordering::SeqCst);
        }
    }

    #[test]
    fn test_register_unregister_roundtrip() {
        // Register a counting listener.
        let count = Arc::new(AtomicUsize::new(0));
        let listener: Arc<dyn FfiEventListener> = Arc::new(CountingListener {
            count: count.clone(),
        });
        register_event_listener_inner(listener).unwrap();

        // Fire an event — listener should receive it.
        let observer = AndroidObserver;
        observer.record_event(&ObserverEvent::HeartbeatTick);
        assert!(
            count.load(Ordering::SeqCst) >= 1,
            "listener should have received at least one event"
        );

        // Unregister, then fire another event and check that the count
        // does not increase. We wait briefly to let any in-flight
        // `record_event` calls from parallel tests that may have cloned
        // the Arc before our unregister to finish their callbacks.
        unregister_event_listener_inner().unwrap();
        std::thread::sleep(std::time::Duration::from_millis(10));
        let snapshot = count.load(Ordering::SeqCst);
        observer.record_event(&ObserverEvent::HeartbeatTick);
        std::thread::sleep(std::time::Duration::from_millis(10));

        // After unregister, new events from this test must not reach
        // the listener. The snapshot already accounts for any straggler
        // callbacks from parallel tests that cloned the Arc pre-unregister.
        assert_eq!(
            count.load(Ordering::SeqCst),
            snapshot,
            "listener should not receive events after unregister"
        );
    }

    #[test]
    fn test_get_recent_events_returns_valid_json() {
        drain_buffer();

        let observer = AndroidObserver;
        observer.record_event(&ObserverEvent::HeartbeatTick);
        observer.record_event(&ObserverEvent::TurnComplete);

        let json_str = get_recent_events_inner(10).unwrap();
        let parsed: serde_json::Value = serde_json::from_str(&json_str).unwrap();
        let arr = parsed.as_array().unwrap();
        assert_eq!(arr.len(), 2);

        // Verify structure of each event.
        for event in arr {
            assert!(event.get("id").is_some());
            assert!(event.get("timestamp_ms").is_some());
            assert!(event.get("kind").is_some());
            assert!(event.get("data").is_some());
        }
    }

    #[test]
    fn test_get_recent_events_respects_limit() {
        drain_buffer();

        let observer = AndroidObserver;
        for _ in 0..5 {
            observer.record_event(&ObserverEvent::HeartbeatTick);
        }

        let json_str = get_recent_events_inner(3).unwrap();
        let parsed: serde_json::Value = serde_json::from_str(&json_str).unwrap();
        assert_eq!(parsed.as_array().unwrap().len(), 3);
    }

    #[test]
    fn test_format_event_json_llm_response() {
        let event = ObserverEvent::LlmResponse {
            provider: "openai".into(),
            model: "gpt-4".into(),
            duration: Duration::from_millis(150),
            success: true,
            error_message: None,
        };
        let json_str = format_event_json(42, &event);
        let parsed: serde_json::Value = serde_json::from_str(&json_str).unwrap();
        assert_eq!(parsed["id"], 42);
        assert_eq!(parsed["kind"], "llm_response");
        assert_eq!(parsed["data"]["provider"], "openai");
        assert_eq!(parsed["data"]["success"], true);
        assert!(parsed["data"]["error"].is_null());
    }

    #[test]
    fn test_format_event_json_llm_response_with_error() {
        let event = ObserverEvent::LlmResponse {
            provider: "anthropic".into(),
            model: "claude-sonnet-4".into(),
            duration: Duration::from_secs(1),
            success: false,
            error_message: Some("rate limited".into()),
        };
        let json_str = format_event_json(99, &event);
        let parsed: serde_json::Value = serde_json::from_str(&json_str).unwrap();
        assert_eq!(parsed["kind"], "llm_response");
        assert_eq!(parsed["data"]["error"], "rate limited");
        assert_eq!(parsed["data"]["success"], false);
    }

    #[test]
    fn test_format_event_json_tool_call() {
        let event = ObserverEvent::ToolCall {
            tool: "shell".into(),
            duration: Duration::from_millis(250),
            success: true,
        };
        let json_str = format_event_json(7, &event);
        let parsed: serde_json::Value = serde_json::from_str(&json_str).unwrap();
        assert_eq!(parsed["kind"], "tool_call");
        assert_eq!(parsed["data"]["tool"], "shell");
        assert_eq!(parsed["data"]["duration_ms"], 250);
    }

    #[test]
    fn test_format_event_json_heartbeat_tick() {
        let json_str = format_event_json(0, &ObserverEvent::HeartbeatTick);
        let parsed: serde_json::Value = serde_json::from_str(&json_str).unwrap();
        assert_eq!(parsed["kind"], "heartbeat_tick");
        assert_eq!(parsed["data"], serde_json::json!({}));
    }

    #[test]
    fn test_format_event_json_error_with_quotes() {
        let event = ObserverEvent::Error {
            component: "gateway".into(),
            message: r#"failed to parse "config""#.into(),
        };
        let json_str = format_event_json(1, &event);
        let parsed: serde_json::Value = serde_json::from_str(&json_str).unwrap();
        assert_eq!(parsed["kind"], "error");
        assert!(
            parsed["data"]["message"]
                .as_str()
                .unwrap()
                .contains("config")
        );
    }

    #[test]
    fn test_format_event_json_agent_end() {
        let event = ObserverEvent::AgentEnd {
            duration: Duration::from_secs(5),
            tokens_used: Some(1200),
        };
        let json_str = format_event_json(3, &event);
        let parsed: serde_json::Value = serde_json::from_str(&json_str).unwrap();
        assert_eq!(parsed["kind"], "agent_end");
        assert_eq!(parsed["data"]["tokens"], 1200);
        assert_eq!(parsed["data"]["duration_ms"], 5000);
    }

    #[test]
    fn test_format_event_json_agent_end_no_tokens() {
        let event = ObserverEvent::AgentEnd {
            duration: Duration::from_millis(100),
            tokens_used: None,
        };
        let json_str = format_event_json(4, &event);
        let parsed: serde_json::Value = serde_json::from_str(&json_str).unwrap();
        assert!(parsed["data"]["tokens"].is_null());
    }

    #[test]
    fn test_ring_buffer_drops_oldest() {
        drain_buffer();

        let observer = AndroidObserver;
        for _ in 0..(EVENT_BUFFER_CAPACITY + 50) {
            observer.record_event(&ObserverEvent::HeartbeatTick);
        }

        let buf = event_buffer().lock().unwrap();
        assert_eq!(buf.len(), EVENT_BUFFER_CAPACITY);
    }

    #[test]
    fn test_android_observer_name() {
        let observer = AndroidObserver;
        assert_eq!(observer.name(), "android");
    }

    #[test]
    fn test_escape_json_string() {
        assert_eq!(escape_json_string(r#"a"b"#), r#"a\"b"#);
        assert_eq!(escape_json_string(r"a\b"), r"a\\b");
        assert_eq!(escape_json_string("plain"), "plain");
        assert_eq!(escape_json_string("line\nbreak"), "line\\nbreak");
        assert_eq!(escape_json_string("car\rret"), "car\\rret");
        assert_eq!(escape_json_string("tab\there"), "tab\\there");
        assert_eq!(escape_json_string("null\x00byte"), "null\\u0000byte");
        assert_eq!(escape_json_string("bell\x07ring"), "bell\\u0007ring");
    }

    #[test]
    fn test_format_event_json_llm_request() {
        let event = ObserverEvent::LlmRequest {
            provider: "openai".into(),
            model: "gpt-4o".into(),
            messages_count: 5,
        };
        let json_str = format_event_json(10, &event);
        let parsed: serde_json::Value = serde_json::from_str(&json_str).unwrap();
        assert_eq!(parsed["kind"], "llm_request");
        assert_eq!(parsed["data"]["provider"], "openai");
        assert_eq!(parsed["data"]["model"], "gpt-4o");
        assert_eq!(parsed["data"]["messages"], 5);
    }

    #[test]
    fn test_format_event_json_tool_call_start() {
        let event = ObserverEvent::ToolCallStart {
            tool: "web_search".into(),
        };
        let json_str = format_event_json(11, &event);
        let parsed: serde_json::Value = serde_json::from_str(&json_str).unwrap();
        assert_eq!(parsed["kind"], "tool_call_start");
        assert_eq!(parsed["data"]["tool"], "web_search");
    }

    #[test]
    fn test_format_event_json_channel_message() {
        let event = ObserverEvent::ChannelMessage {
            channel: "discord".into(),
            direction: "inbound".into(),
        };
        let json_str = format_event_json(12, &event);
        let parsed: serde_json::Value = serde_json::from_str(&json_str).unwrap();
        assert_eq!(parsed["kind"], "channel_message");
        assert_eq!(parsed["data"]["channel"], "discord");
        assert_eq!(parsed["data"]["direction"], "inbound");
    }

    #[test]
    fn test_format_event_json_agent_start() {
        let event = ObserverEvent::AgentStart {
            provider: "anthropic".into(),
            model: "claude-sonnet-4".into(),
        };
        let json_str = format_event_json(13, &event);
        let parsed: serde_json::Value = serde_json::from_str(&json_str).unwrap();
        assert_eq!(parsed["kind"], "agent_start");
        assert_eq!(parsed["data"]["provider"], "anthropic");
        assert_eq!(parsed["data"]["model"], "claude-sonnet-4");
    }

    #[test]
    fn test_format_event_json_turn_complete() {
        let json_str = format_event_json(14, &ObserverEvent::TurnComplete);
        let parsed: serde_json::Value = serde_json::from_str(&json_str).unwrap();
        assert_eq!(parsed["kind"], "turn_complete");
        assert_eq!(parsed["data"], serde_json::json!({}));
    }

    #[test]
    fn test_get_recent_events_empty() {
        drain_buffer();
        let json_str = get_recent_events_inner(10).unwrap();
        assert_eq!(json_str, "[]");
    }
}
