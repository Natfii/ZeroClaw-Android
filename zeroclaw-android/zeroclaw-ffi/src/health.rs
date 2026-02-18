/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Structured health detail for the Android dashboard.

use crate::error::FfiError;

/// Per-component health status.
#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiComponentHealth {
    /// Component name (e.g. "gateway", "scheduler").
    pub name: String,
    /// Status string: "ok", "error", or "starting".
    pub status: String,
    /// Last error message, if any.
    pub last_error: Option<String>,
    /// Number of times this component has been restarted.
    pub restart_count: u64,
}

/// Full daemon health detail with per-component breakdown.
#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiHealthDetail {
    /// Whether the daemon process is currently running.
    pub daemon_running: bool,
    /// Process ID of the host application.
    pub pid: u32,
    /// Daemon uptime in seconds.
    pub uptime_seconds: u64,
    /// Health status of each supervised component.
    pub components: Vec<FfiComponentHealth>,
}

/// Returns structured health detail for all daemon components.
pub(crate) fn get_health_detail_inner() -> Result<FfiHealthDetail, FfiError> {
    let daemon_running = crate::runtime::is_daemon_running()?;

    let snapshot = zeroclaw::health::snapshot();
    let components = snapshot
        .components
        .into_iter()
        .map(|(name, ch)| FfiComponentHealth {
            name,
            status: ch.status,
            last_error: ch.last_error,
            restart_count: ch.restart_count,
        })
        .collect();

    Ok(FfiHealthDetail {
        daemon_running,
        pid: snapshot.pid,
        uptime_seconds: snapshot.uptime_seconds,
        components,
    })
}

/// Returns health for a single named component.
pub(crate) fn get_component_health_inner(name: String) -> Option<FfiComponentHealth> {
    let snapshot = zeroclaw::health::snapshot();
    snapshot.components.get(&name).map(|ch| FfiComponentHealth {
        name,
        status: ch.status.clone(),
        last_error: ch.last_error.clone(),
        restart_count: ch.restart_count,
    })
}

#[cfg(test)]
#[allow(clippy::unwrap_used)]
mod tests {
    use super::*;

    #[test]
    fn test_get_health_detail_returns_struct() {
        let detail = get_health_detail_inner().unwrap();
        // Daemon is not running in tests
        assert!(!detail.daemon_running);
        assert_eq!(detail.pid, std::process::id());
    }

    #[test]
    fn test_get_component_health_missing() {
        let result = get_component_health_inner("nonexistent".into());
        assert!(result.is_none());
    }
}
