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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Tab index for the installed plugins tab. */
const val TAB_INSTALLED = 0

/** Tab index for the available plugins tab. */
const val TAB_AVAILABLE = 1

/**
 * ViewModel for the plugin list screen.
 *
 * Provides tab-filtered and search-filtered plugin lists along with
 * install/uninstall and toggle operations.
 *
 * @param application Application context for accessing the plugin repository.
 */
class PluginsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = (application as ZeroClawApplication).pluginRepository

    private val _selectedTab = MutableStateFlow(TAB_INSTALLED)

    /** Currently selected tab index. */
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    /** Current search query text. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Filtered plugin list based on tab and search query. */
    val plugins: StateFlow<List<Plugin>> =
        combine(repository.plugins, _selectedTab, _searchQuery) { all, tab, query ->
            val tabFiltered =
                when (tab) {
                    TAB_INSTALLED -> all.filter { it.isInstalled }
                    else -> all.filter { !it.isInstalled }
                }
            if (query.isBlank()) {
                tabFiltered
            } else {
                tabFiltered.filter { plugin ->
                    plugin.name.contains(query, ignoreCase = true) ||
                        plugin.description.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    /**
     * Selects the tab at the given index.
     *
     * @param tab Tab index to select.
     */
    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }

    /**
     * Updates the search query for filtering plugins.
     *
     * @param query New search text.
     */
    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    /**
     * Installs the plugin with the given identifier.
     *
     * @param pluginId Unique plugin identifier.
     */
    fun installPlugin(pluginId: String) {
        viewModelScope.launch {
            repository.install(pluginId)
        }
    }

    /**
     * Uninstalls the plugin with the given identifier.
     *
     * @param pluginId Unique plugin identifier.
     */
    fun uninstallPlugin(pluginId: String) {
        viewModelScope.launch {
            repository.uninstall(pluginId)
        }
    }

    /**
     * Toggles the enabled state of the given plugin.
     *
     * @param pluginId Unique plugin identifier.
     */
    fun togglePlugin(pluginId: String) {
        viewModelScope.launch {
            repository.toggleEnabled(pluginId)
        }
    }
}
