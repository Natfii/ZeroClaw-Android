/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * A single message in the daemon console conversation.
 *
 * Messages are persisted in Room and survive navigation and app restarts.
 *
 * @property id Auto-generated primary key from Room (0 for unsaved messages).
 * @property content The message text content.
 * @property isFromUser True if the message was sent by the user, false if from the daemon.
 * @property timestamp Epoch milliseconds when the message was created.
 */
data class ChatMessage(
    val id: Long = 0,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)
