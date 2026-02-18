/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.model.LogLevel
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for reading and writing application settings.
 *
 * Implementations must provide a [Flow] of [AppSettings] that emits
 * the current settings and any subsequent changes.
 */
interface SettingsRepository {
    /** Observable stream of the current application settings. */
    val settings: Flow<AppSettings>

    /**
     * Updates the gateway host address.
     *
     * @param host New bind address.
     */
    suspend fun setHost(host: String)

    /**
     * Updates the gateway port.
     *
     * @param port New bind port.
     */
    suspend fun setPort(port: Int)

    /**
     * Toggles the auto-start on boot setting.
     *
     * @param enabled Whether to start the daemon on boot.
     */
    suspend fun setAutoStartOnBoot(enabled: Boolean)

    /**
     * Updates the minimum log level.
     *
     * @param level New minimum severity.
     */
    suspend fun setLogLevel(level: LogLevel)

    /**
     * Updates the default provider for new agents.
     *
     * @param provider Provider ID (e.g. "openai", "anthropic").
     */
    suspend fun setDefaultProvider(provider: String)

    /**
     * Updates the default model for new agents.
     *
     * @param model Model name (e.g. "gpt-4o").
     */
    suspend fun setDefaultModel(model: String)

    /**
     * Updates the default inference temperature.
     *
     * @param temperature Temperature value (0.0–2.0).
     */
    suspend fun setDefaultTemperature(temperature: Float)

    /**
     * Toggles compact context mode.
     *
     * @param enabled Whether compact context is active.
     */
    suspend fun setCompactContext(enabled: Boolean)

    /**
     * Toggles cost limit enforcement.
     *
     * @param enabled Whether spending limits are enforced.
     */
    suspend fun setCostEnabled(enabled: Boolean)

    /**
     * Updates the daily cost limit.
     *
     * @param limit Maximum daily spend in USD.
     */
    suspend fun setDailyLimitUsd(limit: Float)

    /**
     * Updates the monthly cost limit.
     *
     * @param limit Maximum monthly spend in USD.
     */
    suspend fun setMonthlyLimitUsd(limit: Float)

    /**
     * Updates the cost warning threshold percentage.
     *
     * @param percent Percentage of limit at which to warn.
     */
    suspend fun setCostWarnAtPercent(percent: Int)

    /**
     * Updates the number of provider retries before fallback.
     *
     * @param retries Retry count.
     */
    suspend fun setProviderRetries(retries: Int)

    /**
     * Updates the comma-separated list of fallback providers.
     *
     * @param providers Comma-separated provider IDs.
     */
    suspend fun setFallbackProviders(providers: String)

    /**
     * Updates the memory backend.
     *
     * @param backend Backend name ("sqlite", "none", "markdown", "lucid").
     */
    suspend fun setMemoryBackend(backend: String)

    /**
     * Toggles the memory auto-save setting.
     *
     * @param enabled Whether the memory backend auto-saves conversation context.
     */
    suspend fun setMemoryAutoSave(enabled: Boolean)

    /**
     * Updates the AIEOS identity JSON blob.
     *
     * @param json AIEOS v1.1 JSON string.
     */
    suspend fun setIdentityJson(json: String)
}
