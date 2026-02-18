/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.data.local.dao.ChatMessageDao
import com.zeroclaw.android.data.local.entity.ChatMessageEntity
import com.zeroclaw.android.data.local.entity.toModel
import com.zeroclaw.android.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Room-backed [ChatMessageRepository] implementation.
 *
 * The [append] method is suspend (unlike [RoomLogRepository]) because the
 * ViewModel needs the generated ID back for immediate UI update. The [clear]
 * method is fire-and-forget via the provided [ioScope].
 *
 * Automatically prunes messages when the count exceeds [maxMessages].
 *
 * @param dao The data access object for chat message operations.
 * @param ioScope Coroutine scope for background database writes.
 * @param maxMessages Maximum number of messages to retain before pruning.
 */
class RoomChatMessageRepository(
    private val dao: ChatMessageDao,
    private val ioScope: CoroutineScope,
    private val maxMessages: Int = DEFAULT_MAX_MESSAGES,
) : ChatMessageRepository {
    override val messages: Flow<List<ChatMessage>> =
        dao.observeRecent(maxMessages).map { entities ->
            entities.map { it.toModel() }.reversed()
        }

    override suspend fun append(
        content: String,
        isFromUser: Boolean,
    ): ChatMessage {
        val now = System.currentTimeMillis()
        val entity =
            ChatMessageEntity(
                timestamp = now,
                content = content,
                isFromUser = isFromUser,
            )
        val generatedId = dao.insert(entity)
        pruneIfNeeded()
        return ChatMessage(
            id = generatedId,
            content = content,
            isFromUser = isFromUser,
            timestamp = now,
        )
    }

    override fun clear() {
        ioScope.launch {
            dao.deleteAll()
        }
    }

    /**
     * Prunes old messages when the total count exceeds [maxMessages].
     */
    private suspend fun pruneIfNeeded() {
        val count = dao.count()
        if (count > maxMessages) {
            dao.pruneOldest(maxMessages)
        }
    }

    /** Constants for [RoomChatMessageRepository]. */
    companion object {
        /** Default maximum number of chat messages retained. */
        const val DEFAULT_MAX_MESSAGES = 1000
    }
}
