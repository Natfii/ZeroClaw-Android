/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

/**
 * Classification of API key-related errors extracted from FFI error messages.
 */
enum class KeyErrorType {
    /** Provider returned 401/403 or reported the key as invalid. */
    AUTHENTICATION_FAILED,

    /** Provider returned 429 or indicated a rate limit. */
    RATE_LIMITED,
}

/**
 * Classifies FFI error messages to detect API key rejections.
 *
 * Scans error detail strings for known patterns that indicate provider-side
 * authentication failures or rate limiting. Returns null for errors
 * unrelated to API key validity.
 */
object ApiKeyErrorClassifier {
    private val authPatterns =
        listOf(
            "401",
            "403",
            "unauthorized",
            "forbidden",
            "invalid_api_key",
            "incorrect_api_key",
        )

    private val rateLimitPatterns =
        listOf(
            "429",
            "rate limit",
            "rate_limit",
        )

    /**
     * Attempts to classify an error detail string as a key-related error.
     *
     * @param errorDetail The error message from the FFI layer.
     * @return The [KeyErrorType] if the error matches a known pattern, or null
     *   if the error is unrelated to API key validity.
     */
    fun classify(errorDetail: String): KeyErrorType? {
        val lower = errorDetail.lowercase()
        if (authPatterns.any { lower.contains(it) }) {
            return KeyErrorType.AUTHENTICATION_FAILED
        }
        if (rateLimitPatterns.any { lower.contains(it) }) {
            return KeyErrorType.RATE_LIMITED
        }
        return null
    }
}
