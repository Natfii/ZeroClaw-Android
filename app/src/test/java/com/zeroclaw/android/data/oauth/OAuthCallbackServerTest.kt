/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.oauth

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Unit tests for [OAuthCallbackServer]. */
@DisplayName("OAuthCallbackServer")
class OAuthCallbackServerTest {
    /** Delay in milliseconds to allow the server socket to bind. */
    private val serverStartDelayMs = 100L

    /** Short timeout for the timeout-verification test. */
    private val shortTimeoutMs = 500L

    /** Port used for tests to avoid conflicts with production default. */
    private val testPort = OAuthCallbackServer.DEFAULT_PORT

    @Test
    @DisplayName("server receives callback with code and state")
    fun `server receives callback with code and state`() =
        runTest {
            val server = OAuthCallbackServer(port = testPort)
            server.start()
            try {
                delay(serverStartDelayMs)

                launch {
                    val url = URL("http://localhost:$testPort/auth/callback?code=abc123&state=xyz789")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.responseCode
                    conn.disconnect()
                }

                val result = server.awaitCallback()

                assertNotNull(result) { "Callback result should not be null" }
                assertEquals("abc123", result?.code) {
                    "Authorization code should match the query parameter"
                }
                assertEquals("xyz789", result?.state) {
                    "State nonce should match the query parameter"
                }
            } finally {
                server.stop()
            }
        }

    @Test
    @DisplayName("server returns null on timeout")
    fun `server returns null on timeout`() =
        runTest {
            val server = OAuthCallbackServer(port = testPort, timeoutMs = shortTimeoutMs)
            server.start()
            try {
                delay(serverStartDelayMs)

                val result = server.awaitCallback()

                assertNull(result) { "Callback result should be null when timeout elapses" }
            } finally {
                server.stop()
            }
        }
}
