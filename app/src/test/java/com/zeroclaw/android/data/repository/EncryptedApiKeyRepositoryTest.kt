/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.data.MapSharedPreferences
import com.zeroclaw.android.data.StorageHealth
import com.zeroclaw.android.model.KeyStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [EncryptedApiKeyRepository] using an injected
 * [MapSharedPreferences] to avoid Android Keystore dependencies.
 */
@DisplayName("EncryptedApiKeyRepository")
class EncryptedApiKeyRepositoryTest {
    @Test
    @DisplayName("loads valid keys from preferences")
    fun `loads valid keys from preferences`() = runTest {
        val prefs = MapSharedPreferences()
        prefs.edit().putString(
            "key1",
            JSONObject().apply {
                put("id", "key1")
                put("provider", "OpenAI")
                put("key", "sk-test")
                put("created_at", 1000L)
            }.toString(),
        ).apply()

        val repo = EncryptedApiKeyRepository(prefsOverride = prefs)
        val keys = repo.keys.first()
        assertEquals(1, keys.size)
        assertEquals("OpenAI", keys[0].provider)
    }

    @Test
    @DisplayName("counts corrupt entries and skips them")
    fun `counts corrupt entries and skips them`() = runTest {
        val prefs = MapSharedPreferences()
        prefs.edit().putString(
            "good",
            JSONObject().apply {
                put("id", "good")
                put("provider", "OpenAI")
                put("key", "sk-test")
                put("created_at", 1000L)
            }.toString(),
        ).apply()
        prefs.edit().putString("bad", "not valid json at all").apply()

        val repo = EncryptedApiKeyRepository(prefsOverride = prefs)
        val keys = repo.keys.first()
        assertEquals(1, keys.size)
        assertEquals(1, repo.corruptKeyCount.value)
    }

    @Test
    @DisplayName("corrupt count resets on reload after save")
    fun `corrupt count resets on reload after save`() = runTest {
        val prefs = MapSharedPreferences()
        prefs.edit().putString("bad", "broken json").apply()

        val repo = EncryptedApiKeyRepository(prefsOverride = prefs)
        assertEquals(1, repo.corruptKeyCount.value)

        prefs.edit().remove("bad").apply()
        repo.save(
            com.zeroclaw.android.model.ApiKey(
                id = "new",
                provider = "Test",
                key = "test-key",
            ),
        )
        assertEquals(0, repo.corruptKeyCount.value)
    }

    @Test
    @DisplayName("storageHealth is Healthy with prefs override")
    fun `storageHealth is Healthy with prefs override`() {
        val prefs = MapSharedPreferences()
        val repo = EncryptedApiKeyRepository(prefsOverride = prefs)
        assertEquals(StorageHealth.Healthy, repo.storageHealth)
    }

    @Test
    @DisplayName("save persists KeyStatus")
    fun `save persists KeyStatus`() = runTest {
        val prefs = MapSharedPreferences()
        val repo = EncryptedApiKeyRepository(prefsOverride = prefs)
        repo.save(
            com.zeroclaw.android.model.ApiKey(
                id = "1",
                provider = "OpenAI",
                key = "sk-test",
                status = KeyStatus.INVALID,
            ),
        )

        val stored = JSONObject(prefs.getString("1", "{}") ?: "{}")
        assertEquals("INVALID", stored.getString("status"))
    }

    @Test
    @DisplayName("load parses KeyStatus from stored JSON")
    fun `load parses KeyStatus from stored JSON`() = runTest {
        val prefs = MapSharedPreferences()
        prefs.edit().putString(
            "key1",
            JSONObject().apply {
                put("id", "key1")
                put("provider", "OpenAI")
                put("key", "sk-test")
                put("created_at", 1000L)
                put("status", "INVALID")
            }.toString(),
        ).apply()

        val repo = EncryptedApiKeyRepository(prefsOverride = prefs)
        val keys = repo.keys.first()
        assertEquals(KeyStatus.INVALID, keys[0].status)
    }

    @Test
    @DisplayName("unknown status string maps to UNKNOWN")
    fun `unknown status string maps to UNKNOWN`() = runTest {
        val prefs = MapSharedPreferences()
        prefs.edit().putString(
            "key1",
            JSONObject().apply {
                put("id", "key1")
                put("provider", "OpenAI")
                put("key", "sk-test")
                put("created_at", 1000L)
                put("status", "REVOKED")
            }.toString(),
        ).apply()

        val repo = EncryptedApiKeyRepository(prefsOverride = prefs)
        val keys = repo.keys.first()
        assertEquals(KeyStatus.UNKNOWN, keys[0].status)
    }

    @Test
    @DisplayName("missing status field defaults to ACTIVE")
    fun `missing status field defaults to ACTIVE`() = runTest {
        val prefs = MapSharedPreferences()
        prefs.edit().putString(
            "key1",
            JSONObject().apply {
                put("id", "key1")
                put("provider", "OpenAI")
                put("key", "sk-test")
                put("created_at", 1000L)
            }.toString(),
        ).apply()

        val repo = EncryptedApiKeyRepository(prefsOverride = prefs)
        val keys = repo.keys.first()
        assertEquals(KeyStatus.ACTIVE, keys[0].status)
    }

    @Test
    @DisplayName("save persists baseUrl")
    fun `save persists baseUrl`() = runTest {
        val prefs = MapSharedPreferences()
        val repo = EncryptedApiKeyRepository(prefsOverride = prefs)
        repo.save(
            com.zeroclaw.android.model.ApiKey(
                id = "1",
                provider = "ollama",
                key = "",
                baseUrl = "http://192.168.1.100:11434",
            ),
        )

        val stored = JSONObject(prefs.getString("1", "{}") ?: "{}")
        assertEquals("http://192.168.1.100:11434", stored.getString("base_url"))
    }

    @Test
    @DisplayName("load parses baseUrl from stored JSON")
    fun `load parses baseUrl from stored JSON`() = runTest {
        val prefs = MapSharedPreferences()
        prefs.edit().putString(
            "key1",
            JSONObject().apply {
                put("id", "key1")
                put("provider", "lmstudio")
                put("key", "")
                put("base_url", "http://localhost:1234/v1")
                put("created_at", 1000L)
            }.toString(),
        ).apply()

        val repo = EncryptedApiKeyRepository(prefsOverride = prefs)
        val keys = repo.keys.first()
        assertEquals("http://localhost:1234/v1", keys[0].baseUrl)
    }

    @Test
    @DisplayName("missing baseUrl field defaults to empty string")
    fun `missing baseUrl field defaults to empty string`() = runTest {
        val prefs = MapSharedPreferences()
        prefs.edit().putString(
            "key1",
            JSONObject().apply {
                put("id", "key1")
                put("provider", "OpenAI")
                put("key", "sk-test")
                put("created_at", 1000L)
            }.toString(),
        ).apply()

        val repo = EncryptedApiKeyRepository(prefsOverride = prefs)
        val keys = repo.keys.first()
        assertEquals("", keys[0].baseUrl)
    }

    @Test
    @DisplayName("save persists refreshToken and expiresAt")
    fun `save persists refreshToken and expiresAt`() = runTest {
        val prefs = MapSharedPreferences()
        val repo = EncryptedApiKeyRepository(prefsOverride = prefs)
        repo.save(
            com.zeroclaw.android.model.ApiKey(
                id = "1",
                provider = "Anthropic",
                key = "sk-ant-oat01-token",
                refreshToken = "sk-ant-ort01-refresh",
                expiresAt = 1739836800000L,
            ),
        )

        val stored = JSONObject(prefs.getString("1", "{}") ?: "{}")
        assertEquals("sk-ant-ort01-refresh", stored.getString("refresh_token"))
        assertEquals(1739836800000L, stored.getLong("expires_at"))
    }

    @Test
    @DisplayName("load parses refreshToken and expiresAt")
    fun `load parses refreshToken and expiresAt`() = runTest {
        val prefs = MapSharedPreferences()
        prefs.edit().putString(
            "key1",
            JSONObject().apply {
                put("id", "key1")
                put("provider", "Anthropic")
                put("key", "sk-ant-oat01-token")
                put("created_at", 1000L)
                put("refresh_token", "sk-ant-ort01-refresh")
                put("expires_at", 1739836800000L)
            }.toString(),
        ).apply()

        val repo = EncryptedApiKeyRepository(prefsOverride = prefs)
        val keys = repo.keys.first()
        assertEquals("sk-ant-ort01-refresh", keys[0].refreshToken)
        assertEquals(1739836800000L, keys[0].expiresAt)
    }

    @Test
    @DisplayName("missing OAuth fields default to empty/zero for backward compat")
    fun `missing OAuth fields default to empty and zero for backward compat`() = runTest {
        val prefs = MapSharedPreferences()
        prefs.edit().putString(
            "key1",
            JSONObject().apply {
                put("id", "key1")
                put("provider", "OpenAI")
                put("key", "sk-test")
                put("created_at", 1000L)
            }.toString(),
        ).apply()

        val repo = EncryptedApiKeyRepository(prefsOverride = prefs)
        val keys = repo.keys.first()
        assertEquals("", keys[0].refreshToken)
        assertEquals(0L, keys[0].expiresAt)
    }
}
