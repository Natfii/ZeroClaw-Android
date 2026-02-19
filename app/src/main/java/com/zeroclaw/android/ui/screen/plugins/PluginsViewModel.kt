/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.plugins

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.data.remote.OkHttpPluginRegistryClient
import com.zeroclaw.android.model.Plugin
import com.zeroclaw.android.util.ErrorSanitizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Tab index for the installed plugins tab. */
const val TAB_INSTALLED = 0

/** Tab index for the available plugins tab. */
const val TAB_AVAILABLE = 1

/** Tab index for the skills tab. */
const val TAB_SKILLS = 2

/**
 * ViewModel for the plugin list screen.
 *
 * Provides tab-filtered and search-filtered plugin lists along with
 * install/uninstall and toggle operations. Also supports manual
 * and automatic plugin registry synchronisation.
 *
 * @param application Application context for accessing the plugin repository.
 */
class PluginsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = application as ZeroClawApplication
    private val repository = app.pluginRepository
    private val settingsRepository = app.settingsRepository

    private val _selectedTab = MutableStateFlow(TAB_INSTALLED)

    /** Currently selected tab index. */
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    /** Current search query text. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _syncState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)

    /** Current state of the plugin registry sync operation. */
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** One-shot messages for display in a [SnackbarHost]. */
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

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

    /** Number of installed plugins that have a newer version available. */
    val updatesAvailableCount: StateFlow<Int> =
        repository.plugins
            .map { all ->
                all.count { plugin ->
                    plugin.isInstalled &&
                        plugin.remoteVersion != null &&
                        plugin.remoteVersion != plugin.version
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)

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
     * Emits a [snackbarMessage] on failure.
     *
     * @param pluginId Unique plugin identifier.
     */
    @Suppress("TooGenericExceptionCaught")
    fun installPlugin(pluginId: String) {
        viewModelScope.launch {
            try {
                repository.install(pluginId)
            } catch (e: Exception) {
                Log.w(TAG, "Install failed for $pluginId", e)
                _snackbarMessage.tryEmit("Install failed: ${ErrorSanitizer.sanitizeForUi(e)}")
            }
        }
    }

    /**
     * Uninstalls the plugin with the given identifier.
     *
     * Emits a [snackbarMessage] on failure.
     *
     * @param pluginId Unique plugin identifier.
     */
    @Suppress("TooGenericExceptionCaught")
    fun uninstallPlugin(pluginId: String) {
        viewModelScope.launch {
            try {
                repository.uninstall(pluginId)
            } catch (e: Exception) {
                Log.w(TAG, "Uninstall failed for $pluginId", e)
                _snackbarMessage.tryEmit("Uninstall failed: ${ErrorSanitizer.sanitizeForUi(e)}")
            }
        }
    }

    /**
     * Toggles the enabled state of the given plugin.
     *
     * Emits a [snackbarMessage] on failure.
     *
     * @param pluginId Unique plugin identifier.
     */
    @Suppress("TooGenericExceptionCaught")
    fun togglePlugin(pluginId: String) {
        viewModelScope.launch {
            try {
                repository.toggleEnabled(pluginId)
            } catch (e: Exception) {
                Log.w(TAG, "Toggle failed for $pluginId", e)
                _snackbarMessage.tryEmit("Toggle failed: ${ErrorSanitizer.sanitizeForUi(e)}")
            }
        }
    }

    /**
     * Triggers an immediate plugin registry sync.
     *
     * Fetches the remote plugin catalog and merges it into the local
     * database. Updates [syncState] throughout the operation.
     */
    @Suppress("TooGenericExceptionCaught")
    fun syncNow() {
        if (_syncState.value is SyncUiState.Syncing) return
        _syncState.value = SyncUiState.Syncing

        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                val client = OkHttpPluginRegistryClient()
                val remotePlugins = client.fetchPlugins(settings.pluginRegistryUrl)
                repository.mergeRemotePlugins(remotePlugins)
                settingsRepository.setLastPluginSyncTimestamp(System.currentTimeMillis())
                val successState = SyncUiState.Success(remotePlugins.size)
                _syncState.value = successState
                launch {
                    delay(SYNC_SUCCESS_DISPLAY_MS)
                    _syncState.compareAndSet(successState, SyncUiState.Idle)
                }
            } catch (e: Exception) {
                _syncState.value =
                    SyncUiState.Error(ErrorSanitizer.sanitizeForUi(e))
            }
        }
    }

    /** Constants for [PluginsViewModel]. */
    companion object {
        private const val TAG = "PluginsVM"
        private const val SYNC_SUCCESS_DISPLAY_MS = 3000L
    }
}
