/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.model.Plugin
import com.zeroclaw.android.model.RemotePlugin
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
     * Observes the plugin with the given [id].
     *
     * Emits a new value whenever the plugin changes, or null if the
     * plugin does not exist or is deleted.
     *
     * @param id Unique plugin identifier.
     * @return A [Flow] emitting the current state of the plugin.
     */
    fun observeById(id: String): Flow<Plugin?>

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
    suspend fun updateConfig(
        pluginId: String,
        key: String,
        value: String,
    )

    /**
     * Merges remote plugin metadata into the local database.
     *
     * For existing plugins, updates name/description/version/author/category
     * and sets [Plugin.remoteVersion] without touching local install state
     * or config. For new plugins, inserts them as uninstalled.
     *
     * @param remotePlugins List of plugin metadata from the remote registry.
     */
    suspend fun mergeRemotePlugins(remotePlugins: List<RemotePlugin>)
}
