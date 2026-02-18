/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import com.zeroclaw.android.model.Agent
import com.zeroclaw.android.model.ConnectedChannel
import com.zeroclaw.android.model.FieldInputType

/**
 * Resolved agent data ready for TOML serialization.
 *
 * All provider/URL resolution is performed before constructing this class
 * so that [ConfigTomlBuilder.buildAgentsToml] only needs to emit values.
 *
 * Upstream `[agents.<name>]` supports `temperature` (`Option<f64>`) and
 * `max_depth` (`u32`) — see `.claude/submodule-api-map.md` lines 235–236.
 *
 * @property name Agent name used as the TOML table key (`[agents.<name>]`).
 * @property provider Resolved upstream factory name (e.g. `"custom:http://host/v1"`).
 * @property model Model identifier (e.g. `"google/gemma-3-12b"`).
 * @property apiKey Decrypted API key value; blank if the provider needs none.
 * @property systemPrompt Agent system prompt; blank if not configured.
 * @property temperature Per-agent temperature override; null omits the field.
 * @property maxDepth Maximum reasoning depth; default omits the field.
 */
data class AgentTomlEntry(
    val name: String,
    val provider: String,
    val model: String,
    val apiKey: String = "",
    val systemPrompt: String = "",
    val temperature: Float? = null,
    val maxDepth: Int = Agent.DEFAULT_MAX_DEPTH,
)

/**
 * Aggregated global configuration values for TOML generation.
 *
 * Grouping these fields into a single data class avoids exceeding the
 * detekt `LongParameterList` threshold (6 parameters).
 *
 * Upstream sections mapped (see `.claude/submodule-api-map.md`):
 * - `default_temperature` (line 219)
 * - `[agent]` compact_context (line 225)
 * - `[cost]` (lines 414–420)
 * - `[reliability]` provider_retries, fallback_providers (lines 286–289)
 * - `[memory]` backend (line 315)
 * - `[identity]` aieos_inline (lines 405–410)
 *
 * @property provider Android provider ID (e.g. "openai", "lmstudio").
 * @property model Model name (e.g. "gpt-4o").
 * @property apiKey Secret API key value.
 * @property baseUrl Provider endpoint URL.
 * @property temperature Default inference temperature (0.0–2.0).
 * @property compactContext Whether compact context mode is enabled.
 * @property costEnabled Whether cost limits are enforced.
 * @property dailyLimitUsd Daily spending cap in USD.
 * @property monthlyLimitUsd Monthly spending cap in USD.
 * @property costWarnAtPercent Percentage of limit at which to warn.
 * @property providerRetries Number of retries before fallback.
 * @property fallbackProviders Ordered list of fallback provider IDs.
 * @property memoryBackend Memory backend name.
 * @property memoryAutoSave Whether the memory backend auto-saves conversation context.
 * @property identityJson AIEOS v1.1 identity JSON blob.
 */
data class GlobalTomlConfig(
    val provider: String,
    val model: String,
    val apiKey: String,
    val baseUrl: String,
    val temperature: Float = DEFAULT_GLOBAL_TEMPERATURE,
    val compactContext: Boolean = false,
    val costEnabled: Boolean = false,
    val dailyLimitUsd: Float = DEFAULT_DAILY_LIMIT,
    val monthlyLimitUsd: Float = DEFAULT_MONTHLY_LIMIT,
    val costWarnAtPercent: Int = DEFAULT_WARN_PERCENT,
    val providerRetries: Int = DEFAULT_RETRIES,
    val fallbackProviders: List<String> = emptyList(),
    val memoryBackend: String = DEFAULT_MEMORY,
    val memoryAutoSave: Boolean = true,
    val identityJson: String = "",
) {
    /** Constants for [GlobalTomlConfig]. */
    companion object {
        /** Default inference temperature. */
        const val DEFAULT_GLOBAL_TEMPERATURE = 0.7f

        /** Default daily cost limit in USD. */
        const val DEFAULT_DAILY_LIMIT = 10f

        /** Default monthly cost limit in USD. */
        const val DEFAULT_MONTHLY_LIMIT = 100f

        /** Default cost warning threshold percentage. */
        const val DEFAULT_WARN_PERCENT = 80

        /** Default number of provider retries. */
        const val DEFAULT_RETRIES = 2

        /** Default memory backend. */
        const val DEFAULT_MEMORY = "sqlite"
    }
}

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

    /**
     * Placeholder API key injected for self-hosted providers (LM Studio,
     * vLLM, LocalAI, Ollama) that don't require authentication.
     *
     * The upstream [OpenAiCompatibleProvider] unconditionally requires
     * `api_key` to be `Some(...)` and will error before sending any HTTP
     * request if it is `None`. Local servers ignore the resulting
     * `Authorization: Bearer not-needed` header.
     */
    private const val PLACEHOLDER_API_KEY = "not-needed"

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
    ): String = build(
        GlobalTomlConfig(
            provider = provider,
            model = model,
            apiKey = apiKey,
            baseUrl = baseUrl,
        ),
    )

    /**
     * Builds a complete TOML configuration string from a [GlobalTomlConfig].
     *
     * Emits all upstream-supported sections conditionally based on the
     * config values. Sections with only default values are omitted to
     * keep the TOML output minimal.
     *
     * @param config Aggregated global configuration values.
     * @return A valid TOML configuration string.
     */
    fun build(config: GlobalTomlConfig): String = buildString {
        appendLine("default_temperature = ${config.temperature}")

        val resolvedProvider = resolveProvider(config.provider, config.baseUrl)
        if (resolvedProvider.isNotBlank()) {
            appendLine("default_provider = ${tomlString(resolvedProvider)}")
        }

        if (config.model.isNotBlank()) {
            appendLine("default_model = ${tomlString(config.model)}")
        }

        val effectiveKey = config.apiKey.ifBlank {
            if (needsPlaceholderKey(resolvedProvider)) PLACEHOLDER_API_KEY else ""
        }
        if (effectiveKey.isNotBlank()) {
            appendLine("api_key = ${tomlString(effectiveKey)}")
        }

        appendLine()
        appendLine("[gateway]")
        appendLine("require_pairing = false")

        if (config.compactContext) {
            appendLine()
            appendLine("[agent]")
            appendLine("compact_context = true")
        }

        appendLine()
        appendLine("[memory]")
        appendLine("backend = ${tomlString(config.memoryBackend)}")
        appendLine("auto_save = ${config.memoryAutoSave}")

        if (config.identityJson.isNotBlank()) {
            appendLine()
            appendLine("[identity]")
            appendLine("format = \"aieos\"")
            appendLine("aieos_inline = ${tomlString(config.identityJson)}")
        }

        if (config.costEnabled) {
            appendLine()
            appendLine("[cost]")
            appendLine("enabled = true")
            appendLine("daily_limit_usd = ${config.dailyLimitUsd}")
            appendLine("monthly_limit_usd = ${config.monthlyLimitUsd}")
            appendLine("warn_at_percent = ${config.costWarnAtPercent}")
        }

        appendReliabilitySection(config)
    }

    /**
     * Appends the `[reliability]` TOML section when non-default values exist.
     *
     * @param config Configuration to read reliability values from.
     */
    private fun StringBuilder.appendReliabilitySection(config: GlobalTomlConfig) {
        val hasCustomRetries =
            config.providerRetries != GlobalTomlConfig.DEFAULT_RETRIES
        val hasFallbacks = config.fallbackProviders.isNotEmpty()
        if (!hasCustomRetries && !hasFallbacks) return

        appendLine()
        appendLine("[reliability]")
        if (hasCustomRetries) {
            appendLine("provider_retries = ${config.providerRetries}")
        }
        if (hasFallbacks) {
            val list = config.fallbackProviders
                .joinToString(", ") { tomlString(it) }
            appendLine("fallback_providers = [$list]")
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
                    val value = values[spec.key].orEmpty()
                    if (value.isBlank() && !spec.isRequired) continue
                    appendTomlField(spec.key, value, spec.inputType)
                }
            }
        }
    }

    /**
     * Builds `[agents.<name>]` TOML sections for per-agent provider configuration.
     *
     * The upstream [DelegateAgentConfig] struct supports `provider`, `model`,
     * `system_prompt`, and `api_key` fields per agent. Only non-blank optional
     * fields are emitted.
     *
     * @param agents Resolved agent entries to serialize.
     * @return TOML string with one `[agents.<name>]` section per entry,
     *   or empty if [agents] is empty.
     */
    fun buildAgentsToml(agents: List<AgentTomlEntry>): String {
        if (agents.isEmpty()) return ""
        return buildString {
            for (entry in agents) {
                appendLine()
                appendLine("[agents.${tomlKey(entry.name)}]")
                appendLine("provider = ${tomlString(entry.provider)}")
                appendLine("model = ${tomlString(entry.model)}")
                if (entry.systemPrompt.isNotBlank()) {
                    appendLine("system_prompt = ${tomlString(entry.systemPrompt)}")
                }
                val effectiveKey = entry.apiKey.ifBlank {
                    if (needsPlaceholderKey(entry.provider)) PLACEHOLDER_API_KEY else ""
                }
                if (effectiveKey.isNotBlank()) {
                    appendLine("api_key = ${tomlString(effectiveKey)}")
                }
                if (entry.temperature != null) {
                    appendLine("temperature = ${entry.temperature}")
                }
                if (entry.maxDepth != Agent.DEFAULT_MAX_DEPTH) {
                    appendLine("max_depth = ${entry.maxDepth}")
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
     * Returns true if the resolved provider requires a placeholder API key.
     *
     * The upstream [OpenAiCompatibleProvider] unconditionally demands
     * `api_key` to be non-null. Self-hosted servers (LM Studio, vLLM,
     * LocalAI, Ollama) don't need authentication, but the provider
     * factory still needs *some* value to avoid a "key not set" error.
     *
     * @param resolvedProvider The resolved TOML provider string.
     * @return True if [PLACEHOLDER_API_KEY] should be injected.
     */
    private fun needsPlaceholderKey(resolvedProvider: String): Boolean =
        resolvedProvider.startsWith("custom:") || resolvedProvider == "ollama"

    /**
     * Formats a value as a quoted TOML key.
     *
     * Bare keys may only contain ASCII letters, digits, dashes, and underscores.
     * Keys containing any other characters (spaces, dots, etc.) must be quoted.
     *
     * @param key Raw key value.
     * @return The key suitable for use in a TOML table header or dotted key.
     */
    private fun tomlKey(key: String): String {
        val isBareKey = key.isNotEmpty() && key.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        return if (isBareKey) key else tomlString(key)
    }

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
