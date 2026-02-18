/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

use crate::error::FfiError;
use chrono::Utc;
use std::future::Future;
use std::path::PathBuf;
use std::sync::{Mutex, OnceLock};
use tokio::runtime::Runtime;
use tokio::task::JoinHandle;
use tokio::time::Duration;
use zeroclaw::Config;

/// Single tokio runtime shared across all FFI calls.
///
/// Created on first `start_daemon` call, never destroyed. Tokio runtimes
/// are expensive to create/destroy, so we keep one for the process lifetime.
static RUNTIME: OnceLock<Runtime> = OnceLock::new();

/// Guarded daemon state. `None` when the daemon is not running.
static DAEMON: OnceLock<Mutex<Option<DaemonState>>> = OnceLock::new();

/// Mutable state for a running daemon instance.
struct DaemonState {
    /// Handles for all spawned component supervisors.
    handles: Vec<JoinHandle<()>>,
    /// Port the gateway HTTP server is listening on.
    gateway_port: u16,
}

/// Returns a reference to the daemon state mutex, initialising it on first access.
fn daemon_mutex() -> &'static Mutex<Option<DaemonState>> {
    DAEMON.get_or_init(|| Mutex::new(None))
}

/// Returns whether the daemon is currently running.
///
/// Acquires the daemon mutex briefly to check if state is `Some`.
/// Crate-visible so that sibling modules (e.g. `health`) can query
/// daemon liveness without accessing `DaemonState` directly.
///
/// # Errors
///
/// Returns [`FfiError::StateCorrupted`] if the daemon mutex is poisoned.
pub(crate) fn is_daemon_running() -> Result<bool, FfiError> {
    let guard = daemon_mutex()
        .lock()
        .map_err(|_| FfiError::StateCorrupted {
            detail: "daemon mutex poisoned".into(),
        })?;
    Ok(guard.is_some())
}

/// Returns a reference to the tokio runtime, creating it on first access.
///
/// Uses [`OnceLock::get_or_init`] for atomic, race-free initialisation.
/// Panics only if the tokio runtime builder fails, which is unrecoverable.
fn get_or_create_runtime() -> &'static Runtime {
    RUNTIME.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .thread_name("zeroclaw-ffi")
            .build()
            .expect("fatal: failed to create tokio runtime")
    })
}

/// Starts the `ZeroClaw` daemon with the provided configuration.
///
/// Parses `config_toml` into a [`Config`], overrides Android-specific paths
/// with `data_dir`, then spawns all daemon components (gateway, channels,
/// scheduler, heartbeat) under supervised tasks with exponential backoff.
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

    std::fs::create_dir_all(&config.workspace_dir).map_err(|e| FfiError::ConfigError {
        detail: format!("failed to create workspace dir: {e}"),
    })?;

    let runtime = get_or_create_runtime();

    let mut guard = daemon_mutex()
        .lock()
        .map_err(|e| FfiError::StateCorrupted {
            detail: format!("daemon mutex poisoned: {e}"),
        })?;

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

    let handles = runtime.block_on(async {
        zeroclaw::health::mark_component_ok("daemon");

        if config.heartbeat.enabled {
            let _ = zeroclaw::heartbeat::engine::HeartbeatEngine::ensure_heartbeat_file(
                &config.workspace_dir,
            )
            .await;
        }

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
            zeroclaw::health::mark_component_ok("channels");
            tracing::info!("No real-time channels configured; channel supervisor disabled");
        }

        if config.heartbeat.enabled {
            let heartbeat_cfg = config.clone();
            handles.push(spawn_component_supervisor(
                "heartbeat",
                initial_backoff,
                max_backoff,
                move || {
                    let cfg = heartbeat_cfg.clone();
                    async move { run_heartbeat_worker(cfg).await }
                },
            ));
        }

        {
            let scheduler_cfg = config.clone();
            handles.push(spawn_component_supervisor(
                "scheduler",
                initial_backoff,
                max_backoff,
                move || {
                    let cfg = scheduler_cfg.clone();
                    async move { zeroclaw::cron::scheduler::run(cfg).await }
                },
            ));
        }

        handles
    });

    *guard = Some(DaemonState {
        handles,
        gateway_port: port,
    });

    tracing::info!("ZeroClaw daemon started on {host}:{port}");

    Ok(())
}

/// Stops a running `ZeroClaw` daemon by signaling shutdown and aborting
/// all component supervisor tasks.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// or [`FfiError::StateCorrupted`] if the daemon mutex is poisoned.
pub(crate) fn stop_daemon_inner() -> Result<(), FfiError> {
    let runtime = get_or_create_runtime();

    let mut guard = daemon_mutex()
        .lock()
        .map_err(|e| FfiError::StateCorrupted {
            detail: format!("daemon mutex poisoned: {e}"),
        })?;

    let state = guard.take().ok_or_else(|| FfiError::StateError {
        detail: "daemon not running".to_string(),
    })?;

    for handle in &state.handles {
        handle.abort();
    }

    runtime.block_on(async {
        for handle in state.handles {
            let _ = handle.await;
        }
    });

    zeroclaw::health::mark_component_error("daemon", "shutdown requested");
    tracing::info!("ZeroClaw daemon stopped");

    Ok(())
}

/// Returns a JSON string describing the health of all daemon components.
///
/// Includes the upstream health snapshot plus a `daemon_running` boolean.
///
/// # Errors
///
/// Returns [`FfiError::StateCorrupted`] if the daemon mutex is poisoned,
/// or [`FfiError::SpawnError`] if the health snapshot cannot be serialised.
pub(crate) fn get_status_inner() -> Result<String, FfiError> {
    let guard = daemon_mutex()
        .lock()
        .map_err(|e| FfiError::StateCorrupted {
            detail: format!("daemon mutex poisoned: {e}"),
        })?;

    let daemon_running = guard.is_some();
    drop(guard);

    let mut snapshot = zeroclaw::health::snapshot_json();
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
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// [`FfiError::StateCorrupted`] if the daemon mutex is poisoned,
/// or [`FfiError::SpawnError`] on HTTP or parse failure.
pub(crate) fn send_message_inner(message: String) -> Result<String, FfiError> {
    const MAX_MESSAGE_BYTES: usize = 1_048_576;
    // Ten minutes — generous timeout for large local models (e.g. 70B+ on CPU).
    const GATEWAY_TIMEOUT_SECS: u64 = 600;
    if message.len() > MAX_MESSAGE_BYTES {
        return Err(FfiError::ConfigError {
            detail: format!(
                "message too large ({} bytes, max {MAX_MESSAGE_BYTES})",
                message.len()
            ),
        });
    }

    let runtime = get_or_create_runtime();

    let gateway_port = {
        let guard = daemon_mutex()
            .lock()
            .map_err(|e| FfiError::StateCorrupted {
                detail: format!("daemon mutex poisoned: {e}"),
            })?;
        guard
            .as_ref()
            .ok_or_else(|| FfiError::StateError {
                detail: "daemon not running".to_string(),
            })?
            .gateway_port
    };

    let url = format!("http://127.0.0.1:{gateway_port}/webhook");

    runtime.block_on(async {
        let client = reqwest::Client::builder()
            .timeout(Duration::from_secs(GATEWAY_TIMEOUT_SECS))
            .build()
            .map_err(|e| FfiError::SpawnError {
                detail: format!("failed to build HTTP client: {e}"),
            })?;
        let response = client
            .post(&url)
            .json(&serde_json::json!({ "message": message }))
            .send()
            .await
            .map_err(|e| FfiError::SpawnError {
                detail: format!("gateway request failed: {e}"),
            })?;

        let status = response.status();
        if !status.is_success() {
            let hint = if status.as_u16() == 408 {
                " (model may need more time to respond — try a smaller prompt or faster model)"
            } else {
                ""
            };
            return Err(FfiError::SpawnError {
                detail: format!("gateway returned status {status}{hint}"),
            });
        }

        let body: serde_json::Value = response.json().await.map_err(|e| FfiError::SpawnError {
            detail: format!("failed to parse gateway response: {e}"),
        })?;

        body["response"]
            .as_str()
            .map(String::from)
            .ok_or_else(|| FfiError::SpawnError {
                detail: "gateway response missing 'response' field".to_string(),
            })
    })
}

/// Writes a health snapshot JSON to disk every 5 seconds.
///
/// Module-private helper that replicates `daemon/mod.rs` state writer
/// with identical behaviour. Runs as a background task for the lifetime
/// of the daemon, writing `daemon_state.json` next to the config file.
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
            let mut json = zeroclaw::health::snapshot_json();
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
/// Module-private helper that replicates `daemon/mod.rs:spawn_component_supervisor`
/// since the upstream version is not exported. Marks component health via
/// `zeroclaw::health` and doubles `backoff` on each failure up to `max_backoff_secs`.
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
            zeroclaw::health::mark_component_ok(name);
            match run_component().await {
                Ok(()) => {
                    zeroclaw::health::mark_component_error(name, "component exited unexpectedly");
                    tracing::warn!("Daemon component '{name}' exited unexpectedly");
                    backoff = initial_backoff_secs.max(1);
                }
                Err(e) => {
                    zeroclaw::health::mark_component_error(name, e.to_string());
                    tracing::error!("Daemon component '{name}' failed: {e}");
                }
            }

            zeroclaw::health::bump_component_restart(name);
            tokio::time::sleep(Duration::from_secs(backoff)).await;
            backoff = backoff.saturating_mul(2).min(max_backoff);
        }
    })
}

/// Runs the heartbeat worker loop.
///
/// Module-private helper replicating `daemon/mod.rs:run_heartbeat_worker`.
/// Collects heartbeat tasks at the configured interval and dispatches each
/// through the agent runner.
async fn run_heartbeat_worker(config: Config) -> anyhow::Result<()> {
    let observer: std::sync::Arc<dyn zeroclaw::observability::Observer> = std::sync::Arc::from(
        zeroclaw::observability::create_observer(&config.observability),
    );
    let engine = zeroclaw::heartbeat::engine::HeartbeatEngine::new(
        config.heartbeat.clone(),
        config.workspace_dir.clone(),
        observer,
    );

    let interval_mins = config.heartbeat.interval_minutes.max(5);
    let mut interval = tokio::time::interval(Duration::from_secs(u64::from(interval_mins) * 60));

    loop {
        interval.tick().await;

        let tasks = engine.collect_tasks().await?;
        if tasks.is_empty() {
            continue;
        }

        for task in tasks {
            let prompt = format!("[Heartbeat Task] {task}");
            let temp = config.default_temperature;
            if let Err(e) =
                zeroclaw::agent::run(config.clone(), Some(prompt), None, None, temp, vec![]).await
            {
                zeroclaw::health::mark_component_error("heartbeat", e.to_string());
                tracing::warn!("Heartbeat task failed: {e}");
            } else {
                zeroclaw::health::mark_component_ok("heartbeat");
            }
        }
    }
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

/// Runs channel health checks without starting the daemon.
///
/// Parses the TOML config, overrides paths with `data_dir` (same as
/// [`start_daemon_inner`]), then calls the upstream
/// `channels::doctor_channels()` with a 10-second timeout per channel.
/// Returns a JSON array of `{"name":"...", "status":"healthy|unhealthy|timeout"}`.
///
/// Uses the shared [`RUNTIME`] for async execution but does NOT acquire
/// the [`DAEMON`] mutex.
///
/// # Errors
///
/// Returns [`FfiError::ConfigError`] on TOML parse or path failure,
/// or [`FfiError::SpawnError`] on channel-check or serialisation failure.
pub(crate) fn doctor_channels_inner(
    config_toml: String,
    data_dir: String,
) -> Result<String, FfiError> {
    let mut config: Config = toml::from_str(&config_toml).map_err(|e| FfiError::ConfigError {
        detail: format!("failed to parse config TOML: {e}"),
    })?;

    let data_path = PathBuf::from(&data_dir);
    config.workspace_dir = data_path.join("workspace");
    config.config_path = data_path.join("config.toml");

    let runtime = get_or_create_runtime();

    let results = runtime.block_on(async {
        match tokio::time::timeout(
            Duration::from_secs(30),
            zeroclaw::channels::doctor_channels(config),
        )
        .await
        {
            Ok(Ok(())) => Ok(serde_json::json!([
                {"name": "all_channels", "status": "healthy"}
            ])),
            Ok(Err(e)) => Ok(serde_json::json!([
                {"name": "channels", "status": "unhealthy", "detail": e.to_string()}
            ])),
            Err(_) => Ok(serde_json::json!([
                {"name": "channels", "status": "timeout"}
            ])),
        }
    })?;

    serde_json::to_string(&results).map_err(|e| FfiError::SpawnError {
        detail: format!("failed to serialise doctor results: {e}"),
    })
}

/// Returns `true` if any real-time channel is configured and needs supervision.
///
/// Module-private helper that checks all channel types (Telegram, Discord,
/// Slack, iMessage, Matrix, `WhatsApp`, Email) in the config.
fn has_supervised_channels(config: &Config) -> bool {
    config.channels_config.telegram.is_some()
        || config.channels_config.discord.is_some()
        || config.channels_config.slack.is_some()
        || config.channels_config.imessage.is_some()
        || config.channels_config.matrix.is_some()
        || config.channels_config.whatsapp.is_some()
        || config.channels_config.email.is_some()
}
