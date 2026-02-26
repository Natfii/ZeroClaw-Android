/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.validation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ChannelValidator")
class ChannelValidatorTest {
    @Nested
    @DisplayName("classifyTelegramResponse")
    inner class ClassifyTelegramResponse {
        @Test
        @DisplayName("valid response extracts username with @ prefix")
        fun `valid response extracts username with at prefix`() {
            val body = """{"ok":true,"result":{"username":"test_bot"}}"""
            val result = ChannelValidator.classifyTelegramResponse(HTTP_OK, body)
            assertTrue(result is ValidationResult.Success)
            val success = result as ValidationResult.Success
            assertEquals("Connected as @test_bot", success.details)
        }

        @Test
        @DisplayName("ok false with description returns Failure")
        fun `ok false with description returns Failure`() {
            val body = """{"ok":false,"description":"Unauthorized"}"""
            val result = ChannelValidator.classifyTelegramResponse(HTTP_OK, body)
            assertTrue(result is ValidationResult.Failure)
            val failure = result as ValidationResult.Failure
            assertFalse(failure.retryable)
        }

        @Test
        @DisplayName("HTTP 401 returns non-retryable Failure")
        fun `HTTP 401 returns non-retryable Failure`() {
            val result =
                ChannelValidator.classifyTelegramResponse(HTTP_UNAUTHORIZED, "")
            assertTrue(result is ValidationResult.Failure)
            val failure = result as ValidationResult.Failure
            assertFalse(failure.retryable)
            assertTrue("Invalid token" in failure.message)
        }

        @Test
        @DisplayName("malformed JSON returns Offline")
        fun `malformed JSON returns Offline`() {
            val result =
                ChannelValidator.classifyTelegramResponse(HTTP_OK, "not json {{{")
            assertTrue(result is ValidationResult.Offline)
        }
    }

    @Nested
    @DisplayName("classifyDiscordResponse")
    inner class ClassifyDiscordResponse {
        @Test
        @DisplayName("valid response extracts username")
        fun `valid response extracts username`() {
            val body = """{"username":"TestBot","discriminator":"0"}"""
            val result = ChannelValidator.classifyDiscordResponse(HTTP_OK, body)
            assertTrue(result is ValidationResult.Success)
            val success = result as ValidationResult.Success
            assertEquals("Connected as TestBot", success.details)
        }

        @Test
        @DisplayName("HTTP 401 returns non-retryable Failure")
        fun `HTTP 401 returns non-retryable Failure`() {
            val result =
                ChannelValidator.classifyDiscordResponse(HTTP_UNAUTHORIZED, "")
            assertTrue(result is ValidationResult.Failure)
            val failure = result as ValidationResult.Failure
            assertFalse(failure.retryable)
            assertTrue("Invalid token" in failure.message)
        }
    }

    @Nested
    @DisplayName("classifySlackResponse")
    inner class ClassifySlackResponse {
        @Test
        @DisplayName("valid response extracts team and user")
        fun `valid response extracts team and user`() {
            val body = """{"ok":true,"team":"MyTeam","user":"bot"}"""
            val result = ChannelValidator.classifySlackResponse(HTTP_OK, body)
            assertTrue(result is ValidationResult.Success)
            val success = result as ValidationResult.Success
            assertEquals("Connected to MyTeam as bot", success.details)
        }

        @Test
        @DisplayName("ok false with error returns Failure")
        fun `ok false with error returns Failure`() {
            val body = """{"ok":false,"error":"invalid_auth"}"""
            val result = ChannelValidator.classifySlackResponse(HTTP_OK, body)
            assertTrue(result is ValidationResult.Failure)
            val failure = result as ValidationResult.Failure
            assertFalse(failure.retryable)
        }
    }

    @Nested
    @DisplayName("classifyMatrixResponse")
    inner class ClassifyMatrixResponse {
        @Test
        @DisplayName("valid response extracts user_id")
        fun `valid response extracts user_id`() {
            val body = """{"user_id":"@bot:matrix.org"}"""
            val result = ChannelValidator.classifyMatrixResponse(HTTP_OK, body)
            assertTrue(result is ValidationResult.Success)
            val success = result as ValidationResult.Success
            assertEquals("Connected as @bot:matrix.org", success.details)
        }

        @Test
        @DisplayName("HTTP 401 returns non-retryable Failure")
        fun `HTTP 401 returns non-retryable Failure`() {
            val result =
                ChannelValidator.classifyMatrixResponse(HTTP_UNAUTHORIZED, "")
            assertTrue(result is ValidationResult.Failure)
            val failure = result as ValidationResult.Failure
            assertFalse(failure.retryable)
            assertTrue("Invalid token" in failure.message)
        }
    }

    @Nested
    @DisplayName("other channels")
    inner class OtherChannels {
        @Test
        @DisplayName("IRC returns deferred validation Success")
        fun `IRC returns deferred validation Success`() {
            val result = ChannelValidator.classifyOtherChannel()
            assertEquals(
                "Will be verified when daemon starts",
                result.details,
            )
        }

        @Test
        @DisplayName("EMAIL returns deferred validation Success")
        fun `EMAIL returns same deferred validation Success`() {
            val result = ChannelValidator.classifyOtherChannel()
            assertEquals(
                "Will be verified when daemon starts",
                result.details,
            )
        }
    }

    companion object {
        /** HTTP 200 OK. */
        private const val HTTP_OK = 200

        /** HTTP 401 Unauthorized. */
        private const val HTTP_UNAUTHORIZED = 401
    }
}
