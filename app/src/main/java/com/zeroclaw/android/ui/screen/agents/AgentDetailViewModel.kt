/*
 * Copyright 2026 ZeroClaw Contributors
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the agent detail / edit screen.
 *
 * Loads a single agent by ID and provides save and delete operations.
 *
 * @param application Application context for accessing the agent repository.
 */
class AgentDetailViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = (application as ZeroClawApplication).agentRepository

    private val _agent = MutableStateFlow<Agent?>(null)

    /** The currently loaded agent, or null if not yet loaded. */
    val agent: StateFlow<Agent?> = _agent.asStateFlow()

    /**
     * Loads the agent with the given [agentId].
     *
     * @param agentId Unique identifier of the agent to load.
     */
    fun loadAgent(agentId: String) {
        viewModelScope.launch {
            _agent.value = repository.getById(agentId)
        }
    }

    /**
     * Saves the given agent (creates or updates).
     *
     * @param agent The agent to persist.
     */
    fun saveAgent(agent: Agent) {
        viewModelScope.launch {
            repository.save(agent)
        }
    }

    /**
     * Deletes the agent with the given [agentId].
     *
     * @param agentId Unique identifier of the agent to delete.
     */
    fun deleteAgent(agentId: String) {
        viewModelScope.launch {
            repository.delete(agentId)
        }
    }
}
