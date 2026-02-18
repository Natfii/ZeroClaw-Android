/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * A gallery image that has been downscaled, compressed, and base64-encoded
 * for transmission across the FFI boundary to the vision API.
 *
 * @property base64Data Base64-encoded JPEG image data (NO_WRAP encoding).
 * @property mimeType MIME type of the compressed image (always "image/jpeg").
 * @property width Width of the processed image in pixels.
 * @property height Height of the processed image in pixels.
 * @property originalUri Content URI of the original image from the gallery picker.
 * @property displayName Human-readable filename from the content provider, or "image" if unknown.
 */
data class ProcessedImage(
    val base64Data: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val originalUri: String,
    val displayName: String,
)
