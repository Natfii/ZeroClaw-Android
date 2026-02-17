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
}
