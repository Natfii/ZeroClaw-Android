/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.data.local.dao.AgentDao
import com.zeroclaw.android.data.local.entity.toEntity
import com.zeroclaw.android.data.local.entity.toModel
import com.zeroclaw.android.model.Agent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [AgentRepository] implementation.
 *
 * Delegates all persistence operations to [AgentDao] and maps between
 * entity and domain model layers.
 *
 * @param dao The data access object for agent operations.
 */
class RoomAgentRepository(
    private val dao: AgentDao,
) : AgentRepository {
    override val agents: Flow<List<Agent>> =
        dao.observeAll().map { entities -> entities.map { it.toModel() } }

    override suspend fun getById(id: String): Agent? = dao.getById(id)?.toModel()

    override suspend fun save(agent: Agent) {
        dao.upsert(agent.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    override suspend fun toggleEnabled(id: String) {
        dao.toggleEnabled(id)
    }
}
