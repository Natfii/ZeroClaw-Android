/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings.apikeys

import com.zeroclaw.android.data.repository.InMemoryApiKeyRepository
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.ui.screen.settings.TestSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for the key-deletion side-effect logic extracted from [ApiKeysViewModel].
 *
 * Tests the function that clears [com.zeroclaw.android.model.AppSettings.defaultProvider]
 * when the key for the current default provider is deleted, and falls back to the first
 * remaining key.
 */
@DisplayName("ApiKeysViewModel key-deletion logic")
class ApiKeysViewModelLogicTest {
    @Test
    @DisplayName("clears defaultProvider when its key is deleted and no keys remain")
    fun `clears defaultProvider when its key is deleted and no keys remain`() =
        runTest {
            val keyRepo = InMemoryApiKeyRepository()
            val settingsRepo = TestSettingsRepository()
            val anthropicKey = ApiKey(id = "1", provider = "anthropic", key = "sk-ant-oat01-x")
            keyRepo.save(anthropicKey)
            settingsRepo.setDefaultProvider("anthropic")
            settingsRepo.setDefaultModel("claude-sonnet-4-6")

            keyRepo.delete("1")

            clearDefaultProviderIfNeeded(
                deletedKey = anthropicKey,
                keyRepo = keyRepo,
                settingsRepo = settingsRepo,
            )

            val settings = settingsRepo.settings.first()
            assertEquals("", settings.defaultProvider)
            assertEquals("", settings.defaultModel)
        }

    @Test
    @DisplayName("switches defaultProvider to first remaining key when another exists")
    fun `switches defaultProvider to first remaining key when another exists`() =
        runTest {
            val keyRepo = InMemoryApiKeyRepository()
            val settingsRepo = TestSettingsRepository()
            val anthropicKey = ApiKey(id = "1", provider = "anthropic", key = "sk-ant-oat01-x")
            val openaiKey = ApiKey(id = "2", provider = "openai", key = "sk-openai-y")
            keyRepo.save(anthropicKey)
            keyRepo.save(openaiKey)
            settingsRepo.setDefaultProvider("anthropic")
            settingsRepo.setDefaultModel("claude-sonnet-4-6")

            keyRepo.delete("1")

            clearDefaultProviderIfNeeded(
                deletedKey = anthropicKey,
                keyRepo = keyRepo,
                settingsRepo = settingsRepo,
            )

            val settings = settingsRepo.settings.first()
            assertEquals("openai", settings.defaultProvider)
            assertEquals("", settings.defaultModel)
        }

    @Test
    @DisplayName("does not touch defaultProvider when deleted key is not the default")
    fun `does not touch defaultProvider when deleted key is not the default`() =
        runTest {
            val keyRepo = InMemoryApiKeyRepository()
            val settingsRepo = TestSettingsRepository()
            val anthropicKey = ApiKey(id = "1", provider = "anthropic", key = "sk-ant-oat01-x")
            val openaiKey = ApiKey(id = "2", provider = "openai", key = "sk-openai-y")
            keyRepo.save(anthropicKey)
            keyRepo.save(openaiKey)
            settingsRepo.setDefaultProvider("anthropic")
            settingsRepo.setDefaultModel("claude-sonnet-4-6")

            clearDefaultProviderIfNeeded(
                deletedKey = openaiKey,
                keyRepo = keyRepo,
                settingsRepo = settingsRepo,
            )

            val settings = settingsRepo.settings.first()
            assertEquals("anthropic", settings.defaultProvider)
            assertEquals("claude-sonnet-4-6", settings.defaultModel)
        }
}
