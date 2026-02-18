/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import com.zeroclaw.android.model.ProcessedImage
import com.zeroclaw.ffi.FfiException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridge between the Android UI layer and the Rust vision FFI.
 *
 * Wraps [com.zeroclaw.ffi.sendVisionMessage] in a coroutine-safe suspend
 * function dispatched to [Dispatchers.IO]. Accepts [ProcessedImage] objects
 * and extracts the base64 data and MIME types for the FFI call.
 *
 * @param ioDispatcher Dispatcher for blocking FFI calls. Defaults to [Dispatchers.IO].
 */
class VisionBridge(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Sends a vision (image + text) message to the configured provider.
     *
     * Bypasses the `ZeroClaw` agent loop and calls the provider's multimodal
     * API directly. Returns the assistant's text response.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @param text User's text prompt accompanying the images.
     * @param images Processed images with base64 data and MIME types.
     * @return The assistant's text response from the vision API.
     * @throws FfiException if the native layer reports an error (unsupported
     *   provider, HTTP failure, invalid input).
     */
    @Throws(FfiException::class)
    suspend fun send(
        text: String,
        images: List<ProcessedImage>,
    ): String =
        withContext(ioDispatcher) {
            com.zeroclaw.ffi.sendVisionMessage(
                text = text,
                imageData = images.map { it.base64Data },
                mimeTypes = images.map { it.mimeType },
            )
        }
}
