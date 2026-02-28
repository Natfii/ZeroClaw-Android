# Live Agent Session for Terminal REPL

**Date:** 2026-02-28
**Status:** Approved
**Branch:** `feature/live-agent-session`

## Problem

The terminal/REPL currently sends stale blocking messages via `evalRepl()` through the Rhai engine. Messages go to the gateway as one-shot calls with no conversation history, no streaming, and no tool execution. The user sees a loading spinner and then a complete response. This is not a real agent experience.

## Goal

Turn the REPL into a live agent session that mirrors ZeroClaw's full `run_tool_call_loop()` behavior: persistent conversation history, streaming responses, thinking/reasoning display, tool execution with full transparency, and auto-compaction. Users who don't want to set up a bot on another platform get a first-class chat experience directly in the app.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| History owner | Rust-side session | Matches upstream exactly. `Vec<ChatMessage>` in Rust memory. |
| Agent scope | Full agent loop | Memory, tools, multi-turn reasoning via `run_tool_call_loop()` |
| Tool visibility | Full transparency | Show tool starts, results, and output |
| Slash commands | Dual path | `/status`, `/cost`, etc. stay on Rhai (instant). Chat messages go to agent session. |
| Session lifecycle | Implicit + auto-compaction | Single session, compaction at 50 messages (keep 20), progress message shown |
| Callback design | Rich typed callbacks | Separate methods for thinking, tools, progress, compaction |
| Approach | Direct `run_tool_call_loop()` wrapper | Minimal custom code, upstream bug fixes flow through automatically |

## Architecture

### Approach: Direct Wrapper

Wrap upstream's `run_tool_call_loop()` in a new `session.rs` FFI module. The function is `pub(crate)` upstream, so we replicate it in our FFI crate (~30-40 LOC of delegation, not reimplementation). A bridge task reads `on_delta` strings and parses them into typed listener callbacks.

Setup logic from `agent::run()` is replicated for session initialization (~60 LOC): building provider, tools registry, memory, observer, and system prompt from daemon config.

Compaction and history trimming are called after each turn (same as upstream's interactive REPL path in `agent::run()`).

## FFI Surface (`session.rs`)

### Session Struct

```rust
struct Session {
    history: Vec<ChatMessage>,
    config: Config,
    cancellation_token: CancellationToken,
    tools_registry: Vec<Box<dyn Tool>>,
    provider: Box<dyn Provider>,
    observer: Arc<dyn Observer>,  // MultiObserver wrapping real + FFI observer
    memory: Option<Arc<dyn Memory>>,
    system_prompt: String,
    model: String,
    temperature: f64,
}
```

Stored in `static SESSION: Mutex<Option<Session>>`.

### Exported Functions

All `#[uniffi::export]`, all wrapped in `catch_unwind`, all return `Result<T, FfiError>`.

| Function | Signature | Purpose |
|----------|-----------|---------|
| `session_start()` | `() -> Result<(), FfiError>` | Create session from daemon config. Builds provider, tools, memory, observer, system prompt. |
| `session_seed(messages)` | `(Vec<SessionMessage>) -> Result<(), FfiError>` | Seed history from Room entries on cold start. Capped at 20 entries. |
| `session_send(message, listener)` | `(String, Box<dyn FfiSessionListener>) -> Result<(), FfiError>` | Full agent loop turn. Spawns `run_tool_call_loop` on tokio runtime with `on_delta` channel. Bridge task parses progress strings into typed callbacks. Calls compaction + trim after loop returns. |
| `session_send_vision(message, images, mimes, listener)` | `(String, Vec<String>, Vec<String>, Box<dyn FfiSessionListener>) -> Result<(), FfiError>` | Same as `session_send` but with base64 images and MIME types injected into the user message. |
| `session_cancel()` | `() -> Result<(), FfiError>` | Cancel current turn via `CancellationToken`. |
| `session_clear()` | `() -> Result<(), FfiError>` | Reset history to system prompt only. |
| `session_history()` | `() -> Result<Vec<SessionMessage>, FfiError>` | Return current history for Kotlin to persist to Room. |
| `session_destroy()` | `() -> Result<(), FfiError>` | Drop the session. |

### UniFFI Types

```rust
#[derive(uniffi::Record)]
pub struct SessionMessage {
    pub role: String,
    pub content: String,
}
```

### Callback Interface

```rust
#[uniffi::export(callback_interface)]
pub trait FfiSessionListener: Send + Sync {
    /// Model is producing thinking/reasoning tokens.
    fn on_thinking(text: String);

    /// Streaming response chunk (final answer, progressive).
    fn on_response_chunk(text: String);

    /// Agent is about to execute a tool.
    fn on_tool_start(name: String, arguments_hint: String);

    /// Tool execution completed (from Observer events).
    fn on_tool_result(name: String, success: bool, duration_secs: u64);

    /// Tool output extracted from history after tool iteration.
    fn on_tool_output(name: String, output: String);

    /// Progress message (e.g., "Thinking (round 2)...").
    fn on_progress(message: String);

    /// History was auto-compacted.
    fn on_compaction(summary: String);

    /// Turn completed successfully.
    fn on_complete(full_response: String);

    /// Error occurred during the turn.
    fn on_error(error: String);

    /// Turn was cancelled by user.
    fn on_cancelled();
}
```

### on_delta String Parsing Rules

| `on_delta` string pattern | Callback |
|---------------------------|----------|
| `\u{1f914} Thinking...\n` | `on_progress("Thinking...")` |
| `\u{1f914} Thinking (round N)...\n` | `on_progress("Thinking (round N)...")` |
| `\u{23f3} TOOL_NAME\n` | `on_tool_start(TOOL_NAME, "")` |
| `\u{23f3} TOOL_NAME: HINT\n` | `on_tool_start(TOOL_NAME, HINT)` |
| `\u{2705} TOOL_NAME (Ns)\n` | `on_tool_result(TOOL_NAME, true, N)` |
| `\u{274c} TOOL_NAME (Ns)\n` | `on_tool_result(TOOL_NAME, false, N)` |
| `\u{1f4ac} Got N tool call(s) (Ns)\n` | `on_progress("Got N tool call(s) (Ns)")` |
| `\x00CLEAR\x00` | Internal: switch from progress to response streaming mode |
| Subsequent text chunks | `on_response_chunk(chunk)` |

### Observer Integration

Create an `FfiObserver` implementing the upstream `Observer` trait. Wrap it with the daemon's existing observer using `MultiObserver`. The `FfiObserver` relays `ToolCall { tool, duration, success }` events to the listener for real-time tool completion notifications.

### Tool Output Extraction

After each iteration of `run_tool_call_loop` (when the loop returns or between iterations), diff the history to find new `ChatMessage { role: "tool", ... }` entries. Extract tool name and output content, fire `on_tool_output(name, output)` for each.

Since we're wrapping `run_tool_call_loop` and it returns only after the full turn, tool outputs are extracted post-turn from the history. The `on_tool_start` and `on_tool_result` callbacks provide real-time progress during execution.

## Kotlin Side

### Dual-Path Dispatch

```
User input
  -> CommandRegistry.parseAndTranslate()
     -> Recognized slash command -> executeRhai() [unchanged, instant]
     -> RhaiExpression -> executeRhai() [unchanged]
     -> ChatMessage -> executeAgentTurn() [NEW]
        -> session_send(message, KotlinSessionListener)
```

### StreamingState Extension

```kotlin
enum class StreamingPhase {
    IDLE, THINKING, TOOL_EXECUTING, RESPONDING, COMPACTING, COMPLETE, CANCELLED, ERROR
}

data class StreamingState(
    val phase: StreamingPhase = StreamingPhase.IDLE,
    val thinkingText: String = "",
    val responseText: String = "",
    val errorMessage: String? = null,
    val activeTools: List<ToolProgress> = emptyList(),
    val toolResults: List<ToolResultEntry> = emptyList(),
    val progressMessage: String? = null,
)

data class ToolProgress(val name: String, val hint: String)
data class ToolResultEntry(
    val name: String,
    val success: Boolean,
    val durationSecs: Long,
    val output: String,
)
```

### KotlinSessionListener

Implements `FfiSessionListener`. Updates `_streamingState: MutableStateFlow<StreamingState>` via `Dispatchers.Main.immediate` from the callback thread. Each callback method atomically updates the relevant field.

### UI Changes

**ThinkingCard (merged with tool activity):**

The existing `ThinkingCard` is extended to show tool activity below the thinking text. Layout:

```
+---------------------------------------------+
| [BrailleSpinner] Thinking (round 2)  [Cancel]|
|                                               |
| ...reasoning tokens (scrollable)...          |
|                                               |
| [hourglass] web_search: weather NYC           |
| [check] memory_recall (1s)                    |
+---------------------------------------------+
```

- Header: spinner + phase label + cancel button (existing)
- Body: thinking text (existing, scrollable)
- Footer: tool progress list (NEW — active tools with spinner, completed with icon + duration)

**StreamingResponseBlock:**

Reuses `TerminalBlock.Response` styling. Grows as `on_response_chunk` callbacks arrive. When `on_complete` fires, the streaming block is finalized and converted to a standard `TerminalBlock.Response` in the block list.

**CompactionCard:**

Brief inline message in ThinkingCard footer: "Compacting history..." then "History compacted" with summary. Shown only during `StreamingPhase.COMPACTING`.

### Persistence

- Room `TerminalEntryEntity` stores display history (input/response pairs)
- On `on_complete`: persist user input + full response to Room
- Tool calls visible only during streaming, not persisted as separate Room entries
- `session_history()` available for Kotlin to snapshot Rust-side history periodically

### Session Seeding (Cold Start)

1. `session_start()` creates fresh session
2. Query Room for last 20 entries (input + response pairs)
3. Convert to `Vec<SessionMessage>` with role mapping
4. `session_seed(messages)` appends to Rust history
5. Compaction runs if seeded history exceeds threshold

## Error Recovery

| Failure | Recovery |
|---------|----------|
| Provider API error | `on_error(message)`. History rolled back (user message removed). User can retry. |
| Tool execution error | Not a turn failure. Tool result `success=false`, LLM adjusts. `on_tool_result(name, false, ...)` fired. Loop continues. |
| User cancellation | `on_cancelled()`. Partial history preserved. CancellationToken reset for next turn. |
| Compaction failure | Logged, not fatal. `trim_history` hard-caps as safety net. |
| Session mutex poisoned | `session_start()` replaces poisoned session. Old state lost. |
| Process death | Fresh `session_start()` + `session_seed()` from Room on next launch. |

### History Rollback

```rust
let history_len_before = session.history.len();
session.history.push(ChatMessage::user(message));

match run_tool_call_loop(...).await {
    Ok(response) => {
        // auto_compact_history + trim_history
        listener.on_complete(response);
    }
    Err(e) if is_cancellation(&e) => {
        listener.on_cancelled();
    }
    Err(e) => {
        session.history.truncate(history_len_before);
        listener.on_error(sanitize_error(&e));
    }
}
```

## Upstream Constants (Must Match)

| Constant | Value | Source |
|----------|-------|--------|
| `DEFAULT_MAX_TOOL_ITERATIONS` | 10 | `loop_.rs:89` |
| `DEFAULT_MAX_HISTORY_MESSAGES` | 50 | `loop_.rs:91` |
| `COMPACTION_KEEP_RECENT_MESSAGES` | 20 | `loop_.rs:93` |
| `COMPACTION_MAX_SOURCE_CHARS` | 12,000 | `loop_.rs:97` |
| `COMPACTION_MAX_SUMMARY_CHARS` | 2,000 | `loop_.rs:100` |
| `STREAM_CHUNK_MIN_CHARS` | 80 | `loop_.rs:85` |
| `DRAFT_CLEAR_SENTINEL` | `"\x00CLEAR\x00"` | `loop_.rs:107` |
| `PROGRESS_MIN_INTERVAL_MS` | 500 | `loop_.rs:103` |
| Compaction summarizer temperature | 0.2 | `loop_.rs:auto_compact_history` |
| Compaction system prompt | "You are a conversation compaction engine..." | `loop_.rs:218` |

## Files to Create/Modify

### Rust (zeroclaw-ffi)

| File | Action | Purpose |
|------|--------|---------|
| `src/session.rs` | CREATE | Session struct, FFI exports, on_delta bridge, compaction wrapper |
| `src/observer.rs` | CREATE | FfiObserver implementing upstream Observer trait |
| `src/lib.rs` | MODIFY | Add `mod session; mod observer;` and `#[uniffi::export]` wrappers |
| `src/error.rs` | MODIFY | Add session-specific error variants if needed |

### Kotlin (app)

| File | Action | Purpose |
|------|--------|---------|
| `StreamingState.kt` | MODIFY | Add `TOOL_EXECUTING`, `COMPACTING` phases; `ToolProgress`, `ToolResultEntry`; extend `StreamingState` |
| `ThinkingCard.kt` | MODIFY | Add tool progress footer section |
| `TerminalViewModel.kt` | MODIFY | Add `_streamingState`, `executeAgentTurn()`, `KotlinSessionListener`, session lifecycle |
| `TerminalScreen.kt` | MODIFY | Wire `streamingState`, show ThinkingCard + StreamingResponseBlock |
| `TerminalBlock.kt` | MODIFY | Add `StreamingResponse` variant if needed |

### Tests

| File | Action | Purpose |
|------|--------|---------|
| `src/session.rs` (tests module) | CREATE | on_delta parsing, history management, error recovery |
| `TerminalViewModelTest.kt` | MODIFY | Dual-path dispatch, session lifecycle, streaming state transitions |
