/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.model.LogLevel
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for reading and writing application settings.
 *
 * Implementations must provide a [Flow] of [AppSettings] that emits
 * the current settings and any subsequent changes.
 */
@Suppress("TooManyFunctions")
interface SettingsRepository {
    /** Observable stream of the current application settings. */
    val settings: Flow<AppSettings>

    /**
     * Updates the gateway host address.
     *
     * @param host New bind address.
     */
    suspend fun setHost(host: String)

    /**
     * Updates the gateway port.
     *
     * @param port New bind port.
     */
    suspend fun setPort(port: Int)

    /**
     * Toggles the auto-start on boot setting.
     *
     * @param enabled Whether to start the daemon on boot.
     */
    suspend fun setAutoStartOnBoot(enabled: Boolean)

    /**
     * Updates the minimum log level.
     *
     * @param level New minimum severity.
     */
    suspend fun setLogLevel(level: LogLevel)

    /**
     * Updates the default provider for new agents.
     *
     * @param provider Provider ID (e.g. "openai", "anthropic").
     */
    suspend fun setDefaultProvider(provider: String)

    /**
     * Updates the default model for new agents.
     *
     * @param model Model name (e.g. "gpt-4o").
     */
    suspend fun setDefaultModel(model: String)

    /**
     * Updates the default inference temperature.
     *
     * @param temperature Temperature value (0.0–2.0).
     */
    suspend fun setDefaultTemperature(temperature: Float)

    /**
     * Toggles compact context mode.
     *
     * @param enabled Whether compact context is active.
     */
    suspend fun setCompactContext(enabled: Boolean)

    /**
     * Toggles cost limit enforcement.
     *
     * @param enabled Whether spending limits are enforced.
     */
    suspend fun setCostEnabled(enabled: Boolean)

    /**
     * Updates the daily cost limit.
     *
     * @param limit Maximum daily spend in USD.
     */
    suspend fun setDailyLimitUsd(limit: Float)

    /**
     * Updates the monthly cost limit.
     *
     * @param limit Maximum monthly spend in USD.
     */
    suspend fun setMonthlyLimitUsd(limit: Float)

    /**
     * Updates the cost warning threshold percentage.
     *
     * @param percent Percentage of limit at which to warn.
     */
    suspend fun setCostWarnAtPercent(percent: Int)

    /**
     * Updates the number of provider retries before fallback.
     *
     * @param retries Retry count.
     */
    suspend fun setProviderRetries(retries: Int)

    /**
     * Updates the comma-separated list of fallback providers.
     *
     * @param providers Comma-separated provider IDs.
     */
    suspend fun setFallbackProviders(providers: String)

    /**
     * Updates the memory backend.
     *
     * @param backend Backend name ("sqlite", "none", "markdown", "lucid").
     */
    suspend fun setMemoryBackend(backend: String)

    /**
     * Toggles the memory auto-save setting.
     *
     * @param enabled Whether the memory backend auto-saves conversation context.
     */
    suspend fun setMemoryAutoSave(enabled: Boolean)

    /**
     * Updates the AIEOS identity JSON blob.
     *
     * @param json AIEOS v1.1 JSON string.
     */
    suspend fun setIdentityJson(json: String)

    /**
     * Updates the autonomy level.
     *
     * @param level One of "readonly", "supervised", or "full".
     */
    suspend fun setAutonomyLevel(level: String)

    /**
     * Toggles workspace-only restriction.
     *
     * @param enabled Whether file access is restricted to the workspace.
     */
    suspend fun setWorkspaceOnly(enabled: Boolean)

    /**
     * Updates the allowed shell commands list.
     *
     * @param commands Comma-separated command names.
     */
    suspend fun setAllowedCommands(commands: String)

    /**
     * Updates the forbidden filesystem paths list.
     *
     * @param paths Comma-separated paths.
     */
    suspend fun setForbiddenPaths(paths: String)

    /**
     * Updates the maximum agent actions per hour.
     *
     * @param max Actions per hour limit.
     */
    suspend fun setMaxActionsPerHour(max: Int)

    /**
     * Updates the maximum daily cost in cents.
     *
     * @param cents Daily cost cap.
     */
    suspend fun setMaxCostPerDayCents(cents: Int)

    /**
     * Toggles approval requirement for medium-risk actions.
     *
     * @param required Whether approval is needed.
     */
    suspend fun setRequireApprovalMediumRisk(required: Boolean)

    /**
     * Toggles blocking of high-risk commands.
     *
     * @param blocked Whether high-risk commands are blocked.
     */
    suspend fun setBlockHighRiskCommands(blocked: Boolean)

    /**
     * Updates the tunnel provider.
     *
     * @param provider One of "none", "cloudflare", "tailscale", "ngrok", "custom".
     */
    suspend fun setTunnelProvider(provider: String)

    /**
     * Updates the Cloudflare tunnel token.
     *
     * @param token Authentication token.
     */
    suspend fun setTunnelCloudflareToken(token: String)

    /**
     * Toggles Tailscale Funnel.
     *
     * @param enabled Whether Funnel is active.
     */
    suspend fun setTunnelTailscaleFunnel(enabled: Boolean)

    /**
     * Updates the Tailscale hostname.
     *
     * @param hostname Custom hostname.
     */
    suspend fun setTunnelTailscaleHostname(hostname: String)

    /**
     * Updates the ngrok auth token.
     *
     * @param token Authentication token.
     */
    suspend fun setTunnelNgrokAuthToken(token: String)

    /**
     * Updates the ngrok domain.
     *
     * @param domain Custom domain.
     */
    suspend fun setTunnelNgrokDomain(domain: String)

    /**
     * Updates the custom tunnel start command.
     *
     * @param command Shell command to start the tunnel.
     */
    suspend fun setTunnelCustomCommand(command: String)

    /**
     * Updates the custom tunnel health URL.
     *
     * @param url Health check endpoint.
     */
    suspend fun setTunnelCustomHealthUrl(url: String)

    /**
     * Updates the custom tunnel URL pattern.
     *
     * @param pattern URL extraction regex.
     */
    suspend fun setTunnelCustomUrlPattern(pattern: String)

    /**
     * Toggles gateway pairing requirement.
     *
     * @param required Whether pairing tokens are enforced.
     */
    suspend fun setGatewayRequirePairing(required: Boolean)

    /**
     * Toggles public bind on the gateway.
     *
     * @param allowed Whether 0.0.0.0 binding is permitted.
     */
    suspend fun setGatewayAllowPublicBind(allowed: Boolean)

    /**
     * Updates the list of authorized pairing tokens.
     *
     * @param tokens Comma-separated token strings.
     */
    suspend fun setGatewayPairedTokens(tokens: String)

    /**
     * Updates the pairing rate limit.
     *
     * @param limit Requests per minute.
     */
    suspend fun setGatewayPairRateLimit(limit: Int)

    /**
     * Updates the webhook rate limit.
     *
     * @param limit Requests per minute.
     */
    suspend fun setGatewayWebhookRateLimit(limit: Int)

    /**
     * Updates the idempotency TTL.
     *
     * @param seconds TTL in seconds.
     */
    suspend fun setGatewayIdempotencyTtl(seconds: Int)

    /**
     * Toggles the task scheduler.
     *
     * @param enabled Whether the scheduler is active.
     */
    suspend fun setSchedulerEnabled(enabled: Boolean)

    /**
     * Updates the scheduler max tasks.
     *
     * @param max Maximum number of scheduled tasks.
     */
    suspend fun setSchedulerMaxTasks(max: Int)

    /**
     * Updates the scheduler max concurrent executions.
     *
     * @param max Maximum concurrent task count.
     */
    suspend fun setSchedulerMaxConcurrent(max: Int)

    /**
     * Toggles the heartbeat engine.
     *
     * @param enabled Whether heartbeat is active.
     */
    suspend fun setHeartbeatEnabled(enabled: Boolean)

    /**
     * Updates the heartbeat interval.
     *
     * @param minutes Interval in minutes.
     */
    suspend fun setHeartbeatIntervalMinutes(minutes: Int)

    /**
     * Updates the observability backend.
     *
     * @param backend One of "none", "log", "otel".
     */
    suspend fun setObservabilityBackend(backend: String)

    /**
     * Updates the OTel collector endpoint.
     *
     * @param endpoint URL string.
     */
    suspend fun setObservabilityOtelEndpoint(endpoint: String)

    /**
     * Updates the OTel service name.
     *
     * @param name Service identifier.
     */
    suspend fun setObservabilityOtelServiceName(name: String)

    /**
     * Updates the model routes JSON.
     *
     * @param json JSON array of route objects.
     */
    suspend fun setModelRoutesJson(json: String)

    /**
     * Toggles memory hygiene (archival and purge).
     *
     * @param enabled Whether hygiene is active.
     */
    suspend fun setMemoryHygieneEnabled(enabled: Boolean)

    /**
     * Updates the memory archive threshold.
     *
     * @param days Days before entries are archived.
     */
    suspend fun setMemoryArchiveAfterDays(days: Int)

    /**
     * Updates the memory purge threshold.
     *
     * @param days Days before archived entries are purged.
     */
    suspend fun setMemoryPurgeAfterDays(days: Int)

    /**
     * Updates the embedding provider.
     *
     * @param provider One of "none", "openai", or "custom:URL".
     */
    suspend fun setMemoryEmbeddingProvider(provider: String)

    /**
     * Updates the embedding model name.
     *
     * @param model Model identifier.
     */
    suspend fun setMemoryEmbeddingModel(model: String)

    /**
     * Updates the vector similarity weight for recall.
     *
     * @param weight Weight value (0.0–1.0).
     */
    suspend fun setMemoryVectorWeight(weight: Float)

    /**
     * Updates the keyword matching weight for recall.
     *
     * @param weight Weight value (0.0–1.0).
     */
    suspend fun setMemoryKeywordWeight(weight: Float)

    /**
     * Toggles Composio tool integration.
     *
     * @param enabled Whether Composio is active.
     */
    suspend fun setComposioEnabled(enabled: Boolean)

    /**
     * Updates the Composio API key.
     *
     * @param key API key string.
     */
    suspend fun setComposioApiKey(key: String)

    /**
     * Updates the Composio entity ID.
     *
     * @param entityId Entity identifier.
     */
    suspend fun setComposioEntityId(entityId: String)

    /**
     * Toggles the browser tool.
     *
     * @param enabled Whether the browser tool is active.
     */
    suspend fun setBrowserEnabled(enabled: Boolean)

    /**
     * Updates the browser allowed domains list.
     *
     * @param domains Comma-separated domain names.
     */
    suspend fun setBrowserAllowedDomains(domains: String)

    /**
     * Toggles the HTTP request tool.
     *
     * @param enabled Whether the HTTP request tool is active.
     */
    suspend fun setHttpRequestEnabled(enabled: Boolean)

    /**
     * Updates the HTTP request allowed domains list.
     *
     * @param domains Comma-separated domain names.
     */
    suspend fun setHttpRequestAllowedDomains(domains: String)
}
