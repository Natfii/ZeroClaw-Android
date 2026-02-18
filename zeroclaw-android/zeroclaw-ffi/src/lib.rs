/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

#![deny(missing_docs)]

//! UniFFI-annotated facade for `ZeroClaw` Android bindings.
//!
//! This crate provides a thin FFI layer over the `ZeroClaw` daemon,
//! exposing `start_daemon`, `stop_daemon`, `get_status`, `send_message`,
//! `get_version`, `validate_config`, `doctor_channels`,
//! `scaffold_workspace`, `get_health_detail`, `get_component_health`,
//! `get_cost_summary`, `get_daily_cost`, `get_monthly_cost`,
//! `check_budget`, `register_event_listener`, `unregister_event_listener`,
//! and `get_recent_events` to Kotlin via UniFFI-generated bindings.

uniffi::setup_scaffolding!();

mod cost;
mod error;
mod events;
mod health;
mod runtime;
#[allow(dead_code)]
mod types;
mod workspace;

use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::Arc;

pub use error::FfiError;

/// Extracts a human-readable message from a caught panic payload.
fn panic_detail(payload: &Box<dyn std::any::Any + Send>) -> String {
    payload
        .downcast_ref::<&str>()
        .map(std::string::ToString::to_string)
        .or_else(|| payload.downcast_ref::<String>().cloned())
        .unwrap_or_else(|| "unknown panic".to_string())
}

/// Starts the `ZeroClaw` daemon with the given TOML configuration.
///
/// Parses `config_toml`, overrides paths using `data_dir` (typically
/// `context.filesDir` from Kotlin), and spawns the gateway on
/// `host:port`. All daemon components run as supervised async tasks.
///
/// # Errors
///
/// Returns [`FfiError::ConfigError`] for TOML parse failures,
/// [`FfiError::StateError`] if the daemon is already running,
/// [`FfiError::SpawnError`] on spawn failure,
/// [`FfiError::StateCorrupted`] if internal state is poisoned, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn start_daemon(
    config_toml: String,
    data_dir: String,
    host: String,
    port: u16,
) -> Result<(), FfiError> {
    catch_unwind(|| runtime::start_daemon_inner(config_toml, data_dir, host, port)).unwrap_or_else(
        |e| {
            Err(FfiError::InternalPanic {
                detail: panic_detail(&e),
            })
        },
    )
}

/// Stops the running `ZeroClaw` daemon.
///
/// Signals all component supervisors to shut down and waits for
/// their tasks to complete.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// [`FfiError::StateCorrupted`] if internal state is poisoned, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn stop_daemon() -> Result<(), FfiError> {
    catch_unwind(runtime::stop_daemon_inner).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Returns a JSON string describing daemon and component health.
///
/// The JSON includes upstream health fields (`pid`, `uptime_seconds`,
/// `components`) plus a `daemon_running` boolean.
///
/// # Errors
///
/// Returns [`FfiError::SpawnError`] on serialisation failure,
/// [`FfiError::StateCorrupted`] if internal state is poisoned, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn get_status() -> Result<String, FfiError> {
    catch_unwind(runtime::get_status_inner).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Returns structured health detail for all daemon components.
///
/// Unlike [`get_status`] which returns raw JSON, this function returns
/// typed component-level data including restart counts and last errors.
///
/// # Errors
///
/// Returns [`FfiError::StateCorrupted`] if internal state is poisoned, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn get_health_detail() -> Result<health::FfiHealthDetail, FfiError> {
    catch_unwind(health::get_health_detail_inner).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Returns health for a single named component.
///
/// Returns `None` if no component with the given name exists.
///
/// # Errors
///
/// Returns [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn get_component_health(name: String) -> Result<Option<health::FfiComponentHealth>, FfiError> {
    catch_unwind(|| Ok(health::get_component_health_inner(name))).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Sends a message to the daemon's gateway and returns the agent response.
///
/// POSTs to the local HTTP gateway's `/webhook` endpoint and returns
/// the `response` field from the JSON reply.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// [`FfiError::SpawnError`] on HTTP or parse failure,
/// [`FfiError::StateCorrupted`] if internal state is poisoned, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn send_message(message: String) -> Result<String, FfiError> {
    catch_unwind(|| runtime::send_message_inner(message)).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Validates a TOML config string without starting the daemon.
///
/// Parses `config_toml` using the same `toml::from_str::<Config>()` path
/// as [`start_daemon`]. Returns an empty string on success, or a
/// human-readable error message on parse failure.
///
/// No state mutation, no mutex acquisition, no file I/O.
///
/// # Errors
///
/// Returns [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn validate_config(config_toml: String) -> Result<String, FfiError> {
    catch_unwind(|| runtime::validate_config_inner(config_toml)).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Runs channel health checks without starting the daemon.
///
/// Parses the TOML config, overrides paths with `data_dir`, then
/// instantiates each configured channel and calls `health_check()` with
/// a timeout. Returns a JSON array of channel statuses.
///
/// # Errors
///
/// Returns [`FfiError::ConfigError`] on TOML parse failure,
/// [`FfiError::SpawnError`] on channel-check or serialisation failure,
/// or [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn doctor_channels(config_toml: String, data_dir: String) -> Result<String, FfiError> {
    catch_unwind(|| runtime::doctor_channels_inner(config_toml, data_dir)).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Returns the version string of the native library.
///
/// Reads from the crate version set at compile time via `CARGO_PKG_VERSION`.
///
/// # Errors
///
/// Returns [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn get_version() -> Result<String, FfiError> {
    catch_unwind(|| env!("CARGO_PKG_VERSION").to_string()).map_err(|e| FfiError::InternalPanic {
        detail: panic_detail(&e),
    })
}

/// Scaffolds the `ZeroClaw` workspace directory with identity files.
///
/// Creates 5 subdirectories (`sessions/`, `memory/`, `state/`, `cron/`,
/// `skills/`) and writes 8 markdown template files (`IDENTITY.md`,
/// `AGENTS.md`, `HEARTBEAT.md`, `SOUL.md`, `USER.md`, `TOOLS.md`,
/// `BOOTSTRAP.md`, `MEMORY.md`) inside `workspace_path`.
///
/// Idempotent: existing files are never overwritten. Empty parameter
/// strings are replaced with upstream defaults (e.g. agent name
/// defaults to `"ZeroClaw"`).
///
/// # Errors
///
/// Returns [`FfiError::ConfigError`] if directory creation or file
/// writing fails, or [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn scaffold_workspace(
    workspace_path: String,
    agent_name: String,
    user_name: String,
    timezone: String,
    communication_style: String,
) -> Result<(), FfiError> {
    catch_unwind(|| {
        workspace::create_workspace(
            &workspace_path,
            &agent_name,
            &user_name,
            &timezone,
            &communication_style,
        )
    })
    .unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Returns the current cost summary for session, day, and month.
///
/// Requires the daemon to be running with cost tracking enabled.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running or
/// cost tracking is disabled,
/// [`FfiError::SpawnError`] on tracker or serialisation failure, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn get_cost_summary() -> Result<cost::FfiCostSummary, FfiError> {
    catch_unwind(cost::get_cost_summary_inner).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Returns the cost for a specific day in USD.
///
/// Requires the daemon to be running with cost tracking enabled.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running or
/// cost tracking is disabled,
/// [`FfiError::SpawnError`] on invalid date or tracker failure, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn get_daily_cost(year: i32, month: u32, day: u32) -> Result<f64, FfiError> {
    catch_unwind(|| cost::get_daily_cost_inner(year, month, day)).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Returns the cost for a specific month in USD.
///
/// Requires the daemon to be running with cost tracking enabled.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running or
/// cost tracking is disabled,
/// [`FfiError::SpawnError`] on tracker failure, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn get_monthly_cost(year: i32, month: u32) -> Result<f64, FfiError> {
    catch_unwind(|| cost::get_monthly_cost_inner(year, month)).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Checks whether an estimated cost fits within configured budget limits.
///
/// Returns [`cost::FfiBudgetStatus::Allowed`] when within budget,
/// [`cost::FfiBudgetStatus::Warning`] when approaching limits, or
/// [`cost::FfiBudgetStatus::Exceeded`] when limits are breached.
///
/// Requires the daemon to be running with cost tracking enabled.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running or
/// cost tracking is disabled,
/// [`FfiError::SpawnError`] on tracker failure, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn check_budget(estimated_cost_usd: f64) -> Result<cost::FfiBudgetStatus, FfiError> {
    catch_unwind(|| cost::check_budget_inner(estimated_cost_usd)).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Registers a Kotlin-side event listener to receive live observer events.
///
/// Only one listener can be registered at a time. Registering a new
/// listener replaces the previous one.
///
/// # Errors
///
/// Returns [`FfiError::StateCorrupted`] if internal state is poisoned, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn register_event_listener(
    listener: Box<dyn events::FfiEventListener>,
) -> Result<(), FfiError> {
    let listener: Arc<dyn events::FfiEventListener> = Arc::from(listener);
    catch_unwind(AssertUnwindSafe(|| {
        events::register_event_listener_inner(listener)
    }))
    .unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Unregisters the current event listener.
///
/// After this call, events are still buffered in the ring buffer but
/// no longer forwarded to Kotlin.
///
/// # Errors
///
/// Returns [`FfiError::StateCorrupted`] if internal state is poisoned, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn unregister_event_listener() -> Result<(), FfiError> {
    // Direct function reference preferred over closure by clippy::redundant_closure.
    catch_unwind(events::unregister_event_listener_inner).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Returns the most recent events as a JSON array.
///
/// Events are ordered chronologically (oldest first). The `limit`
/// parameter caps how many events to return.
///
/// # Errors
///
/// Returns [`FfiError::StateCorrupted`] if internal state is poisoned, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn get_recent_events(limit: u32) -> Result<String, FfiError> {
    catch_unwind(|| events::get_recent_events_inner(limit)).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

#[cfg(test)]
#[allow(clippy::unwrap_used)]
mod tests {
    use super::*;

    #[test]
    fn test_get_version() {
        let version = get_version().unwrap();
        assert_eq!(version, "0.0.6");
    }

    #[test]
    fn test_start_daemon_invalid_toml() {
        let result = start_daemon(
            "this is not valid toml {{{{".to_string(),
            "/tmp/test".to_string(),
            "127.0.0.1".to_string(),
            8080,
        );
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::ConfigError { detail } => {
                assert!(detail.contains("failed to parse config TOML"));
            }
            other => panic!("expected ConfigError, got {other:?}"),
        }
    }

    #[test]
    fn test_stop_daemon_not_running() {
        let result = stop_daemon();
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_send_message_not_running() {
        let result = send_message("hello".to_string());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_get_status_returns_json() {
        let status = get_status().unwrap();
        let parsed: serde_json::Value = serde_json::from_str(&status).unwrap();
        assert!(parsed.get("daemon_running").is_some());
    }

    #[test]
    fn test_validate_config_valid() {
        let toml = "default_temperature = 0.7\n";
        let result = validate_config(toml.to_string()).unwrap();
        assert!(
            result.is_empty(),
            "expected empty string for valid config, got: {result}"
        );
    }

    #[test]
    fn test_validate_config_invalid() {
        let toml = "this is not valid {{{{";
        let result = validate_config(toml.to_string()).unwrap();
        assert!(
            !result.is_empty(),
            "expected non-empty error message for invalid config"
        );
    }

    #[test]
    fn test_doctor_channels_invalid_toml() {
        let result = doctor_channels("not valid {{".to_string(), "/tmp/test".to_string());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::ConfigError { detail } => {
                assert!(detail.contains("failed to parse config TOML"));
            }
            other => panic!("expected ConfigError, got {other:?}"),
        }
    }

    #[test]
    fn test_scaffold_workspace_creates_files() {
        let dir = std::env::temp_dir().join("zeroclaw_test_scaffold");
        let _ = std::fs::remove_dir_all(&dir);

        let result = scaffold_workspace(
            dir.to_string_lossy().to_string(),
            "TestAgent".to_string(),
            "TestUser".to_string(),
            "America/New_York".to_string(),
            String::new(),
        );
        assert!(result.is_ok());

        for subdir in &["sessions", "memory", "state", "cron", "skills"] {
            assert!(dir.join(subdir).is_dir(), "missing directory: {subdir}");
        }

        let expected_files = [
            "IDENTITY.md",
            "AGENTS.md",
            "HEARTBEAT.md",
            "SOUL.md",
            "USER.md",
            "TOOLS.md",
            "BOOTSTRAP.md",
            "MEMORY.md",
        ];
        for filename in &expected_files {
            assert!(dir.join(filename).is_file(), "missing file: {filename}");
        }

        let identity = std::fs::read_to_string(dir.join("IDENTITY.md")).unwrap();
        assert!(identity.contains("TestAgent"));

        let user_md = std::fs::read_to_string(dir.join("USER.md")).unwrap();
        assert!(user_md.contains("TestUser"));
        assert!(user_md.contains("America/New_York"));

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn test_scaffold_workspace_idempotent() {
        let dir = std::env::temp_dir().join("zeroclaw_test_idem");
        let _ = std::fs::remove_dir_all(&dir);

        scaffold_workspace(
            dir.to_string_lossy().to_string(),
            "Agent1".to_string(),
            String::new(),
            String::new(),
            String::new(),
        )
        .unwrap();

        scaffold_workspace(
            dir.to_string_lossy().to_string(),
            "Agent2".to_string(),
            String::new(),
            String::new(),
            String::new(),
        )
        .unwrap();

        let identity = std::fs::read_to_string(dir.join("IDENTITY.md")).unwrap();
        assert!(
            identity.contains("Agent1"),
            "existing file should not be overwritten"
        );
        assert!(!identity.contains("Agent2"));

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn test_scaffold_workspace_defaults() {
        let dir = std::env::temp_dir().join("zeroclaw_test_defaults");
        let _ = std::fs::remove_dir_all(&dir);

        scaffold_workspace(
            dir.to_string_lossy().to_string(),
            String::new(),
            String::new(),
            String::new(),
            String::new(),
        )
        .unwrap();

        let identity = std::fs::read_to_string(dir.join("IDENTITY.md")).unwrap();
        assert!(identity.contains("ZeroClaw"), "default agent name");

        let user_md = std::fs::read_to_string(dir.join("USER.md")).unwrap();
        assert!(user_md.contains("**Name:** User"), "default user name");
        assert!(user_md.contains("**Timezone:** UTC"), "default timezone");

        let _ = std::fs::remove_dir_all(&dir);
    }
}
