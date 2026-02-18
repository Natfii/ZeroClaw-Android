// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.util

import java.util.Locale

/** Number of cents in one dollar, used for converting dollar amounts to cents. */
internal const val CENTS_PER_DOLLAR = 100

/** Default monthly budget cap in USD when no user-configured limit is set. */
internal const val DEFAULT_MONTHLY_BUDGET_USD = 50.0

/** Progress ratio threshold at which the budget indicator turns the warning colour. */
internal const val BUDGET_WARNING_THRESHOLD = 0.8f

/** Maximum progress ratio for budget indicators, clamped to 1.0. */
internal const val MAX_PROGRESS = 1.0f

/**
 * Formats a USD cost value into a human-readable dollar string.
 *
 * Values under one dollar are shown as cents (e.g. "2\u00A2").
 * Values at or above one dollar use standard "$X.XX" format.
 *
 * @param usd Cost in US dollars.
 * @return Formatted cost string.
 */
internal fun formatUsd(usd: Double): String =
    if (usd < 1.0) {
        "${(usd * CENTS_PER_DOLLAR).toInt()}\u00A2"
    } else {
        "$${String.format(Locale.US, "%.2f", usd)}"
    }
