/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.plugins

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.Plugin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the plugin detail screen.
 *
 * Derives plugin state reactively from [PluginRepository.observeById] so that
 * database changes from any source (user actions, plugin sync, etc.) are
 * automatically reflected without redundant one-shot `getById` calls or
 * manual in-memory state patches.
 *
 * @param application Application context for accessing the plugin repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PluginDetailViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = (application as ZeroClawApplication).pluginRepository

    private val pluginId = MutableStateFlow<String?>(null)

    /**
     * The currently observed plugin, or null if no plugin ID has been set
     * or the plugin does not exist.
     *
     * Derived from [PluginRepository.observeById] and updated automatically
     * whenever the underlying database row changes.
     */
    val plugin: StateFlow<Plugin?> =
        pluginId
            .flatMapLatest { id ->
                if (id != null) repository.observeById(id) else flowOf(null)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Sets the plugin ID to observe.
     *
     * Triggers the reactive [plugin] Flow to start emitting updates for
     * the specified plugin. Subsequent calls with the same ID are no-ops
     * since [MutableStateFlow] deduplicates identical values.
     *
     * @param pluginId Unique identifier of the plugin to observe.
     */
    fun loadPlugin(pluginId: String) {
        this.pluginId.value = pluginId
    }

    /**
     * Installs the plugin with the given [pluginId].
     *
     * The UI updates automatically via the reactive [plugin] Flow after the
     * database write completes.
     *
     * @param pluginId Unique identifier of the plugin.
     */
    fun install(pluginId: String) {
        viewModelScope.launch {
            repository.install(pluginId)
        }
    }

    /**
     * Uninstalls the plugin with the given [pluginId].
     *
     * The UI updates automatically via the reactive [plugin] Flow after the
     * database write completes.
     *
     * @param pluginId Unique identifier of the plugin.
     */
    fun uninstall(pluginId: String) {
        viewModelScope.launch {
            repository.uninstall(pluginId)
        }
    }

    /**
     * Toggles the enabled state of the plugin with the given [pluginId].
     *
     * The UI updates automatically via the reactive [plugin] Flow after the
     * database write completes.
     *
     * @param pluginId Unique identifier of the plugin.
     */
    fun toggleEnabled(pluginId: String) {
        viewModelScope.launch {
            repository.toggleEnabled(pluginId)
        }
    }

    /**
     * Updates a configuration value for the plugin.
     *
     * The UI updates automatically via the reactive [plugin] Flow after the
     * database write completes.
     *
     * @param pluginId Unique plugin identifier.
     * @param key Configuration field key.
     * @param value New value for the field.
     */
    fun updateConfig(
        pluginId: String,
        key: String,
        value: String,
    ) {
        viewModelScope.launch {
            repository.updateConfig(pluginId, key, value)
        }
    }

    /** Constants for [PluginDetailViewModel]. */
    companion object {
        /** Upstream subscription timeout for [SharingStarted.WhileSubscribed]. */
        private const val STOP_TIMEOUT_MS = 5000L
    }
}
