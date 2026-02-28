/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import android.util.Log
import com.zeroclaw.android.util.LogSanitizer
import com.zeroclaw.ffi.FfiStreamListener
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Events emitted by the streaming FFI callback.
 *
 * Each variant corresponds to a callback method on the native
 * [FfiStreamListener] interface. Events are emitted from the Rust
 * tokio runtime thread and collected on Kotlin coroutine dispatchers.
 */
sealed class StreamEvent {
    /**
     * A chunk of thinking/reasoning tokens from the model.
     *
     * @property text The thinking token text delta.
     */
    data class ThinkingChunk(
        val text: String,
    ) : StreamEvent()

    /**
     * A chunk of response content tokens from the model.
     *
     * @property text The response token text delta.
     */
    data class ResponseChunk(
        val text: String,
    ) : StreamEvent()

    /**
     * The stream completed successfully.
     *
     * @property fullResponse The complete assembled response text.
     */
    data class Complete(
        val fullResponse: String,
    ) : StreamEvent()

    /**
     * An error occurred during streaming.
     *
     * @property message Human-readable error description.
     */
    data class Error(
        val message: String,
    ) : StreamEvent()
}

/**
 * Bridge between the Rust streaming callback interface and Kotlin reactive layer.
 *
 * Implements [FfiStreamListener] so it can be passed to [com.zeroclaw.ffi.sendMessageStreaming].
 * Incoming chunks are emitted on a [SharedFlow] for ViewModel consumption.
 *
 * Thread-safe: the native callbacks arrive from the Rust tokio runtime thread.
 * [MutableSharedFlow] is thread-safe and uses [BufferOverflow.DROP_OLDEST] with a
 * capacity of 256 events to avoid back-pressure blocking the native callback thread.
 */
class StreamingBridge : FfiStreamListener {
    private val _events =
        MutableSharedFlow<StreamEvent>(
            extraBufferCapacity = BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /**
     * Observable stream of streaming events from the native callback.
     *
     * Collectors should process events in order: [StreamEvent.ThinkingChunk] during
     * the thinking phase, [StreamEvent.ResponseChunk] during response generation,
     * and [StreamEvent.Complete] or [StreamEvent.Error] as terminal events.
     */
    val events: SharedFlow<StreamEvent> = _events.asSharedFlow()

    /**
     * Called by the native layer with each thinking/reasoning token chunk.
     *
     * @param text The thinking token text delta.
     */
    override fun onThinkingChunk(text: String) {
        if (!_events.tryEmit(StreamEvent.ThinkingChunk(text))) {
            Log.w(TAG, "Thinking chunk dropped — buffer full")
        }
    }

    /**
     * Called by the native layer with each response content token chunk.
     *
     * @param text The response token text delta.
     */
    override fun onResponseChunk(text: String) {
        if (!_events.tryEmit(StreamEvent.ResponseChunk(text))) {
            Log.w(TAG, "Response chunk dropped — buffer full")
        }
    }

    /**
     * Called by the native layer when the stream completes successfully.
     *
     * @param fullResponse The complete assembled response text.
     */
    override fun onComplete(fullResponse: String) {
        _events.tryEmit(StreamEvent.Complete(fullResponse))
    }

    /**
     * Called by the native layer when an error occurs during streaming.
     *
     * @param error Human-readable error description.
     */
    override fun onError(error: String) {
        Log.e(TAG, "Streaming error: ${LogSanitizer.sanitizeLogMessage(error)}")
        _events.tryEmit(StreamEvent.Error(error))
    }

    /** Constants for [StreamingBridge]. */
    companion object {
        private const val TAG = "StreamingBridge"
        private const val BUFFER_CAPACITY = 256
    }
}
