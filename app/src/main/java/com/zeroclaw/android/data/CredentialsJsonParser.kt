/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data

import com.zeroclaw.android.model.ApiKey
import java.util.UUID
import org.json.JSONObject

/**
 * Parses Claude Code `.credentials.json` files into [ApiKey] instances.
 *
 * The expected file format is:
 * ```json
 * {
 *   "claudeAiOauth": {
 *     "accessToken": "sk-ant-oat01-...",
 *     "refreshToken": "sk-ant-ort01-...",
 *     "expiresAt": "2026-02-17T12:00:00.000Z",
 *     "scopes": ["..."],
 *     "subscriptionType": "pro"
 *   }
 * }
 * ```
 */
object CredentialsJsonParser {
    private const val KEY_OAUTH = "claudeAiOauth"
    private const val KEY_ACCESS_TOKEN = "accessToken"
    private const val KEY_REFRESH_TOKEN = "refreshToken"
    private const val KEY_EXPIRES_AT = "expiresAt"
    private const val PROVIDER_ANTHROPIC = "anthropic"

    /**
     * Parses a `.credentials.json` file and extracts the OAuth credentials.
     *
     * @param jsonContent Raw JSON string content of the credentials file.
     * @return An [ApiKey] with the access token and refresh token, or null
     *   if the file does not contain a valid `claudeAiOauth` block.
     */
    @Suppress("TooGenericExceptionCaught")
    fun parse(jsonContent: String): ApiKey? {
        return try {
            val root = JSONObject(jsonContent)
            val oauth = root.optJSONObject(KEY_OAUTH) ?: return null
            val accessToken = oauth.optString(KEY_ACCESS_TOKEN, "")
            if (accessToken.isBlank()) return null

            val refreshToken = oauth.optString(KEY_REFRESH_TOKEN, "")
            val expiresAtStr = oauth.optString(KEY_EXPIRES_AT, "")
            val expiresAt = parseIsoTimestamp(expiresAtStr)

            ApiKey(
                id = UUID.randomUUID().toString(),
                provider = PROVIDER_ANTHROPIC,
                key = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses an ISO 8601 timestamp string to epoch milliseconds.
     *
     * @param isoString ISO 8601 date-time string (e.g. "2026-02-17T12:00:00.000Z").
     * @return Epoch milliseconds, or 0 if parsing fails.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun parseIsoTimestamp(isoString: String): Long {
        if (isoString.isBlank()) return 0L
        return try {
            java.time.Instant
                .parse(isoString)
                .toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }
}
