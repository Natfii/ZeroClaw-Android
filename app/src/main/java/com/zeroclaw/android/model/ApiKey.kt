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
 * @property refreshToken OAuth refresh token; empty for static API keys.
 * @property expiresAt Epoch milliseconds when the access token expires; 0 means never.
 */
data class ApiKey(
    val id: String,
    val provider: String,
    val key: String,
    val baseUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val status: KeyStatus = KeyStatus.ACTIVE,
    val refreshToken: String = "",
    val expiresAt: Long = 0L,
)

/** Buffer time before actual expiry to trigger a refresh (5 minutes). */
private const val REFRESH_BUFFER_MS = 5L * 60 * 1000

/** Whether this key is an OAuth token that can be refreshed. */
val ApiKey.isOAuthToken: Boolean get() = refreshToken.isNotEmpty()

/**
 * Checks whether the access token is expired or close to expiring.
 *
 * Returns false for static API keys (expiresAt == 0).
 *
 * @param bufferMs Milliseconds before actual expiry to consider the token expired.
 * @return True if the token needs refreshing.
 */
fun ApiKey.isExpired(bufferMs: Long = REFRESH_BUFFER_MS): Boolean {
    if (expiresAt == 0L) return false
    return System.currentTimeMillis() >= expiresAt - bufferMs
}
