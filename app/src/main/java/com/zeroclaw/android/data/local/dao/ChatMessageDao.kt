/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zeroclaw.android.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for daemon console chat message operations.
 */
@Dao
interface ChatMessageDao {
    /**
     * Observes the most recent chat messages, newest first.
     *
     * @param limit Maximum number of messages to return.
     * @return A [Flow] emitting the current list of messages on every change.
     */
    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ChatMessageEntity>>

    /**
     * Inserts a new chat message and returns its auto-generated ID.
     *
     * @param entity The chat message entity to insert.
     * @return The auto-generated row ID.
     */
    @Insert
    suspend fun insert(entity: ChatMessageEntity): Long

    /**
     * Deletes all chat messages.
     */
    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()

    /**
     * Prunes messages to retain only the most recent [retainCount] rows.
     *
     * Deletes all rows whose id is not in the top [retainCount] by id descending.
     *
     * @param retainCount Number of most-recent messages to keep.
     */
    @Query(
        """
        DELETE FROM chat_messages WHERE id NOT IN (
            SELECT id FROM chat_messages ORDER BY id DESC LIMIT :retainCount
        )
        """,
    )
    suspend fun pruneOldest(retainCount: Int)

    /**
     * Returns the total number of chat messages.
     *
     * @return Message count.
     */
    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun count(): Int
}
