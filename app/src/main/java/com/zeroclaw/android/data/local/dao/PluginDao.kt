/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.zeroclaw.android.data.local.entity.PluginEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for plugin management operations.
 */
@Dao
interface PluginDao {
    /**
     * Observes all plugins ordered by name.
     *
     * @return A [Flow] emitting the current list of plugins on every change.
     */
    @Query("SELECT * FROM plugins ORDER BY name ASC")
    fun observeAll(): Flow<List<PluginEntity>>

    /**
     * Returns the plugin with the given [id], or null if not found.
     *
     * @param id Unique plugin identifier.
     * @return The matching [PluginEntity] or null.
     */
    @Query("SELECT * FROM plugins WHERE id = :id")
    suspend fun getById(id: String): PluginEntity?

    /**
     * Inserts or updates a plugin.
     *
     * @param entity The plugin entity to upsert.
     */
    @Upsert
    suspend fun upsert(entity: PluginEntity)

    /**
     * Marks the plugin with the given [id] as installed.
     *
     * @param id Unique plugin identifier.
     */
    @Query("UPDATE plugins SET is_installed = 1 WHERE id = :id")
    suspend fun setInstalled(id: String)

    /**
     * Marks the plugin with the given [id] as uninstalled and disabled.
     *
     * @param id Unique plugin identifier.
     */
    @Query("UPDATE plugins SET is_installed = 0, is_enabled = 0 WHERE id = :id")
    suspend fun uninstall(id: String)

    /**
     * Toggles the enabled state of the plugin with the given [id].
     *
     * Only affects installed plugins via the repository layer check.
     *
     * @param id Unique plugin identifier.
     */
    @Query("UPDATE plugins SET is_enabled = NOT is_enabled WHERE id = :id AND is_installed = 1")
    suspend fun toggleEnabled(id: String)

    /**
     * Updates the JSON config field for the given plugin.
     *
     * @param id Unique plugin identifier.
     * @param configJson New JSON-serialized configuration map.
     */
    @Query("UPDATE plugins SET config_json = :configJson WHERE id = :id")
    suspend fun updateConfigJson(id: String, configJson: String)

    /**
     * Returns the total number of plugins.
     *
     * @return Plugin count.
     */
    @Query("SELECT COUNT(*) FROM plugins")
    suspend fun count(): Int

    /**
     * Inserts plugins, ignoring any that already exist by primary key.
     *
     * Used for seeding initial data without overwriting user changes.
     *
     * @param entities The plugin entities to insert.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoreConflicts(entities: List<PluginEntity>)
}
