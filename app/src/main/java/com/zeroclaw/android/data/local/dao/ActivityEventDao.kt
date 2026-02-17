/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zeroclaw.android.data.local.entity.ActivityEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for activity feed event operations.
 */
@Dao
interface ActivityEventDao {
    /**
     * Observes the most recent activity events, newest first.
     *
     * @param limit Maximum number of events to return.
     * @return A [Flow] emitting the current list of events on every change.
     */
    @Query("SELECT * FROM activity_events ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActivityEventEntity>>

    /**
     * Inserts a new activity event.
     *
     * @param entity The activity event entity to insert.
     */
    @Insert
    suspend fun insert(entity: ActivityEventEntity)

    /**
     * Prunes events to retain only the most recent [retainCount] rows.
     *
     * Deletes all rows whose id is not in the top [retainCount] by id descending.
     *
     * @param retainCount Number of most-recent events to keep.
     */
    @Query(
        """
        DELETE FROM activity_events WHERE id NOT IN (
            SELECT id FROM activity_events ORDER BY id DESC LIMIT :retainCount
        )
        """,
    )
    suspend fun pruneOldest(retainCount: Int)

    /**
     * Returns the total number of activity events.
     *
     * @return Event count.
     */
    @Query("SELECT COUNT(*) FROM activity_events")
    suspend fun count(): Int
}
