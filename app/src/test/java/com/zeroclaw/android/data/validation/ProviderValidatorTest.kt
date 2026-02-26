/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.validation

import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ProviderValidator")
class ProviderValidatorTest {
    @Nested
    @DisplayName("classifyProbeResult")
    inner class ClassifyProbeResult {
        @Test
        @DisplayName("blank provider ID returns Idle")
        fun `blank provider ID returns Idle`() {
            val result =
                ProviderValidator.classifyProbeResult(
                    providerId = "",
                    probeResult = Result.success(listOf("model-a")),
                )
            assertEquals(ValidationResult.Idle, result)
        }

        @Test
        @DisplayName("successful probe with 2 models returns Success containing 2")
        fun `successful probe with 2 models returns Success containing count`() {
            val result =
                ProviderValidator.classifyProbeResult(
                    providerId = "openai",
                    probeResult = Result.success(listOf("gpt-4o", "gpt-4o-mini")),
                )
            assertTrue(result is ValidationResult.Success)
            val success = result as ValidationResult.Success
            assertTrue("2" in success.details)
        }

        @Test
        @DisplayName("HTTP 401 error returns non-retryable Failure")
        fun `HTTP 401 error returns non-retryable Failure`() {
            val result =
                ProviderValidator.classifyProbeResult(
                    providerId = "openai",
                    probeResult = Result.failure(IOException("HTTP 401 from https://api.openai.com/v1/models")),
                )
            assertTrue(result is ValidationResult.Failure)
            val failure = result as ValidationResult.Failure
            assertFalse(failure.retryable)
        }

        @Test
        @DisplayName("HTTP 403 error returns Failure")
        fun `HTTP 403 error returns Failure`() {
            val result =
                ProviderValidator.classifyProbeResult(
                    providerId = "anthropic",
                    probeResult = Result.failure(IOException("HTTP 403 from https://api.anthropic.com/v1/models")),
                )
            assertTrue(result is ValidationResult.Failure)
            val failure = result as ValidationResult.Failure
            assertFalse(failure.retryable)
        }

        @Test
        @DisplayName("network error returns Offline")
        fun `network error returns Offline`() {
            val result =
                ProviderValidator.classifyProbeResult(
                    providerId = "openai",
                    probeResult = Result.failure(IOException("Unable to resolve host")),
                )
            assertTrue(result is ValidationResult.Offline)
        }

        @Test
        @DisplayName("HTTP 500 returns Offline as transient error")
        fun `HTTP 500 returns Offline as transient error`() {
            val result =
                ProviderValidator.classifyProbeResult(
                    providerId = "openai",
                    probeResult = Result.failure(IOException("HTTP 500 from https://api.openai.com/v1/models")),
                )
            assertTrue(result is ValidationResult.Offline)
        }

        @Test
        @DisplayName("successful probe with 0 models returns Success containing 0")
        fun `successful probe with 0 models returns Success containing zero`() {
            val result =
                ProviderValidator.classifyProbeResult(
                    providerId = "ollama",
                    probeResult = Result.success(emptyList()),
                )
            assertTrue(result is ValidationResult.Success)
            val success = result as ValidationResult.Success
            assertTrue("0" in success.details)
        }

        @Test
        @DisplayName("successful probe with 1 model uses singular form")
        fun `successful probe with 1 model uses singular form`() {
            val result =
                ProviderValidator.classifyProbeResult(
                    providerId = "openai",
                    probeResult = Result.success(listOf("gpt-4o")),
                )
            assertTrue(result is ValidationResult.Success)
            val success = result as ValidationResult.Success
            assertTrue("1 model" in success.details)
            assertFalse("models" in success.details)
        }

        @Test
        @DisplayName("unknown provider returns non-retryable Failure")
        fun `unknown provider returns non-retryable Failure`() {
            val result =
                ProviderValidator.classifyProbeResult(
                    providerId = "totally-unknown-provider",
                    probeResult = Result.success(emptyList()),
                )
            assertTrue(result is ValidationResult.Failure)
            val failure = result as ValidationResult.Failure
            assertFalse(failure.retryable)
            assertTrue("Unknown provider" in failure.message)
        }

        @Test
        @DisplayName("provider with NONE model list format returns Success with no-endpoint message")
        fun `provider with NONE format returns Success with no endpoint message`() {
            val result =
                ProviderValidator.classifyProbeResult(
                    providerId = "xai",
                    probeResult = Result.success(emptyList()),
                )
            assertTrue(result is ValidationResult.Success)
            val success = result as ValidationResult.Success
            assertTrue("no endpoint" in success.details.lowercase())
        }
    }
}
