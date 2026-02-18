/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.data.local.dao.PluginDao
import com.zeroclaw.android.data.local.entity.toModel
import com.zeroclaw.android.model.Plugin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room-backed [PluginRepository] implementation.
 *
 * Delegates all persistence operations to [PluginDao] and maps between
 * entity and domain model layers. Config field updates merge the new
 * key into the existing JSON map.
 *
 * @param dao The data access object for plugin operations.
 */
class RoomPluginRepository(
    private val dao: PluginDao,
) : PluginRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override val plugins: Flow<List<Plugin>> =
        dao.observeAll().map { entities -> entities.map { it.toModel() } }

    override suspend fun getById(id: String): Plugin? = dao.getById(id)?.toModel()

    override suspend fun install(id: String) {
        dao.setInstalled(id)
    }

    override suspend fun uninstall(id: String) {
        dao.uninstall(id)
    }

    override suspend fun toggleEnabled(id: String) {
        dao.toggleEnabled(id)
    }

    override suspend fun updateConfig(
        pluginId: String,
        key: String,
        value: String,
    ) {
        val entity = dao.getById(pluginId) ?: return
        val currentConfig: Map<String, String> =
            runCatching {
                json.decodeFromString<Map<String, String>>(entity.configJson)
            }.getOrDefault(emptyMap())
        val updatedConfig = currentConfig + (key to value)
        dao.updateConfigJson(pluginId, json.encodeToString(updatedConfig))
    }
}
