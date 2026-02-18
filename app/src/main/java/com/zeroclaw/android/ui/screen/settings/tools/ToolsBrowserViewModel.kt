// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.ui.screen.settings.tools

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.ToolSpec
import com.zeroclaw.android.service.ToolsBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the tools browser screen.
 *
 * @param T The type of content data.
 */
sealed interface ToolsUiState<out T> {
    /** Data is being loaded from the bridge. */
    data object Loading : ToolsUiState<Nothing>

    /**
     * Loading failed.
     *
     * @property detail Human-readable error message.
     */
    data class Error(
        val detail: String,
    ) : ToolsUiState<Nothing>

    /**
     * Data loaded successfully.
     *
     * @param T Content data type.
     * @property data The loaded content.
     */
    data class Content<T>(
        val data: T,
    ) : ToolsUiState<T>
}

/** Filter value for showing all tools. */
const val SOURCE_ALL = "All"

/** Filter value for showing only built-in tools. */
const val SOURCE_BUILT_IN = "built-in"

/**
 * ViewModel for the tools inventory browser screen.
 *
 * Loads the tool inventory from [ToolsBridge] and exposes search and
 * source-based filtering.
 *
 * @param application Application context for accessing [ZeroClawApplication.toolsBridge].
 */
class ToolsBrowserViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val toolsBridge: ToolsBridge =
        (application as ZeroClawApplication).toolsBridge

    private val _uiState =
        MutableStateFlow<ToolsUiState<List<ToolSpec>>>(ToolsUiState.Loading)

    /** Observable UI state for the tools list. */
    val uiState: StateFlow<ToolsUiState<List<ToolSpec>>> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    /** Current search query text. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sourceFilter = MutableStateFlow(SOURCE_ALL)

    /** Current source filter value. */
    val sourceFilter: StateFlow<String> = _sourceFilter.asStateFlow()

    init {
        loadTools()
    }

    /** Reloads the tools list from the native layer. */
    fun loadTools() {
        _uiState.value = ToolsUiState.Loading
        viewModelScope.launch {
            loadToolsInternal()
        }
    }

    /**
     * Updates the search query for filtering tools.
     *
     * @param query New search text.
     */
    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    /**
     * Sets the source filter.
     *
     * @param source Source name to filter by, or [SOURCE_ALL] for no filter.
     */
    fun setSourceFilter(source: String) {
        _sourceFilter.value = source
    }

    /**
     * Returns the unique source values from the current tool list.
     *
     * @return Sorted list of unique sources including [SOURCE_ALL].
     */
    fun availableSources(): List<String> {
        val state = _uiState.value
        return if (state is ToolsUiState.Content) {
            val sources =
                state.data
                    .map { it.source }
                    .distinct()
                    .sorted()
            listOf(SOURCE_ALL) + sources
        } else {
            listOf(SOURCE_ALL)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadToolsInternal() {
        try {
            val tools = toolsBridge.listTools()
            _uiState.value = ToolsUiState.Content(tools)
        } catch (e: Exception) {
            _uiState.value =
                ToolsUiState.Error(
                    e.message ?: "Failed to load tools",
                )
        }
    }
}
