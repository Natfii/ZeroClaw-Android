/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Emergency stop (kill-all) for the `ZeroClaw` daemon.
//!
//! Provides a global atomic flag that blocks all agent execution when engaged.
//! State is persisted to `{data_dir}/estop-state.json` so it survives process
//! death.

use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};

use serde::{Deserialize, Serialize};

use crate::error::FfiError;

/// Global estop flag. Checked at entry of every agent-executing FFI function.
static ESTOP_ENGAGED: AtomicBool = AtomicBool::new(false);

/// JSON-persisted estop state.
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
struct EstopStateFile {
    /// Whether the kill-all flag is set.
    kill_all: bool,
    /// RFC 3339 timestamp when estop was last engaged.
    #[serde(default)]
    engaged_at: Option<String>,
}

/// UniFFI record returned by [`crate::get_estop_status`].
#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiEstopStatus {
    /// Whether the emergency stop is currently engaged.
    pub engaged: bool,
    /// Epoch milliseconds when estop was engaged, if available.
    pub engaged_at_ms: Option<i64>,
}

/// Returns `true` if the emergency stop is currently engaged.
pub(crate) fn is_engaged() -> bool {
    ESTOP_ENGAGED.load(Ordering::Relaxed)
}

/// Loads estop state from disk during daemon startup.
///
/// Called from [`crate::runtime::start_daemon_inner`] after config is parsed
/// and `data_path` is resolved.
pub(crate) fn load_state(data_dir: &Path) {
    let path = state_path(data_dir);
    let Ok(contents) = std::fs::read_to_string(&path) else {
        return;
    };
    let Ok(state) = serde_json::from_str::<EstopStateFile>(&contents) else {
        return;
    };
    ESTOP_ENGAGED.store(state.kill_all, Ordering::Relaxed);
    if state.kill_all {
        tracing::warn!("Estop state restored from disk: kill_all=true");
    }
}

/// Engages the emergency stop, cancels active sessions, persists state.
///
/// The `Result` return type maintains consistency with all other `_inner`
/// FFI functions and allows future error paths without API changes.
#[allow(clippy::unnecessary_wraps)]
pub(crate) fn engage_estop_inner() -> Result<(), FfiError> {
    ESTOP_ENGAGED.store(true, Ordering::Relaxed);

    // Best-effort cancel of any active session.
    crate::session::session_cancel_inner();

    if let Ok(data_dir) = get_data_dir() {
        let state = EstopStateFile {
            kill_all: true,
            engaged_at: Some(chrono::Utc::now().to_rfc3339()),
        };
        persist_state(&data_dir, &state);
    }

    tracing::warn!("Emergency stop ENGAGED");
    Ok(())
}

/// Returns current estop status.
///
/// The `Result` return type maintains consistency with all other `_inner`
/// FFI functions and allows future error paths without API changes.
#[allow(clippy::unnecessary_wraps)]
pub(crate) fn get_estop_status_inner() -> Result<FfiEstopStatus, FfiError> {
    let engaged = ESTOP_ENGAGED.load(Ordering::Relaxed);
    let engaged_at_ms = if engaged {
        read_engaged_timestamp()
    } else {
        None
    };

    Ok(FfiEstopStatus {
        engaged,
        engaged_at_ms,
    })
}

/// Reads the engaged-at timestamp from the persisted state file.
///
/// Returns `None` if the daemon is not running, the file is missing,
/// the JSON is malformed, or the timestamp cannot be parsed.
fn read_engaged_timestamp() -> Option<i64> {
    let data_dir = get_data_dir().ok()?;
    let path = state_path(&data_dir);
    let contents = std::fs::read_to_string(&path).ok()?;
    let state: EstopStateFile = serde_json::from_str(&contents).ok()?;
    let ts = state.engaged_at.as_ref()?;
    let dt = chrono::DateTime::parse_from_rfc3339(ts).ok()?;
    Some(dt.timestamp_millis())
}

/// Resumes from emergency stop, persists state.
///
/// The `Result` return type maintains consistency with all other `_inner`
/// FFI functions and allows future error paths without API changes.
#[allow(clippy::unnecessary_wraps)]
pub(crate) fn resume_estop_inner() -> Result<(), FfiError> {
    ESTOP_ENGAGED.store(false, Ordering::Relaxed);

    if let Ok(data_dir) = get_data_dir() {
        let state = EstopStateFile {
            kill_all: false,
            engaged_at: None,
        };
        persist_state(&data_dir, &state);
    }

    tracing::info!("Emergency stop RESUMED");
    Ok(())
}

/// Returns the path to the estop state file.
fn state_path(data_dir: &Path) -> PathBuf {
    data_dir.join("estop-state.json")
}

/// Derives the data directory from the running daemon's config.
///
/// The daemon's `workspace_dir` is `{data_dir}/workspace`, so we go up
/// one level to get the data directory itself.
fn get_data_dir() -> Result<PathBuf, FfiError> {
    crate::runtime::with_daemon_config(|c| {
        c.workspace_dir
            .parent()
            .unwrap_or(&c.workspace_dir)
            .to_path_buf()
    })
}

/// Writes the estop state to disk as pretty-printed JSON.
fn persist_state(data_dir: &Path, state: &EstopStateFile) {
    let path = state_path(data_dir);
    if let Ok(json) = serde_json::to_string_pretty(state) {
        let _ = std::fs::write(&path, json);
    }
}

#[cfg(test)]
#[allow(clippy::unwrap_used)]
mod tests {
    use super::*;

    /// Tests all atomic-flag and `_inner` operations in one sequential
    /// test to avoid parallel interference on the shared static
    /// [`ESTOP_ENGAGED`] flag.
    #[test]
    fn test_estop_lifecycle() {
        // 1. Raw atomic cycle
        ESTOP_ENGAGED.store(false, Ordering::Relaxed);
        assert!(!is_engaged());
        ESTOP_ENGAGED.store(true, Ordering::Relaxed);
        assert!(is_engaged());
        ESTOP_ENGAGED.store(false, Ordering::Relaxed);
        assert!(!is_engaged());

        // 2. Status when disengaged (daemon not running -> no timestamp)
        let status = get_estop_status_inner().unwrap();
        assert!(!status.engaged);
        assert!(status.engaged_at_ms.is_none());

        // 3. engage_estop_inner succeeds even without a running daemon
        let result = engage_estop_inner();
        assert!(result.is_ok());
        assert!(is_engaged());

        // 4. resume_estop_inner clears the flag
        let result = resume_estop_inner();
        assert!(result.is_ok());
        assert!(!is_engaged());
    }

    #[test]
    fn test_estop_state_serialization() {
        let state = EstopStateFile {
            kill_all: true,
            engaged_at: Some("2026-03-01T12:00:00Z".to_string()),
        };
        let json = serde_json::to_string(&state).unwrap();
        let parsed: EstopStateFile = serde_json::from_str(&json).unwrap();
        assert!(parsed.kill_all);
        assert_eq!(parsed.engaged_at.unwrap(), "2026-03-01T12:00:00Z");
    }
}
