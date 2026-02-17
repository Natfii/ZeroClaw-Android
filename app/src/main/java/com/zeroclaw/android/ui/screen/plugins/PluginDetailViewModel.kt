/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.plugins

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.Plugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the plugin detail screen.
 *
 * Loads a single plugin and provides install/uninstall, toggle,
 * and configuration update operations.
 *
 * @param application Application context for accessing the plugin repository.
 */
class PluginDetailViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = (application as ZeroClawApplication).pluginRepository

    private val _plugin = MutableStateFlow<Plugin?>(null)

    /** The currently loaded plugin, or null if not yet loaded. */
    val plugin: StateFlow<Plugin?> = _plugin.asStateFlow()

    /**
     * Loads the plugin with the given [pluginId].
     *
     * @param pluginId Unique identifier of the plugin to load.
     */
    fun loadPlugin(pluginId: String) {
        viewModelScope.launch {
            _plugin.value = repository.getById(pluginId)
        }
    }

    /**
     * Installs the currently loaded plugin.
     *
     * @param pluginId Unique identifier of the plugin.
     */
    fun install(pluginId: String) {
        viewModelScope.launch {
            repository.install(pluginId)
            _plugin.value = repository.getById(pluginId)
        }
    }

    /**
     * Uninstalls the currently loaded plugin.
     *
     * @param pluginId Unique identifier of the plugin.
     */
    fun uninstall(pluginId: String) {
        viewModelScope.launch {
            repository.uninstall(pluginId)
            _plugin.value = repository.getById(pluginId)
        }
    }

    /**
     * Toggles the enabled state of the currently loaded plugin.
     *
     * @param pluginId Unique identifier of the plugin.
     */
    fun toggleEnabled(pluginId: String) {
        viewModelScope.launch {
            repository.toggleEnabled(pluginId)
            _plugin.value = repository.getById(pluginId)
        }
    }

    /**
     * Updates a configuration value for the plugin.
     *
     * @param pluginId Unique plugin identifier.
     * @param key Configuration field key.
     * @param value New value for the field.
     */
    fun updateConfig(pluginId: String, key: String, value: String) {
        viewModelScope.launch {
            repository.updateConfig(pluginId, key, value)
            _plugin.value = repository.getById(pluginId)
        }
    }
}
