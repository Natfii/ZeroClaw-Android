/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zeroclaw.android.data.local.entity.LogEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for log entry operations.
 */
@Dao
interface LogEntryDao {
    /**
     * Observes the most recent log entries, newest first.
     *
     * @param limit Maximum number of entries to return.
     * @return A [Flow] emitting the current list of entries on every change.
     */
    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LogEntryEntity>>

    /**
     * Inserts a new log entry.
     *
     * @param entity The log entry entity to insert.
     */
    @Insert
    suspend fun insert(entity: LogEntryEntity)

    /**
     * Deletes all log entries.
     */
    @Query("DELETE FROM log_entries")
    suspend fun deleteAll()

    /**
     * Prunes entries to retain only the most recent [retainCount] rows.
     *
     * Deletes all rows whose id is not in the top [retainCount] by id descending.
     *
     * @param retainCount Number of most-recent entries to keep.
     */
    @Query(
        """
        DELETE FROM log_entries WHERE id NOT IN (
            SELECT id FROM log_entries ORDER BY id DESC LIMIT :retainCount
        )
        """,
    )
    suspend fun pruneOldest(retainCount: Int)

    /**
     * Returns the total number of log entries.
     *
     * @return Entry count.
     */
    @Query("SELECT COUNT(*) FROM log_entries")
    suspend fun count(): Int
}
