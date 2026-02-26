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

@DisplayName("ValidationResult")
class ValidationResultTest {
    @Nested
    @DisplayName("isTerminal")
    inner class IsTerminal {
        @Test
        @DisplayName("Idle is not terminal")
        fun `Idle is not terminal`() {
            assertFalse(ValidationResult.Idle.isTerminal)
        }

        @Test
        @DisplayName("Loading is not terminal")
        fun `Loading is not terminal`() {
            assertFalse(ValidationResult.Loading.isTerminal)
        }

        @Test
        @DisplayName("Success is terminal")
        fun `Success is terminal`() {
            val result = ValidationResult.Success(details = "Key validated successfully")
            assertTrue(result.isTerminal)
        }

        @Test
        @DisplayName("Failure is terminal")
        fun `Failure is terminal`() {
            val result = ValidationResult.Failure(message = "Invalid key format")
            assertTrue(result.isTerminal)
        }

        @Test
        @DisplayName("Offline is terminal")
        fun `Offline is terminal`() {
            val result = ValidationResult.Offline(message = "No network connection")
            assertTrue(result.isTerminal)
        }
    }

    @Nested
    @DisplayName("Success")
    inner class SuccessVariant {
        @Test
        @DisplayName("details are accessible")
        fun `details are accessible`() {
            val details = "Model list returned 3 models"
            val result = ValidationResult.Success(details = details)
            assertEquals(details, result.details)
        }
    }

    @Nested
    @DisplayName("Failure")
    inner class FailureVariant {
        @Test
        @DisplayName("message is accessible")
        fun `message is accessible`() {
            val message = "HTTP 401 Unauthorized"
            val result = ValidationResult.Failure(message = message)
            assertEquals(message, result.message)
        }

        @Test
        @DisplayName("retryable defaults to true")
        fun `retryable defaults to true`() {
            val result = ValidationResult.Failure(message = "Temporary error")
            assertTrue(result.retryable)
        }

        @Test
        @DisplayName("retryable can be set to false")
        fun `retryable can be set to false`() {
            val result =
                ValidationResult.Failure(
                    message = "Invalid key format",
                    retryable = false,
                )
            assertFalse(result.retryable)
        }
    }

    @Nested
    @DisplayName("Offline")
    inner class OfflineVariant {
        @Test
        @DisplayName("message is accessible")
        fun `message is accessible`() {
            val message = "Device is offline"
            val result = ValidationResult.Offline(message = message)
            assertEquals(message, result.message)
        }
    }
}
