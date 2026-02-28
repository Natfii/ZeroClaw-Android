/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.oauth

import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Result of a successful OAuth redirect callback.
 *
 * Extracted from the query parameters of the authorization server's
 * redirect to the loopback callback URI.
 *
 * @property code Authorization code to exchange for tokens via
 *   [OpenAiOAuthManager.exchangeCodeForTokens].
 * @property state CSRF state nonce that must match the value from the
 *   original [PkceState].
 */
data class OAuthCallbackResult(
    val code: String,
    val state: String,
)

/**
 * Lightweight HTTP server that listens on localhost for the OAuth redirect
 * callback from the authorization server.
 *
 * When the user completes authentication in the browser, the authorization
 * server redirects to `http://localhost:{port}/auth/callback?code=XXX&state=YYY`.
 * This server catches that request, extracts the authorization code and state,
 * serves a success HTML page so the user sees confirmation, and completes the
 * [awaitCallback] deferred with the result.
 *
 * @param port TCP port to bind on localhost. Defaults to [DEFAULT_PORT] (1455),
 *   matching the upstream Rust implementation.
 * @param timeoutMs Maximum time in milliseconds to wait for the callback before
 *   returning null. Defaults to [DEFAULT_TIMEOUT_MS] (120 seconds).
 */
class OAuthCallbackServer(
    private val port: Int = DEFAULT_PORT,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    /** The actual TCP port this server is bound to. */
    val boundPort: Int get() = port

    /** Deferred that completes when the callback is received or the server is stopped. */
    private val result = CompletableDeferred<OAuthCallbackResult?>()

    /** Inner NanoHTTPD server instance. */
    private val httpServer = CallbackHttpServer(port, result)

    /**
     * Starts the loopback HTTP server, binding to localhost on [port].
     *
     * The server runs in a daemon thread and does not block the calling thread.
     * Call [awaitCallback] to suspend until the callback arrives or [stop] to
     * shut down immediately.
     *
     * @throws IOException if the port is already in use or binding fails.
     */
    fun start() {
        httpServer.start()
    }

    /**
     * Suspends until the OAuth callback is received or the timeout elapses.
     *
     * Safe to call from the main thread. Returns null if the timeout expires
     * before a valid callback arrives, or if the server is stopped before
     * receiving a callback.
     *
     * @return The [OAuthCallbackResult] containing the authorization code and
     *   state, or null if the timeout elapsed.
     */
    suspend fun awaitCallback(): OAuthCallbackResult? =
        withTimeoutOrNull(timeoutMs) {
            result.await()
        }

    /**
     * Stops the HTTP server and releases the bound port.
     *
     * If no callback has been received, the deferred is completed with null
     * so that any suspended [awaitCallback] call returns immediately.
     */
    fun stop() {
        result.complete(null)
        httpServer.stop()
    }

    /**
     * Inner NanoHTTPD server that handles the OAuth callback request.
     *
     * @param port TCP port to bind on localhost.
     * @param deferred Deferred to complete with the extracted callback result.
     */
    private class CallbackHttpServer(
        port: Int,
        private val deferred: CompletableDeferred<OAuthCallbackResult?>,
    ) : NanoHTTPD(LOOPBACK_HOST, port) {
        /**
         * Handles incoming HTTP requests.
         *
         * Only `/auth/callback` with both `code` and `state` query parameters
         * produces a success response. All other paths or missing parameters
         * return HTTP 400.
         *
         * @param session The incoming HTTP session from NanoHTTPD.
         * @return An HTML response indicating success or failure.
         */
        override fun serve(session: IHTTPSession): Response {
            if (session.uri != CALLBACK_PATH) {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    NanoHTTPD.MIME_HTML,
                    BAD_REQUEST_HTML,
                )
            }

            val code = session.parms["code"]
            val state = session.parms["state"]

            if (code.isNullOrEmpty() || state.isNullOrEmpty()) {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    NanoHTTPD.MIME_HTML,
                    MISSING_PARAMS_HTML,
                )
            }

            deferred.complete(OAuthCallbackResult(code = code, state = state))

            return newFixedLengthResponse(
                Response.Status.OK,
                NanoHTTPD.MIME_HTML,
                SUCCESS_HTML,
            )
        }
    }

    /** Constants for port configuration and timeout defaults. */
    companion object {
        /** Loopback address per RFC 8252 Section 8.3; never bind on 0.0.0.0. */
        private const val LOOPBACK_HOST = "127.0.0.1"

        /** Default TCP port matching the upstream Rust redirect URI. */
        const val DEFAULT_PORT = 1455

        /** Fallback TCP port if [DEFAULT_PORT] is already in use. */
        private const val FALLBACK_PORT = 1456

        /** Default timeout in milliseconds (2 minutes). */
        private const val DEFAULT_TIMEOUT_MS = 120_000L

        /** URI path expected for the OAuth callback. */
        private const val CALLBACK_PATH = "/auth/callback"

        /** HTML response body for a successful callback. */
        private const val SUCCESS_HTML =
            "<html><body><h1>Login successful!</h1>" +
                "<p>You can close this tab.</p></body></html>"

        /** HTML response body when the request path is wrong. */
        private const val BAD_REQUEST_HTML =
            "<html><body><h1>Bad Request</h1>" +
                "<p>Unexpected path.</p></body></html>"

        /** HTML response body when required query parameters are missing. */
        private const val MISSING_PARAMS_HTML =
            "<html><body><h1>Bad Request</h1>" +
                "<p>Missing code or state parameter.</p></body></html>"

        /**
         * Creates and starts an [OAuthCallbackServer], falling back to
         * [FALLBACK_PORT] if [DEFAULT_PORT] is already occupied.
         *
         * @return A started [OAuthCallbackServer] ready to receive callbacks.
         * @throws IOException if neither port can be bound.
         */
        @Suppress("TooGenericExceptionCaught")
        fun startWithFallback(): OAuthCallbackServer {
            try {
                val server = OAuthCallbackServer(port = DEFAULT_PORT)
                server.start()
                return server
            } catch (_: IOException) {
                val server = OAuthCallbackServer(port = FALLBACK_PORT)
                server.start()
                return server
            }
        }
    }
}
