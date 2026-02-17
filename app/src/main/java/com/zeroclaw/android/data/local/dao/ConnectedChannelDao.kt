/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroclaw.android.data.local.entity.ConnectedChannelEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for connected channel CRUD operations.
 */
@Dao
interface ConnectedChannelDao {
    /**
     * Observes all connected channels ordered by creation date.
     *
     * @return A [Flow] emitting the current list of channels on every change.
     */
    @Query("SELECT * FROM connected_channels ORDER BY created_at ASC")
    fun observeAll(): Flow<List<ConnectedChannelEntity>>

    /**
     * Returns the channel with the given [id], or null if not found.
     *
     * @param id Unique channel identifier.
     * @return The matching [ConnectedChannelEntity] or null.
     */
    @Query("SELECT * FROM connected_channels WHERE id = :id")
    suspend fun getById(id: String): ConnectedChannelEntity?

    /**
     * Returns the channel with the given [type], or null if not found.
     *
     * @param type Channel type name.
     * @return The matching [ConnectedChannelEntity] or null.
     */
    @Query("SELECT * FROM connected_channels WHERE type = :type")
    suspend fun getByType(type: String): ConnectedChannelEntity?

    /**
     * Inserts or updates a channel.
     *
     * @param entity The channel entity to upsert.
     */
    @Upsert
    suspend fun upsert(entity: ConnectedChannelEntity)

    /**
     * Deletes the channel with the given [id].
     *
     * @param id Unique channel identifier.
     */
    @Query("DELETE FROM connected_channels WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Toggles the enabled state of the channel with the given [id].
     *
     * @param id Unique channel identifier.
     */
    @Query("UPDATE connected_channels SET is_enabled = NOT is_enabled WHERE id = :id")
    suspend fun toggleEnabled(id: String)
}
