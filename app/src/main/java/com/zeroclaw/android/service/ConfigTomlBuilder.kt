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
 * - `default_temperature`, `default_provider`, `default_model`, `api_key`
 * - `[agent]` compact_context
 * - `[gateway]` host, port, pairing, rate limits, idempotency
 * - `[memory]` backend, hygiene, embedding, recall weights
 * - `[identity]` aieos_inline
 * - `[cost]` enabled, daily/monthly limits, warn percent
 * - `[reliability]` provider_retries, fallback_providers
 * - `[autonomy]` level, workspace, commands, paths, limits
 * - `[tunnel]` provider + sub-tables (cloudflare/tailscale/ngrok/custom)
 * - `[scheduler]` enabled, max_tasks, max_concurrent
 * - `[heartbeat]` enabled, interval_minutes
 * - `[observability]` backend, otel_endpoint, otel_service_name
 * - `[[model_routes]]` hint, provider, model
 * - `[composio]` enabled, api_key, entity_id
 * - `[browser]` enabled, allowed_domains
 * - `[http_request]` enabled, allowed_domains
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
 * @property autonomyLevel Autonomy level: "readonly", "supervised", or "full".
 * @property workspaceOnly Whether to restrict file access to workspace only.
 * @property allowedCommands Allowed shell commands list.
 * @property forbiddenPaths Forbidden filesystem paths list.
 * @property maxActionsPerHour Maximum agent actions per hour.
 * @property maxCostPerDayCents Maximum daily cost in cents.
 * @property requireApprovalMediumRisk Whether medium-risk actions require approval.
 * @property blockHighRiskCommands Whether to block high-risk commands entirely.
 * @property tunnelProvider Tunnel provider name.
 * @property tunnelCloudflareToken Cloudflare tunnel auth token.
 * @property tunnelTailscaleFunnel Whether to enable Tailscale Funnel.
 * @property tunnelTailscaleHostname Custom Tailscale hostname.
 * @property tunnelNgrokAuthToken ngrok authentication token.
 * @property tunnelNgrokDomain Custom ngrok domain.
 * @property tunnelCustomCommand Custom tunnel start command.
 * @property tunnelCustomHealthUrl Health check URL for custom tunnel.
 * @property tunnelCustomUrlPattern URL extraction pattern for custom tunnel.
 * @property gatewayHost Gateway bind address.
 * @property gatewayPort Gateway bind port.
 * @property gatewayRequirePairing Whether gateway requires pairing tokens.
 * @property gatewayAllowPublicBind Whether to allow binding to 0.0.0.0.
 * @property gatewayPairedTokens Authorized pairing tokens list.
 * @property gatewayPairRateLimit Pairing rate limit per minute.
 * @property gatewayWebhookRateLimit Webhook rate limit per minute.
 * @property gatewayIdempotencyTtl Idempotency TTL in seconds.
 * @property schedulerEnabled Whether the task scheduler is active.
 * @property schedulerMaxTasks Maximum scheduler tasks.
 * @property schedulerMaxConcurrent Maximum concurrent task executions.
 * @property heartbeatEnabled Whether the heartbeat engine is active.
 * @property heartbeatIntervalMinutes Interval between heartbeat ticks.
 * @property observabilityBackend Observability backend name.
 * @property observabilityOtelEndpoint OpenTelemetry collector endpoint.
 * @property observabilityOtelServiceName Service name for OTel traces.
 * @property modelRoutesJson JSON array of model route objects.
 * @property memoryHygieneEnabled Whether memory hygiene is active.
 * @property memoryArchiveAfterDays Days before memory entries are archived.
 * @property memoryPurgeAfterDays Days before archived entries are purged.
 * @property memoryEmbeddingProvider Embedding provider name.
 * @property memoryEmbeddingModel Embedding model name.
 * @property memoryVectorWeight Weight for vector similarity in recall.
 * @property memoryKeywordWeight Weight for keyword matching in recall.
 * @property composioEnabled Whether Composio tool integration is active.
 * @property composioApiKey Composio API key.
 * @property composioEntityId Composio entity identifier.
 * @property browserEnabled Whether the browser tool is enabled.
 * @property browserAllowedDomains Allowed browser domains list.
 * @property httpRequestEnabled Whether the HTTP request tool is enabled.
 * @property httpRequestAllowedDomains Allowed HTTP domains list.
 */
@Suppress("LongParameterList")
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
    val autonomyLevel: String = "supervised",
    val workspaceOnly: Boolean = true,
    val allowedCommands: List<String> = emptyList(),
    val forbiddenPaths: List<String> = emptyList(),
    val maxActionsPerHour: Int = DEFAULT_MAX_ACTIONS,
    val maxCostPerDayCents: Int = DEFAULT_MAX_COST_CENTS,
    val requireApprovalMediumRisk: Boolean = true,
    val blockHighRiskCommands: Boolean = true,
    val tunnelProvider: String = "none",
    val tunnelCloudflareToken: String = "",
    val tunnelTailscaleFunnel: Boolean = false,
    val tunnelTailscaleHostname: String = "",
    val tunnelNgrokAuthToken: String = "",
    val tunnelNgrokDomain: String = "",
    val tunnelCustomCommand: String = "",
    val tunnelCustomHealthUrl: String = "",
    val tunnelCustomUrlPattern: String = "",
    val gatewayHost: String = "127.0.0.1",
    val gatewayPort: Int = DEFAULT_GATEWAY_PORT,
    val gatewayRequirePairing: Boolean = false,
    val gatewayAllowPublicBind: Boolean = false,
    val gatewayPairedTokens: List<String> = emptyList(),
    val gatewayPairRateLimit: Int = DEFAULT_PAIR_RATE,
    val gatewayWebhookRateLimit: Int = DEFAULT_WEBHOOK_RATE,
    val gatewayIdempotencyTtl: Int = DEFAULT_IDEMPOTENCY_TTL,
    val schedulerEnabled: Boolean = true,
    val schedulerMaxTasks: Int = DEFAULT_SCHEDULER_TASKS,
    val schedulerMaxConcurrent: Int = DEFAULT_SCHEDULER_CONCURRENT,
    val heartbeatEnabled: Boolean = false,
    val heartbeatIntervalMinutes: Int = DEFAULT_HEARTBEAT_INTERVAL,
    val observabilityBackend: String = "none",
    val observabilityOtelEndpoint: String = "",
    val observabilityOtelServiceName: String = "zeroclaw",
    val modelRoutesJson: String = "[]",
    val memoryHygieneEnabled: Boolean = true,
    val memoryArchiveAfterDays: Int = DEFAULT_ARCHIVE_DAYS,
    val memoryPurgeAfterDays: Int = DEFAULT_PURGE_DAYS,
    val memoryEmbeddingProvider: String = "none",
    val memoryEmbeddingModel: String = "",
    val memoryVectorWeight: Float = DEFAULT_VECTOR_WEIGHT,
    val memoryKeywordWeight: Float = DEFAULT_KEYWORD_WEIGHT,
    val composioEnabled: Boolean = false,
    val composioApiKey: String = "",
    val composioEntityId: String = "default",
    val browserEnabled: Boolean = false,
    val browserAllowedDomains: List<String> = emptyList(),
    val httpRequestEnabled: Boolean = false,
    val httpRequestAllowedDomains: List<String> = emptyList(),
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

        /** Default max actions per hour. */
        const val DEFAULT_MAX_ACTIONS = 20

        /** Default max cost per day in cents. */
        const val DEFAULT_MAX_COST_CENTS = 500

        /** Default gateway port. */
        const val DEFAULT_GATEWAY_PORT = 3000

        /** Default pair rate limit per minute. */
        const val DEFAULT_PAIR_RATE = 10

        /** Default webhook rate limit per minute. */
        const val DEFAULT_WEBHOOK_RATE = 60

        /** Default idempotency TTL in seconds. */
        const val DEFAULT_IDEMPOTENCY_TTL = 300

        /** Default scheduler max tasks. */
        const val DEFAULT_SCHEDULER_TASKS = 64

        /** Default scheduler max concurrent. */
        const val DEFAULT_SCHEDULER_CONCURRENT = 4

        /** Default heartbeat interval in minutes. */
        const val DEFAULT_HEARTBEAT_INTERVAL = 30

        /** Default memory archive threshold. */
        const val DEFAULT_ARCHIVE_DAYS = 7

        /** Default memory purge threshold. */
        const val DEFAULT_PURGE_DAYS = 30

        /** Default vector weight. */
        const val DEFAULT_VECTOR_WEIGHT = 0.7f

        /** Default keyword weight. */
        const val DEFAULT_KEYWORD_WEIGHT = 0.3f
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
    private val OPENAI_COMPATIBLE_SELF_HOSTED =
        setOf(
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
    ): String =
        build(
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
    @Suppress("CognitiveComplexMethod", "LongMethod")
    fun build(config: GlobalTomlConfig): String =
        buildString {
            appendLine("default_temperature = ${config.temperature}")

            val resolvedProvider = resolveProvider(config.provider, config.baseUrl)
            if (resolvedProvider.isNotBlank()) {
                appendLine("default_provider = ${tomlString(resolvedProvider)}")
            }

            if (config.model.isNotBlank()) {
                appendLine("default_model = ${tomlString(config.model)}")
            }

            val effectiveKey =
                config.apiKey.ifBlank {
                    if (needsPlaceholderKey(resolvedProvider)) PLACEHOLDER_API_KEY else ""
                }
            if (effectiveKey.isNotBlank()) {
                appendLine("api_key = ${tomlString(effectiveKey)}")
            }

            if (config.compactContext) {
                appendLine()
                appendLine("[agent]")
                appendLine("compact_context = true")
            }

            appendGatewaySection(config)
            appendMemorySection(config)

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
            appendAutonomySection(config)
            appendTunnelSection(config)
            appendSchedulerSection(config)
            appendHeartbeatSection(config)
            appendObservabilitySection(config)
            appendModelRoutesSection(config)
            appendComposioSection(config)
            appendBrowserSection(config)
            appendHttpRequestSection(config)
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
            val list =
                config.fallbackProviders
                    .joinToString(", ") { tomlString(it) }
            appendLine("fallback_providers = [$list]")
        }
    }

    /**
     * Appends the `[gateway]` TOML section with all gateway-related fields.
     *
     * Upstream fields: host, port, require_pairing, allow_public_bind,
     * paired_tokens, pair_rate_limit_per_minute, webhook_rate_limit_per_minute,
     * idempotency_ttl_secs (see `.claude/submodule-api-map.md` lines 349-358).
     *
     * @param config Configuration to read gateway values from.
     */
    private fun StringBuilder.appendGatewaySection(config: GlobalTomlConfig) {
        appendLine()
        appendLine("[gateway]")
        appendLine("host = ${tomlString(config.gatewayHost)}")
        appendLine("port = ${config.gatewayPort}")
        appendLine("require_pairing = ${config.gatewayRequirePairing}")
        appendLine("allow_public_bind = ${config.gatewayAllowPublicBind}")
        if (config.gatewayPairedTokens.isNotEmpty()) {
            val list = config.gatewayPairedTokens.joinToString(", ") { tomlString(it) }
            appendLine("paired_tokens = [$list]")
        }
        appendLine("pair_rate_limit_per_minute = ${config.gatewayPairRateLimit}")
        appendLine("webhook_rate_limit_per_minute = ${config.gatewayWebhookRateLimit}")
        appendLine("idempotency_ttl_secs = ${config.gatewayIdempotencyTtl}")
    }

    /**
     * Appends the `[memory]` TOML section with backend and hygiene fields.
     *
     * Upstream fields: backend, auto_save, hygiene_enabled, archive_after_days,
     * purge_after_days, embedding_provider, embedding_model, vector_weight,
     * keyword_weight (see `.claude/submodule-api-map.md` lines 314-327).
     *
     * @param config Configuration to read memory values from.
     */
    private fun StringBuilder.appendMemorySection(config: GlobalTomlConfig) {
        appendLine()
        appendLine("[memory]")
        appendLine("backend = ${tomlString(config.memoryBackend)}")
        appendLine("auto_save = ${config.memoryAutoSave}")
        appendLine("hygiene_enabled = ${config.memoryHygieneEnabled}")
        appendLine("archive_after_days = ${config.memoryArchiveAfterDays}")
        appendLine("purge_after_days = ${config.memoryPurgeAfterDays}")
        if (config.memoryEmbeddingProvider != "none") {
            appendLine("embedding_provider = ${tomlString(config.memoryEmbeddingProvider)}")
            if (config.memoryEmbeddingModel.isNotBlank()) {
                appendLine("embedding_model = ${tomlString(config.memoryEmbeddingModel)}")
            }
        }
        appendLine("vector_weight = ${config.memoryVectorWeight}")
        appendLine("keyword_weight = ${config.memoryKeywordWeight}")
    }

    /**
     * Appends the `[autonomy]` TOML section.
     *
     * Upstream fields: level, workspace_only, allowed_commands, forbidden_paths,
     * max_actions_per_hour, max_cost_per_day_cents, require_approval_for_medium_risk,
     * block_high_risk_commands (see `.claude/submodule-api-map.md` lines 258-266).
     *
     * @param config Configuration to read autonomy values from.
     */
    private fun StringBuilder.appendAutonomySection(config: GlobalTomlConfig) {
        appendLine()
        appendLine("[autonomy]")
        appendLine("level = ${tomlString(config.autonomyLevel)}")
        appendLine("workspace_only = ${config.workspaceOnly}")
        if (config.allowedCommands.isNotEmpty()) {
            val list = config.allowedCommands.joinToString(", ") { tomlString(it) }
            appendLine("allowed_commands = [$list]")
        }
        if (config.forbiddenPaths.isNotEmpty()) {
            val list = config.forbiddenPaths.joinToString(", ") { tomlString(it) }
            appendLine("forbidden_paths = [$list]")
        }
        appendLine("max_actions_per_hour = ${config.maxActionsPerHour}")
        appendLine("max_cost_per_day_cents = ${config.maxCostPerDayCents}")
        appendLine("require_approval_for_medium_risk = ${config.requireApprovalMediumRisk}")
        appendLine("block_high_risk_commands = ${config.blockHighRiskCommands}")
    }

    /**
     * Appends the `[tunnel]` TOML section when a tunnel provider is configured.
     *
     * Upstream fields: provider, cloudflare.token, tailscale.funnel/hostname,
     * ngrok.auth_token/domain, custom.start_command/health_url/url_pattern
     * (see `.claude/submodule-api-map.md` lines 332-346).
     *
     * @param config Configuration to read tunnel values from.
     */
    @Suppress("CognitiveComplexMethod")
    private fun StringBuilder.appendTunnelSection(config: GlobalTomlConfig) {
        if (config.tunnelProvider == "none") return
        appendLine()
        appendLine("[tunnel]")
        appendLine("provider = ${tomlString(config.tunnelProvider)}")
        when (config.tunnelProvider) {
            "cloudflare" -> {
                appendLine("[tunnel.cloudflare]")
                appendLine("token = ${tomlString(config.tunnelCloudflareToken)}")
            }
            "tailscale" -> {
                appendLine("[tunnel.tailscale]")
                appendLine("funnel = ${config.tunnelTailscaleFunnel}")
                if (config.tunnelTailscaleHostname.isNotBlank()) {
                    appendLine("hostname = ${tomlString(config.tunnelTailscaleHostname)}")
                }
            }
            "ngrok" -> {
                appendLine("[tunnel.ngrok]")
                appendLine("auth_token = ${tomlString(config.tunnelNgrokAuthToken)}")
                if (config.tunnelNgrokDomain.isNotBlank()) {
                    appendLine("domain = ${tomlString(config.tunnelNgrokDomain)}")
                }
            }
            "custom" -> {
                appendLine("[tunnel.custom]")
                appendLine("start_command = ${tomlString(config.tunnelCustomCommand)}")
                if (config.tunnelCustomHealthUrl.isNotBlank()) {
                    appendLine("health_url = ${tomlString(config.tunnelCustomHealthUrl)}")
                }
                if (config.tunnelCustomUrlPattern.isNotBlank()) {
                    appendLine("url_pattern = ${tomlString(config.tunnelCustomUrlPattern)}")
                }
            }
        }
    }

    /**
     * Appends the `[scheduler]` TOML section.
     *
     * Upstream fields: enabled, max_tasks, max_concurrent
     * (see `.claude/submodule-api-map.md` lines 299-303).
     *
     * @param config Configuration to read scheduler values from.
     */
    private fun StringBuilder.appendSchedulerSection(config: GlobalTomlConfig) {
        appendLine()
        appendLine("[scheduler]")
        appendLine("enabled = ${config.schedulerEnabled}")
        appendLine("max_tasks = ${config.schedulerMaxTasks}")
        appendLine("max_concurrent = ${config.schedulerMaxConcurrent}")
    }

    /**
     * Appends the `[heartbeat]` TOML section.
     *
     * Upstream fields: enabled, interval_minutes
     * (see `.claude/submodule-api-map.md` lines 306-310).
     *
     * @param config Configuration to read heartbeat values from.
     */
    private fun StringBuilder.appendHeartbeatSection(config: GlobalTomlConfig) {
        appendLine()
        appendLine("[heartbeat]")
        appendLine("enabled = ${config.heartbeatEnabled}")
        appendLine("interval_minutes = ${config.heartbeatIntervalMinutes}")
    }

    /**
     * Appends the `[observability]` TOML section.
     *
     * Upstream fields: backend, otel_endpoint, otel_service_name
     * (see `.claude/submodule-api-map.md` lines 250-253).
     *
     * @param config Configuration to read observability values from.
     */
    private fun StringBuilder.appendObservabilitySection(config: GlobalTomlConfig) {
        appendLine()
        appendLine("[observability]")
        appendLine("backend = ${tomlString(config.observabilityBackend)}")
        if (config.observabilityBackend == "otel") {
            if (config.observabilityOtelEndpoint.isNotBlank()) {
                appendLine("otel_endpoint = ${tomlString(config.observabilityOtelEndpoint)}")
            }
            appendLine("otel_service_name = ${tomlString(config.observabilityOtelServiceName)}")
        }
    }

    /**
     * Appends `[[model_routes]]` TOML array entries from the JSON array.
     *
     * Upstream fields: hint, provider, model
     * (see `.claude/submodule-api-map.md` lines 241-245).
     *
     * @param config Configuration to read model routes JSON from.
     */
    private fun StringBuilder.appendModelRoutesSection(config: GlobalTomlConfig) {
        if (config.modelRoutesJson == "[]" || config.modelRoutesJson.isBlank()) return
        try {
            val arr = org.json.JSONArray(config.modelRoutesJson)
            for (i in 0 until arr.length()) {
                val route = arr.getJSONObject(i)
                val hint = route.optString("hint", "")
                val provider = route.optString("provider", "")
                val model = route.optString("model", "")
                if (hint.isBlank() || provider.isBlank() || model.isBlank()) continue
                appendLine()
                appendLine("[[model_routes]]")
                appendLine("hint = ${tomlString(hint)}")
                appendLine("provider = ${tomlString(provider)}")
                appendLine("model = ${tomlString(model)}")
            }
        } catch (_: org.json.JSONException) {
            // Ignore malformed JSON
        }
    }

    /**
     * Appends the `[composio]` TOML section when Composio is enabled.
     *
     * Upstream fields: enabled, api_key, entity_id
     * (see `.claude/submodule-api-map.md` lines 363-367).
     *
     * @param config Configuration to read Composio values from.
     */
    private fun StringBuilder.appendComposioSection(config: GlobalTomlConfig) {
        if (!config.composioEnabled) return
        appendLine()
        appendLine("[composio]")
        appendLine("enabled = true")
        if (config.composioApiKey.isNotBlank()) {
            appendLine("api_key = ${tomlString(config.composioApiKey)}")
        }
        appendLine("entity_id = ${tomlString(config.composioEntityId)}")
    }

    /**
     * Appends the `[browser]` TOML section when the browser tool is enabled.
     *
     * Upstream fields: enabled, allowed_domains
     * (see `.claude/submodule-api-map.md` lines 377-379).
     *
     * @param config Configuration to read browser values from.
     */
    private fun StringBuilder.appendBrowserSection(config: GlobalTomlConfig) {
        if (!config.browserEnabled) return
        appendLine()
        appendLine("[browser]")
        appendLine("enabled = true")
        if (config.browserAllowedDomains.isNotEmpty()) {
            val list = config.browserAllowedDomains.joinToString(", ") { tomlString(it) }
            appendLine("allowed_domains = [$list]")
        }
    }

    /**
     * Appends the `[http_request]` TOML section when HTTP requests are enabled.
     *
     * Upstream fields: enabled, allowed_domains
     * (see `.claude/submodule-api-map.md` lines 396-399).
     *
     * @param config Configuration to read HTTP request values from.
     */
    private fun StringBuilder.appendHttpRequestSection(config: GlobalTomlConfig) {
        if (!config.httpRequestEnabled) return
        appendLine()
        appendLine("[http_request]")
        appendLine("enabled = true")
        if (config.httpRequestAllowedDomains.isNotEmpty()) {
            val list = config.httpRequestAllowedDomains.joinToString(", ") { tomlString(it) }
            appendLine("allowed_domains = [$list]")
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
    @Suppress("CognitiveComplexMethod")
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
                val effectiveKey =
                    entry.apiKey.ifBlank {
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
                val items =
                    value
                        .split(",")
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
    internal fun resolveProvider(
        provider: String,
        baseUrl: String,
    ): String {
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
    private fun needsPlaceholderKey(resolvedProvider: String): Boolean = resolvedProvider.startsWith("custom:") || resolvedProvider == "ollama"

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
        val isBareKey =
            key.isNotEmpty() && key.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        return if (isBareKey) key else tomlString(key)
    }

    internal fun tomlString(value: String): String =
        buildString {
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
