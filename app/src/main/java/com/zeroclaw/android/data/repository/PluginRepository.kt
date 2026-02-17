/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.model.Plugin
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for plugin management operations.
 */
interface PluginRepository {
    /** Observable list of all known plugins (installed and available). */
    val plugins: Flow<List<Plugin>>

    /**
     * Returns the plugin with the given [id], or null if not found.
     *
     * @param id Unique plugin identifier.
     * @return The matching [Plugin] or null.
     */
    suspend fun getById(id: String): Plugin?

    /**
     * Installs the plugin with the given [id].
     *
     * @param id Unique plugin identifier.
     */
    suspend fun install(id: String)

    /**
     * Uninstalls the plugin with the given [id].
     *
     * @param id Unique plugin identifier.
     */
    suspend fun uninstall(id: String)

    /**
     * Toggles the enabled state of the plugin with the given [id].
     *
     * @param id Unique plugin identifier.
     */
    suspend fun toggleEnabled(id: String)

    /**
     * Updates a configuration value for the given plugin.
     *
     * @param pluginId Unique plugin identifier.
     * @param key Configuration field key.
     * @param value New value for the field.
     */
    suspend fun updateConfig(pluginId: String, key: String, value: String)
}
