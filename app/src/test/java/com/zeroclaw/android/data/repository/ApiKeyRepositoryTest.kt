/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.KeyStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for the [ApiKeyRepository] contract.
 *
 * Uses [InMemoryApiKeyRepository] to verify CRUD operations and
 * export/import round-trips without requiring Android EncryptedSharedPreferences.
 */
@DisplayName("ApiKeyRepository")
class ApiKeyRepositoryTest {
    @Test
    @DisplayName("initially returns empty list")
    fun `initially returns empty list`() = runTest {
        val repo = InMemoryApiKeyRepository()
        assertEquals(emptyList<ApiKey>(), repo.keys.first())
    }

    @Test
    @DisplayName("save and retrieve by id")
    fun `save and retrieve by id`() = runTest {
        val repo = InMemoryApiKeyRepository()
        val key = ApiKey(id = "1", provider = "OpenAI", key = "sk-test123")
        repo.save(key)
        assertEquals(key, repo.getById("1"))
    }

    @Test
    @DisplayName("delete removes key")
    fun `delete removes key`() = runTest {
        val repo = InMemoryApiKeyRepository()
        repo.save(ApiKey(id = "1", provider = "OpenAI", key = "sk-test"))
        repo.delete("1")
        assertNull(repo.getById("1"))
        assertEquals(0, repo.keys.first().size)
    }

    @Test
    @DisplayName("export and import round-trip")
    fun `export and import round-trip`() = runTest {
        val repo = InMemoryApiKeyRepository()
        repo.save(ApiKey(id = "1", provider = "OpenAI", key = "sk-abc"))
        repo.save(ApiKey(id = "2", provider = "Anthropic", key = "ant-xyz"))

        val exported = repo.exportAll(TEST_PASSPHRASE)
        val newRepo = InMemoryApiKeyRepository()
        val count = newRepo.importFrom(exported, TEST_PASSPHRASE)

        assertEquals(2, count)
        assertEquals(2, newRepo.keys.first().size)
    }

    @Test
    @DisplayName("update replaces existing key")
    fun `update replaces existing key`() = runTest {
        val repo = InMemoryApiKeyRepository()
        repo.save(ApiKey(id = "1", provider = "OpenAI", key = "old-key"))
        repo.save(ApiKey(id = "1", provider = "OpenAI", key = "new-key"))
        assertEquals("new-key", repo.getById("1")?.key)
        assertEquals(1, repo.keys.first().size)
    }

    @Test
    @DisplayName("save key with default status is ACTIVE")
    fun `save key with default status is ACTIVE`() = runTest {
        val repo = InMemoryApiKeyRepository()
        repo.save(ApiKey(id = "1", provider = "OpenAI", key = "sk-test"))
        assertEquals(KeyStatus.ACTIVE, repo.getById("1")?.status)
    }

    @Test
    @DisplayName("markKeyStatus to INVALID persists correctly")
    fun `markKeyStatus to INVALID persists correctly`() = runTest {
        val repo = InMemoryApiKeyRepository()
        repo.save(ApiKey(id = "1", provider = "OpenAI", key = "sk-test"))
        repo.markKeyStatus("1", KeyStatus.INVALID)
        assertEquals(KeyStatus.INVALID, repo.getById("1")?.status)
    }

    @Test
    @DisplayName("export and import preserves KeyStatus")
    fun `export and import preserves KeyStatus`() = runTest {
        val repo = InMemoryApiKeyRepository()
        repo.save(ApiKey(id = "1", provider = "OpenAI", key = "sk-test", status = KeyStatus.INVALID))

        val exported = repo.exportAll(TEST_PASSPHRASE)
        val newRepo = InMemoryApiKeyRepository()
        newRepo.importFrom(exported, TEST_PASSPHRASE)

        val imported = newRepo.keys.first()
        assertEquals(1, imported.size)
        assertEquals(KeyStatus.INVALID, imported[0].status)
    }

    @Test
    @DisplayName("import with wrong passphrase throws")
    fun `import with wrong passphrase throws`() = runTest {
        val repo = InMemoryApiKeyRepository()
        repo.save(ApiKey(id = "1", provider = "OpenAI", key = "sk-abc"))
        val exported = repo.exportAll(TEST_PASSPHRASE)

        val newRepo = InMemoryApiKeyRepository()
        try {
            newRepo.importFrom(exported, "wrong-passphrase!")
            fail("Expected exception for wrong passphrase")
        } catch (@Suppress("SwallowedException") expected: Exception) {
            assertTrue(newRepo.keys.first().isEmpty())
        }
    }

    @Test
    @DisplayName("imported keys receive fresh UUIDs")
    fun `imported keys receive fresh UUIDs`() = runTest {
        val repo = InMemoryApiKeyRepository()
        repo.save(ApiKey(id = "original-id", provider = "OpenAI", key = "sk-abc"))

        val exported = repo.exportAll(TEST_PASSPHRASE)
        val newRepo = InMemoryApiKeyRepository()
        newRepo.importFrom(exported, TEST_PASSPHRASE)

        val imported = newRepo.keys.first()
        assertEquals(1, imported.size)
        assertNotEquals("original-id", imported[0].id)
        assertEquals("OpenAI", imported[0].provider)
        assertEquals("sk-abc", imported[0].key)
    }

    @Test
    @DisplayName("save and retrieve baseUrl")
    fun `save and retrieve baseUrl`() = runTest {
        val repo = InMemoryApiKeyRepository()
        val key = ApiKey(
            id = "1",
            provider = "ollama",
            key = "",
            baseUrl = "http://192.168.1.100:11434",
        )
        repo.save(key)
        assertEquals("http://192.168.1.100:11434", repo.getById("1")?.baseUrl)
    }

    @Test
    @DisplayName("export and import preserves baseUrl")
    fun `export and import preserves baseUrl`() = runTest {
        val repo = InMemoryApiKeyRepository()
        repo.save(
            ApiKey(
                id = "1",
                provider = "lmstudio",
                key = "lm-key",
                baseUrl = "http://localhost:1234/v1",
            ),
        )

        val exported = repo.exportAll(TEST_PASSPHRASE)
        val newRepo = InMemoryApiKeyRepository()
        newRepo.importFrom(exported, TEST_PASSPHRASE)

        val imported = newRepo.keys.first()
        assertEquals(1, imported.size)
        assertEquals("http://localhost:1234/v1", imported[0].baseUrl)
    }

    @Test
    @DisplayName("default baseUrl is empty string")
    fun `default baseUrl is empty string`() = runTest {
        val repo = InMemoryApiKeyRepository()
        repo.save(ApiKey(id = "1", provider = "OpenAI", key = "sk-test"))
        assertEquals("", repo.getById("1")?.baseUrl)
    }

    @Test
    @DisplayName("save and retrieve OAuth fields")
    fun `save and retrieve OAuth fields`() = runTest {
        val repo = InMemoryApiKeyRepository()
        val key = ApiKey(
            id = "1",
            provider = "Anthropic",
            key = "sk-ant-oat01-token",
            refreshToken = "sk-ant-ort01-refresh",
            expiresAt = 1739836800000L,
        )
        repo.save(key)
        val retrieved = repo.getById("1")
        assertEquals("sk-ant-ort01-refresh", retrieved?.refreshToken)
        assertEquals(1739836800000L, retrieved?.expiresAt)
    }

    @Test
    @DisplayName("export and import preserves OAuth fields")
    fun `export and import preserves OAuth fields`() = runTest {
        val repo = InMemoryApiKeyRepository()
        repo.save(
            ApiKey(
                id = "1",
                provider = "Anthropic",
                key = "sk-ant-oat01-token",
                refreshToken = "sk-ant-ort01-refresh",
                expiresAt = 1739836800000L,
            ),
        )

        val exported = repo.exportAll(TEST_PASSPHRASE)
        val newRepo = InMemoryApiKeyRepository()
        newRepo.importFrom(exported, TEST_PASSPHRASE)

        val imported = newRepo.keys.first()
        assertEquals(1, imported.size)
        assertEquals("sk-ant-ort01-refresh", imported[0].refreshToken)
        assertEquals(1739836800000L, imported[0].expiresAt)
    }

    @Test
    @DisplayName("default OAuth fields are empty and zero")
    fun `default OAuth fields are empty and zero`() = runTest {
        val repo = InMemoryApiKeyRepository()
        repo.save(ApiKey(id = "1", provider = "OpenAI", key = "sk-test"))
        val retrieved = repo.getById("1")
        assertEquals("", retrieved?.refreshToken)
        assertEquals(0L, retrieved?.expiresAt)
    }

    /** Constants for test fixtures. */
    companion object {
        private const val TEST_PASSPHRASE = "test-passphrase-12345"
    }
}
