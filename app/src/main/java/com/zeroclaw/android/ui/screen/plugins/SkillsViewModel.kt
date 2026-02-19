// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.ui.screen.plugins

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.Skill
import com.zeroclaw.android.service.SkillsBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state for the skills tab.
 *
 * @param T The type of content data.
 */
sealed interface SkillsUiState<out T> {
    /** Data is being loaded from the bridge. */
    data object Loading : SkillsUiState<Nothing>

    /**
     * Loading or mutation failed.
     *
     * @property detail Human-readable error message.
     */
    data class Error(
        val detail: String,
    ) : SkillsUiState<Nothing>

    /**
     * Data loaded successfully.
     *
     * @param T Content data type.
     * @property data The loaded content.
     */
    data class Content<T>(
        val data: T,
    ) : SkillsUiState<T>
}

/**
 * ViewModel for the skills tab inside the Plugins and Skills screen.
 *
 * Loads skills from [SkillsBridge] and exposes install/remove operations.
 *
 * @param application Application context for accessing [ZeroClawApplication.skillsBridge].
 */
class SkillsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val skillsBridge: SkillsBridge =
        (application as ZeroClawApplication).skillsBridge

    private val _uiState =
        MutableStateFlow<SkillsUiState<List<Skill>>>(SkillsUiState.Loading)

    /** Observable UI state for the skills list. */
    val uiState: StateFlow<SkillsUiState<List<Skill>>> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    /** Current search query text. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)

    /**
     * One-shot snackbar message shown after a successful mutation.
     *
     * Collect with `collectAsStateWithLifecycle` and call [clearSnackbar]
     * after displaying.
     */
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    /**
     * Pre-filtered UI state combining the raw skills list with the
     * current search query.
     *
     * Filtering runs in the ViewModel so the composable receives
     * an already-filtered list without recomputing on every recomposition.
     */
    val filteredUiState: StateFlow<SkillsUiState<List<Skill>>> =
        combine(_uiState, _searchQuery) { state, query ->
            if (state is SkillsUiState.Content) {
                SkillsUiState.Content(filterSkills(state.data, query))
            } else {
                state
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = SkillsUiState.Loading,
        )

    init {
        loadSkills()
    }

    /** Reloads the skills list from the native layer. */
    fun loadSkills() {
        _uiState.value = SkillsUiState.Loading
        viewModelScope.launch {
            loadSkillsInternal()
        }
    }

    /**
     * Updates the search query for filtering skills.
     *
     * @param query New search text.
     */
    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    /**
     * Installs a skill from the given source URL or path.
     *
     * @param source URL or local filesystem path to the skill.
     */
    fun installSkill(source: String) {
        viewModelScope.launch {
            runMutation("Skill installed") {
                skillsBridge.installSkill(source)
            }
        }
    }

    /**
     * Removes an installed skill by name.
     *
     * @param name The skill name to remove.
     */
    fun removeSkill(name: String) {
        viewModelScope.launch {
            runMutation("Skill removed") {
                skillsBridge.removeSkill(name)
            }
        }
    }

    /** Clears the current snackbar message. */
    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadSkillsInternal() {
        try {
            val skills = skillsBridge.listSkills()
            _uiState.value = SkillsUiState.Content(skills)
        } catch (e: Exception) {
            _uiState.value =
                SkillsUiState.Error(
                    e.message ?: "Failed to load skills",
                )
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
            loadSkillsInternal()
        } catch (e: Exception) {
            _snackbarMessage.value = "Error: ${e.message ?: "unknown"}"
        }
    }

    /** Utility functions for skills filtering. */
    companion object {
        /**
         * Filters skills by search query against name and description.
         *
         * @param skills All skills from the bridge.
         * @param query Search query text.
         * @return Filtered list of skills.
         */
        private fun filterSkills(
            skills: List<Skill>,
            query: String,
        ): List<Skill> {
            if (query.isBlank()) return skills
            return skills.filter { skill ->
                skill.name.contains(query, ignoreCase = true) ||
                    skill.description.contains(query, ignoreCase = true)
            }
        }
    }
}
