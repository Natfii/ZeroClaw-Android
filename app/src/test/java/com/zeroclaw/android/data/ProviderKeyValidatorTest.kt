/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data

import com.zeroclaw.android.model.ProviderAuthType
import com.zeroclaw.android.model.ProviderInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ProviderKeyValidator")
class ProviderKeyValidatorTest {

    private fun provider(
        keyPrefix: String = "",
        keyPrefixHint: String = "",
    ) = ProviderInfo(
        id = "test",
        displayName = "Test",
        authType = ProviderAuthType.API_KEY_ONLY,
        keyPrefix = keyPrefix,
        keyPrefixHint = keyPrefixHint,
    )

    @Nested
    @DisplayName("validateKeyFormat")
    inner class ValidateKeyFormat {
        @Test
        @DisplayName("returns null when keyPrefix is empty")
        fun `returns null when keyPrefix is empty`() {
            assertNull(ProviderKeyValidator.validateKeyFormat(provider(), "any-key"))
        }

        @Test
        @DisplayName("returns null when key is blank")
        fun `returns null when key is blank`() {
            assertNull(
                ProviderKeyValidator.validateKeyFormat(
                    provider(keyPrefix = "sk-", keyPrefixHint = "Keys start with sk-"),
                    "",
                ),
            )
        }

        @Test
        @DisplayName("returns null when key matches prefix")
        fun `returns null when key matches prefix`() {
            assertNull(
                ProviderKeyValidator.validateKeyFormat(
                    provider(keyPrefix = "sk-", keyPrefixHint = "Keys start with sk-"),
                    "sk-proj-abc123",
                ),
            )
        }

        @Test
        @DisplayName("returns hint when key does not match prefix")
        fun `returns hint when key does not match prefix`() {
            assertEquals(
                "Keys start with sk-",
                ProviderKeyValidator.validateKeyFormat(
                    provider(keyPrefix = "sk-", keyPrefixHint = "Keys start with sk-"),
                    "wrong-key-format",
                ),
            )
        }

        @Test
        @DisplayName("handles Anthropic prefix correctly")
        fun `handles Anthropic prefix correctly`() {
            val anthropic = provider(keyPrefix = "sk-ant-", keyPrefixHint = "Keys start with sk-ant-")
            assertNull(ProviderKeyValidator.validateKeyFormat(anthropic, "sk-ant-api03-xyz"))
            assertEquals(
                "Keys start with sk-ant-",
                ProviderKeyValidator.validateKeyFormat(anthropic, "sk-other-key"),
            )
        }

        @Test
        @DisplayName("handles Groq prefix correctly")
        fun `handles Groq prefix correctly`() {
            val groq = provider(keyPrefix = "gsk_", keyPrefixHint = "Keys start with gsk_")
            assertNull(ProviderKeyValidator.validateKeyFormat(groq, "gsk_abc123"))
            assertEquals(
                "Keys start with gsk_",
                ProviderKeyValidator.validateKeyFormat(groq, "sk-wrong"),
            )
        }

        @Test
        @DisplayName("handles Google Gemini prefix correctly")
        fun `handles Google Gemini prefix correctly`() {
            val gemini = provider(keyPrefix = "AIza", keyPrefixHint = "Keys start with AIza")
            assertNull(ProviderKeyValidator.validateKeyFormat(gemini, "AIzaSyAbc123"))
            assertEquals(
                "Keys start with AIza",
                ProviderKeyValidator.validateKeyFormat(gemini, "sk-wrong"),
            )
        }
    }

    @Nested
    @DisplayName("isJsonBodyAuthError")
    inner class IsJsonBodyAuthError {
        @Test
        @DisplayName("returns false for null body")
        fun `returns false for null body`() {
            assertFalse(ProviderKeyValidator.isJsonBodyAuthError(null))
        }

        @Test
        @DisplayName("returns false for empty body")
        fun `returns false for empty body`() {
            assertFalse(ProviderKeyValidator.isJsonBodyAuthError(""))
        }

        @Test
        @DisplayName("returns false for valid model list response")
        fun `returns false for valid model list response`() {
            val json = """{"data": [{"id": "gpt-4o"}]}"""
            assertFalse(ProviderKeyValidator.isJsonBodyAuthError(json))
        }

        @Test
        @DisplayName("detects Anthropic authentication_error")
        fun `detects Anthropic authentication_error`() {
            val json = """{"type": "error", "error": {"type": "authentication_error", "message": "invalid x-api-key"}}"""
            assertTrue(ProviderKeyValidator.isJsonBodyAuthError(json))
        }

        @Test
        @DisplayName("detects OpenAI invalid_api_key")
        fun `detects OpenAI invalid_api_key`() {
            val json = """{"error": {"message": "Incorrect API key", "type": "invalid_request_error", "code": "invalid_api_key"}}"""
            assertTrue(ProviderKeyValidator.isJsonBodyAuthError(json))
        }

        @Test
        @DisplayName("detects Google UNAUTHENTICATED status")
        fun `detects Google UNAUTHENTICATED status`() {
            val json = """{"error": {"code": 401, "message": "API key not valid", "status": "UNAUTHENTICATED"}}"""
            assertTrue(ProviderKeyValidator.isJsonBodyAuthError(json))
        }

        @Test
        @DisplayName("detects generic unauthorized in error object")
        fun `detects generic unauthorized in error object`() {
            val json = """{"error": {"message": "unauthorized access"}}"""
            assertTrue(ProviderKeyValidator.isJsonBodyAuthError(json))
        }

        @Test
        @DisplayName("returns false for non-auth error in body")
        fun `returns false for non-auth error in body`() {
            val json = """{"error": {"message": "rate limit exceeded", "code": "rate_limit"}}"""
            assertFalse(ProviderKeyValidator.isJsonBodyAuthError(json))
        }

        @Test
        @DisplayName("returns false for malformed JSON")
        fun `returns false for malformed JSON`() {
            assertFalse(ProviderKeyValidator.isJsonBodyAuthError("not json {"))
        }
    }
}
