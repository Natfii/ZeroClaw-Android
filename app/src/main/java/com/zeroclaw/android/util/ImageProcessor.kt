/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.zeroclaw.android.model.ProcessedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Processes gallery images for transmission to vision APIs via the FFI bridge.
 *
 * Handles downscaling, JPEG compression, and base64 encoding entirely on
 * [Dispatchers.IO]. Images that are unreadable or exceed size limits after
 * maximum compression are silently skipped.
 */
object ImageProcessor {
    /** Maximum pixel dimension on the longest edge after scaling. */
    private const val MAX_DIMENSION = 1568

    /** Maximum encoded image size in bytes (5 MB). */
    private const val MAX_BYTES = 5 * 1024 * 1024

    /** Initial JPEG compression quality. */
    private const val INITIAL_QUALITY = 80

    /** Minimum JPEG compression quality before giving up. */
    private const val MIN_QUALITY = 50

    /** Quality step-down increment when image exceeds [MAX_BYTES]. */
    private const val QUALITY_STEP = 10

    /** MIME type for all processed output images. */
    private const val OUTPUT_MIME = "image/jpeg"

    /** Fallback display name when the content provider has no filename. */
    private const val FALLBACK_NAME = "image"

    /**
     * Processes a list of image URIs into base64-encoded [ProcessedImage] objects.
     *
     * Each image is decoded, downscaled if necessary, JPEG-compressed, and
     * base64-encoded. Images that fail to decode or remain over [MAX_BYTES]
     * after stepping quality down to [MIN_QUALITY] are excluded from the result.
     *
     * Safe to call from the main thread; all I/O runs on [Dispatchers.IO].
     *
     * @param contentResolver Application content resolver for reading image streams.
     * @param uris Content URIs from the photo picker.
     * @return List of successfully processed images (may be smaller than input).
     */
    suspend fun process(
        contentResolver: ContentResolver,
        uris: List<Uri>,
    ): List<ProcessedImage> =
        withContext(Dispatchers.IO) {
            uris.mapNotNull { uri -> processOne(contentResolver, uri) }
        }

    /**
     * Processes a single image URI.
     *
     * @return [ProcessedImage] on success, or null if the image cannot be processed.
     */
    private fun processOne(
        contentResolver: ContentResolver,
        uri: Uri,
    ): ProcessedImage? {
        val bitmap = decodeSampled(contentResolver, uri) ?: return null
        val scaled = scaleIfNeeded(bitmap)
        val (bytes, quality) = compressWithStepDown(scaled)
        if (scaled !== bitmap) bitmap.recycle()

        if (bytes == null) {
            scaled.recycle()
            return null
        }

        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val name = queryDisplayName(contentResolver, uri)

        val result = ProcessedImage(
            base64Data = base64,
            mimeType = OUTPUT_MIME,
            width = scaled.width,
            height = scaled.height,
            originalUri = uri.toString(),
            displayName = name,
        )
        scaled.recycle()
        return result
    }

    /**
     * Decodes a bitmap with power-of-two subsampling to reduce initial memory.
     *
     * First reads only the image dimensions via [BitmapFactory.Options.inJustDecodeBounds],
     * then calculates an appropriate [BitmapFactory.Options.inSampleSize] to
     * avoid allocating a full-resolution bitmap when the source is much larger
     * than [MAX_DIMENSION].
     */
    private fun decodeSampled(
        contentResolver: ContentResolver,
        uri: Uri,
    ): Bitmap? =
        runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

            options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight)
            options.inJustDecodeBounds = false

            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()

    /**
     * Calculates power-of-two sample size so the decoded bitmap's longest edge
     * is at most [MAX_DIMENSION].
     */
    private fun calculateSampleSize(
        width: Int,
        height: Int,
    ): Int {
        var sampleSize = 1
        val longest = maxOf(width, height)
        while (longest / sampleSize > MAX_DIMENSION * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * Scales the bitmap proportionally if the longest edge exceeds [MAX_DIMENSION].
     *
     * Returns the original bitmap unmodified if no scaling is needed.
     */
    private fun scaleIfNeeded(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_DIMENSION) return bitmap

        val scale = MAX_DIMENSION.toFloat() / longest
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Compresses the bitmap to JPEG, stepping quality down if the result exceeds [MAX_BYTES].
     *
     * @return Pair of (compressed bytes, quality used), or (null, 0) if the image
     * cannot be compressed under [MAX_BYTES] even at [MIN_QUALITY].
     */
    private fun compressWithStepDown(bitmap: Bitmap): Pair<ByteArray?, Int> {
        var quality = INITIAL_QUALITY
        while (quality >= MIN_QUALITY) {
            val buffer = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer)
            val bytes = buffer.toByteArray()
            if (bytes.size <= MAX_BYTES) return bytes to quality
            quality -= QUALITY_STEP
        }
        return null to 0
    }

    /**
     * Queries the display name (filename) for a content URI.
     *
     * @return The filename from the content provider, or [FALLBACK_NAME] if unavailable.
     */
    private fun queryDisplayName(
        contentResolver: ContentResolver,
        uri: Uri,
    ): String =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else {
                        null
                    }
                }
        }.getOrNull() ?: FALLBACK_NAME
}
