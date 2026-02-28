/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.oauth

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Result of a successful OAuth authorization code exchange.
 *
 * @property accessToken Bearer token for OpenAI API requests.
 * @property refreshToken Single-use refresh token for obtaining new access tokens.
 * @property expiresAt Epoch milliseconds when [accessToken] expires.
 */
data class OAuthTokenResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
)

/**
 * Exception thrown when an OAuth authorization code exchange fails.
 *
 * @param message Human-readable error description.
 * @property httpStatusCode HTTP status code from the token endpoint, or 0 for
 *   non-HTTP errors (e.g. network failure, JSON parse error).
 * @param cause Optional underlying cause.
 */
class OAuthExchangeException(
    message: String,
    val httpStatusCode: Int = 0,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Orchestrates the OpenAI OAuth 2.0 authorization code flow with PKCE.
 *
 * This object mirrors the upstream Rust implementation in
 * `zeroclaw/src/auth/openai_oauth.rs`, replicating the same client ID,
 * endpoints, and PKCE parameters. The flow is implemented in pure Kotlin
 * to avoid FFI complexity for authentication.
 *
 * Typical usage:
 * 1. Call [generatePkceState] to create a fresh PKCE state.
 * 2. Call [buildAuthorizeUrl] to get the browser URL.
 * 3. Launch the URL in a Custom Tab or browser.
 * 4. Receive the authorization code via the loopback callback server.
 * 5. Call [exchangeCodeForTokens] to trade the code for tokens.
 */
object OpenAiOAuthManager {
    /** OpenAI OAuth application client identifier. */
    private const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"

    /** OpenAI OAuth authorization endpoint. */
    private const val AUTHORIZE_URL = "https://auth.openai.com/oauth/authorize"

    /** OpenAI OAuth token exchange endpoint. */
    private const val TOKEN_URL = "https://auth.openai.com/oauth/token"

    /** Loopback redirect URI base matching the upstream Rust implementation. */
    private const val REDIRECT_URI_BASE = "http://localhost"

    /** Callback path within the loopback redirect URI. */
    private const val REDIRECT_PATH = "/auth/callback"

    /** OAuth scopes requested during authorization. */
    private const val SCOPES = "openid profile email offline_access"

    /** Number of random bytes for the PKCE code verifier. */
    private const val CODE_VERIFIER_BYTE_LENGTH = 64

    /** Number of random bytes for the CSRF state nonce. */
    private const val STATE_NONCE_BYTE_LENGTH = 24

    /** HTTP connection timeout in milliseconds. */
    private const val CONNECT_TIMEOUT_MS = 10_000

    /** HTTP read timeout in milliseconds. */
    private const val READ_TIMEOUT_MS = 15_000

    /** Conversion factor from seconds to milliseconds. */
    private const val MILLIS_PER_SECOND = 1000L

    /** Lower bound of successful HTTP status codes (inclusive). */
    private const val HTTP_OK_START = 200

    /** Upper bound of successful HTTP status codes (inclusive). */
    private const val HTTP_OK_END = 299

    /**
     * Generates a fresh PKCE state with cryptographically random values.
     *
     * The code verifier is [CODE_VERIFIER_BYTE_LENGTH] random bytes
     * encoded as base64url without padding. The code challenge is the
     * SHA-256 digest of the verifier, also base64url-encoded without
     * padding. The state nonce is [STATE_NONCE_BYTE_LENGTH] random bytes
     * encoded as base64url.
     *
     * @return A new [PkceState] ready for use in [buildAuthorizeUrl].
     */
    fun generatePkceState(): PkceState {
        val verifierBytes = ByteArray(CODE_VERIFIER_BYTE_LENGTH)
        SecureRandom().nextBytes(verifierBytes)
        val codeVerifier = base64UrlEncode(verifierBytes)

        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray())
        val codeChallenge = base64UrlEncode(digest)

        val stateBytes = ByteArray(STATE_NONCE_BYTE_LENGTH)
        SecureRandom().nextBytes(stateBytes)
        val state = base64UrlEncode(stateBytes)

        return PkceState(
            codeVerifier = codeVerifier,
            codeChallenge = codeChallenge,
            state = state,
        )
    }

    /**
     * Builds the full OpenAI authorization URL with all required parameters.
     *
     * The returned URL includes the PKCE code challenge, client ID, redirect
     * URI, scopes, and the state nonce for CSRF protection. Parameters match
     * the upstream Rust `build_authorize_url` function exactly.
     *
     * @param pkce PKCE state from [generatePkceState].
     * @param port The actual port the callback server is listening on. This
     *   ensures the redirect URI in the authorization request matches the
     *   server port, even when using a fallback port.
     * @return Fully-formed authorization URL to open in a browser or Custom Tab.
     */
    fun buildAuthorizeUrl(
        pkce: PkceState,
        port: Int = OAuthCallbackServer.DEFAULT_PORT,
    ): String {
        val redirectUri = "$REDIRECT_URI_BASE:$port$REDIRECT_PATH"
        val params =
            linkedMapOf(
                "response_type" to "code",
                "client_id" to CLIENT_ID,
                "redirect_uri" to redirectUri,
                "scope" to SCOPES,
                "code_challenge" to pkce.codeChallenge,
                "code_challenge_method" to "S256",
                "state" to pkce.state,
            )

        val query =
            params.entries.joinToString("&") { (key, value) ->
                "${urlEncode(key)}=${urlEncode(value)}"
            }

        return "$AUTHORIZE_URL?$query"
    }

    /**
     * Exchanges an authorization code for access and refresh tokens.
     *
     * Performs an HTTP POST to the OpenAI token endpoint with the
     * authorization code and PKCE code verifier. Safe to call from the
     * main thread; switches to the provided IO dispatcher internally.
     *
     * @param code Authorization code received from the callback server.
     * @param codeVerifier The [PkceState.codeVerifier] used when building
     *   the authorization URL.
     * @param port The callback server port used in the authorization request.
     *   Must match the `redirect_uri` sent during authorization.
     * @param ioDispatcher Coroutine dispatcher for the blocking HTTP call.
     *   Defaults to [Dispatchers.IO].
     * @return An [OAuthTokenResult] containing the access token, refresh
     *   token, and expiry timestamp.
     * @throws OAuthExchangeException if the token exchange fails for any
     *   reason (network, HTTP error, malformed response).
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun exchangeCodeForTokens(
        code: String,
        codeVerifier: String,
        port: Int = OAuthCallbackServer.DEFAULT_PORT,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): OAuthTokenResult =
        withContext(ioDispatcher) {
            val redirectUri = "$REDIRECT_URI_BASE:$port$REDIRECT_PATH"
            val formBody =
                listOf(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "client_id" to CLIENT_ID,
                    "redirect_uri" to redirectUri,
                    "code_verifier" to codeVerifier,
                ).joinToString("&") { (key, value) ->
                    "${urlEncode(key)}=${urlEncode(value)}"
                }

            val url = URL(TOKEN_URL)
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.doOutput = true

                conn.outputStream.use { it.write(formBody.toByteArray(Charsets.UTF_8)) }

                val statusCode = conn.responseCode
                if (statusCode !in HTTP_OK_START..HTTP_OK_END) {
                    val errorBody =
                        try {
                            conn.errorStream
                                ?.bufferedReader()
                                ?.readText()
                                .orEmpty()
                        } catch (_: IOException) {
                            ""
                        }
                    throw OAuthExchangeException(
                        "Token exchange failed: HTTP $statusCode - $errorBody",
                        httpStatusCode = statusCode,
                    )
                }

                val responseBody = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(responseBody)

                val expiresInSeconds = json.optLong("expires_in", 0L)
                OAuthTokenResult(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.getString("refresh_token"),
                    expiresAt = System.currentTimeMillis() + expiresInSeconds * MILLIS_PER_SECOND,
                )
            } catch (e: OAuthExchangeException) {
                throw e
            } catch (e: Exception) {
                throw OAuthExchangeException("Token exchange failed", cause = e)
            } finally {
                conn.disconnect()
            }
        }

    /**
     * Encodes raw bytes as a base64url string without padding.
     *
     * Uses [java.util.Base64] (not `android.util.Base64`) for JVM unit
     * test compatibility.
     *
     * @param bytes The raw byte array to encode.
     * @return Base64url-encoded string without trailing `=` padding.
     */
    private fun base64UrlEncode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /**
     * Percent-encodes a string per RFC 3986.
     *
     * @param value The string to encode.
     * @return URL-encoded string with spaces encoded as `%20`.
     */
    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
