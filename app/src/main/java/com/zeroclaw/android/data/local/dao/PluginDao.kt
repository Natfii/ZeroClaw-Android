/*
 * Copyright 2026 ZeroClaw Community
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
     * Observes the plugin with the given [id].
     *
     * Emits a new value whenever the row changes, or null if the plugin
     * does not exist or is deleted.
     *
     * @param id Unique plugin identifier.
     * @return A [Flow] emitting the current state of the plugin.
     */
    @Query("SELECT * FROM plugins WHERE id = :id")
    fun observeById(id: String): Flow<PluginEntity?>

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
    suspend fun updateConfigJson(
        id: String,
        configJson: String,
    )

    /**
     * Updates remote metadata for a plugin without touching local state.
     *
     * Preserves `is_installed`, `is_enabled`, and `config_json`.
     *
     * @param id Unique plugin identifier.
     * @param name Updated display name.
     * @param description Updated description.
     * @param version Updated current version.
     * @param author Updated author.
     * @param category Updated category name.
     * @param remoteVersion Latest version available in the remote registry.
     */
    @Suppress("LongParameterList")
    @Query(
        """
        UPDATE plugins SET
            name = :name,
            description = :description,
            version = :version,
            author = :author,
            category = :category,
            remote_version = :remoteVersion
        WHERE id = :id
        """,
    )
    suspend fun updateMetadata(
        id: String,
        name: String,
        description: String,
        version: String,
        author: String,
        category: String,
        remoteVersion: String?,
    )

    /**
     * Returns the IDs from [ids] that already exist in the plugins table.
     *
     * Used by [RoomPluginRepository.mergeRemotePlugins] to batch-check
     * existence in a single query instead of N individual lookups.
     *
     * @param ids Plugin identifiers to check.
     * @return Subset of [ids] that have matching rows.
     */
    @Query("SELECT id FROM plugins WHERE id IN (:ids)")
    suspend fun getExistingIds(ids: List<String>): List<String>

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
