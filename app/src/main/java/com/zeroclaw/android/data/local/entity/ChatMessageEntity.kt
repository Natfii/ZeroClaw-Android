/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single chat message in the daemon console.
 *
 * The timestamp column is indexed for efficient time-ordered queries and pruning.
 *
 * @property id Auto-generated primary key.
 * @property timestamp Epoch milliseconds when the message was created.
 * @property content The message text content.
 * @property isFromUser True if the message was sent by the user, false if from the daemon.
 */
@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["timestamp"])],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val content: String,
    @ColumnInfo(name = "is_from_user")
    val isFromUser: Boolean,
)
