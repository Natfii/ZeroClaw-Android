/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * Status outcome of a single diagnostic check.
 */
enum class CheckStatus {
    /** Check passed without issues. */
    PASS,

    /** Check completed with non-critical warnings. */
    WARN,

    /** Check failed, indicating a problem that needs attention. */
    FAIL,

    /** Check is currently executing. */
    RUNNING,
}

/**
 * Category grouping for diagnostic checks.
 */
enum class DiagnosticCategory {
    /** TOML configuration parsing and validation. */
    CONFIG,

    /** API key presence and status checks. */
    API_KEYS,

    /** Network and channel connectivity checks. */
    CONNECTIVITY,

    /** Daemon and component health checks. */
    DAEMON_HEALTH,

    /** System-level prerequisites (battery, storage, permissions). */
    SYSTEM,
}

/**
 * Result of a single diagnostic check run by [DoctorValidator][com.zeroclaw.android.service.DoctorValidator].
 *
 * @property id Unique identifier for this check result.
 * @property category Grouping category for display in collapsible sections.
 * @property title Human-readable check name.
 * @property status Outcome of the check.
 * @property detail Additional context about the result (error message, measured value, etc.).
 * @property actionLabel Optional label for a remediation action button.
 * @property actionRoute Optional navigation route string for the action button.
 */
data class DiagnosticCheck(
    val id: String,
    val category: DiagnosticCategory,
    val title: String,
    val status: CheckStatus,
    val detail: String = "",
    val actionLabel: String? = null,
    val actionRoute: String? = null,
)

/**
 * Aggregated summary of diagnostic check outcomes.
 *
 * @property passCount Number of checks that passed.
 * @property warnCount Number of checks with warnings.
 * @property failCount Number of checks that failed.
 */
data class DoctorSummary(
    val passCount: Int,
    val warnCount: Int,
    val failCount: Int,
) {
    /** Factory methods for [DoctorSummary]. */
    companion object {
        /**
         * Computes a summary from a list of completed checks.
         *
         * [CheckStatus.RUNNING] checks are excluded from the counts.
         *
         * @param checks The diagnostic checks to summarize.
         * @return Aggregated [DoctorSummary].
         */
        fun from(checks: List<DiagnosticCheck>): DoctorSummary =
            DoctorSummary(
                passCount = checks.count { it.status == CheckStatus.PASS },
                warnCount = checks.count { it.status == CheckStatus.WARN },
                failCount = checks.count { it.status == CheckStatus.FAIL },
            )
    }
}
