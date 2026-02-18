/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.console

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.ChatMessage
import com.zeroclaw.ffi.FfiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the global daemon console screen.
 *
 * Manages the persisted chat message list and sends messages to the daemon
 * gateway via FFI. Messages are stored in Room and survive navigation and
 * app restarts.
 *
 * @param application Application context for accessing repositories and the daemon bridge.
 */
class ConsoleViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = application as ZeroClawApplication
    private val chatMessageRepository = app.chatMessageRepository
    private val daemonBridge = app.daemonBridge

    /** Ordered list of chat messages (oldest first), backed by Room. */
    val messages: StateFlow<List<ChatMessage>> =
        chatMessageRepository.messages
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _isLoading = MutableStateFlow(false)

    /** True while waiting for a response from the daemon. */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Sends a user message to the daemon and persists both the request and response.
     *
     * The user message is immediately persisted via Room. While waiting for the
     * FFI response, [isLoading] is true. On success the daemon response is persisted;
     * on failure an error message is stored instead.
     *
     * @param text The message text to send.
     */
    @Suppress("TooGenericExceptionCaught")
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        _isLoading.value = true

        viewModelScope.launch {
            chatMessageRepository.append(content = text, isFromUser = true)

            try {
                val response = daemonBridge.send(text)
                chatMessageRepository.append(content = response, isFromUser = false)
            } catch (e: FfiException) {
                chatMessageRepository.append(
                    content = "Error: ${e.message ?: "Failed to get response"}",
                    isFromUser = false,
                )
            } catch (e: Exception) {
                chatMessageRepository.append(
                    content = "Error: ${e.message ?: "Unexpected error"}",
                    isFromUser = false,
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Clears all console chat history.
     */
    fun clearHistory() {
        chatMessageRepository.clear()
    }

    /** Constants for [ConsoleViewModel]. */
    companion object {
        /** Timeout in milliseconds before upstream Flow collection stops when UI is hidden. */
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
