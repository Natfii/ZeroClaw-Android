/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings.apikeys

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [mapConnectionError], the error-message mapper used by
 * [ApiKeysViewModel.testConnection] to convert HTTP and network exceptions
 * into user-facing text.
 */
@DisplayName("Connection error message mapping")
class ConnectionErrorMessageTest {
    @Test
    @DisplayName("maps HTTP 401 to authentication failure")
    fun `maps HTTP 401 to authentication failure`() {
        val e = IOException("HTTP 401 from https://api.openai.com/v1/models")
        assertEquals(
            "Authentication failed — check your API key",
            mapConnectionError(e),
        )
    }

    @Test
    @DisplayName("maps HTTP 403 to access denied")
    fun `maps HTTP 403 to access denied`() {
        val e = IOException("HTTP 403 from https://api.anthropic.com/v1/models")
        assertEquals(
            "Access denied — check your API key permissions",
            mapConnectionError(e),
        )
    }

    @Test
    @DisplayName("maps HTTP 404 to endpoint not found")
    fun `maps HTTP 404 to endpoint not found`() {
        val e = IOException("HTTP 404 from https://localhost:11434/models")
        assertEquals(
            "Endpoint not found — check the base URL",
            mapConnectionError(e),
        )
    }

    @Test
    @DisplayName("maps HTTP 429 to rate limited")
    fun `maps HTTP 429 to rate limited`() {
        val e = IOException("HTTP 429 from https://api.openai.com/v1/models")
        assertEquals(
            "Rate limited — try again shortly",
            mapConnectionError(e),
        )
    }

    @Test
    @DisplayName("maps other HTTP errors to generic provider error")
    fun `maps other HTTP errors to generic provider error`() {
        val e = IOException("HTTP 500 from https://api.openai.com/v1/models")
        assertEquals(
            "Provider returned an error — try again later",
            mapConnectionError(e),
        )
    }

    @Test
    @DisplayName("maps HTTP 502 to generic provider error")
    fun `maps HTTP 502 to generic provider error`() {
        val e = IOException("HTTP 502 from https://api.openai.com/v1/models")
        assertEquals(
            "Provider returned an error — try again later",
            mapConnectionError(e),
        )
    }

    @Test
    @DisplayName("maps SocketTimeoutException to connection timed out")
    fun `maps SocketTimeoutException to connection timed out`() {
        val e = SocketTimeoutException("connect timeout")
        assertEquals(
            "Connection timed out — check your network",
            mapConnectionError(e),
        )
    }

    @Test
    @DisplayName("maps timeout in message to connection timed out")
    fun `maps timeout in message to connection timed out`() {
        val e = IOException("Read timed out")
        assertEquals(
            "Connection timed out — check your network",
            mapConnectionError(e),
        )
    }

    @Test
    @DisplayName("maps timeout case-insensitively")
    fun `maps timeout case-insensitively`() {
        val e = IOException("Connection TIMEOUT after 5000ms")
        assertEquals(
            "Connection timed out — check your network",
            mapConnectionError(e),
        )
    }

    @Test
    @DisplayName("maps unknown exception to generic connection failed")
    fun `maps unknown exception to generic connection failed`() {
        val e = IOException("Connection refused")
        assertEquals(
            "Connection failed — check credentials and URL",
            mapConnectionError(e),
        )
    }

    @Test
    @DisplayName("handles null exception message gracefully")
    fun `handles null exception message gracefully`() {
        val e = RuntimeException()
        assertEquals(
            "Connection failed — check credentials and URL",
            mapConnectionError(e),
        )
    }

    @Test
    @DisplayName("does not false-positive on URL containing 401")
    fun `does not false-positive on URL containing 401`() {
        val e = IOException("Connection refused to https://example.com:4013/models")
        assertEquals(
            "Connection failed — check credentials and URL",
            mapConnectionError(e),
        )
    }

    @Test
    @DisplayName("maps JSON-body auth error (HTTP 401 from response body) to authentication failure")
    fun `maps JSON-body auth error to authentication failure`() {
        val e = IOException("HTTP 401 from https://api.anthropic.com/v1/models (auth error in response body)")
        assertEquals(
            "Authentication failed — check your API key",
            mapConnectionError(e),
        )
    }
}
