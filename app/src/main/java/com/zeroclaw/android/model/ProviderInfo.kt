/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * Authentication mechanism required by an AI provider.
 */
enum class ProviderAuthType {
    /** Provider requires only an API key. */
    API_KEY_ONLY,

    /** Provider requires only a base URL (e.g. local Ollama). */
    URL_ONLY,

    /** Provider accepts a base URL with an optional API key. */
    URL_AND_OPTIONAL_KEY,

    /** Provider requires no credentials (e.g. synthetic/test). */
    NONE,
}

/**
 * Grouping category for provider display in sectioned dropdowns.
 */
enum class ProviderCategory {
    /** Major providers with broad model selection. */
    PRIMARY,

    /** Specialized or regional providers. */
    ECOSYSTEM,

    /** User-defined OpenAI/Anthropic-compatible endpoints. */
    CUSTOM,
}

/**
 * Format of the provider's model listing API response.
 */
enum class ModelListFormat {
    /** `{"data": [{"id": "..."}]}` with Bearer auth (OpenAI, Groq, Mistral, DeepSeek). */
    OPENAI_COMPATIBLE,

    /** `{"data": [{"id": "..."}]}` with `X-Api-Key` header (Anthropic). */
    ANTHROPIC,

    /** `{"models": [{"name": "models/..."}]}` with `?key=` query param (Google Gemini). */
    GOOGLE_GEMINI,

    /** `{"models": [{"name": "..."}]}` with no auth (Ollama). */
    OLLAMA,

    /** `{"data": [{"id": "..."}]}` with optional Bearer auth (OpenRouter). */
    OPENROUTER,

    /** Bare array `[{"id": "..."}]` with Bearer auth (Together AI). */
    TOGETHER,

    /** `{"models": [{"name": "..."}]}` with Bearer auth (Cohere). */
    COHERE,

    /** No model listing endpoint available. */
    NONE,
}

/**
 * Metadata describing a single AI provider supported by ZeroClaw.
 *
 * @property id Canonical lowercase identifier matching the upstream factory key.
 * @property displayName Human-readable name for UI display.
 * @property authType Authentication mechanism required by this provider.
 * @property defaultBaseUrl Pre-filled base URL for providers that need one, empty otherwise.
 * @property suggestedModels Popular model names offered as suggestions.
 * @property aliases Alternative IDs that resolve to this provider (e.g. "grok" for xAI).
 * @property category Grouping for sectioned dropdown display.
 * @property iconUrl URL to the provider's logo image for display in the UI.
 * @property modelListUrl URL for fetching available models from this provider's API.
 * @property modelListFormat Response format of the model listing endpoint.
 * @property keyCreationUrl URL to the provider's API key creation page, opened in the system browser.
 * @property keyPrefix Expected prefix for client-side key format validation (e.g. "sk-").
 * @property keyPrefixHint Human-readable hint shown when the key does not match [keyPrefix].
 * @property helpText Provider-specific onboarding note displayed below the provider dropdown.
 */
data class ProviderInfo(
    val id: String,
    val displayName: String,
    val authType: ProviderAuthType,
    val defaultBaseUrl: String = "",
    val suggestedModels: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val category: ProviderCategory = ProviderCategory.ECOSYSTEM,
    val iconUrl: String = "",
    val modelListUrl: String = "",
    val modelListFormat: ModelListFormat = ModelListFormat.NONE,
    val keyCreationUrl: String = "",
    val keyPrefix: String = "",
    val keyPrefixHint: String = "",
    val helpText: String = "",
)
