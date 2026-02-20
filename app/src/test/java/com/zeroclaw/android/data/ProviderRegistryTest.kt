/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data

import com.zeroclaw.android.model.ModelListFormat
import com.zeroclaw.android.model.ProviderAuthType
import com.zeroclaw.android.model.ProviderCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ProviderRegistry].
 */
@DisplayName("ProviderRegistry")
class ProviderRegistryTest {
    @Test
    @DisplayName("findById returns correct info for primary ID")
    fun `findById returns correct info for primary ID`() {
        val openai = ProviderRegistry.findById("openai")
        assertNotNull(openai)
        assertEquals("openai", openai!!.id)
        assertEquals("OpenAI", openai.displayName)
        assertEquals(ProviderAuthType.API_KEY_ONLY, openai.authType)
        assertTrue(openai.suggestedModels.isNotEmpty())
    }

    @Test
    @DisplayName("findById resolves aliases")
    fun `findById resolves aliases`() {
        val grok = ProviderRegistry.findById("grok")
        assertNotNull(grok)
        assertEquals("xai", grok!!.id)
        assertEquals("xAI / Grok", grok.displayName)

        val google = ProviderRegistry.findById("google")
        assertNotNull(google)
        assertEquals("google-gemini", google!!.id)

        val gemini = ProviderRegistry.findById("gemini")
        assertNotNull(gemini)
        assertEquals("google-gemini", gemini!!.id)

        val kimi = ProviderRegistry.findById("kimi")
        assertNotNull(kimi)
        assertEquals("moonshot", kimi!!.id)

        val baidu = ProviderRegistry.findById("baidu")
        assertNotNull(baidu)
        assertEquals("qianfan", baidu!!.id)
    }

    @Test
    @DisplayName("findById is case insensitive")
    fun `findById is case insensitive`() {
        val upper = ProviderRegistry.findById("OpenAI")
        assertNotNull(upper)
        assertEquals("openai", upper!!.id)

        val mixed = ProviderRegistry.findById("Anthropic")
        assertNotNull(mixed)
        assertEquals("anthropic", mixed!!.id)
    }

    @Test
    @DisplayName("findById returns null for unknown provider")
    fun `findById returns null for unknown provider`() {
        assertNull(ProviderRegistry.findById("nonexistent"))
        assertNull(ProviderRegistry.findById(""))
    }

    @Test
    @DisplayName("allByCategory returns non-empty groups")
    fun `allByCategory returns non-empty groups`() {
        val grouped = ProviderRegistry.allByCategory()
        assertTrue(grouped.containsKey(ProviderCategory.PRIMARY))
        assertTrue(grouped.containsKey(ProviderCategory.ECOSYSTEM))
        assertTrue(grouped.containsKey(ProviderCategory.CUSTOM))
        assertTrue(grouped[ProviderCategory.PRIMARY]!!.isNotEmpty())
        assertTrue(grouped[ProviderCategory.ECOSYSTEM]!!.isNotEmpty())
        assertTrue(grouped[ProviderCategory.CUSTOM]!!.isNotEmpty())
    }

    @Test
    @DisplayName("no duplicate IDs across all providers")
    fun `no duplicate IDs across all providers`() {
        val ids = ProviderRegistry.allProviders.map { it.id }
        val dupes = ids.groupBy { it }.filter { it.value.size > 1 }.keys
        assertEquals(ids.size, ids.toSet().size, "Duplicate IDs: $dupes")
    }

    @Test
    @DisplayName("no alias collisions with primary IDs")
    fun `no alias collisions with primary IDs`() {
        val primaryIds = ProviderRegistry.allProviders.map { it.id }.toSet()
        val allAliases = ProviderRegistry.allProviders.flatMap { it.aliases }
        val collisions = allAliases.filter { it in primaryIds }
        assertTrue(collisions.isEmpty(), "Aliases collide with primary IDs: $collisions")
    }

    @Test
    @DisplayName("custom providers have URL_AND_OPTIONAL_KEY auth type")
    fun `custom providers have URL_AND_OPTIONAL_KEY auth type`() {
        val customProviders = ProviderRegistry.allByCategory()[ProviderCategory.CUSTOM]!!
        customProviders.forEach { provider ->
            assertEquals(
                ProviderAuthType.URL_AND_OPTIONAL_KEY,
                provider.authType,
                "${provider.id} should have URL_AND_OPTIONAL_KEY auth type",
            )
        }
    }

    @Test
    @DisplayName("ollama has URL_ONLY auth type and default base URL")
    fun `ollama has URL_ONLY auth type and default base URL`() {
        val ollama = ProviderRegistry.findById("ollama")
        assertNotNull(ollama)
        assertEquals(ProviderAuthType.URL_ONLY, ollama!!.authType)
        assertTrue(ollama.defaultBaseUrl.isNotBlank())
    }

    @Test
    @DisplayName("synthetic provider has NONE auth type")
    fun `synthetic provider has NONE auth type`() {
        val synthetic = ProviderRegistry.findById("synthetic")
        assertNotNull(synthetic)
        assertEquals(ProviderAuthType.NONE, synthetic!!.authType)
    }

    @Test
    @DisplayName("major cloud providers have iconUrl populated")
    fun `major cloud providers have iconUrl populated`() {
        val expectedWithIcons =
            listOf(
                "openai",
                "anthropic",
                "openrouter",
                "google-gemini",
                "ollama",
                "groq",
                "mistral",
                "deepseek",
            )
        expectedWithIcons.forEach { id ->
            val provider = ProviderRegistry.findById(id)
            assertNotNull(provider, "$id should exist")
            assertTrue(provider!!.iconUrl.isNotBlank(), "$id should have a non-blank iconUrl")
        }
    }

    @Test
    @DisplayName("major cloud providers have modelListUrl populated")
    fun `major cloud providers have modelListUrl populated`() {
        val expectedWithModelList =
            mapOf(
                "openai" to ModelListFormat.OPENAI_COMPATIBLE,
                "anthropic" to ModelListFormat.ANTHROPIC,
                "openrouter" to ModelListFormat.OPENROUTER,
                "google-gemini" to ModelListFormat.GOOGLE_GEMINI,
                "ollama" to ModelListFormat.OLLAMA,
                "groq" to ModelListFormat.OPENAI_COMPATIBLE,
                "mistral" to ModelListFormat.OPENAI_COMPATIBLE,
                "deepseek" to ModelListFormat.OPENAI_COMPATIBLE,
            )
        expectedWithModelList.forEach { (id, expectedFormat) ->
            val provider = ProviderRegistry.findById(id)
            assertNotNull(provider, "$id should exist")
            assertTrue(provider!!.modelListUrl.isNotBlank(), "$id should have a modelListUrl")
            assertEquals(
                expectedFormat,
                provider.modelListFormat,
                "$id should have format $expectedFormat",
            )
        }
    }

    @Test
    @DisplayName("custom providers have no iconUrl")
    fun `custom providers have no iconUrl`() {
        val customProviders = ProviderRegistry.allByCategory()[ProviderCategory.CUSTOM]!!
        customProviders.forEach { provider ->
            assertTrue(
                provider.iconUrl.isEmpty(),
                "${provider.id} custom provider should have empty iconUrl",
            )
        }
    }

    @Test
    @DisplayName("local providers use OPENAI_COMPATIBLE model list format")
    fun `local providers use OPENAI_COMPATIBLE model list format`() {
        listOf("lmstudio", "vllm", "localai").forEach { id ->
            val provider = ProviderRegistry.findById(id)
            assertNotNull(provider, "$id should exist")
            assertEquals(
                ModelListFormat.OPENAI_COMPATIBLE,
                provider!!.modelListFormat,
                "$id should use OPENAI_COMPATIBLE format",
            )
        }
    }

    @Test
    @DisplayName("priority providers have keyCreationUrl populated")
    fun `priority providers have keyCreationUrl populated`() {
        val expectedWithUrl =
            listOf(
                "openai", "anthropic", "openrouter", "google-gemini",
                "groq", "xai", "deepseek", "together",
            )
        expectedWithUrl.forEach { id ->
            val provider = ProviderRegistry.findById(id)
            assertNotNull(provider, "$id should exist")
            assertTrue(
                provider!!.keyCreationUrl.isNotBlank(),
                "$id should have a non-blank keyCreationUrl",
            )
            assertTrue(
                provider.keyCreationUrl.startsWith("https://"),
                "$id keyCreationUrl should start with https://",
            )
        }
    }

    @Test
    @DisplayName("providers with keyPrefix also have keyPrefixHint")
    fun `providers with keyPrefix also have keyPrefixHint`() {
        ProviderRegistry.allProviders
            .filter { it.keyPrefix.isNotEmpty() }
            .forEach { provider ->
                assertTrue(
                    provider.keyPrefixHint.isNotBlank(),
                    "${provider.id} has keyPrefix but missing keyPrefixHint",
                )
            }
    }

    @Test
    @DisplayName("all 13 priority providers have helpText populated")
    fun `all 13 priority providers have helpText populated`() {
        val priorityIds =
            listOf(
                "openai", "anthropic", "openrouter", "google-gemini",
                "ollama", "lmstudio", "vllm", "localai",
                "groq", "mistral", "xai", "deepseek", "together",
            )
        priorityIds.forEach { id ->
            val provider = ProviderRegistry.findById(id)
            assertNotNull(provider, "$id should exist")
            assertTrue(
                provider!!.helpText.isNotBlank(),
                "$id should have non-blank helpText",
            )
        }
    }

    @Test
    @DisplayName("local providers have no keyPrefix")
    fun `local providers have no keyPrefix`() {
        listOf("ollama", "lmstudio", "vllm", "localai").forEach { id ->
            val provider = ProviderRegistry.findById(id)
            assertNotNull(provider, "$id should exist")
            assertTrue(
                provider!!.keyPrefix.isEmpty(),
                "$id local provider should have empty keyPrefix",
            )
        }
    }
}
