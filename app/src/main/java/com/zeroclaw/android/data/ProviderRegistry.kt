/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data

import com.zeroclaw.android.model.ProviderAuthType
import com.zeroclaw.android.model.ProviderCategory
import com.zeroclaw.android.model.ProviderInfo

/**
 * Kotlin-side registry of AI providers supported by ZeroClaw.
 *
 * Source of truth: `zeroclaw/src/providers/mod.rs` (factory function, lines 183-303).
 * This registry mirrors the upstream provider list so the UI can present structured
 * dropdowns instead of free-text fields. When the upstream submodule is updated
 * (via `upstream-sync.yml`), review this registry for new providers.
 */
object ProviderRegistry {
    /** All known providers ordered by category then display name. */
    val allProviders: List<ProviderInfo> = buildList {
        addAll(primaryProviders())
        addAll(ecosystemProviders())
        addAll(customProviders())
    }

    private val byId: Map<String, ProviderInfo> by lazy {
        buildMap {
            allProviders.forEach { provider ->
                put(provider.id, provider)
                provider.aliases.forEach { alias -> put(alias, provider) }
            }
        }
    }

    /**
     * Looks up a provider by its canonical ID or any of its aliases.
     *
     * @param id Provider identifier to search for (case-insensitive).
     * @return The matching [ProviderInfo] or null if unknown.
     */
    fun findById(id: String): ProviderInfo? = byId[id.lowercase()]

    /**
     * Returns all providers grouped by [ProviderCategory].
     *
     * @return Map from category to providers in that category.
     */
    fun allByCategory(): Map<ProviderCategory, List<ProviderInfo>> =
        allProviders.groupBy { it.category }

    private fun primaryProviders(): List<ProviderInfo> =
        listOf(
            ProviderInfo(
                id = "openai",
                displayName = "OpenAI",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels = listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "o1", "o1-mini"),
                category = ProviderCategory.PRIMARY,
            ),
            ProviderInfo(
                id = "anthropic",
                displayName = "Anthropic",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels =
                    listOf(
                        "claude-sonnet-4-5-20250929",
                        "claude-haiku-4-5-20251001",
                        "claude-opus-4-6",
                    ),
                category = ProviderCategory.PRIMARY,
            ),
            ProviderInfo(
                id = "openrouter",
                displayName = "OpenRouter",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels =
                    listOf(
                        "openai/gpt-4o",
                        "anthropic/claude-sonnet-4-5-20250929",
                        "google/gemini-pro-1.5",
                    ),
                category = ProviderCategory.PRIMARY,
            ),
            ProviderInfo(
                id = "google-gemini",
                displayName = "Google Gemini",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels = listOf("gemini-1.5-pro", "gemini-1.5-flash", "gemini-2.0-flash"),
                aliases = listOf("google", "gemini"),
                category = ProviderCategory.PRIMARY,
            ),
            ProviderInfo(
                id = "ollama",
                displayName = "Ollama",
                authType = ProviderAuthType.URL_ONLY,
                defaultBaseUrl = "http://localhost:11434",
                suggestedModels = listOf("llama3", "mistral", "codellama", "phi3"),
                category = ProviderCategory.PRIMARY,
            ),
        )

    @Suppress("LongMethod")
    private fun ecosystemProviders(): List<ProviderInfo> =
        listOf(
            ProviderInfo(
                id = "groq",
                displayName = "Groq",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels = listOf("llama-3.1-70b-versatile", "mixtral-8x7b-32768"),
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "mistral",
                displayName = "Mistral",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels = listOf("mistral-large-latest", "mistral-small-latest", "codestral-latest"),
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "xai",
                displayName = "xAI / Grok",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels = listOf("grok-2", "grok-2-mini"),
                aliases = listOf("grok"),
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "deepseek",
                displayName = "DeepSeek",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels = listOf("deepseek-chat", "deepseek-coder"),
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "together",
                displayName = "Together AI",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels = listOf("meta-llama/Llama-3-70b-chat-hf"),
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "fireworks",
                displayName = "Fireworks AI",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels = listOf("accounts/fireworks/models/llama-v3p1-70b-instruct"),
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "perplexity",
                displayName = "Perplexity",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels = listOf("llama-3.1-sonar-large-128k-online"),
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "cohere",
                displayName = "Cohere",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels = listOf("command-r-plus", "command-r"),
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "github-copilot",
                displayName = "GitHub Copilot",
                authType = ProviderAuthType.API_KEY_ONLY,
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "venice",
                displayName = "Venice",
                authType = ProviderAuthType.API_KEY_ONLY,
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "vercel",
                displayName = "Vercel AI",
                authType = ProviderAuthType.API_KEY_ONLY,
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "moonshot",
                displayName = "Moonshot / Kimi",
                authType = ProviderAuthType.API_KEY_ONLY,
                aliases = listOf("kimi"),
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "minimax",
                displayName = "MiniMax",
                authType = ProviderAuthType.API_KEY_ONLY,
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "glm",
                displayName = "GLM / Zhipu",
                authType = ProviderAuthType.API_KEY_ONLY,
                aliases = listOf("zhipu"),
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "qianfan",
                displayName = "Qianfan / Baidu",
                authType = ProviderAuthType.API_KEY_ONLY,
                aliases = listOf("baidu"),
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "cloudflare",
                displayName = "Cloudflare AI",
                authType = ProviderAuthType.URL_AND_OPTIONAL_KEY,
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "bedrock",
                displayName = "Amazon Bedrock",
                authType = ProviderAuthType.URL_AND_OPTIONAL_KEY,
                aliases = listOf("amazon-bedrock"),
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "synthetic",
                displayName = "Synthetic",
                authType = ProviderAuthType.NONE,
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "opencode-zen",
                displayName = "OpenCode Zen",
                authType = ProviderAuthType.API_KEY_ONLY,
                category = ProviderCategory.ECOSYSTEM,
            ),
            ProviderInfo(
                id = "zai",
                displayName = "Z.AI",
                authType = ProviderAuthType.API_KEY_ONLY,
                category = ProviderCategory.ECOSYSTEM,
            ),
        )

    private fun customProviders(): List<ProviderInfo> =
        listOf(
            ProviderInfo(
                id = "custom-openai",
                displayName = "Custom OpenAI-compatible",
                authType = ProviderAuthType.URL_AND_OPTIONAL_KEY,
                aliases = listOf("custom"),
                category = ProviderCategory.CUSTOM,
            ),
            ProviderInfo(
                id = "custom-anthropic",
                displayName = "Custom Anthropic-compatible",
                authType = ProviderAuthType.URL_AND_OPTIONAL_KEY,
                category = ProviderCategory.CUSTOM,
            ),
        )
}
