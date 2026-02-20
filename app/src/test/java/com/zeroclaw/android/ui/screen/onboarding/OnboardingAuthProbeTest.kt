/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [isDefinitiveAuthFailure], the probe decision function
 * used by [OnboardingViewModel.complete] to detect bad credentials before
 * persisting them.
 *
 * Only HTTP 401 and 403 should block onboarding; all other failures
 * (network errors, 5xx, timeouts) must pass through so that offline
 * setup and transient provider issues do not prevent completion.
 */
@DisplayName("Onboarding auth probe detection")
class OnboardingAuthProbeTest {
    @Test
    @DisplayName("returns true for HTTP 401")
    fun `returns true for HTTP 401`() {
        val result =
            Result.failure<List<String>>(
                IOException("HTTP 401 from https://api.openai.com/v1/models"),
            )
        assertTrue(isDefinitiveAuthFailure(result))
    }

    @Test
    @DisplayName("returns true for HTTP 403")
    fun `returns true for HTTP 403`() {
        val result =
            Result.failure<List<String>>(
                IOException("HTTP 403 from https://api.anthropic.com/v1/models"),
            )
        assertTrue(isDefinitiveAuthFailure(result))
    }

    @Test
    @DisplayName("returns false for successful probe")
    fun `returns false for successful probe`() {
        val result = Result.success(listOf("gpt-4o", "gpt-4o-mini"))
        assertFalse(isDefinitiveAuthFailure(result))
    }

    @Test
    @DisplayName("returns false for HTTP 500 server error")
    fun `returns false for HTTP 500 server error`() {
        val result =
            Result.failure<List<String>>(
                IOException("HTTP 500 from https://api.openai.com/v1/models"),
            )
        assertFalse(isDefinitiveAuthFailure(result))
    }

    @Test
    @DisplayName("returns false for HTTP 502 gateway error")
    fun `returns false for HTTP 502 gateway error`() {
        val result =
            Result.failure<List<String>>(
                IOException("HTTP 502 from https://api.openai.com/v1/models"),
            )
        assertFalse(isDefinitiveAuthFailure(result))
    }

    @Test
    @DisplayName("returns false for HTTP 429 rate limit")
    fun `returns false for HTTP 429 rate limit`() {
        val result =
            Result.failure<List<String>>(
                IOException("HTTP 429 from https://api.openai.com/v1/models"),
            )
        assertFalse(isDefinitiveAuthFailure(result))
    }

    @Test
    @DisplayName("returns false for HTTP 404 not found")
    fun `returns false for HTTP 404 not found`() {
        val result =
            Result.failure<List<String>>(
                IOException("HTTP 404 from https://localhost:11434/models"),
            )
        assertFalse(isDefinitiveAuthFailure(result))
    }

    @Test
    @DisplayName("returns false for network timeout")
    fun `returns false for network timeout`() {
        val result =
            Result.failure<List<String>>(
                SocketTimeoutException("connect timed out"),
            )
        assertFalse(isDefinitiveAuthFailure(result))
    }

    @Test
    @DisplayName("returns false for DNS resolution failure")
    fun `returns false for DNS resolution failure`() {
        val result =
            Result.failure<List<String>>(
                UnknownHostException("api.openai.com"),
            )
        assertFalse(isDefinitiveAuthFailure(result))
    }

    @Test
    @DisplayName("returns false for connection refused")
    fun `returns false for connection refused`() {
        val result =
            Result.failure<List<String>>(
                IOException("Connection refused"),
            )
        assertFalse(isDefinitiveAuthFailure(result))
    }

    @Test
    @DisplayName("returns false for null exception message")
    fun `returns false for null exception message`() {
        val result = Result.failure<List<String>>(RuntimeException())
        assertFalse(isDefinitiveAuthFailure(result))
    }

    @Test
    @DisplayName("does not false-positive on URL containing 401")
    fun `does not false-positive on URL containing 401`() {
        val result =
            Result.failure<List<String>>(
                IOException("Connection refused to https://example.com:4013/models"),
            )
        assertFalse(isDefinitiveAuthFailure(result))
    }
}
