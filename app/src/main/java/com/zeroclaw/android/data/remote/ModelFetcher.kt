/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.remote

import com.zeroclaw.android.model.ModelListFormat
import com.zeroclaw.android.model.ProviderInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches available model lists from provider APIs.
 *
 * Uses [HttpURLConnection] to avoid adding extra HTTP client dependencies.
 * All network calls run on [Dispatchers.IO].
 */
object ModelFetcher {
    /** Connection timeout in milliseconds. */
    private const val CONNECT_TIMEOUT_MS = 5000

    /** Read timeout in milliseconds. */
    private const val READ_TIMEOUT_MS = 10000

    /** HTTP success status code. */
    private const val HTTP_OK = 200

    /** Anthropic API version header value. */
    private const val ANTHROPIC_VERSION = "2023-06-01"

    /**
     * Fetches the list of available model IDs from a provider's API.
     *
     * For local providers (Ollama, LM Studio, vLLM, LocalAI), the [baseUrl]
     * or [ProviderInfo.defaultBaseUrl] is used with `/models` appended. For
     * cloud providers, the [ProviderInfo.modelListUrl] is used directly.
     *
     * @param provider The provider metadata containing endpoint and format info.
     * @param apiKey API key for authentication (empty for unauthenticated providers).
     * @param baseUrl User-configured base URL override (empty to use defaults).
     * @return [Result] containing model ID strings on success, or a failure.
     */
    @Suppress("TooGenericExceptionCaught", "InjectDispatcher")
    suspend fun fetchModels(
        provider: ProviderInfo,
        apiKey: String = "",
        baseUrl: String = "",
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val url = resolveUrl(provider, baseUrl)
                ?: return@withContext Result.failure(IllegalStateException("No model list URL"))

            val json = executeRequest(url, provider.modelListFormat, apiKey)
            val models = parseModels(json, provider.modelListFormat)
            Result.success(models)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolves the final URL for fetching models.
     *
     * @param provider Provider metadata.
     * @param baseUrl User-supplied base URL override.
     * @return The resolved URL string, or null if none available.
     */
    private fun resolveUrl(provider: ProviderInfo, baseUrl: String): String? {
        if (provider.modelListFormat == ModelListFormat.NONE) return null

        if (provider.modelListUrl.isNotEmpty()) {
            return when (provider.modelListFormat) {
                ModelListFormat.OLLAMA -> {
                    if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}/api/tags"
                    else provider.modelListUrl
                }
                else -> provider.modelListUrl
            }
        }

        val effectiveBase = baseUrl.ifBlank { provider.defaultBaseUrl }
        return if (effectiveBase.isNotBlank()) {
            "${effectiveBase.trimEnd('/')}/models"
        } else {
            null
        }
    }

    /**
     * Executes an HTTP GET request and returns the response body.
     *
     * @param url Endpoint URL.
     * @param format Response format (determines authentication headers).
     * @param apiKey API key for Bearer or header-based authentication.
     * @return Raw JSON response body string.
     * @throws java.io.IOException on network or HTTP errors.
     */
    private fun executeRequest(
        url: String,
        format: ModelListFormat,
        apiKey: String,
    ): String {
        val finalUrl = if (format == ModelListFormat.GOOGLE_GEMINI && apiKey.isNotBlank()) {
            val separator = if ('?' in url) '&' else '?'
            "$url${separator}key=$apiKey"
        } else {
            url
        }

        val connection = URL(finalUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")

            addAuthHeaders(connection, format, apiKey)

            val responseCode = connection.responseCode
            if (responseCode != HTTP_OK) {
                throw java.io.IOException("HTTP $responseCode from $url")
            }

            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Adds authentication headers based on the provider's format.
     *
     * @param connection HTTP connection to add headers to.
     * @param format Provider response format (determines auth scheme).
     * @param apiKey API key value.
     */
    private fun addAuthHeaders(
        connection: HttpURLConnection,
        format: ModelListFormat,
        apiKey: String,
    ) {
        if (apiKey.isBlank()) return

        when (format) {
            ModelListFormat.ANTHROPIC -> {
                connection.setRequestProperty("x-api-key", apiKey)
                connection.setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
            }
            ModelListFormat.GOOGLE_GEMINI -> { }
            ModelListFormat.OLLAMA -> { }
            else -> connection.setRequestProperty("Authorization", "Bearer $apiKey")
        }
    }

    /**
     * Parses the JSON response into a list of model ID strings.
     *
     * @param json Raw JSON response body.
     * @param format Expected response format.
     * @return List of model ID strings extracted from the response.
     */
    internal fun parseModels(json: String, format: ModelListFormat): List<String> =
        when (format) {
            ModelListFormat.OPENAI_COMPATIBLE,
            ModelListFormat.ANTHROPIC,
            ModelListFormat.OPENROUTER,
            -> parseDataArray(json, "id")

            ModelListFormat.GOOGLE_GEMINI -> parseGeminiModels(json)
            ModelListFormat.OLLAMA -> parseOllamaModels(json)
            ModelListFormat.TOGETHER -> parseTogetherModels(json)
            ModelListFormat.COHERE -> parseCohereModels(json)
            ModelListFormat.NONE -> emptyList()
        }

    /**
     * Parses `{"data": [{"<field>": "..."}]}` format.
     *
     * @param json Raw JSON response.
     * @param field Name of the field to extract from each array element.
     * @return List of extracted string values.
     */
    private fun parseDataArray(json: String, field: String): List<String> {
        val root = JSONObject(json)
        val data = root.getJSONArray("data")
        return buildList {
            for (i in 0 until data.length()) {
                val id = data.getJSONObject(i).optString(field, "")
                if (id.isNotEmpty()) add(id)
            }
        }
    }

    /**
     * Parses Google Gemini format: `{"models": [{"name": "models/gemini-..."}]}`.
     *
     * Strips the `models/` prefix from each name.
     *
     * @param json Raw JSON response.
     * @return List of model IDs without the `models/` prefix.
     */
    private fun parseGeminiModels(json: String): List<String> {
        val root = JSONObject(json)
        val models = root.getJSONArray("models")
        return buildList {
            for (i in 0 until models.length()) {
                val name = models.getJSONObject(i).optString("name", "")
                if (name.isNotEmpty()) {
                    add(name.removePrefix("models/"))
                }
            }
        }
    }

    /**
     * Parses Ollama format: `{"models": [{"name": "..."}]}`.
     *
     * @param json Raw JSON response.
     * @return List of model names.
     */
    private fun parseOllamaModels(json: String): List<String> {
        val root = JSONObject(json)
        val models = root.getJSONArray("models")
        return buildList {
            for (i in 0 until models.length()) {
                val name = models.getJSONObject(i).optString("name", "")
                if (name.isNotEmpty()) add(name)
            }
        }
    }

    /**
     * Parses Together AI format: bare array `[{"id": "..."}]`.
     *
     * @param json Raw JSON response.
     * @return List of model IDs.
     */
    private fun parseTogetherModels(json: String): List<String> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val id = array.getJSONObject(i).optString("id", "")
                if (id.isNotEmpty()) add(id)
            }
        }
    }

    /**
     * Parses Cohere format: `{"models": [{"name": "..."}]}`.
     *
     * @param json Raw JSON response.
     * @return List of model names.
     */
    private fun parseCohereModels(json: String): List<String> {
        val root = JSONObject(json)
        val models = root.getJSONArray("models")
        return buildList {
            for (i in 0 until models.length()) {
                val name = models.getJSONObject(i).optString("name", "")
                if (name.isNotEmpty()) add(name)
            }
        }
    }
}
