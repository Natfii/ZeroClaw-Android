/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * Budget enforcement state for a configured spending limit, mirroring the Rust `FfiBudgetStatus`
 * enum.
 *
 * Use exhaustive `when` expressions over this sealed interface to handle all three states without
 * a fallback branch.
 */
sealed interface BudgetStatus {
    /**
     * Spending is within the configured limit; requests may proceed normally.
     */
    data object Allowed : BudgetStatus

    /**
     * Spending is approaching the configured limit; requests are still permitted but a warning
     * should be surfaced to the user.
     *
     * @property currentUsd Accumulated spend so far in the measurement period, in USD.
     * @property limitUsd The configured spending cap for the measurement period, in USD.
     * @property period Human-readable period label (e.g. "daily", "monthly").
     */
    data class Warning(
        val currentUsd: Double,
        val limitUsd: Double,
        val period: String,
    ) : BudgetStatus

    /**
     * Spending has exceeded the configured limit; new requests are blocked until the period resets
     * or the limit is raised.
     *
     * @property currentUsd Accumulated spend so far in the measurement period, in USD.
     * @property limitUsd The configured spending cap for the measurement period, in USD.
     * @property period Human-readable period label (e.g. "daily", "monthly").
     */
    data class Exceeded(
        val currentUsd: Double,
        val limitUsd: Double,
        val period: String,
    ) : BudgetStatus
}
