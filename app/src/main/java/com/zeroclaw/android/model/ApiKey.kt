/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * Validation status of an API key as determined by provider responses.
 */
enum class KeyStatus {
    /** Key has not been rejected by the provider. */
    ACTIVE,

    /** Provider returned an authentication error (401/403). */
    INVALID,

    /** Status could not be determined (e.g. deserialized from unknown value). */
    UNKNOWN,
}

/**
 * An API key for a provider service.
 *
 * @property id Unique identifier (UUID string).
 * @property provider Human-readable provider name (e.g. "OpenAI", "Anthropic").
 * @property key The secret key value.
 * @property baseUrl Provider endpoint URL for self-hosted or custom providers, empty for cloud defaults.
 * @property createdAt Epoch milliseconds when the key was added.
 * @property status Current validation status of the key.
 */
data class ApiKey(
    val id: String,
    val provider: String,
    val key: String,
    val baseUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val status: KeyStatus = KeyStatus.ACTIVE,
)
