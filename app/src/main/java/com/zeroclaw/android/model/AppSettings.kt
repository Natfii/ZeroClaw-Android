/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * Persistent application settings covering all upstream TOML configuration sections.
 *
 * @property host Gateway bind address.
 * @property port Gateway bind port.
 * @property autoStartOnBoot Whether to start the daemon automatically after reboot.
 * @property logLevel Minimum severity for log output.
 * @property defaultProvider Default provider ID for new agents (e.g. "openai").
 * @property defaultModel Default model name for new agents (e.g. "gpt-4o").
 * @property defaultTemperature Global inference temperature (0.0–2.0).
 * @property compactContext Whether to enable compact context mode upstream.
 * @property costEnabled Whether spending limits are enforced.
 * @property dailyLimitUsd Maximum daily spend in USD.
 * @property monthlyLimitUsd Maximum monthly spend in USD.
 * @property costWarnAtPercent Percentage of limit at which to warn.
 * @property providerRetries Number of retries before falling back.
 * @property fallbackProviders Comma-separated list of fallback provider IDs.
 * @property memoryBackend Memory backend name ("sqlite", "none", "markdown", "lucid").
 * @property memoryAutoSave Whether the memory backend auto-saves conversation context.
 * @property identityJson AIEOS v1.1 identity JSON blob.
 * @property autonomyLevel Autonomy level: "readonly", "supervised", or "full".
 * @property workspaceOnly Whether to restrict file access to the workspace directory.
 * @property allowedCommands Comma-separated list of allowed shell commands.
 * @property forbiddenPaths Comma-separated list of forbidden filesystem paths.
 * @property maxActionsPerHour Maximum agent actions per hour.
 * @property maxCostPerDayCents Maximum daily cost in cents.
 * @property requireApprovalMediumRisk Whether medium-risk actions require user approval.
 * @property blockHighRiskCommands Whether to block high-risk shell commands entirely.
 * @property tunnelProvider Tunnel provider: "none", "cloudflare", "tailscale", "ngrok", "custom".
 * @property tunnelCloudflareToken Cloudflare tunnel auth token.
 * @property tunnelTailscaleFunnel Whether to enable Tailscale Funnel.
 * @property tunnelTailscaleHostname Custom Tailscale hostname.
 * @property tunnelNgrokAuthToken ngrok authentication token.
 * @property tunnelNgrokDomain Custom ngrok domain.
 * @property tunnelCustomCommand Custom tunnel start command.
 * @property tunnelCustomHealthUrl Health check URL for custom tunnel.
 * @property tunnelCustomUrlPattern URL extraction pattern for custom tunnel.
 * @property gatewayRequirePairing Whether gateway requires pairing tokens.
 * @property gatewayAllowPublicBind Whether to allow binding to 0.0.0.0.
 * @property gatewayPairedTokens Comma-separated list of authorized pairing tokens.
 * @property gatewayPairRateLimit Pairing rate limit per minute.
 * @property gatewayWebhookRateLimit Webhook rate limit per minute.
 * @property gatewayIdempotencyTtl Idempotency TTL in seconds.
 * @property schedulerEnabled Whether the task scheduler is active.
 * @property schedulerMaxTasks Maximum concurrent scheduler tasks.
 * @property schedulerMaxConcurrent Maximum concurrent task executions.
 * @property heartbeatEnabled Whether the heartbeat engine is active.
 * @property heartbeatIntervalMinutes Interval between heartbeat ticks.
 * @property observabilityBackend Observability backend: "none", "log", "otel".
 * @property observabilityOtelEndpoint OpenTelemetry collector endpoint.
 * @property observabilityOtelServiceName Service name for OTel traces.
 * @property modelRoutesJson JSON array of model route objects.
 * @property memoryHygieneEnabled Whether memory hygiene (archival/purge) is active.
 * @property memoryArchiveAfterDays Days before memory entries are archived.
 * @property memoryPurgeAfterDays Days before archived entries are purged.
 * @property memoryEmbeddingProvider Embedding provider: "none", "openai", or "custom:URL".
 * @property memoryEmbeddingModel Embedding model name.
 * @property memoryVectorWeight Weight for vector similarity in recall (0.0–1.0).
 * @property memoryKeywordWeight Weight for keyword matching in recall (0.0–1.0).
 * @property composioEnabled Whether Composio tool integration is active.
 * @property composioApiKey Composio API key.
 * @property composioEntityId Composio entity identifier.
 * @property browserEnabled Whether the browser tool is enabled.
 * @property browserAllowedDomains Comma-separated list of allowed browser domains.
 * @property httpRequestEnabled Whether the HTTP request tool is enabled.
 * @property httpRequestAllowedDomains Comma-separated list of allowed HTTP domains.
 * @property biometricForService Whether biometric auth is required for service start/stop.
 * @property biometricForSettings Whether biometric auth is required for sensitive settings.
 * @property pluginRegistryUrl URL of the remote plugin registry catalog.
 * @property pluginSyncEnabled Whether automatic plugin registry sync is active.
 * @property pluginSyncIntervalHours Interval in hours between automatic syncs.
 * @property lastPluginSyncTimestamp Unix timestamp of the most recent successful sync.
 */
@Suppress("LongParameterList")
data class AppSettings(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val autoStartOnBoot: Boolean = false,
    val logLevel: LogLevel = LogLevel.INFO,
    val defaultProvider: String = "",
    val defaultModel: String = "",
    val defaultTemperature: Float = DEFAULT_TEMPERATURE,
    val compactContext: Boolean = false,
    val costEnabled: Boolean = false,
    val dailyLimitUsd: Float = DEFAULT_DAILY_LIMIT_USD,
    val monthlyLimitUsd: Float = DEFAULT_MONTHLY_LIMIT_USD,
    val costWarnAtPercent: Int = DEFAULT_COST_WARN_PERCENT,
    val providerRetries: Int = DEFAULT_PROVIDER_RETRIES,
    val fallbackProviders: String = "",
    val memoryBackend: String = DEFAULT_MEMORY_BACKEND,
    val memoryAutoSave: Boolean = true,
    val identityJson: String = "",
    val autonomyLevel: String = DEFAULT_AUTONOMY_LEVEL,
    val workspaceOnly: Boolean = true,
    val allowedCommands: String = DEFAULT_ALLOWED_COMMANDS,
    val forbiddenPaths: String = DEFAULT_FORBIDDEN_PATHS,
    val maxActionsPerHour: Int = DEFAULT_MAX_ACTIONS_PER_HOUR,
    val maxCostPerDayCents: Int = DEFAULT_MAX_COST_PER_DAY_CENTS,
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
    val gatewayRequirePairing: Boolean = false,
    val gatewayAllowPublicBind: Boolean = false,
    val gatewayPairedTokens: String = "",
    val gatewayPairRateLimit: Int = DEFAULT_PAIR_RATE_LIMIT,
    val gatewayWebhookRateLimit: Int = DEFAULT_WEBHOOK_RATE_LIMIT,
    val gatewayIdempotencyTtl: Int = DEFAULT_IDEMPOTENCY_TTL,
    val schedulerEnabled: Boolean = true,
    val schedulerMaxTasks: Int = DEFAULT_SCHEDULER_MAX_TASKS,
    val schedulerMaxConcurrent: Int = DEFAULT_SCHEDULER_MAX_CONCURRENT,
    val heartbeatEnabled: Boolean = false,
    val heartbeatIntervalMinutes: Int = DEFAULT_HEARTBEAT_INTERVAL,
    val observabilityBackend: String = "none",
    val observabilityOtelEndpoint: String = DEFAULT_OTEL_ENDPOINT,
    val observabilityOtelServiceName: String = DEFAULT_OTEL_SERVICE_NAME,
    val modelRoutesJson: String = "[]",
    val memoryHygieneEnabled: Boolean = true,
    val memoryArchiveAfterDays: Int = DEFAULT_ARCHIVE_DAYS,
    val memoryPurgeAfterDays: Int = DEFAULT_PURGE_DAYS,
    val memoryEmbeddingProvider: String = "none",
    val memoryEmbeddingModel: String = DEFAULT_EMBEDDING_MODEL,
    val memoryVectorWeight: Float = DEFAULT_VECTOR_WEIGHT,
    val memoryKeywordWeight: Float = DEFAULT_KEYWORD_WEIGHT,
    val composioEnabled: Boolean = false,
    val composioApiKey: String = "",
    val composioEntityId: String = "default",
    val browserEnabled: Boolean = false,
    val browserAllowedDomains: String = "",
    val httpRequestEnabled: Boolean = false,
    val httpRequestAllowedDomains: String = "",
    val biometricForService: Boolean = false,
    val biometricForSettings: Boolean = false,
    val pluginRegistryUrl: String = DEFAULT_PLUGIN_REGISTRY_URL,
    val pluginSyncEnabled: Boolean = false,
    val pluginSyncIntervalHours: Int = DEFAULT_PLUGIN_SYNC_INTERVAL,
    val lastPluginSyncTimestamp: Long = 0L,
) {
    /** Constants for [AppSettings]. */
    companion object {
        /** Default gateway bind address. */
        const val DEFAULT_HOST = "127.0.0.1"

        /** Default gateway bind port. */
        const val DEFAULT_PORT = 8080

        /** Default inference temperature. */
        const val DEFAULT_TEMPERATURE = 0.7f

        /** Default daily cost limit in USD. */
        const val DEFAULT_DAILY_LIMIT_USD = 10f

        /** Default monthly cost limit in USD. */
        const val DEFAULT_MONTHLY_LIMIT_USD = 100f

        /** Default percentage of cost limit at which to warn. */
        const val DEFAULT_COST_WARN_PERCENT = 80

        /** Default number of provider retries. */
        const val DEFAULT_PROVIDER_RETRIES = 2

        /** Default memory backend. */
        const val DEFAULT_MEMORY_BACKEND = "sqlite"

        /** Default autonomy level. */
        const val DEFAULT_AUTONOMY_LEVEL = "supervised"

        /** Default allowed shell commands (comma-separated). */
        const val DEFAULT_ALLOWED_COMMANDS =
            "git,npm,cargo,ls,cat,grep,find,echo,pwd,wc,head,tail"

        /** Default forbidden filesystem paths (comma-separated). */
        const val DEFAULT_FORBIDDEN_PATHS =
            "/etc,/root,~/.ssh,~/.gnupg,~/.aws,~/.config"

        /** Default max agent actions per hour. */
        const val DEFAULT_MAX_ACTIONS_PER_HOUR = 20

        /** Default max cost per day in cents. */
        const val DEFAULT_MAX_COST_PER_DAY_CENTS = 500

        /** Default gateway pairing rate limit per minute. */
        const val DEFAULT_PAIR_RATE_LIMIT = 10

        /** Default gateway webhook rate limit per minute. */
        const val DEFAULT_WEBHOOK_RATE_LIMIT = 60

        /** Default idempotency TTL in seconds. */
        const val DEFAULT_IDEMPOTENCY_TTL = 300

        /** Default scheduler max tasks. */
        const val DEFAULT_SCHEDULER_MAX_TASKS = 64

        /** Default scheduler max concurrent executions. */
        const val DEFAULT_SCHEDULER_MAX_CONCURRENT = 4

        /** Default heartbeat interval in minutes. */
        const val DEFAULT_HEARTBEAT_INTERVAL = 30

        /** Default OpenTelemetry endpoint. */
        const val DEFAULT_OTEL_ENDPOINT = "http://localhost:4318"

        /** Default OTel service name. */
        const val DEFAULT_OTEL_SERVICE_NAME = "zeroclaw"

        /** Default memory archive threshold in days. */
        const val DEFAULT_ARCHIVE_DAYS = 7

        /** Default memory purge threshold in days. */
        const val DEFAULT_PURGE_DAYS = 30

        /** Default embedding model name. */
        const val DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small"

        /** Default vector weight for memory recall. */
        const val DEFAULT_VECTOR_WEIGHT = 0.7f

        /** Default keyword weight for memory recall. */
        const val DEFAULT_KEYWORD_WEIGHT = 0.3f

        /** Default plugin registry URL. */
        const val DEFAULT_PLUGIN_REGISTRY_URL = "https://registry.zeroclaw.dev/plugins.json"

        /** Default plugin sync interval in hours. */
        const val DEFAULT_PLUGIN_SYNC_INTERVAL = 24
    }
}

/**
 * Log severity levels for daemon output filtering.
 */
enum class LogLevel {
    /** Verbose debug output. */
    DEBUG,

    /** Standard informational messages. */
    INFO,

    /** Warning conditions. */
    WARN,

    /** Error conditions. */
    ERROR,
}
