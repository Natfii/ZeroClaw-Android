/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * Aggregated cost and token-usage figures for the current daemon session, mirroring the Rust
 * `FfiCostSummary` type.
 *
 * All monetary values are expressed in United States dollars. Token counts are accumulated across
 * all providers active during the measurement window.
 *
 * @property sessionCostUsd Total cost incurred during the current daemon session, in USD.
 * @property dailyCostUsd Rolling 24-hour cost total, in USD.
 * @property monthlyCostUsd Rolling 30-day cost total, in USD.
 * @property totalTokens Cumulative token count across all requests in the session. The underlying
 *   Rust type is `u64`; values are mapped to [Long] for JVM compatibility.
 * @property requestCount Total number of inference requests made in the session. The underlying
 *   Rust type is `u32`; values are mapped to [Int] for JVM compatibility.
 * @property modelBreakdownJson JSON string containing a per-model cost breakdown. Consumers
 *   must parse this field themselves using an appropriate JSON library.
 */
data class CostSummary(
    val sessionCostUsd: Double,
    val dailyCostUsd: Double,
    val monthlyCostUsd: Double,
    val totalTokens: Long,
    val requestCount: Int,
    val modelBreakdownJson: String,
)
