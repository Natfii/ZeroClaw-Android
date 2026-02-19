// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.ui.screen.settings.memory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.MemoryEntry
import com.zeroclaw.android.service.MemoryBridge
import com.zeroclaw.android.util.ErrorSanitizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the memory browser screen.
 *
 * @param T The type of content data.
 */
sealed interface MemoryUiState<out T> {
    /** Data is being loaded from the bridge. */
    data object Loading : MemoryUiState<Nothing>

    /**
     * Loading or mutation failed.
     *
     * @property detail Human-readable error message.
     */
    data class Error(
        val detail: String,
    ) : MemoryUiState<Nothing>

    /**
     * Data loaded successfully.
     *
     * @param T Content data type.
     * @property data The loaded content.
     */
    data class Content<T>(
        val data: T,
    ) : MemoryUiState<T>
}

/** Filter value for showing all categories. */
const val CATEGORY_ALL = "All"

/** Filter value for the "core" memory category. */
const val CATEGORY_CORE = "core"

/** Filter value for the "daily" memory category. */
const val CATEGORY_DAILY = "daily"

/** Filter value for the "conversation" memory category. */
const val CATEGORY_CONVERSATION = "conversation"

/**
 * ViewModel for the memory browser screen.
 *
 * Loads memory entries from [MemoryBridge] and exposes search, category
 * filtering, and delete (forget) operations.
 *
 * @param application Application context for accessing [ZeroClawApplication.memoryBridge].
 */
class MemoryBrowserViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val memoryBridge: MemoryBridge =
        (application as ZeroClawApplication).memoryBridge

    private val _uiState =
        MutableStateFlow<MemoryUiState<List<MemoryEntry>>>(MemoryUiState.Loading)

    /** Observable UI state for the memory entries list. */
    val uiState: StateFlow<MemoryUiState<List<MemoryEntry>>> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    /** Current search query text. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _categoryFilter = MutableStateFlow(CATEGORY_ALL)

    /** Current category filter value. */
    val categoryFilter: StateFlow<String> = _categoryFilter.asStateFlow()

    private val _totalCount = MutableStateFlow(0u)

    /** Total number of memory entries across all categories. */
    val totalCount: StateFlow<UInt> = _totalCount.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)

    /**
     * One-shot snackbar message shown after a successful mutation.
     *
     * Collect with `collectAsStateWithLifecycle` and call [clearSnackbar]
     * after displaying.
     */
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        loadMemories()
    }

    /** Reloads the memory entries from the native layer. */
    fun loadMemories() {
        _uiState.value = MemoryUiState.Loading
        viewModelScope.launch {
            loadMemoriesInternal()
            loadCountInternal()
        }
    }

    /**
     * Updates the search query.
     *
     * When the query is non-blank, performs a recall (relevance search)
     * instead of a list operation.
     *
     * @param query New search text.
     */
    fun updateSearch(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            if (query.isBlank()) {
                loadMemoriesInternal()
            } else {
                searchMemoriesInternal(query)
            }
        }
    }

    /**
     * Sets the category filter and reloads entries.
     *
     * @param category Category name to filter by, or [CATEGORY_ALL] for no filter.
     */
    fun setCategoryFilter(category: String) {
        _categoryFilter.value = category
        _searchQuery.value = ""
        viewModelScope.launch {
            loadMemoriesInternal()
        }
    }

    /**
     * Deletes a memory entry by key.
     *
     * @param key The key of the memory entry to delete.
     */
    fun forgetMemory(key: String) {
        viewModelScope.launch {
            runMutation("Memory entry deleted") {
                memoryBridge.forgetMemory(key)
            }
        }
    }

    /** Clears the current snackbar message. */
    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadMemoriesInternal() {
        try {
            val category =
                if (_categoryFilter.value == CATEGORY_ALL) null else _categoryFilter.value
            val entries = memoryBridge.listMemories(category = category)
            _uiState.value = MemoryUiState.Content(entries)
        } catch (e: Exception) {
            _uiState.value =
                MemoryUiState.Error(ErrorSanitizer.sanitizeForUi(e))
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun searchMemoriesInternal(query: String) {
        try {
            val entries = memoryBridge.recallMemory(query)
            _uiState.value = MemoryUiState.Content(entries)
        } catch (e: Exception) {
            _uiState.value =
                MemoryUiState.Error(ErrorSanitizer.sanitizeForUi(e))
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadCountInternal() {
        try {
            _totalCount.value = memoryBridge.memoryCount()
        } catch (_: Exception) {
            /** Count is non-critical; silently ignore errors. */
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runMutation(
        successMessage: String,
        block: suspend () -> Any?,
    ) {
        try {
            block()
            _snackbarMessage.value = successMessage
            loadMemoriesInternal()
            loadCountInternal()
        } catch (e: Exception) {
            _snackbarMessage.value = ErrorSanitizer.sanitizeForUi(e)
        }
    }
}
