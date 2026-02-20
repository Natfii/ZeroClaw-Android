/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data

import com.zeroclaw.android.model.ProviderInfo
import org.json.JSONObject

/**
 * Validates provider API key formats and parses JSON-body authentication errors.
 *
 * Used by both the onboarding wizard and the settings API key screen to provide
 * client-side feedback before attempting a network probe.
 */
object ProviderKeyValidator {
    /** Substrings in a JSON error body that indicate an authentication failure. */
    private val AUTH_ERROR_INDICATORS =
        listOf(
            "authentication_error",
            "invalid_api_key",
            "unauthenticated",
            "unauthorized",
            "invalid_key",
            "invalid x-api-key",
        )

    /**
     * Validates that [key] matches the expected format for [providerInfo].
     *
     * Returns a human-readable warning hint if the key does not start with
     * the provider's expected prefix. Returns null if the key is valid,
     * blank, or if the provider has no expected prefix.
     *
     * @param providerInfo Provider metadata containing the expected [ProviderInfo.keyPrefix].
     * @param key The API key value entered by the user.
     * @return Warning hint string, or null if no issue detected.
     */
    fun validateKeyFormat(
        providerInfo: ProviderInfo,
        key: String,
    ): String? {
        if (providerInfo.keyPrefix.isEmpty()) return null
        if (key.isBlank()) return null
        if (key.startsWith(providerInfo.keyPrefix)) return null
        return providerInfo.keyPrefixHint
    }

    /**
     * Checks whether an HTTP 200 response body contains a JSON authentication error.
     *
     * Some providers return HTTP 200 with an error object in the body instead
     * of using proper HTTP status codes. This method detects known patterns
     * from Anthropic, OpenAI, Google, and generic error formats.
     *
     * @param responseBody The raw JSON response body, or null.
     * @return True if the body contains a recognizable authentication error.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun isJsonBodyAuthError(responseBody: String?): Boolean {
        if (responseBody.isNullOrBlank()) return false
        return try {
            val root = JSONObject(responseBody)
            val errorText = extractErrorText(root)
            if (errorText.isBlank()) return false
            val lower = errorText.lowercase()
            AUTH_ERROR_INDICATORS.any { it in lower }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extracts concatenated error text from known JSON error envelope formats.
     *
     * Checks for:
     * - `{"error": {"type": "...", "message": "...", "code": "...", "status": "..."}}`
     * - `{"type": "error", "error": {"type": "..."}}`
     *
     * @param root The parsed JSON root object.
     * @return Concatenated error field values, or empty string if no error found.
     */
    private fun extractErrorText(root: JSONObject): String {
        val errorObj = root.optJSONObject("error")
        if (errorObj != null) {
            return buildString {
                append(errorObj.optString("type", ""))
                append(" ")
                append(errorObj.optString("message", ""))
                append(" ")
                append(errorObj.optString("code", ""))
                append(" ")
                append(errorObj.optString("status", ""))
            }
        }
        if (root.optString("type") == "error") {
            val nested = root.optJSONObject("error")
            if (nested != null) {
                return nested.optString("type", "")
            }
        }
        return ""
    }
}
