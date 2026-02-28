/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zeroclaw.android.data.local.entity.TerminalEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for terminal REPL entry operations.
 */
@Dao
interface TerminalEntryDao {
    /**
     * Observes all terminal entries in chronological order (oldest first).
     *
     * @return A [Flow] emitting the current list of entries on every change.
     */
    @Query("SELECT * FROM terminal_entries ORDER BY timestamp ASC, id ASC")
    fun observeAll(): Flow<List<TerminalEntryEntity>>

    /**
     * Inserts a new terminal entry and returns its auto-generated ID.
     *
     * @param entity The terminal entry entity to insert.
     * @return The auto-generated row ID.
     */
    @Insert
    suspend fun insert(entity: TerminalEntryEntity): Long

    /**
     * Deletes all terminal entries.
     */
    @Query("DELETE FROM terminal_entries")
    suspend fun deleteAll()
}
