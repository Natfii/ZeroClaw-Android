/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Cron job CRUD operations for the Android dashboard.
//!
//! Upstream v0.1.6 made the `zeroclaw::cron` module `pub(crate)`, so
//! all operations are now routed through the gateway REST API on the
//! localhost loopback (`/api/cron`).

use crate::error::FfiError;
use crate::gateway_client;

/// A cron job record suitable for transfer across the FFI boundary.
///
/// Fields are parsed from the gateway JSON response rather than the
/// upstream `CronJob` struct (which is no longer accessible).
#[derive(Debug, Clone, serde::Serialize, uniffi::Record)]
pub struct FfiCronJob {
    /// Unique identifier for this job.
    pub id: String,
    /// Cron expression (e.g. `"0 0/5 * * *"`) or one-shot delay marker.
    pub expression: String,
    /// Command string that the scheduler will execute.
    pub command: String,
    /// Epoch milliseconds of the next scheduled run.
    pub next_run_ms: i64,
    /// Epoch milliseconds of the last completed run, if any.
    pub last_run_ms: Option<i64>,
    /// Status string from the last run (e.g. `"ok"`, `"error: ..."`).
    pub last_status: Option<String>,
    /// Whether this job is currently paused (inverse of upstream `enabled`).
    pub paused: bool,
    /// Whether this job self-deletes after a single run (upstream `delete_after_run`).
    pub one_shot: bool,
}

/// Parses a cron job JSON object from the gateway response into an [`FfiCronJob`].
fn parse_job_json(obj: &serde_json::Value) -> FfiCronJob {
    let next_run_ms = obj["next_run"]
        .as_str()
        .and_then(|s| chrono::DateTime::parse_from_rfc3339(s).ok())
        .map_or(0, |dt| dt.timestamp_millis());

    let last_run_ms = obj["last_run"]
        .as_str()
        .and_then(|s| chrono::DateTime::parse_from_rfc3339(s).ok())
        .map(|dt| dt.timestamp_millis());

    let enabled = obj["enabled"].as_bool().unwrap_or(true);

    FfiCronJob {
        id: obj["id"].as_str().unwrap_or("").to_string(),
        expression: obj["expression"]
            .as_str()
            .or_else(|| obj["schedule"].as_str())
            .unwrap_or("")
            .to_string(),
        command: obj["command"].as_str().unwrap_or("").to_string(),
        next_run_ms,
        last_run_ms,
        last_status: obj["last_status"].as_str().map(String::from),
        paused: !enabled,
        one_shot: obj["delete_after_run"].as_bool().unwrap_or(false),
    }
}

/// Lists all cron jobs registered with the daemon.
pub(crate) fn list_cron_jobs_inner() -> Result<Vec<FfiCronJob>, FfiError> {
    let json = gateway_client::gateway_get("/api/cron")?;
    let jobs = json["jobs"]
        .as_array()
        .map(|arr| arr.iter().map(parse_job_json).collect())
        .unwrap_or_default();
    Ok(jobs)
}

/// Retrieves a single cron job by its identifier.
///
/// The gateway does not expose a single-job endpoint, so we fetch the
/// full list and filter. Returns `None` if the job is not found.
pub(crate) fn get_cron_job_inner(id: String) -> Result<Option<FfiCronJob>, FfiError> {
    let jobs = list_cron_jobs_inner()?;
    Ok(jobs.into_iter().find(|j| j.id == id))
}

/// Adds a new recurring cron job.
pub(crate) fn add_cron_job_inner(
    expression: String,
    command: String,
) -> Result<FfiCronJob, FfiError> {
    let body = serde_json::json!({
        "schedule": expression,
        "command": command,
    });
    let json = gateway_client::gateway_post("/api/cron", &body)?;
    Ok(parse_job_json(&json["job"]))
}

/// Adds a one-shot job that fires after the given delay string.
///
/// The gateway does not expose a dedicated one-shot endpoint. We create
/// a regular job and note that one-shot scheduling is handled internally.
pub(crate) fn add_one_shot_job_inner(
    delay: String,
    command: String,
) -> Result<FfiCronJob, FfiError> {
    let body = serde_json::json!({
        "schedule": format!("@once {delay}"),
        "command": command,
    });
    let json = gateway_client::gateway_post("/api/cron", &body)?;
    Ok(parse_job_json(&json["job"]))
}

/// Removes a cron job by its identifier.
pub(crate) fn remove_cron_job_inner(id: String) -> Result<(), FfiError> {
    let path = format!("/api/cron/{id}");
    let _ = gateway_client::gateway_delete(&path)?;
    Ok(())
}

/// Pauses a cron job so it will not fire until resumed.
///
/// The gateway does not expose a dedicated pause endpoint. This is a
/// placeholder that returns an error until the upstream API is extended.
pub(crate) fn pause_cron_job_inner(id: String) -> Result<(), FfiError> {
    // Verify the daemon is running (gateway_get will check this).
    let _ = crate::runtime::get_gateway_port()?;
    Err(FfiError::StateError {
        detail: format!(
            "pause_job not available via gateway API (job {id}); \
             upstream cron module is pub(crate) in v0.1.6"
        ),
    })
}

/// Resumes a previously paused cron job.
///
/// The gateway does not expose a dedicated resume endpoint. This is a
/// placeholder that returns an error until the upstream API is extended.
pub(crate) fn resume_cron_job_inner(id: String) -> Result<(), FfiError> {
    let _ = crate::runtime::get_gateway_port()?;
    Err(FfiError::StateError {
        detail: format!(
            "resume_job not available via gateway API (job {id}); \
             upstream cron module is pub(crate) in v0.1.6"
        ),
    })
}

#[cfg(test)]
#[allow(clippy::unwrap_used)]
mod tests {
    use super::*;

    #[test]
    fn test_list_cron_jobs_not_running() {
        let result = list_cron_jobs_inner();
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_get_cron_job_not_running() {
        let result = get_cron_job_inner("some-id".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_add_cron_job_not_running() {
        let result = add_cron_job_inner("0 * * * *".into(), "echo hello".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_add_one_shot_job_not_running() {
        let result = add_one_shot_job_inner("5m".into(), "echo once".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_remove_cron_job_not_running() {
        let result = remove_cron_job_inner("some-id".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_pause_cron_job_not_running() {
        let result = pause_cron_job_inner("some-id".into());
        assert!(result.is_err());
    }

    #[test]
    fn test_resume_cron_job_not_running() {
        let result = resume_cron_job_inner("some-id".into());
        assert!(result.is_err());
    }

    #[test]
    fn test_parse_job_json() {
        let json = serde_json::json!({
            "id": "abc-123",
            "command": "echo test",
            "next_run": "2026-01-01T00:00:00Z",
            "last_run": null,
            "last_status": "ok",
            "enabled": false,
        });
        let job = parse_job_json(&json);
        assert_eq!(job.id, "abc-123");
        assert_eq!(job.command, "echo test");
        assert!(job.paused);
        assert!(job.last_run_ms.is_none());
        assert_eq!(job.last_status.as_deref(), Some("ok"));
    }
}
