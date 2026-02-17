/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data

import com.zeroclaw.android.model.ModelListFormat
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
    /** Google Favicon API icon size in pixels. */
    private const val FAVICON_SIZE = 128

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

    /**
     * Builds a Google Favicon API URL for the given domain.
     *
     * @param domain Domain to fetch the favicon for.
     * @return URL string pointing to the favicon at [FAVICON_SIZE] pixels.
     */
    private fun faviconUrl(domain: String): String =
        "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON" +
            "&fallback_opts=TYPE,SIZE,URL&url=https://$domain&size=$FAVICON_SIZE"

    @Suppress("LongMethod")
    private fun primaryProviders(): List<ProviderInfo> =
        listOf(
            ProviderInfo(
                id = "openai",
                displayName = "OpenAI",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels =
                    listOf(
                        "gpt-4o", "gpt-4o-mini", "o1", "o1-mini",
                        "o3-mini", "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano",
                    ),
                category = ProviderCategory.PRIMARY,
                iconUrl = "https://cdn.openai.com/API/logo-assets/openai-logomark.png",
                modelListUrl = "https://api.openai.com/v1/models",
                modelListFormat = ModelListFormat.OPENAI_COMPATIBLE,
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
                iconUrl = faviconUrl("anthropic.com"),
                modelListUrl = "https://api.anthropic.com/v1/models",
                modelListFormat = ModelListFormat.ANTHROPIC,
            ),
            ProviderInfo(
                id = "openrouter",
                displayName = "OpenRouter",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels =
                    listOf(
                        "openai/gpt-4o",
                        "anthropic/claude-sonnet-4-5",
                        "google/gemini-2.0-flash",
                    ),
                category = ProviderCategory.PRIMARY,
                iconUrl = faviconUrl("openrouter.ai"),
                modelListUrl = "https://openrouter.ai/api/v1/models",
                modelListFormat = ModelListFormat.OPENROUTER,
            ),
            ProviderInfo(
                id = "google-gemini",
                displayName = "Google Gemini",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels =
                    listOf("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-pro", "gemini-1.5-flash"),
                aliases = listOf("google", "gemini"),
                category = ProviderCategory.PRIMARY,
                iconUrl = faviconUrl("ai.google.dev"),
                modelListUrl = "https://generativelanguage.googleapis.com/v1beta/models",
                modelListFormat = ModelListFormat.GOOGLE_GEMINI,
            ),
            ProviderInfo(
                id = "ollama",
                displayName = "Ollama",
                authType = ProviderAuthType.URL_ONLY,
                defaultBaseUrl = "http://localhost:11434",
                suggestedModels = listOf("llama3.3", "qwen2.5", "mistral", "deepseek-r1", "phi4", "gemma3"),
                category = ProviderCategory.PRIMARY,
                iconUrl = faviconUrl("ollama.com"),
                modelListUrl = "http://localhost:11434/api/tags",
                modelListFormat = ModelListFormat.OLLAMA,
            ),
            ProviderInfo(
                id = "lmstudio",
                displayName = "LM Studio",
                authType = ProviderAuthType.URL_AND_OPTIONAL_KEY,
                defaultBaseUrl = "http://localhost:1234/v1",
                category = ProviderCategory.PRIMARY,
                iconUrl = faviconUrl("lmstudio.ai"),
                modelListFormat = ModelListFormat.OPENAI_COMPATIBLE,
            ),
            ProviderInfo(
                id = "vllm",
                displayName = "vLLM",
                authType = ProviderAuthType.URL_AND_OPTIONAL_KEY,
                defaultBaseUrl = "http://localhost:8000/v1",
                category = ProviderCategory.PRIMARY,
                iconUrl = faviconUrl("docs.vllm.ai"),
                modelListFormat = ModelListFormat.OPENAI_COMPATIBLE,
            ),
            ProviderInfo(
                id = "localai",
                displayName = "LocalAI",
                authType = ProviderAuthType.URL_AND_OPTIONAL_KEY,
                defaultBaseUrl = "http://localhost:8080/v1",
                category = ProviderCategory.PRIMARY,
                iconUrl = faviconUrl("localai.io"),
                modelListFormat = ModelListFormat.OPENAI_COMPATIBLE,
            ),
        )

    @Suppress("LongMethod")
    private fun ecosystemProviders(): List<ProviderInfo> =
        listOf(
            ProviderInfo(
                id = "groq",
                displayName = "Groq",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels =
                    listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768", "gemma2-9b-it"),
                category = ProviderCategory.ECOSYSTEM,
                iconUrl = faviconUrl("groq.com"),
                modelListUrl = "https://api.groq.com/openai/v1/models",
                modelListFormat = ModelListFormat.OPENAI_COMPATIBLE,
            ),
            ProviderInfo(
                id = "mistral",
                displayName = "Mistral",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels =
                    listOf("mistral-large-latest", "mistral-small-latest", "codestral-latest", "pixtral-large-latest"),
                category = ProviderCategory.ECOSYSTEM,
                iconUrl = faviconUrl("mistral.ai"),
                modelListUrl = "https://api.mistral.ai/v1/models",
                modelListFormat = ModelListFormat.OPENAI_COMPATIBLE,
            ),
            ProviderInfo(
                id = "xai",
                displayName = "xAI / Grok",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels = listOf("grok-3", "grok-3-mini", "grok-2"),
                aliases = listOf("grok"),
                category = ProviderCategory.ECOSYSTEM,
                iconUrl = faviconUrl("x.ai"),
            ),
            ProviderInfo(
                id = "deepseek",
                displayName = "DeepSeek",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels = listOf("deepseek-chat", "deepseek-reasoner"),
                category = ProviderCategory.ECOSYSTEM,
                iconUrl = faviconUrl("deepseek.com"),
                modelListUrl = "https://api.deepseek.com/models",
                modelListFormat = ModelListFormat.OPENAI_COMPATIBLE,
            ),
            ProviderInfo(
                id = "together",
                displayName = "Together AI",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels =
                    listOf(
                        "meta-llama/Llama-3.3-70B-Instruct-Turbo",
                        "Qwen/Qwen2.5-72B-Instruct-Turbo",
                    ),
                category = ProviderCategory.ECOSYSTEM,
                iconUrl = faviconUrl("together.ai"),
                modelListUrl = "https://api.together.xyz/v1/models",
                modelListFormat = ModelListFormat.TOGETHER,
            ),
            ProviderInfo(
                id = "fireworks",
                displayName = "Fireworks AI",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels = listOf("accounts/fireworks/models/llama-v3p3-70b-instruct"),
                category = ProviderCategory.ECOSYSTEM,
                iconUrl = faviconUrl("fireworks.ai"),
            ),
            ProviderInfo(
                id = "perplexity",
                displayName = "Perplexity",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels = listOf("sonar-pro", "sonar", "sonar-reasoning-pro"),
                category = ProviderCategory.ECOSYSTEM,
                iconUrl = faviconUrl("perplexity.ai"),
            ),
            ProviderInfo(
                id = "cohere",
                displayName = "Cohere",
                authType = ProviderAuthType.API_KEY_ONLY,
                suggestedModels = listOf("command-r-plus", "command-r", "command-a-03-2025"),
                category = ProviderCategory.ECOSYSTEM,
                iconUrl = faviconUrl("cohere.com"),
                modelListUrl = "https://api.cohere.com/v1/models",
                modelListFormat = ModelListFormat.COHERE,
            ),
            ProviderInfo(
                id = "github-copilot",
                displayName = "GitHub Copilot",
                authType = ProviderAuthType.API_KEY_ONLY,
                category = ProviderCategory.ECOSYSTEM,
                iconUrl = faviconUrl("github.com"),
            ),
            ProviderInfo(
                id = "venice",
                displayName = "Venice",
                authType = ProviderAuthType.API_KEY_ONLY,
                category = ProviderCategory.ECOSYSTEM,
                iconUrl = faviconUrl("venice.ai"),
            ),
            ProviderInfo(
                id = "vercel",
                displayName = "Vercel AI",
                authType = ProviderAuthType.API_KEY_ONLY,
                category = ProviderCategory.ECOSYSTEM,
                iconUrl = faviconUrl("vercel.com"),
            ),
            ProviderInfo(
                id = "moonshot",
                displayName = "Moonshot / Kimi",
                authType = ProviderAuthType.API_KEY_ONLY,
                aliases = listOf("kimi"),
                category = ProviderCategory.ECOSYSTEM,
                iconUrl = faviconUrl("moonshot.cn"),
            ),
            ProviderInfo(
                id = "minimax",
                displayName = "MiniMax",
                authType = ProviderAuthType.API_KEY_ONLY,
                category = ProviderCategory.ECOSYSTEM,
                iconUrl = faviconUrl("minimaxi.com"),
            ),
            ProviderInfo(
                id = "glm",
                displayName = "GLM / Zhipu",
                authType = ProviderAuthType.API_KEY_ONLY,
                aliases = listOf("zhipu"),
                category = ProviderCategory.ECOSYSTEM,
                iconUrl = faviconUrl("zhipuai.cn"),
            ),
            ProviderInfo(
                id = "qianfan",
                displayName = "Qianfan / Baidu",
                authType = ProviderAuthType.API_KEY_ONLY,
                aliases = listOf("baidu"),
                category = ProviderCategory.ECOSYSTEM,
                iconUrl = faviconUrl("cloud.baidu.com"),
            ),
            ProviderInfo(
                id = "cloudflare",
                displayName = "Cloudflare AI",
                authType = ProviderAuthType.URL_AND_OPTIONAL_KEY,
                category = ProviderCategory.ECOSYSTEM,
                iconUrl = faviconUrl("cloudflare.com"),
            ),
            ProviderInfo(
                id = "bedrock",
                displayName = "Amazon Bedrock",
                authType = ProviderAuthType.URL_AND_OPTIONAL_KEY,
                aliases = listOf("amazon-bedrock"),
                category = ProviderCategory.ECOSYSTEM,
                iconUrl = faviconUrl("aws.amazon.com"),
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
