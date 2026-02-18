/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for daemon console chat messages.
 *
 * Implementations persist messages in Room so they survive navigation
 * and app restarts.
 */
interface ChatMessageRepository {
    /** Observable stream of chat messages, oldest first (for chronological display). */
    val messages: Flow<List<ChatMessage>>

    /**
     * Inserts a new message and returns it with the auto-generated ID.
     *
     * @param content The message text.
     * @param isFromUser True if sent by the user, false if from the daemon.
     * @param imageUris Content URIs of images attached to this message.
     * @return The persisted [ChatMessage] with its generated ID.
     */
    suspend fun append(
        content: String,
        isFromUser: Boolean,
        imageUris: List<String> = emptyList(),
    ): ChatMessage

    /** Clears all chat messages (fire-and-forget). */
    fun clear()
}
