/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * Persistent application settings.
 *
 * @property host Gateway bind address.
 * @property port Gateway bind port.
 * @property autoStartOnBoot Whether to start the daemon automatically after reboot.
 * @property logLevel Minimum severity for log output.
 * @property defaultProvider Default provider ID for new agents (e.g. "openai").
 * @property defaultModel Default model name for new agents (e.g. "gpt-4o").
 * @property defaultTemperature Global inference temperature (0.0–2.0).
 * @property compactContext Whether to enable compact context mode upstream.
 * @property costEnabled Whether spending limits are enforced.
 * @property dailyLimitUsd Maximum daily spend in USD.
 * @property monthlyLimitUsd Maximum monthly spend in USD.
 * @property costWarnAtPercent Percentage of limit at which to warn.
 * @property providerRetries Number of retries before falling back.
 * @property fallbackProviders Comma-separated list of fallback provider IDs.
 * @property memoryBackend Memory backend name ("sqlite", "none", "markdown", "lucid").
 * @property memoryAutoSave Whether the memory backend auto-saves conversation context.
 * @property identityJson AIEOS v1.1 identity JSON blob.
 */
data class AppSettings(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val autoStartOnBoot: Boolean = false,
    val logLevel: LogLevel = LogLevel.INFO,
    val defaultProvider: String = "",
    val defaultModel: String = "",
    val defaultTemperature: Float = DEFAULT_TEMPERATURE,
    val compactContext: Boolean = false,
    val costEnabled: Boolean = false,
    val dailyLimitUsd: Float = DEFAULT_DAILY_LIMIT_USD,
    val monthlyLimitUsd: Float = DEFAULT_MONTHLY_LIMIT_USD,
    val costWarnAtPercent: Int = DEFAULT_COST_WARN_PERCENT,
    val providerRetries: Int = DEFAULT_PROVIDER_RETRIES,
    val fallbackProviders: String = "",
    val memoryBackend: String = DEFAULT_MEMORY_BACKEND,
    val memoryAutoSave: Boolean = true,
    val identityJson: String = "",
) {
    /** Constants for [AppSettings]. */
    companion object {
        /** Default gateway bind address. */
        const val DEFAULT_HOST = "127.0.0.1"

        /** Default gateway bind port. */
        const val DEFAULT_PORT = 8080

        /** Default inference temperature. */
        const val DEFAULT_TEMPERATURE = 0.7f

        /** Default daily cost limit in USD. */
        const val DEFAULT_DAILY_LIMIT_USD = 10f

        /** Default monthly cost limit in USD. */
        const val DEFAULT_MONTHLY_LIMIT_USD = 100f

        /** Default percentage of cost limit at which to warn. */
        const val DEFAULT_COST_WARN_PERCENT = 80

        /** Default number of provider retries. */
        const val DEFAULT_PROVIDER_RETRIES = 2

        /** Default memory backend. */
        const val DEFAULT_MEMORY_BACKEND = "sqlite"
    }
}

/**
 * Log severity levels for daemon output filtering.
 */
enum class LogLevel {
    /** Verbose debug output. */
    DEBUG,

    /** Standard informational messages. */
    INFO,

    /** Warning conditions. */
    WARN,

    /** Error conditions. */
    ERROR,
}
