/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Cron job CRUD operations for the Android dashboard.
//!
//! Wraps the upstream `zeroclaw::cron` module behind [`FfiCronJob`] records
//! and typed inner functions suitable for export via UniFFI.

use crate::error::FfiError;
use crate::types;

/// A cron job record suitable for transfer across the FFI boundary.
///
/// Mirrors the upstream [`zeroclaw::cron::CronJob`] but replaces
/// `chrono::DateTime<Utc>` fields with epoch-millisecond integers
/// so that Kotlin can consume them directly as `Long`.
#[derive(Debug, Clone, uniffi::Record)]
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
    /// Whether this job is currently paused.
    pub paused: bool,
    /// Whether this is a one-shot job that fires once then self-removes.
    pub one_shot: bool,
}

/// Converts an upstream [`zeroclaw::cron::CronJob`] to an [`FfiCronJob`].
fn to_ffi(job: &zeroclaw::cron::CronJob) -> FfiCronJob {
    FfiCronJob {
        id: job.id.clone(),
        expression: job.expression.clone(),
        command: job.command.clone(),
        next_run_ms: types::to_epoch_ms(&job.next_run),
        last_run_ms: types::opt_to_epoch_ms(job.last_run.as_ref()),
        last_status: job.last_status.clone(),
        paused: job.paused,
        one_shot: job.one_shot,
    }
}

/// Lists all cron jobs registered with the daemon.
pub(crate) fn list_cron_jobs_inner() -> Result<Vec<FfiCronJob>, FfiError> {
    crate::runtime::with_daemon_config(|config| {
        zeroclaw::cron::list_jobs(config)
            .map(|jobs| jobs.iter().map(to_ffi).collect())
            .map_err(|e| FfiError::SpawnError {
                detail: format!("list_jobs failed: {e}"),
            })
    })?
}

/// Retrieves a single cron job by its identifier.
pub(crate) fn get_cron_job_inner(id: String) -> Result<Option<FfiCronJob>, FfiError> {
    crate::runtime::with_daemon_config(|config| {
        zeroclaw::cron::get_job(config, &id)
            .map(|opt| opt.as_ref().map(to_ffi))
            .map_err(|e| FfiError::SpawnError {
                detail: format!("get_job failed: {e}"),
            })
    })?
}

/// Adds a new recurring cron job.
pub(crate) fn add_cron_job_inner(
    expression: String,
    command: String,
) -> Result<FfiCronJob, FfiError> {
    crate::runtime::with_daemon_config(|config| {
        zeroclaw::cron::add_job(config, &expression, &command)
            .map(|job| to_ffi(&job))
            .map_err(|e| FfiError::SpawnError {
                detail: format!("add_job failed: {e}"),
            })
    })?
}

/// Adds a one-shot job that fires after the given delay string.
pub(crate) fn add_one_shot_job_inner(
    delay: String,
    command: String,
) -> Result<FfiCronJob, FfiError> {
    crate::runtime::with_daemon_config(|config| {
        zeroclaw::cron::add_once(config, &delay, &command)
            .map(|job| to_ffi(&job))
            .map_err(|e| FfiError::SpawnError {
                detail: format!("add_once failed: {e}"),
            })
    })?
}

/// Removes a cron job by its identifier.
pub(crate) fn remove_cron_job_inner(id: String) -> Result<(), FfiError> {
    crate::runtime::with_daemon_config(|config| {
        zeroclaw::cron::remove_job(config, &id).map_err(|e| FfiError::SpawnError {
            detail: format!("remove_job failed: {e}"),
        })
    })?
}

/// Pauses a cron job so it will not fire until resumed.
pub(crate) fn pause_cron_job_inner(id: String) -> Result<(), FfiError> {
    crate::runtime::with_daemon_config(|config| {
        zeroclaw::cron::pause_job(config, &id).map_err(|e| FfiError::SpawnError {
            detail: format!("pause_job failed: {e}"),
        })
    })?
}

/// Resumes a previously paused cron job.
pub(crate) fn resume_cron_job_inner(id: String) -> Result<(), FfiError> {
    crate::runtime::with_daemon_config(|config| {
        zeroclaw::cron::resume_job(config, &id).map_err(|e| FfiError::SpawnError {
            detail: format!("resume_job failed: {e}"),
        })
    })?
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
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_resume_cron_job_not_running() {
        let result = resume_cron_job_inner("some-id".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }
}
