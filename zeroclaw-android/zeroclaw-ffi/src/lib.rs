/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

#![deny(missing_docs)]

//! UniFFI-annotated facade for `ZeroClaw` Android bindings.
//!
//! This crate provides a thin FFI layer over the `ZeroClaw` daemon,
//! exposing daemon lifecycle, health, cost, events, cron, skills, tools,
//! and memory browsing functions to Kotlin via UniFFI-generated bindings.

uniffi::setup_scaffolding!();

mod cost;
mod cron;
mod error;
mod events;
mod health;
mod memory_browse;
mod runtime;
mod skills;
mod tools_browse;
mod types;
mod vision;
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

/// Lists all cron jobs registered with the running daemon.
///
/// Requires the daemon to be running so the cron SQLite database is accessible.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// [`FfiError::SpawnError`] on database access failure, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn list_cron_jobs() -> Result<Vec<cron::FfiCronJob>, FfiError> {
    catch_unwind(cron::list_cron_jobs_inner).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Retrieves a single cron job by its identifier.
///
/// Returns `None` if no job with the given `id` exists.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// [`FfiError::SpawnError`] on database access failure, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn get_cron_job(id: String) -> Result<Option<cron::FfiCronJob>, FfiError> {
    catch_unwind(AssertUnwindSafe(|| cron::get_cron_job_inner(id))).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Adds a new recurring cron job with the given expression and command.
///
/// The `expression` must be a valid cron expression (e.g. `"0 0/5 * * *"`).
/// The `command` is the prompt or action the scheduler will execute.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// [`FfiError::SpawnError`] on invalid expression or database failure, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn add_cron_job(expression: String, command: String) -> Result<cron::FfiCronJob, FfiError> {
    catch_unwind(AssertUnwindSafe(|| {
        cron::add_cron_job_inner(expression, command)
    }))
    .unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Adds a one-shot job that fires once after the given delay.
///
/// The `delay` string uses human-readable durations (e.g. `"5m"`, `"2h"`).
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// [`FfiError::SpawnError`] on invalid delay or database failure, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn add_one_shot_job(delay: String, command: String) -> Result<cron::FfiCronJob, FfiError> {
    catch_unwind(AssertUnwindSafe(|| {
        cron::add_one_shot_job_inner(delay, command)
    }))
    .unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Removes a cron job by its identifier.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// [`FfiError::SpawnError`] if the job does not exist or database fails, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn remove_cron_job(id: String) -> Result<(), FfiError> {
    catch_unwind(AssertUnwindSafe(|| cron::remove_cron_job_inner(id))).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Pauses a cron job so it will not fire until resumed.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// [`FfiError::SpawnError`] if the job does not exist or database fails, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn pause_cron_job(id: String) -> Result<(), FfiError> {
    catch_unwind(AssertUnwindSafe(|| cron::pause_cron_job_inner(id))).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Resumes a previously paused cron job.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// [`FfiError::SpawnError`] if the job does not exist or database fails, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn resume_cron_job(id: String) -> Result<(), FfiError> {
    catch_unwind(AssertUnwindSafe(|| cron::resume_cron_job_inner(id))).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Lists all skills loaded from the workspace's `skills/` directory.
///
/// Each skill includes its name, description, version, author, tags,
/// and the names of any tools it provides.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn list_skills() -> Result<Vec<skills::FfiSkill>, FfiError> {
    catch_unwind(skills::list_skills_inner).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Lists the tools provided by a specific skill.
///
/// Returns an empty list if the skill is not found or has no tools.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn get_skill_tools(skill_name: String) -> Result<Vec<skills::FfiSkillTool>, FfiError> {
    catch_unwind(AssertUnwindSafe(|| {
        skills::get_skill_tools_inner(skill_name)
    }))
    .unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Installs a skill from a URL or local path.
///
/// For URLs, performs a `git clone --depth 1` into the skills directory.
/// For local paths, creates a symlink (or copies on platforms without
/// symlink support).
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// [`FfiError::SpawnError`] on install failure, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn install_skill(source: String) -> Result<(), FfiError> {
    catch_unwind(AssertUnwindSafe(|| skills::install_skill_inner(source))).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Removes an installed skill by name.
///
/// Deletes the skill directory from the workspace's `skills/` folder.
/// Path traversal attempts are rejected.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// [`FfiError::SpawnError`] if removal fails, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn remove_skill(name: String) -> Result<(), FfiError> {
    catch_unwind(AssertUnwindSafe(|| skills::remove_skill_inner(name))).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Lists all available tools based on daemon config and installed skills.
///
/// Returns built-in tools (always present), conditional tools (browser,
/// HTTP, Composio, delegate), and skill-provided tools.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn list_tools() -> Result<Vec<tools_browse::FfiToolSpec>, FfiError> {
    catch_unwind(tools_browse::list_tools_inner).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Lists memory entries, optionally filtered by category.
///
/// Categories: `"core"`, `"daily"`, `"conversation"`, or any custom
/// category name. Pass `None` for all categories.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running or
/// memory is unavailable,
/// [`FfiError::SpawnError`] on backend failure, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn list_memories(
    category: Option<String>,
    limit: u32,
) -> Result<Vec<memory_browse::FfiMemoryEntry>, FfiError> {
    catch_unwind(AssertUnwindSafe(|| {
        memory_browse::list_memories_inner(category, limit)
    }))
    .unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Searches memory entries by keyword query.
///
/// Returns up to `limit` entries ranked by relevance.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running or
/// memory is unavailable,
/// [`FfiError::SpawnError`] on backend failure, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn recall_memory(
    query: String,
    limit: u32,
) -> Result<Vec<memory_browse::FfiMemoryEntry>, FfiError> {
    catch_unwind(AssertUnwindSafe(|| {
        memory_browse::recall_memory_inner(query, limit)
    }))
    .unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Deletes a memory entry by key.
///
/// Returns `true` if the entry was found and deleted, `false` otherwise.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running or
/// memory is unavailable,
/// [`FfiError::SpawnError`] on backend failure, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn forget_memory(key: String) -> Result<bool, FfiError> {
    catch_unwind(AssertUnwindSafe(|| memory_browse::forget_memory_inner(key))).unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Sends a vision (image + text) message directly to the configured provider.
///
/// Bypasses `ZeroClaw`'s text-only agent loop and calls the provider's
/// multimodal API directly. `image_data` contains base64-encoded images
/// and `mime_types` contains the corresponding MIME type for each image.
///
/// # Errors
///
/// Returns [`FfiError::ConfigError`] for validation failures,
/// [`FfiError::StateError`] if the daemon is not running,
/// [`FfiError::SpawnError`] for unsupported providers or HTTP failures, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn send_vision_message(
    text: String,
    image_data: Vec<String>,
    mime_types: Vec<String>,
) -> Result<String, FfiError> {
    catch_unwind(AssertUnwindSafe(|| {
        vision::send_vision_message_inner(text, image_data, mime_types)
    }))
    .unwrap_or_else(|e| {
        Err(FfiError::InternalPanic {
            detail: panic_detail(&e),
        })
    })
}

/// Returns the total number of memory entries.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running or
/// memory is unavailable,
/// [`FfiError::SpawnError`] on backend failure, or
/// [`FfiError::InternalPanic`] if native code panics.
#[uniffi::export]
pub fn memory_count() -> Result<u32, FfiError> {
    catch_unwind(memory_browse::memory_count_inner).unwrap_or_else(|e| {
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
        assert_eq!(version, "0.0.10");
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
