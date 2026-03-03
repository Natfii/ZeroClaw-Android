/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

use crate::error::FfiError;
use chrono::Utc;
use std::future::Future;
use std::path::PathBuf;
use std::sync::{Arc, Mutex, OnceLock};
use tokio::runtime::{Handle, Runtime};
use tokio::task::JoinHandle;
use tokio::time::Duration;
use zeroclaw::Config;

/// Tokio runtime, recreated on each daemon lifecycle.
///
/// Stored in a `Mutex<Option<Runtime>>` so that [`stop_daemon_inner`] can
/// take ownership and call [`Runtime::shutdown_timeout`], which kills all
/// spawned tasks — including orphaned typing-indicator loops that upstream
/// channels leave behind after abort.
static RUNTIME: Mutex<Option<Runtime>> = Mutex::new(None);

/// Guarded daemon state. `None` when the daemon is not running.
static DAEMON: OnceLock<Mutex<Option<DaemonState>>> = OnceLock::new();

/// Mutable state for a running daemon instance.
///
/// Upstream v0.1.6+ made `cost`, `health`, `heartbeat`, `cron`, and
/// `skills` modules `pub(crate)`, so this struct no longer holds a
/// `CostTracker`. Cost data is accessed through the gateway REST API.
struct DaemonState {
    /// Handles for all spawned component supervisors.
    handles: Vec<JoinHandle<()>>,
    /// Port the gateway HTTP server is listening on.
    gateway_port: u16,
    /// Parsed daemon configuration, retained for sibling module access.
    ///
    /// Used by [`with_daemon_config`] for memory modules.
    config: Config,
    /// Memory backend, created during daemon startup for the memory browser.
    ///
    /// Wrapped in `Arc` because `dyn Memory` requires `Send + Sync` and is
    /// accessed from multiple FFI calls concurrently.
    memory: Option<Arc<dyn zeroclaw::memory::Memory>>,
}

/// Returns a reference to the daemon state mutex, initialising it on first access.
fn daemon_mutex() -> &'static Mutex<Option<DaemonState>> {
    DAEMON.get_or_init(|| Mutex::new(None))
}

/// Locks the daemon mutex, recovering from poison if a prior holder panicked.
///
/// Rust's `Mutex` becomes permanently "poisoned" when a thread panics
/// while holding the lock. Without recovery, **every subsequent FFI call
/// fails forever** because the lock can never be acquired again.
///
/// This helper uses [`PoisonError::into_inner`] to reclaim the inner
/// `MutexGuard` after a panic, logging a warning but allowing the app to
/// continue operating. The daemon state inside may be stale, but
/// [`stop_daemon_inner`] can still clear it and [`start_daemon_inner`]
/// can reinitialise from scratch.
fn lock_daemon() -> std::sync::MutexGuard<'static, Option<DaemonState>> {
    daemon_mutex().lock().unwrap_or_else(|e| {
        tracing::warn!("Daemon mutex was poisoned; recovering: {e}");
        e.into_inner()
    })
}

/// Returns whether the daemon is currently running.
///
/// Acquires the daemon mutex briefly to check if state is `Some`.
/// Crate-visible so that sibling modules (e.g. `health`) can query
/// daemon liveness without accessing `DaemonState` directly.
#[allow(clippy::unnecessary_wraps)]
pub(crate) fn is_daemon_running() -> Result<bool, FfiError> {
    Ok(lock_daemon().is_some())
}

/// Returns the gateway port if the daemon is running.
///
/// Used by [`crate::gateway_client`] to construct loopback HTTP URLs.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running.
pub(crate) fn get_gateway_port() -> Result<u16, FfiError> {
    lock_daemon()
        .as_ref()
        .ok_or_else(|| FfiError::StateError {
            detail: "daemon not running".into(),
        })
        .map(|state| state.gateway_port)
}

/// Runs a closure with a reference to the daemon config.
///
/// Returns [`FfiError::StateError`] if the daemon is not running.
pub(crate) fn with_daemon_config<T>(f: impl FnOnce(&Config) -> T) -> Result<T, FfiError> {
    let guard = lock_daemon();
    let state = guard.as_ref().ok_or_else(|| FfiError::StateError {
        detail: "daemon not running".into(),
    })?;
    Ok(f(&state.config))
}

/// Returns an owned clone of the running daemon's [`Config`].
///
/// Acquires the daemon mutex briefly to clone the config, then releases it.
/// Used by session setup to snapshot config without holding the lock during
/// long-running operations like provider creation and prompt building.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// or [`FfiError::StateCorrupted`] if the daemon mutex is poisoned.
pub(crate) fn clone_daemon_config() -> Result<Config, FfiError> {
    with_daemon_config(Config::clone)
}

/// Returns a cloned `Arc<dyn Memory>` from the running daemon.
///
/// Acquires the daemon mutex briefly to clone the `Arc`, then releases it.
/// The returned `Arc` can be used independently without holding the lock,
/// which is important for session operations that need long-lived memory
/// access without blocking other daemon state queries.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running or
/// the memory backend was not initialised during daemon startup,
/// or [`FfiError::StateCorrupted`] if the daemon mutex is poisoned.
#[allow(dead_code)] // Used by session_send_inner, wired in Task 9
pub(crate) fn clone_daemon_memory() -> Result<Arc<dyn zeroclaw::memory::Memory>, FfiError> {
    let guard = lock_daemon();
    let state = guard.as_ref().ok_or_else(|| FfiError::StateError {
        detail: "daemon not running".into(),
    })?;
    let memory = state.memory.as_ref().ok_or_else(|| FfiError::StateError {
        detail: "memory backend not available".into(),
    })?;
    Ok(Arc::clone(memory))
}

/// Runs a closure with a reference to the memory backend and a tokio
/// runtime [`Handle`].
///
/// The closure receives the `Arc<dyn Memory>` and a `&Handle` so it
/// can call async memory methods via `handle.block_on(...)`. Since FFI
/// calls originate from Kotlin's IO dispatcher (not from our tokio
/// runtime), `block_on` is safe and will not deadlock.
///
/// The daemon mutex is released **before** the closure executes. This
/// prevents deadlocks when the `Memory` implementation itself needs to
/// acquire the mutex or perform blocking I/O.
///
/// Returns [`FfiError::StateError`] if the daemon is not running or the
/// memory backend was not initialised.
pub(crate) fn with_memory<T>(
    f: impl FnOnce(&dyn zeroclaw::memory::Memory, &Handle) -> Result<T, FfiError>,
) -> Result<T, FfiError> {
    let memory_arc = {
        let guard = lock_daemon();
        let state = guard.as_ref().ok_or_else(|| FfiError::StateError {
            detail: "daemon not running".into(),
        })?;
        Arc::clone(state.memory.as_ref().ok_or_else(|| FfiError::StateError {
            detail: "memory backend not available".into(),
        })?)
    }; // guard dropped here
    let handle = get_or_create_runtime()?;
    f(memory_arc.as_ref(), &handle)
}

/// Locks the runtime mutex, recovering from poison if a prior holder panicked.
///
/// Same pattern as [`lock_daemon`]: uses [`PoisonError::into_inner`] to
/// reclaim the guard after a panic, allowing the next caller to create a
/// fresh runtime.
fn lock_runtime() -> std::sync::MutexGuard<'static, Option<Runtime>> {
    RUNTIME.lock().unwrap_or_else(|e| {
        tracing::warn!("Runtime mutex was poisoned; recovering: {e}");
        e.into_inner()
    })
}

/// Returns a [`Handle`] to the tokio runtime, creating it on first access.
///
/// The returned `Handle` is an owned, cloneable token that keeps the
/// runtime alive and supports [`Handle::block_on`] with the same API as
/// [`Runtime::block_on`]. Callers should use `handle.block_on(...)`.
///
/// # Errors
///
/// Returns [`FfiError::SpawnError`] if the tokio runtime builder fails.
pub(crate) fn get_or_create_runtime() -> Result<Handle, FfiError> {
    let mut guard = lock_runtime();
    if let Some(rt) = guard.as_ref() {
        return Ok(rt.handle().clone());
    }
    let rt = tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .thread_name("zeroclaw-ffi")
        .build()
        .map_err(|e| FfiError::SpawnError {
            detail: format!("failed to create tokio runtime: {e}"),
        })?;
    let handle = rt.handle().clone();
    *guard = Some(rt);
    Ok(handle)
}

/// Starts the `ZeroClaw` daemon with the provided configuration.
///
/// Parses `config_toml` into a [`Config`], overrides Android-specific paths
/// with `data_dir`, then spawns the gateway and channel supervisors.
///
/// Upstream v0.1.6 made the `cron`, `cost`, `health`, and `heartbeat`
/// modules `pub(crate)`, so we no longer start those components directly.
/// The gateway handles cron CRUD and cost tracking internally; health is
/// tracked via [`crate::ffi_health`]; heartbeat is skipped on mobile.
///
/// # Errors
///
/// Returns [`FfiError::ConfigError`] on TOML parse failure,
/// [`FfiError::StateError`] if the daemon is already running,
/// [`FfiError::StateCorrupted`] if the daemon mutex is poisoned,
/// or [`FfiError::SpawnError`] on component spawn failure.
#[allow(clippy::too_many_lines)]
pub(crate) fn start_daemon_inner(
    config_toml: String,
    data_dir: String,
    host: String,
    port: u16,
) -> Result<(), FfiError> {
    if !data_dir.starts_with('/') {
        return Err(FfiError::ConfigError {
            detail: "data_dir must be an absolute path".to_string(),
        });
    }
    if data_dir.contains("..") {
        return Err(FfiError::ConfigError {
            detail: "data_dir must not contain '..' segments".to_string(),
        });
    }

    if host.is_empty() {
        return Err(FfiError::ConfigError {
            detail: "host must not be empty".to_string(),
        });
    }
    if !host
        .chars()
        .all(|c| c.is_ascii_alphanumeric() || c == '.' || c == ':' || c == '-')
    {
        return Err(FfiError::ConfigError {
            detail: "host contains invalid characters".to_string(),
        });
    }

    let mut config: Config = toml::from_str(&config_toml).map_err(|e| FfiError::ConfigError {
        detail: format!("failed to parse config TOML: {e}"),
    })?;

    let data_path = PathBuf::from(&data_dir);
    config.workspace_dir = data_path.join("workspace");
    config.config_path = data_path.join("config.toml");

    // SECURITY: Force-disable open-skills auto-sync. Upstream clones a
    // third-party GitHub repo (`besoeasy/open-skills`) and injects every
    // markdown file into the LLM system prompt with no integrity checks.
    // This is a supply-chain risk — a compromised repo poisons all users.
    // Android builds must never opt in. See upstream issue for details.
    config.skills.open_skills_enabled = false;
    config.skills.open_skills_dir = None;

    // Android does not ship the agent-browser CLI or desktop screenshot
    // tool. Exclude them from non-CLI channels (Telegram, Discord, etc.)
    // as a safety net in case ConfigTomlBuilder omits the field.
    for tool_name in ["browser", "screenshot"] {
        if !config
            .autonomy
            .non_cli_excluded_tools
            .iter()
            .any(|t| t == tool_name)
        {
            config
                .autonomy
                .non_cli_excluded_tools
                .push(tool_name.to_string());
        }
    }
    tracing::info!(
        excluded = ?config.autonomy.non_cli_excluded_tools,
        "Android tool exclusions applied for non-CLI channels"
    );

    crate::estop::load_state(&data_path);

    std::fs::create_dir_all(&config.workspace_dir).map_err(|e| FfiError::ConfigError {
        detail: format!("failed to create workspace dir: {e}"),
    })?;

    let handle = get_or_create_runtime()?;

    let mut guard = lock_daemon();

    if guard.is_some() {
        return Err(FfiError::StateError {
            detail: "daemon already running".to_string(),
        });
    }

    let initial_backoff = config.reliability.channel_initial_backoff_secs.max(1);
    let max_backoff = config
        .reliability
        .channel_max_backoff_secs
        .max(initial_backoff);

    let memory: Option<Arc<dyn zeroclaw::memory::Memory>> = match zeroclaw::memory::create_memory(
        &config.memory,
        &config.workspace_dir,
        config.api_key.as_deref(),
    ) {
        Ok(mem) => {
            tracing::info!("Memory backend initialised: {}", mem.name());
            Some(Arc::from(mem))
        }
        Err(e) => {
            tracing::warn!("Memory backend unavailable: {e}");
            None
        }
    };

    let stored_config = config.clone();

    let handles = handle.block_on(async {
        crate::ffi_health::mark_component_ok("daemon");

        let mut handles: Vec<JoinHandle<()>> = Vec::new();

        handles.push(spawn_state_writer(config.clone()));

        {
            let gateway_cfg = config.clone();
            let gateway_host = host.clone();
            handles.push(spawn_component_supervisor(
                "gateway",
                initial_backoff,
                max_backoff,
                move || {
                    let cfg = gateway_cfg.clone();
                    let h = gateway_host.clone();
                    async move { zeroclaw::gateway::run_gateway(&h, port, cfg).await }
                },
            ));
        }

        if has_supervised_channels(&config) {
            let channels_cfg = config.clone();
            handles.push(spawn_component_supervisor(
                "channels",
                initial_backoff,
                max_backoff,
                move || {
                    let cfg = channels_cfg.clone();
                    async move { zeroclaw::channels::start_channels(cfg).await }
                },
            ));
        } else {
            crate::ffi_health::mark_component_ok("channels");
            tracing::info!("No real-time channels configured; channel supervisor disabled");
        }

        // NOTE: Heartbeat and cron scheduler are skipped on Android.
        // Upstream v0.1.6 made these modules pub(crate), and they are
        // non-essential for the mobile wrapper. The gateway's internal
        // cron scheduler handles job execution; cron CRUD and cost data
        // are accessed through the gateway REST API.

        handles
    });

    *guard = Some(DaemonState {
        handles,
        gateway_port: port,
        config: stored_config,
        memory,
    });

    tracing::info!("ZeroClaw daemon started on {host}:{port}");

    Ok(())
}

/// Stops a running `ZeroClaw` daemon by aborting all component supervisor
/// tasks and shutting down the tokio runtime.
///
/// Shutting down the runtime (via [`Runtime::shutdown_timeout`]) kills
/// **all** spawned tasks, including orphaned typing-indicator loops and
/// channel listener tasks that survive the component abort. Without this,
/// Telegram's `start_typing` refresh task would continue sending
/// `sendChatAction` every 4 seconds indefinitely after stop.
///
/// A fresh runtime is created on the next [`start_daemon_inner`] call.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// or [`FfiError::StateCorrupted`] if the daemon mutex is poisoned.
pub(crate) fn stop_daemon_inner() -> Result<(), FfiError> {
    let mut guard = lock_daemon();

    let state = guard.take().ok_or_else(|| FfiError::StateError {
        detail: "daemon not running".to_string(),
    })?;

    for task in &state.handles {
        task.abort();
    }

    // Take ownership of the runtime so we can shut it down after awaiting
    // the aborted handles. Other FFI calls that need a runtime during this
    // window will create a fresh one (acceptable — no daemon state exists).
    let rt = { lock_runtime().take() };

    if let Some(rt) = rt {
        rt.block_on(async {
            for task in state.handles {
                let _ = task.await;
            }
        });

        // Kill orphaned tasks: typing indicators, channel listeners, etc.
        rt.shutdown_timeout(Duration::from_secs(5));
    }

    crate::ffi_health::mark_component_error("daemon", "shutdown requested");
    tracing::info!("ZeroClaw daemon stopped (runtime shut down)");

    Ok(())
}

/// Returns a JSON string describing the health of all daemon components.
///
/// Includes the FFI health snapshot plus a `daemon_running` boolean.
///
/// # Errors
///
/// Returns [`FfiError::StateCorrupted`] if the daemon mutex is poisoned,
/// or [`FfiError::SpawnError`] if the health snapshot cannot be serialised.
pub(crate) fn get_status_inner() -> Result<String, FfiError> {
    let guard = lock_daemon();
    let daemon_running = guard.is_some();
    drop(guard);

    let mut snapshot = crate::ffi_health::snapshot_json();
    if let Some(obj) = snapshot.as_object_mut() {
        obj.insert("daemon_running".into(), serde_json::json!(daemon_running));
    }

    serde_json::to_string(&snapshot).map_err(|e| FfiError::SpawnError {
        detail: format!("failed to serialise health snapshot: {e}"),
    })
}

/// Sends a message to the running daemon via its local HTTP gateway.
///
/// POSTs `{"message": "<msg>"}` to `http://127.0.0.1:{port}/webhook`
/// and returns the agent's response string.
///
/// Routes through the full agent loop ([`zeroclaw::agent::process_message`])
/// rather than the stateless gateway webhook. This provides:
/// - Memory recall (relevant past context injected before each turn)
/// - Tool access (shell, file, memory, etc.)
/// - Proper system prompt with workspace identity files
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// [`FfiError::StateCorrupted`] if the daemon mutex is poisoned,
/// or [`FfiError::SpawnError`] if agent processing fails.
pub(crate) fn send_message_inner(message: String) -> Result<String, FfiError> {
    const MAX_MESSAGE_BYTES: usize = 1_048_576;
    if message.len() > MAX_MESSAGE_BYTES {
        return Err(FfiError::ConfigError {
            detail: format!(
                "message too large ({} bytes, max {MAX_MESSAGE_BYTES})",
                message.len()
            ),
        });
    }

    let handle = get_or_create_runtime()?;
    let config = with_daemon_config(Config::clone)?;

    handle.block_on(async {
        zeroclaw::agent::process_message(config, &message)
            .await
            .map_err(|e| FfiError::SpawnError {
                detail: format!("agent processing failed: {e}"),
            })
    })
}

/// Writes an FFI health snapshot JSON to disk every 5 seconds.
fn spawn_state_writer(config: Config) -> JoinHandle<()> {
    tokio::spawn(async move {
        let path = config
            .config_path
            .parent()
            .map_or_else(|| PathBuf::from("."), PathBuf::from)
            .join("daemon_state.json");

        if let Some(parent) = path.parent() {
            let _ = tokio::fs::create_dir_all(parent).await;
        }

        let mut interval = tokio::time::interval(Duration::from_secs(5));
        loop {
            interval.tick().await;
            let mut json = crate::ffi_health::snapshot_json();
            if let Some(obj) = json.as_object_mut() {
                obj.insert(
                    "written_at".into(),
                    serde_json::json!(Utc::now().to_rfc3339()),
                );
            }
            let data = match serde_json::to_vec_pretty(&json) {
                Ok(bytes) => bytes,
                Err(e) => {
                    tracing::warn!("Failed to serialise health snapshot: {e}");
                    b"{}".to_vec()
                }
            };
            let _ = tokio::fs::write(&path, data).await;
        }
    })
}

/// Supervises a daemon component with exponential backoff on failure.
///
/// Uses [`crate::ffi_health`] for health tracking since the upstream
/// `zeroclaw::health` module is `pub(crate)` in v0.1.6.
fn spawn_component_supervisor<F, Fut>(
    name: &'static str,
    initial_backoff_secs: u64,
    max_backoff_secs: u64,
    mut run_component: F,
) -> JoinHandle<()>
where
    F: FnMut() -> Fut + Send + 'static,
    Fut: Future<Output = anyhow::Result<()>> + Send + 'static,
{
    tokio::spawn(async move {
        let mut backoff = initial_backoff_secs.max(1);
        let max_backoff = max_backoff_secs.max(backoff);

        loop {
            crate::ffi_health::mark_component_ok(name);
            match run_component().await {
                Ok(()) => {
                    crate::ffi_health::mark_component_error(name, "component exited unexpectedly");
                    tracing::warn!("Daemon component '{name}' exited unexpectedly");
                    backoff = initial_backoff_secs.max(1);
                }
                Err(e) => {
                    crate::ffi_health::mark_component_error(name, e.to_string());
                    tracing::error!("Daemon component '{name}' failed: {e}");
                }
            }

            crate::ffi_health::bump_component_restart(name);
            tokio::time::sleep(Duration::from_secs(backoff)).await;
            backoff = backoff.saturating_mul(2).min(max_backoff);
        }
    })
}

/// Hot-swaps the default provider and model in the running daemon config.
///
/// Mutates `DaemonState.config` in-place without restarting the daemon.
/// The change takes effect on the next message send (session start will
/// snapshot the updated config). Does not persist to disk; the Kotlin
/// layer is responsible for persisting the setting and rebuilding the
/// TOML on next full restart.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running.
pub(crate) fn swap_provider_inner(
    provider: String,
    model: String,
    api_key: Option<String>,
) -> Result<(), FfiError> {
    let mut guard = lock_daemon();
    let state = guard.as_mut().ok_or_else(|| FfiError::StateError {
        detail: "daemon not running".into(),
    })?;

    state.config.default_provider = Some(provider);
    state.config.default_model = Some(model);
    if let Some(key) = api_key {
        state.config.api_key = Some(key);
    }

    tracing::info!(
        "Provider hot-swapped to {:?}/{:?}",
        state.config.default_provider,
        state.config.default_model
    );
    Ok(())
}

/// Returns the TOML representation of the currently running daemon config.
///
/// Serialises the in-memory [`Config`] back to TOML using
/// `toml::to_string_pretty`. This may differ from the original TOML that
/// was passed to [`start_daemon_inner`] because path overrides and
/// default-filling have been applied.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// or [`FfiError::SpawnError`] if serialisation fails.
pub(crate) fn get_running_config_inner() -> Result<String, FfiError> {
    with_daemon_config(|config| {
        toml::to_string_pretty(config).map_err(|e| FfiError::SpawnError {
            detail: format!("failed to serialize config: {e}"),
        })
    })?
}

/// Validates a TOML config string without starting the daemon.
///
/// Parses `config_toml` using the same `toml::from_str::<Config>()` call
/// as [`start_daemon_inner`]. Returns an empty string on success, or a
/// human-readable error message on parse failure.
///
/// No state mutation, no mutex, no file I/O.
///
/// # Errors
///
/// Returns [`FfiError::InternalPanic`] only if serialisation panics
/// (should never happen).
#[allow(clippy::unnecessary_wraps)]
pub(crate) fn validate_config_inner(config_toml: String) -> Result<String, FfiError> {
    match toml::from_str::<Config>(&config_toml) {
        Ok(_) => Ok(String::new()),
        Err(e) => Ok(format!("{e}")),
    }
}

/// Runs per-channel health checks and returns structured results.
///
/// When `config_toml` is empty, uses the running daemon's config
/// (requires the daemon to be started). When `config_toml` is
/// provided, parses it and overrides paths with `data_dir` (same as
/// [`start_daemon_inner`]).
///
/// Constructs each configured channel independently (replicating
/// upstream's private `collect_configured_channels()` logic from
/// `zeroclaw/src/channels/mod.rs:2683-2967`) and calls
/// [`Channel::health_check()`] with a per-channel 10-second timeout,
/// wrapped in a 30-second outer timeout for the entire loop.
///
/// Returns a JSON array with one entry per channel:
/// ```json
/// [
///   {"name": "Telegram", "status": "healthy"},
///   {"name": "Discord", "status": "unhealthy", "detail": "auth/config/network"},
///   {"name": "Signal", "status": "timeout"}
/// ]
/// ```
///
/// When no channels are configured, returns:
/// ```json
/// [{"name": "channels", "status": "healthy", "detail": "No channels configured"}]
/// ```
///
/// Uses the shared [`RUNTIME`] for async execution but does NOT acquire
/// the [`DAEMON`] mutex.
///
/// # Errors
///
/// Returns [`FfiError::ConfigError`] on TOML parse or path failure,
/// [`FfiError::StateError`] if `config_toml` is empty and the daemon
/// is not running, or [`FfiError::SpawnError`] on serialisation failure.
pub(crate) fn doctor_channels_inner(
    config_toml: String,
    data_dir: String,
) -> Result<String, FfiError> {
    let config: Config = if config_toml.is_empty() {
        clone_daemon_config()?
    } else {
        let mut parsed: Config =
            toml::from_str(&config_toml).map_err(|e| FfiError::ConfigError {
                detail: format!("failed to parse config TOML: {e}"),
            })?;
        let data_path = PathBuf::from(&data_dir);
        parsed.workspace_dir = data_path.join("workspace");
        parsed.config_path = data_path.join("config.toml");
        parsed
    };

    let handle = get_or_create_runtime()?;

    let results = handle.block_on(async {
        let mut channels = collect_channels(&config);
        let mut results = Vec::<serde_json::Value>::new();

        if let Some(ref ns) = config.channels_config.nostr {
            match zeroclaw::channels::NostrChannel::new(
                &ns.private_key,
                ns.relays.clone(),
                &ns.allowed_pubkeys,
            )
            .await
            {
                Ok(ch) => channels.push((
                    "Nostr",
                    Arc::new(ch) as Arc<dyn zeroclaw::channels::Channel>,
                )),
                Err(e) => results.push(serde_json::json!({
                    "name": "Nostr",
                    "status": "unhealthy",
                    "detail": format!("construction failed: {e}")
                })),
            }
        }

        if channels.is_empty() && results.is_empty() {
            return Ok(serde_json::json!([
                {"name": "channels", "status": "healthy", "detail": "No channels configured"}
            ]));
        }

        match tokio::time::timeout(Duration::from_secs(30), async {
            for (name, channel) in &channels {
                let check =
                    tokio::time::timeout(Duration::from_secs(10), channel.health_check()).await;
                match check {
                    Ok(true) => results.push(serde_json::json!({
                        "name": name, "status": "healthy"
                    })),
                    Ok(false) => results.push(serde_json::json!({
                        "name": name,
                        "status": "unhealthy",
                        "detail": "auth/config/network"
                    })),
                    Err(_) => results.push(serde_json::json!({
                        "name": name, "status": "timeout"
                    })),
                }
            }
        })
        .await
        {
            Ok(()) => {}
            Err(_) => {
                tracing::warn!("doctor_channels: 30s outer timeout exceeded");
            }
        }

        Ok::<_, FfiError>(serde_json::Value::Array(results))
    })?;

    serde_json::to_string(&results).map_err(|e| FfiError::SpawnError {
        detail: format!("failed to serialise doctor results: {e}"),
    })
}

/// Constructs all synchronous channels from the given config for health
/// checking.
///
/// Replicates the upstream `collect_configured_channels()` logic (which
/// is private and not accessible from the FFI crate) from
/// `zeroclaw/src/channels/mod.rs` lines 2683-2967. All constructor
/// calls match upstream's usage exactly.
///
/// Feature-gated channels (Matrix, Lark/Feishu) are skipped with a
/// `tracing::warn!` since this build uses `default-features = false`.
/// Nostr is excluded because its constructor is async; the caller
/// handles it separately in the async block.
#[allow(clippy::too_many_lines)] // Repetitive per-channel constructor calls; matches upstream structure
fn collect_channels(config: &Config) -> Vec<(&'static str, Arc<dyn zeroclaw::channels::Channel>)> {
    // Verified constructor signatures against upstream v0.1.7:
    //   TelegramChannel::new(bot_token, allowed_users, mention_only) -> Self
    //   DiscordChannel::new(bot_token, guild_id, allowed_users, listen_to_bots, mention_only) -> Self
    //   SlackChannel::new(bot_token, channel_id, allowed_users) -> Self
    //   MattermostChannel::new(base_url, bot_token, channel_id, allowed_users, thread_replies, mention_only) -> Self
    //   IMessageChannel::new(allowed_contacts) -> Self
    //   SignalChannel::new(http_url, account, group_id, allowed_from, ignore_attachments, ignore_stories) -> Self
    //   WhatsAppChannel::new(access_token, endpoint_id, verify_token, allowed_numbers) -> Self
    //   LinqChannel::new(api_token, from_phone, allowed_senders) -> Self
    //   WatiChannel::new(api_token, api_url, tenant_id, allowed_numbers) -> Self
    //   NextcloudTalkChannel::new(base_url, app_token, allowed_users) -> Self
    //   EmailChannel::new(config: EmailConfig) -> Self
    //   IrcChannel::new(cfg: IrcChannelConfig) -> Self
    //   DingTalkChannel::new(client_id, client_secret, allowed_users) -> Self
    //   QQChannel::new(app_id, app_secret, allowed_users) -> Self
    //   ClawdTalkChannel::new(config: ClawdTalkConfig) -> Self
    //   NostrChannel::new(private_key, relays, allowed_pubkeys) -> Result<Self> [ASYNC, handled by caller]
    use zeroclaw::channels::{
        ClawdTalkChannel, DingTalkChannel, DiscordChannel, EmailChannel, IMessageChannel,
        IrcChannel, LinqChannel, MattermostChannel, NextcloudTalkChannel, QQChannel, SignalChannel,
        SlackChannel, TelegramChannel, WatiChannel, WhatsAppChannel,
    };

    let mut channels: Vec<(&'static str, Arc<dyn zeroclaw::channels::Channel>)> = Vec::new();

    if let Some(ref tg) = config.channels_config.telegram {
        channels.push((
            "Telegram",
            Arc::new(TelegramChannel::new(
                tg.bot_token.clone(),
                tg.allowed_users.clone(),
                tg.mention_only,
            )),
        ));
    }

    if let Some(ref dc) = config.channels_config.discord {
        channels.push((
            "Discord",
            Arc::new(DiscordChannel::new(
                dc.bot_token.clone(),
                dc.guild_id.clone(),
                dc.allowed_users.clone(),
                dc.listen_to_bots,
                dc.mention_only,
            )),
        ));
    }

    if let Some(ref sl) = config.channels_config.slack {
        channels.push((
            "Slack",
            Arc::new(SlackChannel::new(
                sl.bot_token.clone(),
                sl.channel_id.clone(),
                sl.allowed_users.clone(),
            )),
        ));
    }

    if let Some(ref mm) = config.channels_config.mattermost {
        channels.push((
            "Mattermost",
            Arc::new(MattermostChannel::new(
                mm.url.clone(),
                mm.bot_token.clone(),
                mm.channel_id.clone(),
                mm.allowed_users.clone(),
                mm.thread_replies.unwrap_or(true),
                mm.mention_only.unwrap_or(false),
            )),
        ));
    }

    if let Some(ref im) = config.channels_config.imessage {
        channels.push((
            "iMessage",
            Arc::new(IMessageChannel::new(im.allowed_contacts.clone())),
        ));
    }

    if config.channels_config.matrix.is_some() {
        tracing::warn!(
            "Matrix channel is configured but this build was compiled \
             without `channel-matrix`; skipping Matrix health check."
        );
    }

    if let Some(ref sig) = config.channels_config.signal {
        channels.push((
            "Signal",
            Arc::new(SignalChannel::new(
                sig.http_url.clone(),
                sig.account.clone(),
                sig.group_id.clone(),
                sig.allowed_from.clone(),
                sig.ignore_attachments,
                sig.ignore_stories,
            )),
        ));
    }

    if let Some(ref wa) = config.channels_config.whatsapp {
        match wa.backend_type() {
            "cloud" => {
                if wa.is_cloud_config() {
                    channels.push((
                        "WhatsApp",
                        Arc::new(WhatsAppChannel::new(
                            wa.access_token.clone().unwrap_or_default(),
                            wa.phone_number_id.clone().unwrap_or_default(),
                            wa.verify_token.clone().unwrap_or_default(),
                            wa.allowed_numbers.clone(),
                        )),
                    ));
                } else {
                    tracing::warn!(
                        "WhatsApp Cloud API configured but missing required \
                         fields (phone_number_id, access_token, verify_token)"
                    );
                }
            }
            "web" => {
                tracing::warn!(
                    "WhatsApp Web backend requires the `whatsapp-web` feature \
                     which is not enabled in this build; skipping health check."
                );
            }
            _ => {
                tracing::warn!(
                    "WhatsApp config invalid: neither phone_number_id \
                     (Cloud API) nor session_path (Web) is set"
                );
            }
        }
    }

    if let Some(ref lq) = config.channels_config.linq {
        channels.push((
            "Linq",
            Arc::new(LinqChannel::new(
                lq.api_token.clone(),
                lq.from_phone.clone(),
                lq.allowed_senders.clone(),
            )),
        ));
    }

    if let Some(ref wati_cfg) = config.channels_config.wati {
        channels.push((
            "WATI",
            Arc::new(WatiChannel::new(
                wati_cfg.api_token.clone(),
                wati_cfg.api_url.clone(),
                wati_cfg.tenant_id.clone(),
                wati_cfg.allowed_numbers.clone(),
            )),
        ));
    }

    if let Some(ref nc) = config.channels_config.nextcloud_talk {
        channels.push((
            "Nextcloud Talk",
            Arc::new(NextcloudTalkChannel::new(
                nc.base_url.clone(),
                nc.app_token.clone(),
                nc.allowed_users.clone(),
            )),
        ));
    }

    if let Some(ref email_cfg) = config.channels_config.email {
        channels.push(("Email", Arc::new(EmailChannel::new(email_cfg.clone()))));
    }

    if let Some(ref irc_cfg) = config.channels_config.irc {
        channels.push((
            "IRC",
            Arc::new(IrcChannel::new(zeroclaw::channels::irc::IrcChannelConfig {
                server: irc_cfg.server.clone(),
                port: irc_cfg.port,
                nickname: irc_cfg.nickname.clone(),
                username: irc_cfg.username.clone(),
                channels: irc_cfg.channels.clone(),
                allowed_users: irc_cfg.allowed_users.clone(),
                server_password: irc_cfg.server_password.clone(),
                nickserv_password: irc_cfg.nickserv_password.clone(),
                sasl_password: irc_cfg.sasl_password.clone(),
                verify_tls: irc_cfg.verify_tls.unwrap_or(true),
            })),
        ));
    }

    if config.channels_config.lark.is_some() || config.channels_config.feishu.is_some() {
        tracing::warn!(
            "Lark/Feishu channel is configured but this build was compiled \
             without `channel-lark`; skipping health check."
        );
    }

    if let Some(ref dt) = config.channels_config.dingtalk {
        channels.push((
            "DingTalk",
            Arc::new(DingTalkChannel::new(
                dt.client_id.clone(),
                dt.client_secret.clone(),
                dt.allowed_users.clone(),
            )),
        ));
    }

    if let Some(ref qq) = config.channels_config.qq {
        channels.push((
            "QQ",
            Arc::new(QQChannel::new(
                qq.app_id.clone(),
                qq.app_secret.clone(),
                qq.allowed_users.clone(),
            )),
        ));
    }

    if let Some(ref ct) = config.channels_config.clawdtalk {
        channels.push(("ClawdTalk", Arc::new(ClawdTalkChannel::new(ct.clone()))));
    }

    channels
}

/// Returns `true` if any real-time channel is configured and needs supervision.
///
/// Updated for upstream v0.1.7 channel roster. Checks all channel
/// `Option` fields except CLI (which is not supervised).
fn has_supervised_channels(config: &Config) -> bool {
    config.channels_config.telegram.is_some()
        || config.channels_config.discord.is_some()
        || config.channels_config.slack.is_some()
        || config.channels_config.mattermost.is_some()
        || config.channels_config.imessage.is_some()
        || config.channels_config.matrix.is_some()
        || config.channels_config.signal.is_some()
        || config.channels_config.whatsapp.is_some()
        || config.channels_config.wati.is_some()
        || config.channels_config.nextcloud_talk.is_some()
        || config.channels_config.email.is_some()
        || config.channels_config.irc.is_some()
        || config.channels_config.lark.is_some()
        || config.channels_config.feishu.is_some()
        || config.channels_config.dingtalk.is_some()
        || config.channels_config.qq.is_some()
        || config.channels_config.nostr.is_some()
        || config.channels_config.clawdtalk.is_some()
        || config.channels_config.linq.is_some()
        || config.channels_config.webhook.is_some()
}

/// Maps a channel name to the upstream allowlist field name for that channel.
///
/// Returns `None` for unrecognised channel names. The returned string
/// matches the struct field name in the upstream `*Config` type
/// (e.g. `"allowed_users"` for Telegram, `"allowed_numbers"` for WhatsApp).
fn allowlist_field_for_channel(channel: &str) -> Option<&'static str> {
    match channel {
        "telegram" | "discord" | "slack" | "mattermost" | "matrix" | "irc" | "lark" | "feishu"
        | "dingtalk" | "qq" | "nextcloud_talk" => Some("allowed_users"),
        "whatsapp" | "wati" => Some("allowed_numbers"),
        "signal" => Some("allowed_from"),
        "imessage" => Some("allowed_contacts"),
        "email" | "linq" => Some("allowed_senders"),
        "nostr" => Some("allowed_pubkeys"),
        "clawdtalk" => Some("allowed_destinations"),
        _ => None,
    }
}

/// Returns a [`FfiError::ConfigError`] indicating that the given channel
/// is not configured in the running daemon.
#[allow(dead_code)] // Wired in Task 2 (lib.rs FFI exports)
fn not_configured(channel: &str) -> FfiError {
    FfiError::ConfigError {
        detail: format!("{channel} is not configured in the running daemon"),
    }
}

/// Appends `user_id` to the in-memory allowlist for `channel_name`.
///
/// Mutates only the in-memory [`Config`] held by [`DaemonState`]. The
/// caller must restart the daemon (or hot-reload channels) for the
/// change to take effect on the running channel supervisors.
///
/// Returns `"already_bound"` if the identity is already present in the
/// allowlist, or the allowlist field name (e.g. `"allowed_users"`) on
/// success.
///
/// # Telegram normalisation
///
/// For the `telegram` channel, a leading `@` is stripped to match
/// upstream `normalize_telegram_identity`.
///
/// # Errors
///
/// Returns [`FfiError::ConfigError`] if `channel_name` is unknown, the
/// channel is not configured, or `user_id` is empty after trimming.
/// Returns [`FfiError::StateError`] if the daemon is not running.
#[allow(clippy::too_many_lines)]
#[allow(dead_code)] // Wired in Task 2 (lib.rs FFI exports)
pub(crate) fn bind_channel_identity_inner(
    channel_name: String,
    user_id: String,
) -> Result<String, FfiError> {
    let field =
        allowlist_field_for_channel(&channel_name).ok_or_else(|| FfiError::ConfigError {
            detail: format!("unknown channel: {channel_name}"),
        })?;

    let trimmed = user_id.trim().to_string();
    if trimmed.is_empty() {
        return Err(FfiError::ConfigError {
            detail: "user identity must not be empty".to_string(),
        });
    }

    let id = if channel_name == "telegram" {
        trimmed.trim_start_matches('@').to_string()
    } else {
        trimmed
    };

    let mut guard = lock_daemon();
    let state = guard.as_mut().ok_or_else(|| FfiError::StateError {
        detail: "daemon not running".to_string(),
    })?;
    let cc = &mut state.config.channels_config;

    /// Pushes `$id` into `$list` unless it is already present.
    /// Evaluates to `true` if the identity was already bound.
    macro_rules! bind {
        ($cfg:expr, $field:ident, $id:expr) => {{
            let cfg = $cfg.as_mut().ok_or_else(|| not_configured(&channel_name))?;
            if cfg.$field.iter().any(|e| e == &$id) {
                true
            } else {
                cfg.$field.push($id);
                false
            }
        }};
    }

    let already = match channel_name.as_str() {
        "telegram" => bind!(cc.telegram, allowed_users, id),
        "discord" => bind!(cc.discord, allowed_users, id),
        "slack" => bind!(cc.slack, allowed_users, id),
        "mattermost" => bind!(cc.mattermost, allowed_users, id),
        "matrix" => bind!(cc.matrix, allowed_users, id),
        "irc" => bind!(cc.irc, allowed_users, id),
        "lark" => bind!(cc.lark, allowed_users, id),
        "feishu" => bind!(cc.feishu, allowed_users, id),
        "dingtalk" => bind!(cc.dingtalk, allowed_users, id),
        "qq" => bind!(cc.qq, allowed_users, id),
        "nextcloud_talk" => bind!(cc.nextcloud_talk, allowed_users, id),
        "whatsapp" => bind!(cc.whatsapp, allowed_numbers, id),
        "wati" => bind!(cc.wati, allowed_numbers, id),
        "signal" => bind!(cc.signal, allowed_from, id),
        "imessage" => bind!(cc.imessage, allowed_contacts, id),
        "email" => bind!(cc.email, allowed_senders, id),
        "linq" => bind!(cc.linq, allowed_senders, id),
        "nostr" => bind!(cc.nostr, allowed_pubkeys, id),
        "clawdtalk" => bind!(cc.clawdtalk, allowed_destinations, id),
        _ => {
            return Err(FfiError::ConfigError {
                detail: format!("unknown channel: {channel_name}"),
            });
        }
    };

    if already {
        Ok("already_bound".to_string())
    } else {
        Ok(field.to_string())
    }
}

/// Returns the current allowlist for `channel_name` from the running
/// daemon's in-memory config.
///
/// Returns an empty `Vec` if the channel is configured but its
/// allowlist is empty.
///
/// # Errors
///
/// Returns [`FfiError::ConfigError`] if `channel_name` is unknown or
/// the channel is not configured.
/// Returns [`FfiError::StateError`] if the daemon is not running.
#[allow(clippy::too_many_lines)]
#[allow(dead_code)] // Wired in Task 2 (lib.rs FFI exports)
pub(crate) fn get_channel_allowlist_inner(channel_name: String) -> Result<Vec<String>, FfiError> {
    let _field =
        allowlist_field_for_channel(&channel_name).ok_or_else(|| FfiError::ConfigError {
            detail: format!("unknown channel: {channel_name}"),
        })?;

    with_daemon_config(|config| {
        let cc = &config.channels_config;
        match channel_name.as_str() {
            "telegram" => cc
                .telegram
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_users.clone()),
            "discord" => cc
                .discord
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_users.clone()),
            "slack" => cc
                .slack
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_users.clone()),
            "mattermost" => cc
                .mattermost
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_users.clone()),
            "matrix" => cc
                .matrix
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_users.clone()),
            "irc" => cc
                .irc
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_users.clone()),
            "lark" => cc
                .lark
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_users.clone()),
            "feishu" => cc
                .feishu
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_users.clone()),
            "dingtalk" => cc
                .dingtalk
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_users.clone()),
            "qq" => cc
                .qq
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_users.clone()),
            "nextcloud_talk" => cc
                .nextcloud_talk
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_users.clone()),
            "whatsapp" => cc
                .whatsapp
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_numbers.clone()),
            "wati" => cc
                .wati
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_numbers.clone()),
            "signal" => cc
                .signal
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_from.clone()),
            "imessage" => cc
                .imessage
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_contacts.clone()),
            "email" => cc
                .email
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_senders.clone()),
            "linq" => cc
                .linq
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_senders.clone()),
            "nostr" => cc
                .nostr
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_pubkeys.clone()),
            "clawdtalk" => cc
                .clawdtalk
                .as_ref()
                .ok_or_else(|| not_configured(&channel_name))
                .map(|c| c.allowed_destinations.clone()),
            _ => Err(FfiError::ConfigError {
                detail: format!("unknown channel: {channel_name}"),
            }),
        }
    })?
}

/// Returns the names of all channels with non-null config sections in
/// the running daemon's parsed TOML.
///
/// Mirrors [`has_supervised_channels`] but returns the individual
/// channel names instead of a single boolean. Used by the Android UI
/// for per-channel progress tracking during daemon startup.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// or [`FfiError::StateCorrupted`] if the daemon mutex is poisoned.
pub(crate) fn get_configured_channel_names_inner() -> Result<Vec<String>, FfiError> {
    with_daemon_config(|config| {
        let mut names = Vec::new();
        if config.channels_config.telegram.is_some() {
            names.push("telegram".to_string());
        }
        if config.channels_config.discord.is_some() {
            names.push("discord".to_string());
        }
        if config.channels_config.slack.is_some() {
            names.push("slack".to_string());
        }
        if config.channels_config.mattermost.is_some() {
            names.push("mattermost".to_string());
        }
        if config.channels_config.imessage.is_some() {
            names.push("imessage".to_string());
        }
        if config.channels_config.matrix.is_some() {
            names.push("matrix".to_string());
        }
        if config.channels_config.signal.is_some() {
            names.push("signal".to_string());
        }
        if config.channels_config.whatsapp.is_some() {
            names.push("whatsapp".to_string());
        }
        if config.channels_config.wati.is_some() {
            names.push("wati".to_string());
        }
        if config.channels_config.nextcloud_talk.is_some() {
            names.push("nextcloud_talk".to_string());
        }
        if config.channels_config.email.is_some() {
            names.push("email".to_string());
        }
        if config.channels_config.irc.is_some() {
            names.push("irc".to_string());
        }
        if config.channels_config.lark.is_some() {
            names.push("lark".to_string());
        }
        if config.channels_config.feishu.is_some() {
            names.push("feishu".to_string());
        }
        if config.channels_config.dingtalk.is_some() {
            names.push("dingtalk".to_string());
        }
        if config.channels_config.qq.is_some() {
            names.push("qq".to_string());
        }
        if config.channels_config.nostr.is_some() {
            names.push("nostr".to_string());
        }
        if config.channels_config.clawdtalk.is_some() {
            names.push("clawdtalk".to_string());
        }
        if config.channels_config.linq.is_some() {
            names.push("linq".to_string());
        }
        if config.channels_config.webhook.is_some() {
            names.push("webhook".to_string());
        }
        names
    })
}

#[cfg(test)]
#[allow(clippy::unwrap_used)]
mod tests {
    use super::*;

    #[test]
    fn test_allowlist_field_telegram() {
        assert_eq!(
            allowlist_field_for_channel("telegram"),
            Some("allowed_users")
        );
    }

    #[test]
    fn test_allowlist_field_whatsapp() {
        assert_eq!(
            allowlist_field_for_channel("whatsapp"),
            Some("allowed_numbers")
        );
    }

    #[test]
    fn test_allowlist_field_signal() {
        assert_eq!(allowlist_field_for_channel("signal"), Some("allowed_from"));
    }

    #[test]
    fn test_allowlist_field_nostr() {
        assert_eq!(
            allowlist_field_for_channel("nostr"),
            Some("allowed_pubkeys")
        );
    }

    #[test]
    fn test_allowlist_field_clawdtalk() {
        assert_eq!(
            allowlist_field_for_channel("clawdtalk"),
            Some("allowed_destinations")
        );
    }

    #[test]
    fn test_allowlist_field_email() {
        assert_eq!(
            allowlist_field_for_channel("email"),
            Some("allowed_senders")
        );
    }

    #[test]
    fn test_allowlist_field_unknown() {
        assert_eq!(allowlist_field_for_channel("carrier_pigeon"), None);
    }

    #[test]
    fn test_bind_channel_no_daemon() {
        let result = bind_channel_identity_inner("telegram".into(), "alice".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_bind_channel_unknown() {
        let result = bind_channel_identity_inner("carrier_pigeon".into(), "alice".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::ConfigError { detail } => {
                assert!(detail.contains("unknown channel"));
            }
            other => panic!("expected ConfigError, got {other:?}"),
        }
    }

    #[test]
    fn test_bind_channel_empty_identity() {
        let result = bind_channel_identity_inner("telegram".into(), "   ".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::ConfigError { detail } => {
                assert!(detail.contains("must not be empty"));
            }
            other => panic!("expected ConfigError, got {other:?}"),
        }
    }

    #[test]
    fn test_get_allowlist_no_daemon() {
        let result = get_channel_allowlist_inner("telegram".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_get_allowlist_unknown_channel() {
        let result = get_channel_allowlist_inner("carrier_pigeon".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::ConfigError { detail } => {
                assert!(detail.contains("unknown channel"));
            }
            other => panic!("expected ConfigError, got {other:?}"),
        }
    }

    #[test]
    fn test_swap_provider_no_daemon() {
        let result = swap_provider_inner("anthropic".into(), "claude-sonnet-4".into(), None);
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_collect_channels_empty_config() {
        let config: Config = toml::from_str("default_temperature = 0.7").unwrap();
        let channels = collect_channels(&config);
        assert!(
            channels.is_empty(),
            "expected no channels from default config, got {}",
            channels.len()
        );
    }

    #[test]
    fn test_collect_channels_with_telegram() {
        let toml_str = r#"
default_temperature = 0.7

[channels_config]
cli = true

[channels_config.telegram]
bot_token = "fake:token"
allowed_users = ["123"]
mention_only = false
"#;
        let config: Config = toml::from_str(toml_str).unwrap();
        let channels = collect_channels(&config);
        assert_eq!(channels.len(), 1);
        assert_eq!(channels[0].0, "Telegram");
    }

    #[test]
    fn test_collect_channels_multiple() {
        let toml_str = r#"
default_temperature = 0.7

[channels_config]
cli = true

[channels_config.telegram]
bot_token = "fake:token"
allowed_users = []
mention_only = false

[channels_config.discord]
bot_token = "fake_discord_token"
allowed_users = []
listen_to_bots = false
mention_only = false

[channels_config.slack]
bot_token = "xoxb-fake"
allowed_users = []
"#;
        let config: Config = toml::from_str(toml_str).unwrap();
        let channels = collect_channels(&config);
        assert_eq!(channels.len(), 3);
        let names: Vec<&str> = channels.iter().map(|(n, _)| *n).collect();
        assert!(names.contains(&"Telegram"));
        assert!(names.contains(&"Discord"));
        assert!(names.contains(&"Slack"));
    }

    #[test]
    fn test_doctor_channels_no_daemon_empty_toml() {
        let result = doctor_channels_inner(String::new(), "/tmp/test".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_doctor_channels_no_channels_configured() {
        let toml_str = "default_temperature = 0.7\n";
        let result = doctor_channels_inner(toml_str.to_string(), "/tmp/test".into());
        let json_str = result.unwrap();
        let arr: Vec<serde_json::Value> = serde_json::from_str(&json_str).unwrap();
        assert_eq!(arr.len(), 1);
        assert_eq!(arr[0]["name"], "channels");
        assert_eq!(arr[0]["status"], "healthy");
        assert_eq!(arr[0]["detail"], "No channels configured");
    }
}
