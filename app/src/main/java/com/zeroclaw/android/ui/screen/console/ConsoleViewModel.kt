/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.console

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.model.ChatMessage
import com.zeroclaw.android.model.LogSeverity
import com.zeroclaw.android.model.ProcessedImage
import com.zeroclaw.android.util.ErrorSanitizer
import com.zeroclaw.android.util.ImageProcessor
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
 * app restarts. Supports image attachments via the photo picker, with
 * vision requests routed directly to the provider API.
 *
 * @param application Application context for accessing repositories and bridges.
 */
class ConsoleViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = application as ZeroClawApplication
    private val chatMessageRepository = app.chatMessageRepository
    private val daemonBridge = app.daemonBridge
    private val logRepository = app.logRepository
    private val settingsRepository = app.settingsRepository
    private val visionBridge = app.visionBridge

    /** Ordered list of chat messages (oldest first), backed by Room. */
    val messages: StateFlow<List<ChatMessage>> =
        chatMessageRepository.messages
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val cachedSettings: StateFlow<AppSettings> =
        settingsRepository.settings
            .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _isLoading = MutableStateFlow(false)

    /** True while waiting for a response from the daemon or vision API. */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _pendingImages = MutableStateFlow<List<ProcessedImage>>(emptyList())

    /** Images currently staged for the next message (before sending). */
    val pendingImages: StateFlow<List<ProcessedImage>> = _pendingImages.asStateFlow()

    private val _isProcessingImages = MutableStateFlow(false)

    /** True while images are being downscaled and encoded. */
    val isProcessingImages: StateFlow<Boolean> = _isProcessingImages.asStateFlow()

    /**
     * Processes and stages images from the photo picker.
     *
     * Runs [ImageProcessor.process] on [Dispatchers.IO][kotlinx.coroutines.Dispatchers.IO]
     * to downscale, compress, and base64-encode the selected images.
     * Results are appended to [pendingImages], capped at [MAX_IMAGES].
     *
     * @param uris Content URIs returned by the photo picker.
     */
    fun attachImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isProcessingImages.value = true
            try {
                val contentResolver = app.contentResolver
                val processed = ImageProcessor.process(contentResolver, uris)
                val current = _pendingImages.value
                val combined = (current + processed).take(MAX_IMAGES)
                _pendingImages.value = combined
            } finally {
                _isProcessingImages.value = false
            }
        }
    }

    /**
     * Removes a pending image at the given index.
     *
     * @param index Zero-based index into [pendingImages].
     */
    fun removeImage(index: Int) {
        val current = _pendingImages.value
        if (index in current.indices) {
            _pendingImages.value = current.toMutableList().apply { removeAt(index) }
        }
    }

    /**
     * Sends a user message to the daemon and persists both the request and response.
     *
     * When [pendingImages] is non-empty, routes the message through [VisionBridge]
     * for direct-to-provider multimodal dispatch. Otherwise sends through the
     * standard daemon gateway.
     *
     * The user message is immediately persisted via Room. While waiting for the
     * response, [isLoading] is true. On success the response is persisted;
     * on failure an error message is stored instead.
     *
     * @param text The message text to send.
     */
    @Suppress("TooGenericExceptionCaught")
    fun sendMessage(text: String) {
        val images = _pendingImages.value
        if (text.isBlank() && images.isEmpty()) return
        _isLoading.value = true
        _pendingImages.value = emptyList()

        viewModelScope.launch {
            val imageUris = images.map { it.originalUri }
            chatMessageRepository.append(
                content = text,
                isFromUser = true,
                imageUris = imageUris,
            )

            try {
                val rawResponse =
                    if (images.isNotEmpty()) {
                        visionBridge.send(text, images)
                    } else {
                        daemonBridge.send(text)
                    }
                val response =
                    if (cachedSettings.value.stripThinkingTags) {
                        stripThinkingTags(rawResponse)
                    } else {
                        rawResponse
                    }
                chatMessageRepository.append(content = response, isFromUser = false)
            } catch (e: FfiException) {
                val sanitized = ErrorSanitizer.sanitizeForUi(e)
                logRepository.append(LogSeverity.ERROR, TAG, "Send failed: $sanitized")
                chatMessageRepository.append(
                    content = "Error: $sanitized",
                    isFromUser = false,
                )
            } catch (e: Exception) {
                val sanitized = ErrorSanitizer.sanitizeForUi(e)
                logRepository.append(LogSeverity.ERROR, TAG, "Send failed: $sanitized")
                chatMessageRepository.append(
                    content = "Error: $sanitized",
                    isFromUser = false,
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Retries a failed daemon response by re-sending the preceding user message.
     *
     * Looks up the most recent user message with an [id] less than [errorMessageId]
     * and re-sends its content through [sendMessage].
     *
     * @param errorMessageId The [ChatMessage.id] of the error response to retry.
     */
    fun retryMessage(errorMessageId: Long) {
        viewModelScope.launch {
            val current = messages.value
            val lastUserMsg = current.lastOrNull { it.isFromUser && it.id < errorMessageId }
            lastUserMsg?.let { sendMessage(it.content) }
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
        private const val TAG = "Console"

        /** Timeout in milliseconds before upstream Flow collection stops when UI is hidden. */
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Maximum number of images per message (matches FFI-side limit). */
        private const val MAX_IMAGES = 5

        /** Pattern matching common chain-of-thought tag variants across models. */
        private val THINKING_TAG_REGEX =
            Regex("<(?:think|thinking)>[\\s\\S]*?</(?:think|thinking)>", RegexOption.IGNORE_CASE)

        /**
         * Removes chain-of-thought thinking tags from a model response.
         *
         * Strips `<think>...</think>` and `<thinking>...</thinking>` blocks
         * emitted by reasoning models (Gemma, DeepSeek-R1, QwQ, etc.).
         *
         * @param text Raw model response.
         * @return Response with thinking blocks removed and leading whitespace trimmed.
         */
        fun stripThinkingTags(text: String): String = text.replace(THINKING_TAG_REGEX, "").trim()
    }
}
