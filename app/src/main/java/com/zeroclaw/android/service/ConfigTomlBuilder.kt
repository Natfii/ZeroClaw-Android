/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import com.zeroclaw.android.model.ConnectedChannel
import com.zeroclaw.android.model.FieldInputType

/**
 * Builds a valid TOML configuration string for the ZeroClaw daemon.
 *
 * The upstream [Config][zeroclaw::config::Config] struct requires at minimum
 * a `default_temperature` field. This builder constructs a TOML document from
 * the user's stored settings and API key, resolving Android provider IDs to
 * the upstream Rust factory conventions.
 *
 * Upstream provider name conventions (from `create_provider(name, api_key)`):
 * - Standard cloud: `"openai"`, `"anthropic"`, etc. (hardcoded endpoints)
 * - Ollama default: `"ollama"` (hardcoded to `http://localhost:11434`)
 * - Custom OpenAI-compatible: `"custom:http://host/v1"` (URL in name)
 * - Custom Anthropic-compatible: `"anthropic-custom:http://host"` (URL in name)
 */
@Suppress("TooManyFunctions")
object ConfigTomlBuilder {

    private const val DEFAULT_TEMPERATURE = "0.7"

    /** Default Ollama endpoint used by the upstream Rust factory. */
    private const val OLLAMA_DEFAULT_URL = "http://localhost:11434"

    /** Android provider IDs that map to `custom:URL` in the TOML. */
    private val OPENAI_COMPATIBLE_SELF_HOSTED = setOf(
        "lmstudio",
        "vllm",
        "localai",
        "custom-openai",
    )

    /**
     * Builds a TOML configuration string from the given parameters.
     *
     * Fields with blank values are omitted from the output. The
     * `default_temperature` field is always present because the
     * upstream parser requires it.
     *
     * @param provider Android provider ID (e.g. "openai", "lmstudio").
     * @param model Model name (e.g. "gpt-4o").
     * @param apiKey Secret API key value (may be blank for local providers).
     * @param baseUrl Provider endpoint URL (may be blank for cloud providers).
     * @return A valid TOML configuration string.
     */
    fun build(
        provider: String,
        model: String,
        apiKey: String,
        baseUrl: String,
    ): String = buildString {
        appendLine("default_temperature = $DEFAULT_TEMPERATURE")

        val resolvedProvider = resolveProvider(provider, baseUrl)
        if (resolvedProvider.isNotBlank()) {
            appendLine("default_provider = ${tomlString(resolvedProvider)}")
        }

        if (model.isNotBlank()) {
            appendLine("default_model = ${tomlString(model)}")
        }

        if (apiKey.isNotBlank()) {
            appendLine("api_key = ${tomlString(apiKey)}")
        }
    }

    /**
     * Builds the `[channels_config]` TOML section from enabled channels.
     *
     * The CLI channel is disabled (`cli = false`) because the Android app
     * uses the FFI bridge for direct messaging instead of stdin/stdout.
     *
     * @param channelsWithSecrets List of pairs: (channel, all config values including secrets).
     * @return TOML string for the channels_config section, or empty if no channels.
     */
    fun buildChannelsToml(
        channelsWithSecrets: List<Pair<ConnectedChannel, Map<String, String>>>,
    ): String {
        if (channelsWithSecrets.isEmpty()) return ""
        return buildString {
            appendLine()
            appendLine("[channels_config]")
            appendLine("cli = false")

            for ((channel, values) in channelsWithSecrets) {
                appendLine()
                appendLine("[channels_config.${channel.type.tomlKey}]")
                for (spec in channel.type.fields) {
                    val value = values[spec.key] ?: ""
                    if (value.isBlank() && !spec.isRequired) continue
                    appendTomlField(spec.key, value, spec.inputType)
                }
            }
        }
    }

    /**
     * Appends a single TOML field with the appropriate value format.
     *
     * @param key TOML field key.
     * @param value Raw string value from the UI.
     * @param inputType Field input type determining the TOML format.
     */
    private fun StringBuilder.appendTomlField(
        key: String,
        value: String,
        inputType: FieldInputType,
    ) {
        when (inputType) {
            FieldInputType.NUMBER -> appendLine("$key = ${value.ifBlank { "0" }}")
            FieldInputType.BOOLEAN -> appendLine("$key = ${value.lowercase()}")
            FieldInputType.LIST -> {
                val items = value.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(", ") { tomlString(it) }
                appendLine("$key = [$items]")
            }
            else -> appendLine("$key = ${tomlString(value)}")
        }
    }

    /**
     * Maps an Android provider ID and optional base URL to the upstream
     * Rust factory provider name.
     *
     * @param provider Android provider ID.
     * @param baseUrl Optional endpoint URL.
     * @return The resolved provider string for the TOML, or blank if
     *   [provider] is blank.
     */
    internal fun resolveProvider(provider: String, baseUrl: String): String {
        if (provider.isBlank()) return ""

        val trimmedUrl = baseUrl.trim()

        if (provider == "custom-anthropic" && trimmedUrl.isNotEmpty()) {
            return "anthropic-custom:$trimmedUrl"
        }

        if (provider in OPENAI_COMPATIBLE_SELF_HOSTED && trimmedUrl.isNotEmpty()) {
            return "custom:$trimmedUrl"
        }

        if (provider == "ollama" && trimmedUrl.isNotEmpty() && trimmedUrl != OLLAMA_DEFAULT_URL) {
            return "custom:$trimmedUrl"
        }

        return provider
    }

    /**
     * Wraps a value in double quotes with TOML-compliant escaping.
     *
     * Escapes backslashes, double quotes, and common control characters
     * (newline, carriage return, tab, backspace, form feed) as required
     * by the [TOML specification](https://toml.io/en/v1.0.0#string).
     */
    internal fun tomlString(value: String): String = buildString {
        append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> append(ch)
            }
        }
        append('"')
    }
}
