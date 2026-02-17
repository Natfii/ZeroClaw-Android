/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroclaw.android.data.local.entity.AgentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for agent CRUD operations.
 */
@Dao
interface AgentDao {
    /**
     * Observes all agents ordered by name.
     *
     * @return A [Flow] emitting the current list of agents on every change.
     */
    @Query("SELECT * FROM agents ORDER BY name ASC")
    fun observeAll(): Flow<List<AgentEntity>>

    /**
     * Returns the agent with the given [id], or null if not found.
     *
     * @param id Unique agent identifier.
     * @return The matching [AgentEntity] or null.
     */
    @Query("SELECT * FROM agents WHERE id = :id")
    suspend fun getById(id: String): AgentEntity?

    /**
     * Inserts or updates an agent.
     *
     * @param entity The agent entity to upsert.
     */
    @Upsert
    suspend fun upsert(entity: AgentEntity)

    /**
     * Deletes the agent with the given [id].
     *
     * @param id Unique agent identifier.
     */
    @Query("DELETE FROM agents WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Toggles the enabled state of the agent with the given [id].
     *
     * @param id Unique agent identifier.
     */
    @Query("UPDATE agents SET is_enabled = NOT is_enabled WHERE id = :id")
    suspend fun toggleEnabled(id: String)
}
