/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Cost tracking and budget monitoring for the Android dashboard.
//!
//! Upstream v0.1.6 made the `zeroclaw::cost` module `pub(crate)`, so
//! cost data is now accessed through the gateway REST API (`/api/cost`).
//! Daily/monthly cost breakdowns and budget checks are derived from the
//! summary data returned by the gateway.

use crate::error::FfiError;
use crate::gateway_client;

/// Aggregated cost summary across session, day, and month.
#[derive(Debug, Clone, serde::Serialize, uniffi::Record)]
pub struct FfiCostSummary {
    /// Total cost for the current session in USD.
    pub session_cost_usd: f64,
    /// Total cost for today in USD.
    pub daily_cost_usd: f64,
    /// Total cost for the current month in USD.
    pub monthly_cost_usd: f64,
    /// Total tokens consumed across all requests.
    pub total_tokens: u64,
    /// Number of requests made.
    pub request_count: u32,
    /// JSON array of per-model breakdowns.
    pub model_breakdown_json: String,
}

/// Budget check result.
#[derive(Debug, Clone, serde::Serialize, uniffi::Enum)]
pub enum FfiBudgetStatus {
    /// Within budget limits.
    Allowed,
    /// Approaching budget limit.
    Warning {
        /// Current spending in USD.
        current_usd: f64,
        /// Budget limit in USD.
        limit_usd: f64,
        /// Period name: "session", "day", or "month".
        period: String,
    },
    /// Budget exceeded.
    Exceeded {
        /// Current spending in USD.
        current_usd: f64,
        /// Budget limit in USD.
        limit_usd: f64,
        /// Period name: "session", "day", or "month".
        period: String,
    },
}

/// Returns the current cost summary via the gateway REST API.
pub(crate) fn get_cost_summary_inner() -> Result<FfiCostSummary, FfiError> {
    let json = gateway_client::gateway_get("/api/cost")?;
    let cost = &json["cost"];

    let by_model = cost["by_model"].as_object();
    let model_breakdown: Vec<serde_json::Value> = by_model
        .map(|m| {
            m.iter()
                .map(|(model, stats)| {
                    serde_json::json!({
                        "model": model,
                        "cost_usd": stats["cost_usd"].as_f64().unwrap_or(0.0),
                        "tokens": stats["total_tokens"].as_u64().unwrap_or(0),
                        "requests": stats["request_count"].as_u64().unwrap_or(0),
                    })
                })
                .collect()
        })
        .unwrap_or_default();

    Ok(FfiCostSummary {
        session_cost_usd: cost["session_cost_usd"].as_f64().unwrap_or(0.0),
        daily_cost_usd: cost["daily_cost_usd"].as_f64().unwrap_or(0.0),
        monthly_cost_usd: cost["monthly_cost_usd"].as_f64().unwrap_or(0.0),
        total_tokens: cost["total_tokens"].as_u64().unwrap_or(0),
        request_count: cost["request_count"]
            .as_u64()
            .map_or(0, |n| u32::try_from(n).unwrap_or(u32::MAX)),
        model_breakdown_json: serde_json::to_string(&model_breakdown)
            .unwrap_or_else(|_| "[]".into()),
    })
}

/// Returns the cost for a specific day.
///
/// The gateway `/api/cost` does not expose per-day breakdowns, so this
/// returns the daily total from the summary when the requested date is
/// today, or zero otherwise.
pub(crate) fn get_daily_cost_inner(year: i32, month: u32, day: u32) -> Result<f64, FfiError> {
    // Verify daemon is running before doing anything.
    let _ = crate::runtime::get_gateway_port()?;

    let today = chrono::Utc::now().date_naive();
    let requested =
        chrono::NaiveDate::from_ymd_opt(year, month, day).ok_or_else(|| FfiError::ConfigError {
            detail: format!("invalid date: {year}-{month}-{day}"),
        })?;

    if requested == today {
        let summary = get_cost_summary_inner()?;
        Ok(summary.daily_cost_usd)
    } else {
        Ok(0.0)
    }
}

/// Returns the cost for a specific month.
///
/// The gateway `/api/cost` does not expose per-month breakdowns, so
/// this returns the monthly total from the summary when the requested
/// month matches the current month, or zero otherwise.
pub(crate) fn get_monthly_cost_inner(year: i32, month: u32) -> Result<f64, FfiError> {
    // Verify daemon is running before doing anything.
    let _ = crate::runtime::get_gateway_port()?;

    let now = chrono::Utc::now();
    #[allow(clippy::cast_possible_wrap)]
    let current_year = chrono::Datelike::year(&now);
    let current_month = chrono::Datelike::month(&now);

    if year == current_year && month == current_month {
        let summary = get_cost_summary_inner()?;
        Ok(summary.monthly_cost_usd)
    } else {
        Ok(0.0)
    }
}

/// Checks the budget for an estimated cost.
///
/// Derives a client-side budget check from the cost summary (via the
/// gateway `/api/cost` endpoint) and the config's budget limits
/// (`daily_limit_usd`, `monthly_limit_usd`, `warn_at_percent`).
///
/// Returns [`FfiBudgetStatus::Allowed`] when cost tracking is disabled
/// in the config or spending is within limits. Returns
/// [`FfiBudgetStatus::Warning`] when spending plus the estimated cost
/// exceeds the warning threshold. Returns [`FfiBudgetStatus::Exceeded`]
/// when spending plus the estimated cost exceeds the hard limit.
pub(crate) fn check_budget_inner(estimated_cost_usd: f64) -> Result<FfiBudgetStatus, FfiError> {
    let (cost_enabled, daily_limit, monthly_limit, warn_percent) =
        crate::runtime::with_daemon_config(|config| {
            (
                config.cost.enabled,
                config.cost.daily_limit_usd,
                config.cost.monthly_limit_usd,
                config.cost.warn_at_percent,
            )
        })?;

    if !cost_enabled {
        return Ok(FfiBudgetStatus::Allowed);
    }

    let summary = get_cost_summary_inner()?;

    let warn_threshold = f64::from(warn_percent) / 100.0;

    let daily_projected = summary.daily_cost_usd + estimated_cost_usd;
    if daily_projected > daily_limit {
        return Ok(FfiBudgetStatus::Exceeded {
            current_usd: summary.daily_cost_usd,
            limit_usd: daily_limit,
            period: "day".into(),
        });
    }

    let monthly_projected = summary.monthly_cost_usd + estimated_cost_usd;
    if monthly_projected > monthly_limit {
        return Ok(FfiBudgetStatus::Exceeded {
            current_usd: summary.monthly_cost_usd,
            limit_usd: monthly_limit,
            period: "month".into(),
        });
    }

    if daily_projected > daily_limit * warn_threshold {
        return Ok(FfiBudgetStatus::Warning {
            current_usd: summary.daily_cost_usd,
            limit_usd: daily_limit,
            period: "day".into(),
        });
    }

    if monthly_projected > monthly_limit * warn_threshold {
        return Ok(FfiBudgetStatus::Warning {
            current_usd: summary.monthly_cost_usd,
            limit_usd: monthly_limit,
            period: "month".into(),
        });
    }

    Ok(FfiBudgetStatus::Allowed)
}

#[cfg(test)]
#[allow(clippy::unwrap_used)]
mod tests {
    use super::*;

    #[test]
    fn test_get_cost_summary_not_running() {
        let result = get_cost_summary_inner();
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_check_budget_not_running() {
        let result = check_budget_inner(1.0);
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_get_daily_cost_not_running() {
        let result = get_daily_cost_inner(2026, 1, 1);
        assert!(result.is_err());
    }

    #[test]
    fn test_get_monthly_cost_not_running() {
        let result = get_monthly_cost_inner(2026, 1);
        assert!(result.is_err());
    }

    #[test]
    fn test_get_daily_cost_invalid_date() {
        // Invalid date should error even before reaching the gateway
        let result = get_daily_cost_inner(2026, 13, 1);
        assert!(result.is_err());
    }
}
