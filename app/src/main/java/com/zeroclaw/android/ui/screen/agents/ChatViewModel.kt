/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.agents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.ChatMessage
import com.zeroclaw.ffi.FfiException
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the agent chat screen.
 *
 * Manages the chat message list and sends messages to the daemon via FFI.
 * Messages are stored in-memory and are not persisted across process restarts.
 *
 * @param application Application context for accessing repositories and the daemon bridge.
 */
class ChatViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = application as ZeroClawApplication
    private val agentRepository = app.agentRepository
    private val daemonBridge = app.daemonBridge

    private val _agentName = MutableStateFlow("")

    /** Display name of the agent being chatted with. */
    val agentName: StateFlow<String> = _agentName.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())

    /** Ordered list of chat messages (oldest first). */
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)

    /** True while waiting for a response from the daemon. */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Loads the agent name for the given agent ID.
     *
     * @param agentId Unique identifier of the agent.
     */
    fun loadAgent(agentId: String) {
        viewModelScope.launch {
            val agent = agentRepository.getById(agentId)
            _agentName.value = agent?.name ?: "Agent"
        }
    }

    /**
     * Sends a user message to the daemon and appends the response.
     *
     * The user message is immediately added to [messages]. While waiting
     * for the FFI response, [isLoading] is true. On success the agent
     * response is appended; on failure an error message is shown.
     *
     * @param text The message text to send.
     */
    @Suppress("TooGenericExceptionCaught")
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val userMessage =
            ChatMessage(
                id = UUID.randomUUID().toString(),
                content = text,
                isFromUser = true,
            )
        _messages.value = _messages.value + userMessage
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val response = daemonBridge.send(text)
                val agentMessage =
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        content = response,
                        isFromUser = false,
                    )
                _messages.value = _messages.value + agentMessage
            } catch (e: FfiException) {
                val errorMessage =
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        content = "Error: ${e.message ?: "Failed to get response"}",
                        isFromUser = false,
                    )
                _messages.value = _messages.value + errorMessage
            } catch (e: Exception) {
                val errorMessage =
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        content = "Error: ${e.message ?: "Unexpected error"}",
                        isFromUser = false,
                    )
                _messages.value = _messages.value + errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }
}
