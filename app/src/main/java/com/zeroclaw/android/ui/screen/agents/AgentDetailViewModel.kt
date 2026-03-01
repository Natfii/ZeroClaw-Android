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
import com.zeroclaw.android.model.ApiKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the agent detail / edit screen.
 *
 * Loads a single agent by ID and provides save and delete operations.
 *
 * @param application Application context for accessing repositories.
 */
class AgentDetailViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = application as ZeroClawApplication
    private val repository = app.agentRepository
    private val apiKeyRepository = app.apiKeyRepository
    private val daemonBridge = app.daemonBridge

    private val _agent = MutableStateFlow<Agent?>(null)

    /** The currently loaded agent, or null if not yet loaded. */
    val agent: StateFlow<Agent?> = _agent.asStateFlow()

    /** All stored API keys, observed reactively so new keys appear on return from add flow. */
    val apiKeys: StateFlow<List<ApiKey>> =
        apiKeyRepository.keys.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            emptyList(),
        )

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
     * When only the provider or model changed, attempts a hot-swap via
     * [DaemonServiceBridge.hotSwapProvider][com.zeroclaw.android.service.DaemonServiceBridge.hotSwapProvider]
     * so the daemon does not need a full restart. Falls back to
     * [DaemonServiceBridge.markRestartRequired][com.zeroclaw.android.service.DaemonServiceBridge.markRestartRequired]
     * for all other field changes.
     *
     * @param agent The agent to persist.
     */
    fun saveAgent(agent: Agent) {
        viewModelScope.launch {
            val previous = _agent.value
            repository.save(agent)

            if (previous != null && isProviderOnlyChange(previous, agent)) {
                daemonBridge.hotSwapProvider(
                    provider = agent.provider,
                    model = agent.modelName,
                )
            } else {
                daemonBridge.markRestartRequired()
            }
        }
    }

    /**
     * Returns `true` when only provider-related fields differ between agents.
     *
     * Provider-related fields are [Agent.provider] and [Agent.modelName].
     * All other fields (channels, system prompt, temperature, etc.) require
     * a full daemon restart when changed.
     */
    private fun isProviderOnlyChange(
        old: Agent,
        new: Agent,
    ): Boolean =
        old.id == new.id &&
            old.isEnabled == new.isEnabled &&
            old.systemPrompt == new.systemPrompt &&
            old.channels == new.channels &&
            old.temperature == new.temperature &&
            old.maxDepth == new.maxDepth &&
            old.name == new.name &&
            (old.provider != new.provider || old.modelName != new.modelName)

    /**
     * Deletes the agent with the given [agentId].
     *
     * @param agentId Unique identifier of the agent to delete.
     */
    fun deleteAgent(agentId: String) {
        viewModelScope.launch {
            repository.delete(agentId)
            daemonBridge.markRestartRequired()
        }
    }

    /** Constants for [AgentDetailViewModel]. */
    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
