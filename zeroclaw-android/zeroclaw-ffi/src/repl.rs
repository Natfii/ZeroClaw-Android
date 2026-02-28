/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Rhai REPL engine for the terminal screen.
//!
//! Builds a [`rhai::Engine`] with all gateway functions registered as
//! native Rhai calls, then exposes [`eval_repl_inner`] for evaluating
//! arbitrary Rhai expressions from the Kotlin UI layer.
//!
//! Functions that return structured data serialise their results to JSON
//! via `serde_json`. Functions that return `()` produce `"ok"`.
//! Primitives (`String`, `f64`, `bool`, `i64`) are returned directly.

use std::sync::{Mutex, OnceLock};

use rhai::packages::{CorePackage, Package};
use rhai::{Array, Dynamic, Engine, EvalAltResult};

use crate::error::FfiError;
use crate::{cost, cron, events, health, memory_browse, runtime, skills, tools_browse, vision};

/// Lazily initialised Rhai engine with all gateway functions registered.
static ENGINE: OnceLock<Mutex<Engine>> = OnceLock::new();

/// Converts an [`FfiError`] into a boxed [`EvalAltResult`] for Rhai.
///
/// Rhai requires `Box<EvalAltResult>` as the error type in all fallible
/// registered functions, so the box is unavoidable.
#[allow(clippy::unnecessary_box_returns)]
fn ffi_err(e: FfiError) -> Box<EvalAltResult> {
    e.to_string().into()
}

/// Serialises a `serde::Serialize` value to a JSON string, or returns
/// a Rhai error on serialisation failure.
fn to_json<T: serde::Serialize>(value: &T) -> Result<String, Box<EvalAltResult>> {
    serde_json::to_string(value)
        .map_err(|e| -> Box<EvalAltResult> { format!("serialisation failed: {e}").into() })
}

/// Builds and returns the Rhai engine with all gateway functions registered.
///
/// Uses `Engine::new_raw()` plus `CorePackage` for a minimal footprint.
/// Each registered function calls the corresponding `_inner` function
/// directly to avoid double `catch_unwind` at the FFI boundary.
#[allow(clippy::too_many_lines)]
fn build_engine() -> Engine {
    let mut engine = Engine::new_raw();
    engine.register_global_module(CorePackage::new().as_shared_module());

    // ── Sandbox limits (defence-in-depth) ────────────────────────
    //
    // `Engine::new_raw()` already excludes filesystem, network, and OS
    // access. These limits prevent resource exhaustion from malicious or
    // accidental user expressions (infinite loops, huge allocations).
    engine.set_max_operations(100_000);
    engine.set_max_expr_depths(32, 16);
    engine.set_max_string_size(64 * 1024);
    engine.set_max_array_size(1_024);
    engine.set_max_map_size(256);
    engine.set_max_call_levels(16);

    // ── Lifecycle ────────────────────────────────────────────────

    engine.register_fn("status", || -> Result<String, Box<EvalAltResult>> {
        runtime::get_status_inner().map_err(ffi_err)
    });

    engine.register_fn("version", || -> Result<String, Box<EvalAltResult>> {
        crate::get_version().map_err(ffi_err)
    });

    // ── Messaging ────────────────────────────────────────────────

    engine.register_fn(
        "send",
        |msg: String| -> Result<String, Box<EvalAltResult>> {
            runtime::send_message_inner(msg).map_err(ffi_err)
        },
    );

    engine.register_fn(
        "send_vision",
        |text: String, images: Array, mimes: Array| -> Result<String, Box<EvalAltResult>> {
            let image_data: Vec<String> = images
                .into_iter()
                .map(|v| v.into_string().unwrap_or_default())
                .collect();
            let mime_types: Vec<String> = mimes
                .into_iter()
                .map(|v| v.into_string().unwrap_or_default())
                .collect();
            vision::send_vision_message_inner(text, image_data, mime_types).map_err(ffi_err)
        },
    );

    engine.register_fn(
        "validate_config",
        |toml_str: String| -> Result<String, Box<EvalAltResult>> {
            runtime::validate_config_inner(toml_str).map_err(ffi_err)
        },
    );

    // ── Health ────────────────────────────────────────────────────

    engine.register_fn("health", || -> Result<String, Box<EvalAltResult>> {
        let detail = health::get_health_detail_inner().map_err(ffi_err)?;
        to_json(&detail)
    });

    engine.register_fn(
        "health_component",
        |name: String| -> Result<String, Box<EvalAltResult>> {
            let component = health::get_component_health_inner(name);
            to_json(&component)
        },
    );

    engine.register_fn(
        "doctor",
        |config_toml: String, data_dir: String| -> Result<String, Box<EvalAltResult>> {
            runtime::doctor_channels_inner(config_toml, data_dir).map_err(ffi_err)
        },
    );

    // ── Cost ─────────────────────────────────────────────────────

    engine.register_fn("cost", || -> Result<String, Box<EvalAltResult>> {
        let summary = cost::get_cost_summary_inner().map_err(ffi_err)?;
        to_json(&summary)
    });

    engine.register_fn(
        "cost_daily",
        |year: i64, month: i64, day: i64| -> Result<Dynamic, Box<EvalAltResult>> {
            #[allow(clippy::cast_possible_truncation, clippy::cast_sign_loss)]
            let value = cost::get_daily_cost_inner(year as i32, month as u32, day as u32)
                .map_err(ffi_err)?;
            Ok(Dynamic::from_float(value))
        },
    );

    engine.register_fn(
        "cost_monthly",
        |year: i64, month: i64| -> Result<Dynamic, Box<EvalAltResult>> {
            #[allow(clippy::cast_possible_truncation, clippy::cast_sign_loss)]
            let value = cost::get_monthly_cost_inner(year as i32, month as u32).map_err(ffi_err)?;
            Ok(Dynamic::from_float(value))
        },
    );

    engine.register_fn(
        "budget",
        |estimated: f64| -> Result<String, Box<EvalAltResult>> {
            let status = cost::check_budget_inner(estimated).map_err(ffi_err)?;
            to_json(&status)
        },
    );

    // ── Events ───────────────────────────────────────────────────

    engine.register_fn(
        "events",
        |limit: i64| -> Result<String, Box<EvalAltResult>> {
            #[allow(clippy::cast_possible_truncation, clippy::cast_sign_loss)]
            let result = events::get_recent_events_inner(limit as u32).map_err(ffi_err)?;
            Ok(result)
        },
    );

    // ── Cron ─────────────────────────────────────────────────────

    engine.register_fn("cron_list", || -> Result<String, Box<EvalAltResult>> {
        let jobs = cron::list_cron_jobs_inner().map_err(ffi_err)?;
        to_json(&jobs)
    });

    engine.register_fn(
        "cron_get",
        |id: String| -> Result<String, Box<EvalAltResult>> {
            let job = cron::get_cron_job_inner(id).map_err(ffi_err)?;
            to_json(&job)
        },
    );

    engine.register_fn(
        "cron_add",
        |expression: String, command: String| -> Result<String, Box<EvalAltResult>> {
            let job = cron::add_cron_job_inner(expression, command).map_err(ffi_err)?;
            to_json(&job)
        },
    );

    engine.register_fn(
        "cron_oneshot",
        |delay: String, command: String| -> Result<String, Box<EvalAltResult>> {
            let job = cron::add_one_shot_job_inner(delay, command).map_err(ffi_err)?;
            to_json(&job)
        },
    );

    engine.register_fn(
        "cron_remove",
        |id: String| -> Result<String, Box<EvalAltResult>> {
            cron::remove_cron_job_inner(id).map_err(ffi_err)?;
            Ok("ok".into())
        },
    );

    engine.register_fn(
        "cron_pause",
        |id: String| -> Result<String, Box<EvalAltResult>> {
            cron::pause_cron_job_inner(id).map_err(ffi_err)?;
            Ok("ok".into())
        },
    );

    engine.register_fn(
        "cron_resume",
        |id: String| -> Result<String, Box<EvalAltResult>> {
            cron::resume_cron_job_inner(id).map_err(ffi_err)?;
            Ok("ok".into())
        },
    );

    // ── Skills ───────────────────────────────────────────────────

    engine.register_fn("skills", || -> Result<String, Box<EvalAltResult>> {
        let list = skills::list_skills_inner().map_err(ffi_err)?;
        to_json(&list)
    });

    engine.register_fn(
        "skill_tools",
        |name: String| -> Result<String, Box<EvalAltResult>> {
            let tools = skills::get_skill_tools_inner(name).map_err(ffi_err)?;
            to_json(&tools)
        },
    );

    engine.register_fn(
        "skill_install",
        |source: String| -> Result<String, Box<EvalAltResult>> {
            skills::install_skill_inner(source).map_err(ffi_err)?;
            Ok("ok".into())
        },
    );

    engine.register_fn(
        "skill_remove",
        |name: String| -> Result<String, Box<EvalAltResult>> {
            skills::remove_skill_inner(name).map_err(ffi_err)?;
            Ok("ok".into())
        },
    );

    // ── Tools ────────────────────────────────────────────────────

    engine.register_fn("tools", || -> Result<String, Box<EvalAltResult>> {
        let list = tools_browse::list_tools_inner().map_err(ffi_err)?;
        to_json(&list)
    });

    // ── Memory ───────────────────────────────────────────────────

    engine.register_fn(
        "memories",
        |limit: i64| -> Result<String, Box<EvalAltResult>> {
            #[allow(clippy::cast_possible_truncation, clippy::cast_sign_loss)]
            let entries =
                memory_browse::list_memories_inner(None, limit as u32, None).map_err(ffi_err)?;
            to_json(&entries)
        },
    );

    engine.register_fn(
        "memories_by_category",
        |category: String, limit: i64| -> Result<String, Box<EvalAltResult>> {
            #[allow(clippy::cast_possible_truncation, clippy::cast_sign_loss)]
            let entries = memory_browse::list_memories_inner(Some(category), limit as u32, None)
                .map_err(ffi_err)?;
            to_json(&entries)
        },
    );

    engine.register_fn(
        "memory_recall",
        |query: String, limit: i64| -> Result<String, Box<EvalAltResult>> {
            #[allow(clippy::cast_possible_truncation, clippy::cast_sign_loss)]
            let entries =
                memory_browse::recall_memory_inner(query, limit as u32, None).map_err(ffi_err)?;
            to_json(&entries)
        },
    );

    engine.register_fn(
        "memory_forget",
        |key: String| -> Result<bool, Box<EvalAltResult>> {
            memory_browse::forget_memory_inner(key).map_err(ffi_err)
        },
    );

    engine.register_fn("memory_count", || -> Result<i64, Box<EvalAltResult>> {
        let count = memory_browse::memory_count_inner().map_err(ffi_err)?;
        Ok(i64::from(count))
    });

    engine
}

/// Evaluates a Rhai expression against the shared engine.
///
/// Initialises the engine on first call. The `Dynamic` result is
/// converted to a `String` according to these rules:
///
/// - Strings are returned as-is.
/// - Booleans, integers, and floats are converted via `to_string()`.
/// - Unit `()` becomes `"ok"`.
/// - All other types use `to_string()` as a fallback.
///
/// # Errors
///
/// Returns [`FfiError::SpawnError`] if the engine mutex is poisoned or
/// if the Rhai evaluation produces an error.
pub(crate) fn eval_repl_inner(expression: String) -> Result<String, FfiError> {
    let engine_mutex = ENGINE.get_or_init(|| Mutex::new(build_engine()));
    let engine = engine_mutex.lock().map_err(|_| FfiError::StateCorrupted {
        detail: "REPL engine mutex poisoned".into(),
    })?;

    let result: Dynamic =
        engine
            .eval::<Dynamic>(&expression)
            .map_err(|e| FfiError::SpawnError {
                detail: format!("eval error: {e}"),
            })?;

    Ok(dynamic_to_string(result))
}

/// Converts a Rhai [`Dynamic`] value to a display string.
fn dynamic_to_string(value: Dynamic) -> String {
    if value.is_unit() {
        return "ok".into();
    }
    if let Ok(s) = value.clone().into_string() {
        return s;
    }
    value.to_string()
}

#[cfg(test)]
#[allow(clippy::unwrap_used)]
mod tests {
    use super::*;

    #[test]
    fn test_eval_arithmetic() {
        let result = eval_repl_inner("2 + 3".into()).unwrap();
        assert_eq!(result, "5");
    }

    #[test]
    fn test_eval_string_literal() {
        let result = eval_repl_inner(r#""hello""#.into()).unwrap();
        assert_eq!(result, "hello");
    }

    #[test]
    fn test_eval_boolean() {
        let result = eval_repl_inner("true".into()).unwrap();
        assert_eq!(result, "true");
    }

    #[test]
    fn test_eval_unit() {
        let result = eval_repl_inner("let x = 1;".into()).unwrap();
        assert_eq!(result, "ok");
    }

    #[test]
    fn test_eval_float() {
        let result = eval_repl_inner("1.5 + 2.5".into()).unwrap();
        assert_eq!(result, "4.0");
    }

    #[test]
    fn test_eval_syntax_error() {
        let result = eval_repl_inner("{{{{invalid".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::SpawnError { detail } => {
                assert!(detail.contains("eval error"));
            }
            other => panic!("expected SpawnError, got {other:?}"),
        }
    }

    #[test]
    fn test_version_returns_string() {
        let result = eval_repl_inner("version()".into()).unwrap();
        assert_eq!(result, "0.0.29");
    }

    #[test]
    fn test_status_returns_json() {
        let result = eval_repl_inner("status()".into()).unwrap();
        let parsed: serde_json::Value = serde_json::from_str(&result).unwrap();
        assert!(parsed.get("daemon_running").is_some());
    }

    #[test]
    fn test_validate_config_valid() {
        let result =
            eval_repl_inner(r#"validate_config("default_temperature = 0.7\n")"#.into()).unwrap();
        assert!(
            result.is_empty(),
            "expected empty for valid config, got: {result}"
        );
    }

    #[test]
    fn test_validate_config_invalid() {
        let result = eval_repl_inner(r#"validate_config("not valid {{{{")"#.into()).unwrap();
        assert!(
            !result.is_empty(),
            "expected error message for invalid config"
        );
    }

    #[test]
    fn test_send_not_running() {
        let result = eval_repl_inner(r#"send("hello")"#.into());
        assert!(result.is_err());
    }

    #[test]
    fn test_memory_count_not_running() {
        let result = eval_repl_inner("memory_count()".into());
        assert!(result.is_err());
    }

    #[test]
    fn test_dynamic_to_string_unit() {
        assert_eq!(dynamic_to_string(Dynamic::UNIT), "ok");
    }

    #[test]
    fn test_dynamic_to_string_string() {
        assert_eq!(dynamic_to_string(Dynamic::from("abc".to_string())), "abc");
    }

    #[test]
    fn test_dynamic_to_string_int() {
        assert_eq!(dynamic_to_string(Dynamic::from(42_i64)), "42");
    }

    #[test]
    fn test_dynamic_to_string_bool() {
        assert_eq!(dynamic_to_string(Dynamic::from(true)), "true");
    }

    #[test]
    fn test_dynamic_to_string_float() {
        let result = dynamic_to_string(Dynamic::from_float(1.23));
        assert!(result.starts_with("1.23"));
    }

    #[test]
    fn test_sandbox_blocks_infinite_loop() {
        let result = eval_repl_inner("loop { }".into());
        assert!(
            result.is_err(),
            "infinite loop must be terminated by sandbox"
        );
    }

    #[test]
    fn test_sandbox_blocks_deep_recursion() {
        let expr = r"fn recurse(n) { recurse(n + 1) } recurse(0)";
        let result = eval_repl_inner(expr.into());
        assert!(
            result.is_err(),
            "deep recursion must be terminated by sandbox"
        );
    }
}
