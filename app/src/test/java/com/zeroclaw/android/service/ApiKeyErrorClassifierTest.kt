/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ApiKeyErrorClassifier].
 */
@DisplayName("ApiKeyErrorClassifier")
class ApiKeyErrorClassifierTest {
    @Test
    @DisplayName("401 status maps to AUTHENTICATION_FAILED")
    fun `401 status maps to AUTHENTICATION_FAILED`() {
        assertEquals(
            KeyErrorType.AUTHENTICATION_FAILED,
            ApiKeyErrorClassifier.classify("gateway returned status 401"),
        )
    }

    @Test
    @DisplayName("403 Forbidden maps to AUTHENTICATION_FAILED")
    fun `403 Forbidden maps to AUTHENTICATION_FAILED`() {
        assertEquals(
            KeyErrorType.AUTHENTICATION_FAILED,
            ApiKeyErrorClassifier.classify("gateway returned status 403 Forbidden"),
        )
    }

    @Test
    @DisplayName("invalid_api_key maps to AUTHENTICATION_FAILED")
    fun `invalid_api_key maps to AUTHENTICATION_FAILED`() {
        assertEquals(
            KeyErrorType.AUTHENTICATION_FAILED,
            ApiKeyErrorClassifier.classify("invalid_api_key"),
        )
    }

    @Test
    @DisplayName("incorrect_api_key maps to AUTHENTICATION_FAILED")
    fun `incorrect_api_key maps to AUTHENTICATION_FAILED`() {
        assertEquals(
            KeyErrorType.AUTHENTICATION_FAILED,
            ApiKeyErrorClassifier.classify("incorrect_api_key provided"),
        )
    }

    @Test
    @DisplayName("Unauthorized maps to AUTHENTICATION_FAILED")
    fun `Unauthorized maps to AUTHENTICATION_FAILED`() {
        assertEquals(
            KeyErrorType.AUTHENTICATION_FAILED,
            ApiKeyErrorClassifier.classify("Request Unauthorized"),
        )
    }

    @Test
    @DisplayName("429 status maps to RATE_LIMITED")
    fun `429 status maps to RATE_LIMITED`() {
        assertEquals(
            KeyErrorType.RATE_LIMITED,
            ApiKeyErrorClassifier.classify("gateway returned status 429"),
        )
    }

    @Test
    @DisplayName("rate limit phrase maps to RATE_LIMITED")
    fun `rate limit phrase maps to RATE_LIMITED`() {
        assertEquals(
            KeyErrorType.RATE_LIMITED,
            ApiKeyErrorClassifier.classify("rate limit exceeded for this key"),
        )
    }

    @Test
    @DisplayName("connection refused returns null")
    fun `connection refused returns null`() {
        assertNull(ApiKeyErrorClassifier.classify("connection refused"))
    }

    @Test
    @DisplayName("timeout returns null")
    fun `timeout returns null`() {
        assertNull(ApiKeyErrorClassifier.classify("timeout after 30s"))
    }

    @Test
    @DisplayName("empty string returns null")
    fun `empty string returns null`() {
        assertNull(ApiKeyErrorClassifier.classify(""))
    }
}
