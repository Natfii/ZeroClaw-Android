/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CredentialsJsonParser].
 */
@DisplayName("CredentialsJsonParser")
class CredentialsJsonParserTest {

    @Test
    @DisplayName("parses valid credentials.json with all fields")
    fun `parses valid credentials json with all fields`() {
        val json = """
            {
              "claudeAiOauth": {
                "accessToken": "sk-ant-oat01-test-access-token",
                "refreshToken": "sk-ant-ort01-test-refresh-token",
                "expiresAt": "2026-02-17T20:00:00.000Z",
                "scopes": ["user:inference"],
                "subscriptionType": "pro"
              }
            }
        """.trimIndent()

        val result = CredentialsJsonParser.parse(json)

        assertNotNull(result)
        assertEquals("anthropic", result!!.provider)
        assertEquals("sk-ant-oat01-test-access-token", result.key)
        assertEquals("sk-ant-ort01-test-refresh-token", result.refreshToken)
        assertTrue(result.expiresAt > 0L)
    }

    @Test
    @DisplayName("parses credentials without expiresAt")
    fun `parses credentials without expiresAt`() {
        val json = """
            {
              "claudeAiOauth": {
                "accessToken": "sk-ant-oat01-access",
                "refreshToken": "sk-ant-ort01-refresh"
              }
            }
        """.trimIndent()

        val result = CredentialsJsonParser.parse(json)

        assertNotNull(result)
        assertEquals("sk-ant-oat01-access", result!!.key)
        assertEquals("sk-ant-ort01-refresh", result.refreshToken)
        assertEquals(0L, result.expiresAt)
    }

    @Test
    @DisplayName("parses credentials without refreshToken")
    fun `parses credentials without refreshToken`() {
        val json = """
            {
              "claudeAiOauth": {
                "accessToken": "sk-ant-oat01-access"
              }
            }
        """.trimIndent()

        val result = CredentialsJsonParser.parse(json)

        assertNotNull(result)
        assertEquals("sk-ant-oat01-access", result!!.key)
        assertEquals("", result.refreshToken)
    }

    @Test
    @DisplayName("returns null for missing claudeAiOauth block")
    fun `returns null for missing claudeAiOauth block`() {
        val json = """{ "someOtherKey": "value" }"""

        assertNull(CredentialsJsonParser.parse(json))
    }

    @Test
    @DisplayName("returns null for empty accessToken")
    fun `returns null for empty accessToken`() {
        val json = """
            {
              "claudeAiOauth": {
                "accessToken": "",
                "refreshToken": "sk-ant-ort01-refresh"
              }
            }
        """.trimIndent()

        assertNull(CredentialsJsonParser.parse(json))
    }

    @Test
    @DisplayName("returns null for invalid JSON")
    fun `returns null for invalid JSON`() {
        assertNull(CredentialsJsonParser.parse("not valid json"))
    }

    @Test
    @DisplayName("returns null for empty string")
    fun `returns null for empty string`() {
        assertNull(CredentialsJsonParser.parse(""))
    }

    @Test
    @DisplayName("handles invalid expiresAt timestamp gracefully")
    fun `handles invalid expiresAt timestamp gracefully`() {
        val json = """
            {
              "claudeAiOauth": {
                "accessToken": "sk-ant-oat01-access",
                "refreshToken": "sk-ant-ort01-refresh",
                "expiresAt": "not-a-timestamp"
              }
            }
        """.trimIndent()

        val result = CredentialsJsonParser.parse(json)

        assertNotNull(result)
        assertEquals(0L, result!!.expiresAt)
    }
}
