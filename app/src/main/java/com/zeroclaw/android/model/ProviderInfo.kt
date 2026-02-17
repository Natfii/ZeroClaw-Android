/*
 * Copyright 2026 ZeroClaw Contributors
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
 * Metadata describing a single AI provider supported by ZeroClaw.
 *
 * @property id Canonical lowercase identifier matching the upstream factory key.
 * @property displayName Human-readable name for UI display.
 * @property authType Authentication mechanism required by this provider.
 * @property defaultBaseUrl Pre-filled base URL for providers that need one, empty otherwise.
 * @property suggestedModels Popular model names offered as suggestions.
 * @property aliases Alternative IDs that resolve to this provider (e.g. "grok" for xAI).
 * @property category Grouping for sectioned dropdown display.
 */
data class ProviderInfo(
    val id: String,
    val displayName: String,
    val authType: ProviderAuthType,
    val defaultBaseUrl: String = "",
    val suggestedModels: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val category: ProviderCategory = ProviderCategory.ECOSYSTEM,
)
