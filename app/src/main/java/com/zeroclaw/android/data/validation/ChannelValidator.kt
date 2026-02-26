/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.validation

import com.zeroclaw.android.model.ChannelType
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Validates channel tokens by probing the corresponding external API.
 *
 * Each supported channel type has a dedicated endpoint and authentication
 * scheme. Channels without a probing endpoint return a deferred
 * [ValidationResult.Success] indicating that validation will occur when
 * the daemon starts.
 *
 * For unit testability, the per-channel response classification logic is
 * exposed as `internal` functions that accept an HTTP status code and a
 * raw response body, avoiding the need for network access in tests.
 */
object ChannelValidator {
    /** Connection timeout in milliseconds. */
    private const val CONNECT_TIMEOUT_MS = 10000

    /** Read timeout in milliseconds. */
    private const val READ_TIMEOUT_MS = 10000

    /** HTTP 200 OK. */
    private const val HTTP_OK = 200

    /** HTTP 401 Unauthorized. */
    private const val HTTP_UNAUTHORIZED = 401

    /** HTTP 403 Forbidden. */
    private const val HTTP_FORBIDDEN = 403

    /** Message returned for channels without a live validation endpoint. */
    private const val DEFERRED_MESSAGE = "Will be verified when daemon starts"

    /** Error message returned when a token is rejected by the remote API. */
    private const val INVALID_TOKEN_MESSAGE = "Invalid token"

    /**
     * Validates a channel token by probing the channel's external API.
     *
     * Switches to [Dispatchers.IO] for the network call. Returns a terminal
     * [ValidationResult] classifying the probe outcome. The caller should set
     * [ValidationResult.Loading] before invoking this suspend function.
     *
     * @param channelType The type of channel to validate.
     * @param fields Configuration field values keyed by their TOML field name.
     * @return A terminal [ValidationResult] classifying the probe outcome.
     */
    @Suppress("TooGenericExceptionCaught", "InjectDispatcher")
    suspend fun validate(
        channelType: ChannelType,
        fields: Map<String, String>,
    ): ValidationResult =
        withContext(Dispatchers.IO) {
            try {
                when (channelType) {
                    ChannelType.TELEGRAM -> validateTelegram(fields)
                    ChannelType.DISCORD -> validateDiscord(fields)
                    ChannelType.SLACK -> validateSlack(fields)
                    ChannelType.MATRIX -> validateMatrix(fields)
                    else -> classifyOtherChannel()
                }
            } catch (e: Exception) {
                ValidationResult.Offline(
                    message = "Connection failed \u2014 ${e.message.orEmpty()}",
                )
            }
        }

    /**
     * Classifies a Telegram Bot API response.
     *
     * A successful response has `{"ok": true, "result": {"username": "..."}}`.
     * An `ok: false` response or an authentication error HTTP status yields a
     * non-retryable [ValidationResult.Failure].
     *
     * @param responseCode HTTP status code from the Telegram API.
     * @param body Raw JSON response body.
     * @return Classified [ValidationResult].
     */
    internal fun classifyTelegramResponse(
        responseCode: Int,
        body: String,
    ): ValidationResult {
        if (isAuthError(responseCode)) {
            return ValidationResult.Failure(
                message = INVALID_TOKEN_MESSAGE,
                retryable = false,
            )
        }
        if (responseCode != HTTP_OK) {
            return ValidationResult.Offline(
                message = "Telegram API returned HTTP $responseCode",
            )
        }
        return try {
            val json = JSONObject(body)
            if (!json.optBoolean("ok", false)) {
                return ValidationResult.Failure(
                    message = INVALID_TOKEN_MESSAGE,
                    retryable = false,
                )
            }
            val username = json.getJSONObject("result").optString("username", "")
            if (username.isEmpty()) {
                return ValidationResult.Offline(
                    message = "Unexpected response format",
                )
            }
            ValidationResult.Success(details = "Connected as @$username")
        } catch (
            @Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception,
        ) {
            ValidationResult.Offline(message = "Failed to parse response")
        }
    }

    /**
     * Classifies a Discord API response.
     *
     * A successful response has `{"username": "..."}`. Authentication error
     * HTTP status codes yield a non-retryable [ValidationResult.Failure].
     *
     * @param responseCode HTTP status code from the Discord API.
     * @param body Raw JSON response body.
     * @return Classified [ValidationResult].
     */
    internal fun classifyDiscordResponse(
        responseCode: Int,
        body: String,
    ): ValidationResult {
        if (isAuthError(responseCode)) {
            return ValidationResult.Failure(
                message = INVALID_TOKEN_MESSAGE,
                retryable = false,
            )
        }
        if (responseCode != HTTP_OK) {
            return ValidationResult.Offline(
                message = "Discord API returned HTTP $responseCode",
            )
        }
        return try {
            val json = JSONObject(body)
            val username = json.optString("username", "")
            if (username.isEmpty()) {
                return ValidationResult.Offline(
                    message = "Unexpected response format",
                )
            }
            ValidationResult.Success(details = "Connected as $username")
        } catch (
            @Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception,
        ) {
            ValidationResult.Offline(message = "Failed to parse response")
        }
    }

    /**
     * Classifies a Slack API response.
     *
     * A successful response has `{"ok": true, "team": "...", "user": "..."}`.
     * An `ok: false` response indicates an authentication failure.
     *
     * @param responseCode HTTP status code from the Slack API.
     * @param body Raw JSON response body.
     * @return Classified [ValidationResult].
     */
    internal fun classifySlackResponse(
        responseCode: Int,
        body: String,
    ): ValidationResult {
        if (isAuthError(responseCode)) {
            return ValidationResult.Failure(
                message = INVALID_TOKEN_MESSAGE,
                retryable = false,
            )
        }
        if (responseCode != HTTP_OK) {
            return ValidationResult.Offline(
                message = "Slack API returned HTTP $responseCode",
            )
        }
        return try {
            val json = JSONObject(body)
            if (!json.optBoolean("ok", false)) {
                return ValidationResult.Failure(
                    message = INVALID_TOKEN_MESSAGE,
                    retryable = false,
                )
            }
            val team = json.optString("team", "")
            val user = json.optString("user", "")
            if (team.isEmpty() || user.isEmpty()) {
                return ValidationResult.Offline(
                    message = "Unexpected response format",
                )
            }
            ValidationResult.Success(details = "Connected to $team as $user")
        } catch (
            @Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception,
        ) {
            ValidationResult.Offline(message = "Failed to parse response")
        }
    }

    /**
     * Classifies a Matrix API response.
     *
     * A successful response has `{"user_id": "@user:server"}`. Authentication
     * error HTTP status codes yield a non-retryable [ValidationResult.Failure].
     *
     * @param responseCode HTTP status code from the Matrix API.
     * @param body Raw JSON response body.
     * @return Classified [ValidationResult].
     */
    internal fun classifyMatrixResponse(
        responseCode: Int,
        body: String,
    ): ValidationResult {
        if (isAuthError(responseCode)) {
            return ValidationResult.Failure(
                message = INVALID_TOKEN_MESSAGE,
                retryable = false,
            )
        }
        if (responseCode != HTTP_OK) {
            return ValidationResult.Offline(
                message = "Matrix API returned HTTP $responseCode",
            )
        }
        return try {
            val json = JSONObject(body)
            val userId = json.optString("user_id", "")
            if (userId.isEmpty()) {
                return ValidationResult.Offline(
                    message = "Unexpected response format",
                )
            }
            ValidationResult.Success(details = "Connected as $userId")
        } catch (
            @Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception,
        ) {
            ValidationResult.Offline(message = "Failed to parse response")
        }
    }

    /**
     * Returns a deferred validation result for channel types without a
     * live probing endpoint.
     *
     * @return [ValidationResult.Success] with a deferred-validation message.
     */
    internal fun classifyOtherChannel(): ValidationResult.Success = ValidationResult.Success(details = DEFERRED_MESSAGE)

    /**
     * Validates a Telegram bot token by calling the `getMe` endpoint.
     *
     * @param fields Configuration fields; requires `bot_token`.
     * @return Classified [ValidationResult].
     */
    private fun validateTelegram(fields: Map<String, String>): ValidationResult {
        val token = fields["bot_token"]
        if (token.isNullOrBlank()) {
            return ValidationResult.Failure(
                message = "Missing required field: bot_token",
                retryable = true,
            )
        }
        val url = "https://api.telegram.org/bot$token/getMe"
        val (code, body) = executeGet(url)
        return classifyTelegramResponse(code, body)
    }

    /**
     * Validates a Discord bot token by calling the `users/@me` endpoint.
     *
     * @param fields Configuration fields; requires `bot_token`.
     * @return Classified [ValidationResult].
     */
    private fun validateDiscord(fields: Map<String, String>): ValidationResult {
        val token = fields["bot_token"]
        if (token.isNullOrBlank()) {
            return ValidationResult.Failure(
                message = "Missing required field: bot_token",
                retryable = true,
            )
        }
        val url = "https://discord.com/api/v10/users/@me"
        val (code, body) =
            executeGet(url, headers = mapOf("Authorization" to "Bot $token"))
        return classifyDiscordResponse(code, body)
    }

    /**
     * Validates a Slack bot token by calling the `auth.test` endpoint.
     *
     * @param fields Configuration fields; requires `bot_token`.
     * @return Classified [ValidationResult].
     */
    private fun validateSlack(fields: Map<String, String>): ValidationResult {
        val token = fields["bot_token"]
        if (token.isNullOrBlank()) {
            return ValidationResult.Failure(
                message = "Missing required field: bot_token",
                retryable = true,
            )
        }
        val url = "https://slack.com/api/auth.test"
        val (code, body) =
            executePost(url, headers = mapOf("Authorization" to "Bearer $token"))
        return classifySlackResponse(code, body)
    }

    /**
     * Validates a Matrix access token by calling the `whoami` endpoint.
     *
     * @param fields Configuration fields; requires `homeserver` and `access_token`.
     * @return Classified [ValidationResult].
     */
    private fun validateMatrix(fields: Map<String, String>): ValidationResult {
        val homeserver = fields["homeserver"]
        if (homeserver.isNullOrBlank()) {
            return ValidationResult.Failure(
                message = "Missing required field: homeserver",
                retryable = true,
            )
        }
        val accessToken = fields["access_token"]
        if (accessToken.isNullOrBlank()) {
            return ValidationResult.Failure(
                message = "Missing required field: access_token",
                retryable = true,
            )
        }
        val url =
            "${homeserver.trimEnd('/')}/_matrix/client/v3/account/whoami"
        val (code, body) =
            executeGet(
                url,
                headers = mapOf("Authorization" to "Bearer $accessToken"),
            )
        return classifyMatrixResponse(code, body)
    }

    /**
     * Executes an HTTP GET request and returns the response code and body.
     *
     * @param url Endpoint URL.
     * @param headers Optional request headers.
     * @return Pair of HTTP status code and response body string.
     */
    private fun executeGet(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            return readResponse(connection)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Executes an HTTP POST request and returns the response code and body.
     *
     * Used for Slack's `auth.test` endpoint which requires POST.
     *
     * @param url Endpoint URL.
     * @param headers Optional request headers.
     * @return Pair of HTTP status code and response body string.
     */
    private fun executePost(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Length", "0")
            connection.doOutput = true
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            connection.outputStream.close()
            return readResponse(connection)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Reads the response code and body from an HTTP connection.
     *
     * Falls back to the error stream if the input stream is unavailable.
     *
     * @param connection The open HTTP connection.
     * @return Pair of HTTP status code and response body string.
     */
    private fun readResponse(
        connection: HttpURLConnection,
    ): Pair<Int, String> {
        val code = connection.responseCode
        val stream =
            if (code in HTTP_OK until HTTP_REDIRECT_BOUNDARY) {
                connection.inputStream
            } else {
                connection.errorStream
            }
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return code to body
    }

    /**
     * Checks whether an HTTP status code indicates an authentication failure.
     *
     * @param responseCode HTTP status code to check.
     * @return True if the code is 401 or 403.
     */
    private fun isAuthError(responseCode: Int): Boolean = responseCode == HTTP_UNAUTHORIZED || responseCode == HTTP_FORBIDDEN

    /** Upper bound (exclusive) for successful HTTP status codes. */
    private const val HTTP_REDIRECT_BOUNDARY = 300
}
