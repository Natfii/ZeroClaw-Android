/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Cost tracking and budget monitoring for the Android dashboard.

use crate::error::FfiError;

/// Aggregated cost summary across session, day, and month.
#[derive(Debug, Clone, uniffi::Record)]
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
#[derive(Debug, Clone, uniffi::Enum)]
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

/// Returns the current cost summary.
pub(crate) fn get_cost_summary_inner() -> Result<FfiCostSummary, FfiError> {
    crate::runtime::with_cost_tracker(|tracker| {
        let summary = tracker.get_summary()?;
        let model_breakdown: Vec<serde_json::Value> = summary
            .by_model
            .values()
            .map(|ms| {
                serde_json::json!({
                    "model": ms.model,
                    "cost_usd": ms.cost_usd,
                    "tokens": ms.total_tokens,
                    "requests": ms.request_count,
                })
            })
            .collect();
        Ok(FfiCostSummary {
            session_cost_usd: summary.session_cost_usd,
            daily_cost_usd: summary.daily_cost_usd,
            monthly_cost_usd: summary.monthly_cost_usd,
            total_tokens: summary.total_tokens,
            request_count: u32::try_from(summary.request_count).unwrap_or(u32::MAX),
            model_breakdown_json: serde_json::to_string(&model_breakdown)
                .unwrap_or_else(|_| "[]".into()),
        })
    })
}

/// Returns the cost for a specific day.
pub(crate) fn get_daily_cost_inner(year: i32, month: u32, day: u32) -> Result<f64, FfiError> {
    crate::runtime::with_cost_tracker(|tracker| {
        let date = chrono::NaiveDate::from_ymd_opt(year, month, day)
            .ok_or_else(|| anyhow::anyhow!("invalid date: {year}-{month}-{day}"))?;
        tracker.get_daily_cost(date)
    })
}

/// Returns the cost for a specific month.
pub(crate) fn get_monthly_cost_inner(year: i32, month: u32) -> Result<f64, FfiError> {
    crate::runtime::with_cost_tracker(|tracker| tracker.get_monthly_cost(year, month))
}

/// Checks the budget for an estimated cost.
pub(crate) fn check_budget_inner(estimated_cost_usd: f64) -> Result<FfiBudgetStatus, FfiError> {
    crate::runtime::with_cost_tracker(|tracker| {
        let check = tracker.check_budget(estimated_cost_usd)?;
        Ok(match check {
            zeroclaw::cost::BudgetCheck::Allowed => FfiBudgetStatus::Allowed,
            zeroclaw::cost::BudgetCheck::Warning {
                current_usd,
                limit_usd,
                period,
            } => FfiBudgetStatus::Warning {
                current_usd,
                limit_usd,
                period: format_period(period),
            },
            zeroclaw::cost::BudgetCheck::Exceeded {
                current_usd,
                limit_usd,
                period,
            } => FfiBudgetStatus::Exceeded {
                current_usd,
                limit_usd,
                period: format_period(period),
            },
        })
    })
}

/// Converts a [`UsagePeriod`](zeroclaw::cost::UsagePeriod) to a string for FFI.
fn format_period(period: zeroclaw::cost::UsagePeriod) -> String {
    match period {
        zeroclaw::cost::UsagePeriod::Session => "session".into(),
        zeroclaw::cost::UsagePeriod::Day => "day".into(),
        zeroclaw::cost::UsagePeriod::Month => "month".into(),
    }
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
}
