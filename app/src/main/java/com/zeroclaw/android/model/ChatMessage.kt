/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * A single message in an agent chat conversation.
 *
 * Messages are stored in-memory within the ViewModel and are not persisted
 * across process restarts.
 *
 * @property id Unique identifier for the message.
 * @property content The message text content.
 * @property isFromUser True if the message was sent by the user, false if from the agent.
 * @property timestamp Epoch milliseconds when the message was created.
 */
data class ChatMessage(
    val id: String,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)
