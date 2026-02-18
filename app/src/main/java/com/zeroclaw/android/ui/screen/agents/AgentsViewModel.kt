/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.agents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.Agent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the agent list screen.
 *
 * Provides the current agent list with search filtering and
 * agent enable/disable toggling.
 *
 * @param application Application context for accessing the agent repository.
 */
class AgentsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = (application as ZeroClawApplication).agentRepository

    private val _searchQuery = MutableStateFlow("")

    /** Current search query text. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Filtered agent list based on the current search query. */
    val agents: StateFlow<List<Agent>> =
        combine(repository.agents, _searchQuery) { agents, query ->
            if (query.isBlank()) {
                agents
            } else {
                agents.filter { agent ->
                    agent.name.contains(query, ignoreCase = true) ||
                        agent.provider.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    /**
     * Updates the search query used to filter agents.
     *
     * @param query New search text.
     */
    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    /**
     * Toggles the enabled state of the given agent.
     *
     * @param agentId Unique identifier of the agent to toggle.
     */
    fun toggleAgent(agentId: String) {
        viewModelScope.launch {
            repository.toggleEnabled(agentId)
        }
    }
}
