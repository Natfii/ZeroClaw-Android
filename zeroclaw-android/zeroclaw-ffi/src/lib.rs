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
//! `get_version`, `validate_config`, and `doctor_channels` to Kotlin
//! via UniFFI-generated bindings.

uniffi::setup_scaffolding!();

mod error;
mod runtime;

use std::panic::catch_unwind;

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
}
