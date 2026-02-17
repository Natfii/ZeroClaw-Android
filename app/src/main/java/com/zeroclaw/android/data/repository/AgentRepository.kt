/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.model.Agent
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for agent CRUD operations.
 */
interface AgentRepository {
    /** Observable list of all agents. */
    val agents: Flow<List<Agent>>

    /**
     * Returns the agent with the given [id], or null if not found.
     *
     * @param id Unique agent identifier.
     * @return The matching [Agent] or null.
     */
    suspend fun getById(id: String): Agent?

    /**
     * Saves an agent, creating or updating as appropriate.
     *
     * @param agent The agent to persist.
     */
    suspend fun save(agent: Agent)

    /**
     * Deletes the agent with the given [id].
     *
     * @param id Unique agent identifier.
     */
    suspend fun delete(id: String)

    /**
     * Toggles the enabled state of the agent with the given [id].
     *
     * @param id Unique agent identifier.
     */
    suspend fun toggleEnabled(id: String)
}
