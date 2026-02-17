/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Result of a successful OAuth token refresh.
 *
 * @property accessToken New access token.
 * @property refreshToken New single-use refresh token.
 * @property expiresAt Epoch milliseconds when [accessToken] expires.
 */
data class RefreshResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
)

/**
 * Exception thrown when an OAuth token refresh fails.
 *
 * @property httpStatusCode HTTP status code from the refresh endpoint, or 0 for
 *   non-HTTP errors (e.g. network failure, JSON parse error).
 */
class OAuthRefreshException(
    message: String,
    val httpStatusCode: Int = 0,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Refreshes Anthropic OAuth access tokens using the refresh endpoint.
 *
 * Anthropic OAuth refresh tokens are single-use: each successful refresh
 * issues a new (access token, refresh token) pair. Callers must persist
 * both returned tokens immediately.
 */
object OAuthTokenRefresher {
    private const val REFRESH_URL = "https://claude.ai/api/oauth/token"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MILLIS_PER_SECOND = 1000L

    /**
     * Exchanges a refresh token for a new access token pair.
     *
     * Safe to call from the main thread; switches to [Dispatchers.IO]
     * internally.
     *
     * @param refreshToken The current single-use refresh token.
     * @return A [RefreshResult] containing the new tokens and expiry.
     * @throws OAuthRefreshException if the refresh request fails.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun refresh(refreshToken: String): RefreshResult = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("grant_type", "refresh_token")
            put("refresh_token", refreshToken)
        }.toString()

        val url = URL(REFRESH_URL)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.doOutput = true

            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val statusCode = conn.responseCode
            if (statusCode !in HTTP_OK_RANGE) {
                val errorBody = try {
                    conn.errorStream?.bufferedReader()?.readText().orEmpty()
                } catch (_: IOException) {
                    ""
                }
                throw OAuthRefreshException(
                    "Refresh failed: HTTP $statusCode - $errorBody",
                    httpStatusCode = statusCode,
                )
            }

            val responseBody = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(responseBody)

            val expiresInSeconds = json.optLong("expires_in", 0L)
            RefreshResult(
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                expiresAt = System.currentTimeMillis() + expiresInSeconds * MILLIS_PER_SECOND,
            )
        } catch (e: OAuthRefreshException) {
            throw e
        } catch (e: Exception) {
            throw OAuthRefreshException("Token refresh failed", cause = e)
        } finally {
            conn.disconnect()
        }
    }

    private val HTTP_OK_RANGE = 200..299
}
