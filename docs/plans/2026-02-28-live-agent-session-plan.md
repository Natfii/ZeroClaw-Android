# Live Agent Session Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Turn the terminal REPL into a live agent session with streaming responses, tool execution transparency, auto-compaction, and session seeding.

**Architecture:** New `session.rs` FFI module wraps upstream's `run_tool_call_loop()` with `on_delta` string parsing into typed `FfiSessionListener` callbacks. Rust owns `Vec<ChatMessage>` history. Kotlin dual-paths: slash commands stay on Rhai, chat messages go through the agent session. `ThinkingCard` extended to show tool activity.

**Tech Stack:** Rust (tokio, tokio-util CancellationToken, UniFFI 0.29), Kotlin (Jetpack Compose, StateFlow, Room)

**Design doc:** `docs/plans/2026-02-28-live-agent-session-design.md`

---

## Task 1: Add `tokio-util` Dependency

**Files:**
- Modify: `zeroclaw-android/zeroclaw-ffi/Cargo.toml` (line 24, after tokio)

**Step 1: Add dependency**

Add `tokio-util` to `[dependencies]` in `Cargo.toml`:

```toml
tokio-util = { version = "0.7", features = ["rt"] }
```

This provides `CancellationToken` for session cancellation.

**Step 2: Verify it compiles**

Run:
```bash
cd zeroclaw-android && cargo check -p zeroclaw-ffi
```
Expected: compiles without errors.

**Step 3: Commit**

```bash
git add zeroclaw-android/zeroclaw-ffi/Cargo.toml zeroclaw-android/Cargo.lock
git commit -m "build(ffi): add tokio-util for CancellationToken"
```

---

## Task 2: Create `SessionMessage` UniFFI Record and `FfiSessionListener` Callback Interface

**Files:**
- Create: `zeroclaw-android/zeroclaw-ffi/src/session.rs`
- Modify: `zeroclaw-android/zeroclaw-ffi/src/lib.rs` (line 28, add `mod session;`)

**Step 1: Write the session module with types and listener trait**

Create `zeroclaw-android/zeroclaw-ffi/src/session.rs` with:
- Module-level doc comment describing the session lifecycle
- `static SESSION: Mutex<Option<Session>>` for global session state
- `Session` struct with: `history: Vec<ChatMessage>`, `config: Config`, `system_prompt: String`, `model: String`, `temperature: f64`, `provider_name: String`
- `SessionMessage` UniFFI record with `role: String`, `content: String`
- `FfiSessionListener` callback interface with methods: `on_thinking`, `on_response_chunk`, `on_tool_start`, `on_tool_result`, `on_tool_output`, `on_progress`, `on_compaction`, `on_complete`, `on_error`, `on_cancelled`

**Step 2: Add `mod session;` to lib.rs** after line 28

**Step 3: Verify compilation**

```bash
cd zeroclaw-android && cargo check -p zeroclaw-ffi
```

**Step 4: Commit**

```bash
git add zeroclaw-android/zeroclaw-ffi/src/session.rs zeroclaw-android/zeroclaw-ffi/src/lib.rs
git commit -m "feat(ffi): add session types and FfiSessionListener callback interface"
```

---

## Task 3: Implement `on_delta` String Parser with Tests

**Files:**
- Modify: `zeroclaw-android/zeroclaw-ffi/src/session.rs`

**Step 1: Write the parser and tests**

Add to `session.rs`:
- `const DRAFT_CLEAR_SENTINEL: &str = "\x00CLEAR\x00"`
- `fn dispatch_delta(delta, listener, streaming_response_flag)` that parses upstream's `on_delta` strings by emoji prefix and dispatches to the appropriate listener callback
- `fn parse_tool_completion(s) -> (name, seconds)` helper
- `#[cfg(test)] mod tests` with `RecordingListener` and tests for: thinking first round, thinking round N, tool start with/without hint, tool success/failure, got tool calls, sentinel switches to response mode, response chunks after sentinel, parse_tool_completion with/without parens

**Parsing rules (must match upstream `loop_.rs` exactly):**

| Emoji prefix | Callback |
|-------------|----------|
| `\u{1f914}` (thinking face) | `on_progress(rest.trim())` |
| `\u{23f3}` (hourglass) | `on_tool_start(name, hint)` — split on `:` |
| `\u{2705}` (check mark) | `on_tool_result(name, true, secs)` |
| `\u{274c}` (cross mark) | `on_tool_result(name, false, secs)` |
| `\u{1f4ac}` (speech bubble) | `on_progress(rest.trim())` |
| `\x00CLEAR\x00` | Set `streaming_response = true`, no callback |
| After CLEAR sentinel | `on_response_chunk(delta)` |

**Step 2: Run tests**

```bash
cd zeroclaw-android && cargo test -p zeroclaw-ffi -- session::tests --nocapture
```
Expected: all tests pass.

**Step 3: Commit**

```bash
git add zeroclaw-android/zeroclaw-ffi/src/session.rs
git commit -m "feat(ffi): add on_delta string parser with tests"
```

---

## Task 4: Implement `session_start_inner` and Runtime Helpers

**Files:**
- Modify: `zeroclaw-android/zeroclaw-ffi/src/session.rs`
- Modify: `zeroclaw-android/zeroclaw-ffi/src/runtime.rs` (after line 105)

**Step 1: Add helpers to runtime.rs**

After `with_daemon_config` (line 105), add:
- `pub(crate) fn clone_daemon_config() -> Result<Config, FfiError>` — calls `with_daemon_config(|c| c.clone())`
- `pub(crate) fn clone_daemon_memory() -> Result<Arc<dyn Memory>, FfiError>` — clones the Arc from DaemonState

**Step 2: Implement session_start_inner in session.rs**

Mirrors upstream `agent::run()` lines 2726-2973:
1. Clone daemon config via `clone_daemon_config()`
2. Resolve `provider_name` from `config.default_provider` (default: `"openrouter"`)
3. Resolve `model` from `config.default_model` (default: `"anthropic/claude-sonnet-4"`)
4. Load skills via `zeroclaw::skills::load_skills_with_config()`
5. Build tool descriptions (subset relevant to Android)
6. Check native tools support by creating a temporary provider
7. Build system prompt via `zeroclaw::channels::build_system_prompt_with_mode()`
8. Initialize history with `vec![ChatMessage::system(&system_prompt)]`
9. Store in `SESSION` mutex

**Step 3: Verify compilation**

```bash
cd zeroclaw-android && cargo check -p zeroclaw-ffi
```

**Step 4: Commit**

```bash
git add zeroclaw-android/zeroclaw-ffi/src/session.rs zeroclaw-android/zeroclaw-ffi/src/runtime.rs
git commit -m "feat(ffi): implement session_start_inner with daemon config setup"
```

---

## Task 5: Implement `session_send_inner` — Core Agent Loop Turn

**Files:**
- Modify: `zeroclaw-android/zeroclaw-ffi/src/session.rs`

This is the most complex task. It must:
1. Validate message size (max 1 MiB)
2. Create a `CancellationToken` and store in `static CANCEL_TOKEN`
3. Take history out of `SESSION` mutex (for async work)
4. Build memory context via `zeroclaw::agent::build_context()` (check visibility — if private, replicate)
5. Enrich message with memory context + timestamp (matches `loop_.rs:3002-3016`)
6. Append `ChatMessage::user(enriched)` to history
7. Create provider via `create_routed_provider_with_options()`
8. Create tools via `all_tools_with_runtime()`
9. Create observer via `create_observer()`
10. Set up `tokio::sync::mpsc::channel::<String>(64)` for `on_delta`
11. Spawn bridge task that reads from `delta_rx` and calls `dispatch_delta()`
12. Call `run_tool_call_loop()` (or `process_message` if visibility blocks us)
13. Wait for bridge task to complete
14. On success: extract tool outputs from history diff, run compaction, fire `on_complete`
15. On cancel: keep partial history, fire `on_cancelled`
16. On error: truncate history to pre-turn state, fire `on_error`
17. Put history back into `SESSION` mutex
18. Clear `CANCEL_TOKEN`

**Critical: `run_tool_call_loop` visibility check**

```bash
cd zeroclaw-android && cargo check -p zeroclaw-ffi 2>&1 | head -20
```

If `run_tool_call_loop` is inaccessible (`pub(crate)` in upstream), use `process_message()` (which IS `pub`) as fallback. `process_message` creates a fresh history per call, so we'd need to adapt it to accept our existing history. If neither works, replicate the loop (~40 LOC of delegation to `provider.chat()` + tool execution).

**Step 1: Implement the function**

Add `session_send_inner` with all the steps above.

Also add helpers:
- `fn put_history_back(history) -> Result<(), FfiError>` — puts history back into SESSION
- `fn extract_tool_outputs(history, len_before, listener)` — diffs history for tool results

**Step 2: Verify compilation**

```bash
cd zeroclaw-android && cargo check -p zeroclaw-ffi
```

**Step 3: Commit**

```bash
git add zeroclaw-android/zeroclaw-ffi/src/session.rs
git commit -m "feat(ffi): implement session_send_inner with agent loop and delta bridge"
```

---

## Task 6: Implement Remaining Session Functions and Compaction

**Files:**
- Modify: `zeroclaw-android/zeroclaw-ffi/src/session.rs`

**Step 1: Add lifecycle functions**

- `session_seed_inner(messages: Vec<SessionMessage>)` — append to history, cap at 20 entries
- `session_cancel_inner()` — cancel via `CANCEL_TOKEN`
- `session_clear_inner()` — reset history to `[system_prompt]`
- `session_history_inner()` — return `Vec<SessionMessage>` from history
- `session_destroy_inner()` — set `SESSION` to `None`

**Step 2: Replicate compaction and trim**

Replicate upstream's `auto_compact_history` (~40 LOC) and `trim_history` (~10 LOC) from `loop_.rs:148-240`. Must match upstream constants exactly:
- `DEFAULT_MAX_HISTORY_MESSAGES = 50`
- `COMPACTION_KEEP_RECENT = 20`
- `COMPACTION_MAX_SOURCE_CHARS = 12_000`
- `COMPACTION_MAX_SUMMARY_CHARS = 2_000`
- Compaction summarizer system prompt (exact text from line 218)
- Temperature: `0.2`

**Step 3: Verify compilation and tests**

```bash
cd zeroclaw-android && cargo check -p zeroclaw-ffi
cd zeroclaw-android && cargo test -p zeroclaw-ffi --lib
```

**Step 4: Commit**

```bash
git add zeroclaw-android/zeroclaw-ffi/src/session.rs
git commit -m "feat(ffi): implement session lifecycle functions and compaction"
```

---

## Task 7: Add `#[uniffi::export]` Wrappers in `lib.rs`

**Files:**
- Modify: `zeroclaw-android/zeroclaw-ffi/src/lib.rs` (after line 835)

**Step 1: Add 7 session FFI exports**

After the `cancel_streaming` export, add exports for: `session_start`, `session_seed`, `session_send`, `session_cancel`, `session_clear`, `session_history`, `session_destroy`.

All must follow the existing pattern:
- `#[uniffi::export]`
- `std::panic::catch_unwind(|| inner_fn())` (use `AssertUnwindSafe` for closures capturing args)
- Return `Result<T, FfiError>` with `InternalPanic` on caught panic
- Use `panic_detail()` helper (line 39)
- `session_send` takes `Box<dyn FfiSessionListener>` and converts to `Arc::from(listener)`

**Step 2: Verify compilation and run all Rust tests**

```bash
cd zeroclaw-android && cargo check -p zeroclaw-ffi
cd zeroclaw-android && cargo test -p zeroclaw-ffi --lib
```

**Step 3: Commit**

```bash
git add zeroclaw-android/zeroclaw-ffi/src/lib.rs
git commit -m "feat(ffi): add uniffi session exports with catch_unwind"
```

---

## Task 8: Extend `StreamingState.kt`

**Files:**
- Modify: `app/src/main/java/com/zeroclaw/android/ui/screen/terminal/StreamingState.kt`

**Step 1: Add new phases and data classes**

- Add `TOOL_EXECUTING` and `COMPACTING` to `StreamingPhase` enum (with KDoc)
- Add `ToolProgress` data class: `name: String`, `hint: String` (with KDoc)
- Add `ToolResultEntry` data class: `name: String`, `success: Boolean`, `durationSecs: Long`, `output: String` (with KDoc)
- Extend `StreamingState` with: `activeTools: List<ToolProgress>`, `toolResults: List<ToolResultEntry>`, `progressMessage: String?`

**Step 2: Verify compilation**

```bash
./gradlew :app:compileDebugKotlin
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/zeroclaw/android/ui/screen/terminal/StreamingState.kt
git commit -m "feat(ui): extend StreamingState with tool phases and progress models"
```

---

## Task 9: Extend `ThinkingCard.kt` with Tool Activity Footer

**Files:**
- Modify: `app/src/main/java/com/zeroclaw/android/ui/screen/terminal/ThinkingCard.kt`

**Step 1: Update function signature**

Add parameters: `activeTools: List<ToolProgress> = emptyList()`, `completedTools: List<ToolResultEntry> = emptyList()`

**Step 2: Add tool activity footer**

After the thinking text section (inside the `Column`), add:
- `HorizontalDivider` (only when tools are present)
- For each active tool: `Row` with `BrailleSpinner` + tool name/hint text
- For each completed tool: `Row` with check/cross emoji + name + duration

Follow existing patterns: `TerminalTypography.bodySmall`, `MaterialTheme.colorScheme.onSurfaceVariant`, `semantics` for accessibility.

**Step 3: Verify compilation**

```bash
./gradlew :app:compileDebugKotlin
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/zeroclaw/android/ui/screen/terminal/ThinkingCard.kt
git commit -m "feat(ui): add tool activity footer to ThinkingCard"
```

---

## Task 10: Wire `KotlinSessionListener` and Dual-Path Dispatch in ViewModel

**Files:**
- Modify: `app/src/main/java/com/zeroclaw/android/ui/screen/terminal/TerminalViewModel.kt`

**Step 1: Add streaming state flow** (after line 86)

```kotlin
private val _streamingState = MutableStateFlow(StreamingState.idle())
val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()
```

**Step 2: Create `KotlinSessionListener` inner class**

Implements UniFFI-generated `FfiSessionListener`. Each callback updates `_streamingState` via `.update {}`. `on_complete` also persists the response to Room and adds a `TerminalBlock.Response`. `on_error` persists error and adds `TerminalBlock.Error`. `on_cancelled` adds `TerminalBlock.System`.

**Step 3: Add `executeAgentTurn` method**

New method replacing `executeChatMessage` for plain chat: persists input, sets loading, resets streaming state, calls `sessionSend` on `Dispatchers.IO`.

**Step 4: Update `submitInput` dual-path**

Change the `ChatMessage` branch to call `executeAgentTurn` instead of building a Rhai `send()` expression.

**Step 5: Add session lifecycle in init**

Call `sessionStart()` + `sessionSeed()` from Room entries on `Dispatchers.IO` in the ViewModel init block.

**Step 6: Add `cancelSession` method**

Public method calling `sessionCancel()` on `Dispatchers.IO`.

**Step 7: Verify compilation**

```bash
./gradlew :app:compileDebugKotlin
```

**Step 8: Commit**

```bash
git add app/src/main/java/com/zeroclaw/android/ui/screen/terminal/TerminalViewModel.kt
git commit -m "feat(ui): wire KotlinSessionListener and dual-path dispatch in TerminalViewModel"
```

---

## Task 11: Wire `StreamingState` into `TerminalScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/zeroclaw/android/ui/screen/terminal/TerminalScreen.kt`

**Step 1: Collect streaming state**

Add `val streamingState by viewModel.streamingState.collectAsStateWithLifecycle()`

**Step 2: Add streaming UI elements to LazyColumn**

In the reversed `LazyColumn`, add items for:
- `ThinkingCard` when phase is THINKING, TOOL_EXECUTING, or COMPACTING (pass `activeTools` and `toolResults`)
- Streaming response text when phase is RESPONDING (reuse `TerminalBlock.Response` styling)

**Step 3: Wire cancel button**

Pass `viewModel::cancelSession` as `ThinkingCard`'s `onCancel` callback.

**Step 4: Verify compilation**

```bash
./gradlew :app:compileDebugKotlin
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/zeroclaw/android/ui/screen/terminal/TerminalScreen.kt
git commit -m "feat(ui): wire streaming state into TerminalScreen with ThinkingCard"
```

---

## Task 12: Full Build and Fix Issues

**Step 1: Rust build**

```bash
cd zeroclaw-android && cargo build -p zeroclaw-ffi
```

**Step 2: Rust tests**

```bash
cd zeroclaw-android && cargo test -p zeroclaw-ffi --lib
```

**Step 3: Android build**

```bash
./gradlew :app:assembleDebug
```

**Step 4: Kotlin tests**

```bash
./gradlew :app:testDebugUnitTest
```

**Step 5: Fix and commit**

```bash
git add -A && git commit -m "fix(ffi): resolve compilation issues in session module"
```

---

## Task 13: Run Lints

**Step 1: Rust lints**

```bash
cd zeroclaw-android && cargo clippy -p zeroclaw-ffi --all-targets -- -D warnings
cargo fmt -p zeroclaw-ffi --check
```

**Step 2: Kotlin lints**

```bash
./gradlew spotlessCheck
./gradlew detekt
```

**Step 3: Fix and commit**

```bash
./gradlew spotlessApply
cd zeroclaw-android && cargo fmt -p zeroclaw-ffi
git add -A && git commit -m "style: fix lint issues in session module"
```

---

## Upstream Functions Referenced

| Function | File:Line | Visibility | Usage |
|----------|-----------|-----------|-------|
| `run_tool_call_loop` | `loop_.rs:2067` | `pub(crate)` | Wrap or replicate |
| `auto_compact_history` | `loop_.rs:190` | private | Replicate (~40 LOC) |
| `trim_history` | `loop_.rs:148` | private | Replicate (~10 LOC) |
| `build_system_prompt_with_mode` | `channels/mod.rs:2253` | `pub` | Call directly |
| `all_tools_with_runtime` | `tools/mod.rs:197` | `pub` | Call directly |
| `create_routed_provider_with_options` | `providers/mod.rs` | `pub` | Call directly |
| `build_context` | `agent/loop_.rs` | Check visibility | Call or replicate |
| `create_observer` | `observability/mod.rs` | `pub` | Call directly |
| `load_skills_with_config` | `skills/mod.rs` | `pub` | Call directly |
| `process_message` | `loop_.rs:3215` | `pub` | Fallback |

## Upstream Constants (Must Match Exactly)

| Constant | Value | Source |
|----------|-------|--------|
| `DEFAULT_MAX_TOOL_ITERATIONS` | 10 | `loop_.rs:89` |
| `DEFAULT_MAX_HISTORY_MESSAGES` | 50 | `loop_.rs:91` |
| `COMPACTION_KEEP_RECENT_MESSAGES` | 20 | `loop_.rs:93` |
| `COMPACTION_MAX_SOURCE_CHARS` | 12,000 | `loop_.rs:97` |
| `COMPACTION_MAX_SUMMARY_CHARS` | 2,000 | `loop_.rs:100` |
| `STREAM_CHUNK_MIN_CHARS` | 80 | `loop_.rs:85` |
| `DRAFT_CLEAR_SENTINEL` | `"\x00CLEAR\x00"` | `loop_.rs:107` |
| Compaction temperature | 0.2 | `loop_.rs:auto_compact_history` |
