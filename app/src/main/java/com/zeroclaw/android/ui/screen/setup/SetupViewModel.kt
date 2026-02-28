/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.setup

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.service.AgentTomlEntry
import com.zeroclaw.android.service.ConfigTomlBuilder
import com.zeroclaw.android.service.DaemonServiceBridge
import com.zeroclaw.android.service.GlobalTomlConfig
import com.zeroclaw.android.service.HealthBridge
import com.zeroclaw.android.service.SetupOrchestrator
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Drives the daemon setup pipeline from onboarding or settings.
 *
 * Reads all configuration data (provider, API key, channels, identity) from
 * the application's repositories, builds a TOML config string, and delegates
 * the multi-step startup sequence to [SetupOrchestrator]. API key material
 * is held in [ByteArray] buffers that are zero-filled in a `finally` block
 * to limit the window during which secrets reside in heap memory.
 *
 * Exposes [progress] for the UI to render real-time step-by-step feedback.
 *
 * @param application Application context for accessing repositories and bridges.
 */
class SetupViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = application as ZeroClawApplication
    private val daemonBridge: DaemonServiceBridge = app.daemonBridge
    private val healthBridge: HealthBridge = app.healthBridge
    private val orchestrator = SetupOrchestrator(daemonBridge, healthBridge)

    /** Observable progress across all setup steps. */
    val progress: StateFlow<SetupProgress> = orchestrator.progress

    /**
     * Launches the full daemon setup pipeline.
     *
     * Reads provider, model, API key, channel, and identity configuration
     * from the application repositories, builds the TOML config, and runs
     * the orchestrator. All secret [ByteArray] buffers are zero-filled in
     * a `finally` block regardless of outcome.
     *
     * Safe to call from the main thread; repository reads and FFI calls
     * are dispatched internally to background dispatchers.
     *
     * @param onComplete Callback invoked after the pipeline finishes
     *   (whether it succeeded, failed, or was cancelled). Runs on the
     *   calling coroutine's dispatcher.
     */
    @Suppress("TooGenericExceptionCaught")
    fun startSetup(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val secretBuffers = mutableListOf<ByteArray>()
            try {
                runSetupPipeline(secretBuffers)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Setup pipeline failed", e)
            } finally {
                secretBuffers.forEach { it.fill(0) }
            }
            app.onboardingRepository.markComplete()
            onComplete()
        }
    }

    /**
     * Reads all configuration from repositories and drives the orchestrator.
     *
     * Separated from [startSetup] so the public entry point stays under the
     * detekt `LongMethod` threshold while keeping the secret-buffer lifecycle
     * (try/finally zero-fill) in the caller.
     *
     * @param secretBuffers Mutable list to which API key buffers are appended
     *   for post-setup cleanup.
     */
    private suspend fun runSetupPipeline(secretBuffers: MutableList<ByteArray>) {
        val settings = app.settingsRepository.settings.first()
        val effectiveSettings = resolveEffectiveDefaults(settings)

        val apiKey =
            app.apiKeyRepository
                .getByProviderFresh(effectiveSettings.defaultProvider)
        val apiKeyBytes =
            apiKey?.key?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        secretBuffers.add(apiKeyBytes)

        val configToml = buildConfigToml(effectiveSettings, apiKey, apiKeyBytes, secretBuffers)

        val enabledChannels =
            app.channelConfigRepository.channels
                .first()
                .filter { it.isEnabled }
                .map { it.type.tomlKey }

        val identityJson = effectiveSettings.identityJson
        val validPort =
            if (settings.port in VALID_PORT_RANGE) {
                settings.port
            } else {
                AppSettings.DEFAULT_PORT
            }

        orchestrator.runFullSetup(
            context = app,
            configToml = configToml,
            agentName = extractFromIdentity(identityJson, IDENTITY_KEY_AGENT_NAME),
            userName = extractFromIdentity(identityJson, IDENTITY_KEY_USER_NAME),
            timezone = extractFromIdentity(identityJson, IDENTITY_KEY_TIMEZONE),
            communicationStyle = extractFromIdentity(identityJson, IDENTITY_KEY_COMMUNICATION_STYLE),
            expectedChannels = enabledChannels,
            port = validPort.toUShort(),
        )
    }

    /**
     * Builds the complete TOML configuration string from settings and keys.
     *
     * Combines the global config, channel sections, and per-agent sections
     * into a single TOML document.
     *
     * @param settings Effective application settings with resolved defaults.
     * @param apiKey Default provider API key, or null.
     * @param apiKeyBytes Decrypted API key as a [ByteArray] for secure handling.
     * @param secretBuffers Mutable list for agent key buffer cleanup tracking.
     * @return Complete TOML configuration string.
     */
    private suspend fun buildConfigToml(
        settings: AppSettings,
        apiKey: ApiKey?,
        apiKeyBytes: ByteArray,
        secretBuffers: MutableList<ByteArray>,
    ): String {
        val globalConfig =
            buildGlobalTomlConfig(
                settings,
                apiKey,
                String(apiKeyBytes, Charsets.UTF_8),
            )
        val baseToml = ConfigTomlBuilder.build(globalConfig)
        val channelsToml =
            ConfigTomlBuilder.buildChannelsToml(
                app.channelConfigRepository.getEnabledWithSecrets(),
            )
        val agentsToml = buildAgentsToml(secretBuffers)
        return baseToml + channelsToml + agentsToml
    }

    /**
     * Derives effective default provider and model from the agent list.
     *
     * The first enabled agent with a non-blank provider and model name
     * overrides the DataStore values in [settings]. This mirrors the
     * resolution logic in
     * [ZeroClawDaemonService][com.zeroclaw.android.service.ZeroClawDaemonService].
     *
     * @param settings Current application settings (may have stale defaults).
     * @return A copy of [settings] with provider and model overridden by the
     *   primary agent, or unchanged if no qualifying agent exists.
     */
    private suspend fun resolveEffectiveDefaults(settings: AppSettings): AppSettings {
        val agents = app.agentRepository.agents.first()
        val primary =
            agents.firstOrNull {
                it.isEnabled && it.provider.isNotBlank() && it.modelName.isNotBlank()
            } ?: return settings
        return settings.copy(
            defaultProvider = primary.provider,
            defaultModel = primary.modelName,
        )
    }

    /**
     * Converts [AppSettings] and resolved API key into a [GlobalTomlConfig].
     *
     * Comma-separated string fields in [AppSettings] are split into lists
     * for [GlobalTomlConfig] properties that expect `List<String>`. Mirrors
     * the logic in [ZeroClawDaemonService][com.zeroclaw.android.service.ZeroClawDaemonService].
     *
     * @param settings Current application settings.
     * @param apiKey Resolved API key for the default provider, or null.
     * @param apiKeyValue Decrypted API key string from the secure buffer.
     * @return A fully populated [GlobalTomlConfig].
     */
    @Suppress("LongMethod")
    private fun buildGlobalTomlConfig(
        settings: AppSettings,
        apiKey: ApiKey?,
        apiKeyValue: String,
    ): GlobalTomlConfig =
        GlobalTomlConfig(
            provider = settings.defaultProvider,
            model = settings.defaultModel,
            apiKey = apiKeyValue,
            baseUrl = apiKey?.baseUrl.orEmpty(),
            temperature = settings.defaultTemperature,
            compactContext = settings.compactContext,
            costEnabled = settings.costEnabled,
            dailyLimitUsd = settings.dailyLimitUsd,
            monthlyLimitUsd = settings.monthlyLimitUsd,
            costWarnAtPercent = settings.costWarnAtPercent,
            providerRetries = settings.providerRetries,
            fallbackProviders = splitCsv(settings.fallbackProviders),
            memoryBackend = settings.memoryBackend,
            memoryAutoSave = settings.memoryAutoSave,
            identityJson = settings.identityJson,
            autonomyLevel = settings.autonomyLevel,
            workspaceOnly = settings.workspaceOnly,
            allowedCommands = splitCsv(settings.allowedCommands),
            forbiddenPaths = splitCsv(settings.forbiddenPaths),
            maxActionsPerHour = settings.maxActionsPerHour,
            maxCostPerDayCents = settings.maxCostPerDayCents,
            requireApprovalMediumRisk = settings.requireApprovalMediumRisk,
            blockHighRiskCommands = settings.blockHighRiskCommands,
            tunnelProvider = settings.tunnelProvider,
            tunnelCloudflareToken = settings.tunnelCloudflareToken,
            tunnelTailscaleFunnel = settings.tunnelTailscaleFunnel,
            tunnelTailscaleHostname = settings.tunnelTailscaleHostname,
            tunnelNgrokAuthToken = settings.tunnelNgrokAuthToken,
            tunnelNgrokDomain = settings.tunnelNgrokDomain,
            tunnelCustomCommand = settings.tunnelCustomCommand,
            tunnelCustomHealthUrl = settings.tunnelCustomHealthUrl,
            tunnelCustomUrlPattern = settings.tunnelCustomUrlPattern,
            gatewayHost = settings.host,
            gatewayPort = settings.port,
            gatewayRequirePairing = settings.gatewayRequirePairing,
            gatewayAllowPublicBind = settings.gatewayAllowPublicBind,
            gatewayPairedTokens = splitCsv(settings.gatewayPairedTokens),
            gatewayPairRateLimit = settings.gatewayPairRateLimit,
            gatewayWebhookRateLimit = settings.gatewayWebhookRateLimit,
            gatewayIdempotencyTtl = settings.gatewayIdempotencyTtl,
            schedulerEnabled = settings.schedulerEnabled,
            schedulerMaxTasks = settings.schedulerMaxTasks,
            schedulerMaxConcurrent = settings.schedulerMaxConcurrent,
            heartbeatEnabled = settings.heartbeatEnabled,
            heartbeatIntervalMinutes = settings.heartbeatIntervalMinutes,
            observabilityBackend = settings.observabilityBackend,
            observabilityOtelEndpoint = settings.observabilityOtelEndpoint,
            observabilityOtelServiceName = settings.observabilityOtelServiceName,
            modelRoutesJson = settings.modelRoutesJson,
            memoryHygieneEnabled = settings.memoryHygieneEnabled,
            memoryArchiveAfterDays = settings.memoryArchiveAfterDays,
            memoryPurgeAfterDays = settings.memoryPurgeAfterDays,
            memoryEmbeddingProvider = settings.memoryEmbeddingProvider,
            memoryEmbeddingModel = settings.memoryEmbeddingModel,
            memoryVectorWeight = settings.memoryVectorWeight,
            memoryKeywordWeight = settings.memoryKeywordWeight,
            composioEnabled = settings.composioEnabled,
            composioApiKey = settings.composioApiKey,
            composioEntityId = settings.composioEntityId,
            browserEnabled = settings.browserEnabled,
            browserAllowedDomains = splitCsv(settings.browserAllowedDomains),
            httpRequestEnabled = settings.httpRequestEnabled,
            httpRequestAllowedDomains = splitCsv(settings.httpRequestAllowedDomains),
            webFetchEnabled = settings.webFetchEnabled,
            webFetchAllowedDomains = splitCsv(settings.webFetchAllowedDomains),
            webFetchBlockedDomains = splitCsv(settings.webFetchBlockedDomains),
            webFetchMaxResponseSize = settings.webFetchMaxResponseSize,
            webFetchTimeoutSecs = settings.webFetchTimeoutSecs,
            webSearchEnabled = settings.webSearchEnabled,
            webSearchProvider = settings.webSearchProvider,
            webSearchBraveApiKey = settings.webSearchBraveApiKey,
            webSearchMaxResults = settings.webSearchMaxResults,
            webSearchTimeoutSecs = settings.webSearchTimeoutSecs,
            securitySandboxEnabled = settings.securitySandboxEnabled,
            securitySandboxBackend = settings.securitySandboxBackend,
            securitySandboxFirejailArgs = splitCsv(settings.securitySandboxFirejailArgs),
            securityResourcesMaxMemoryMb = settings.securityResourcesMaxMemoryMb,
            securityResourcesMaxCpuTimeSecs = settings.securityResourcesMaxCpuTimeSecs,
            securityResourcesMaxSubprocesses = settings.securityResourcesMaxSubprocesses,
            securityResourcesMemoryMonitoring = settings.securityResourcesMemoryMonitoring,
            securityAuditEnabled = settings.securityAuditEnabled,
            securityOtpEnabled = settings.securityOtpEnabled,
            securityOtpMethod = settings.securityOtpMethod,
            securityOtpTokenTtlSecs = settings.securityOtpTokenTtlSecs,
            securityOtpCacheValidSecs = settings.securityOtpCacheValidSecs,
            securityOtpGatedActions = splitCsv(settings.securityOtpGatedActions),
            securityOtpGatedDomains = splitCsv(settings.securityOtpGatedDomains),
            securityOtpGatedDomainCategories =
                splitCsv(
                    settings.securityOtpGatedDomainCategories,
                ),
            securityEstopEnabled = settings.securityEstopEnabled,
            securityEstopRequireOtpToResume = settings.securityEstopRequireOtpToResume,
            memoryQdrantUrl = settings.memoryQdrantUrl,
            memoryQdrantCollection = settings.memoryQdrantCollection,
            memoryQdrantApiKey = settings.memoryQdrantApiKey,
            embeddingRoutesJson = settings.embeddingRoutesJson,
            queryClassificationEnabled = settings.queryClassificationEnabled,
            proxyEnabled = settings.proxyEnabled,
            proxyHttpProxy = settings.proxyHttpProxy,
            proxyHttpsProxy = settings.proxyHttpsProxy,
            proxyAllProxy = settings.proxyAllProxy,
            proxyNoProxy = splitCsv(settings.proxyNoProxy),
            proxyScope = settings.proxyScope,
            proxyServiceSelectors = splitCsv(settings.proxyServiceSelectors),
            reliabilityBackoffMs = settings.reliabilityBackoffMs,
            reliabilityApiKeysJson = settings.reliabilityApiKeysJson,
        )

    /**
     * Resolves all enabled agents into [AgentTomlEntry] instances and builds
     * the `[agents.<name>]` TOML sections.
     *
     * Each agent's API key is fetched and added to [secretBuffers] for
     * zero-fill cleanup. Agents without a provider or model are skipped.
     *
     * @param secretBuffers Mutable list to which agent API key buffers are
     *   appended for post-setup cleanup.
     * @return TOML string with per-agent sections, or empty if no agents qualify.
     */
    private suspend fun buildAgentsToml(secretBuffers: MutableList<ByteArray>): String {
        val allAgents = app.agentRepository.agents.first()
        val entries =
            allAgents
                .filter { it.isEnabled && it.provider.isNotBlank() && it.modelName.isNotBlank() }
                .map { agent ->
                    val agentKey = app.apiKeyRepository.getByProviderFresh(agent.provider)
                    val keyBytes =
                        agentKey
                            ?.key
                            ?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                    secretBuffers.add(keyBytes)
                    AgentTomlEntry(
                        name = agent.name,
                        provider =
                            ConfigTomlBuilder.resolveProvider(
                                agent.provider,
                                agentKey?.baseUrl.orEmpty(),
                            ),
                        model = agent.modelName,
                        apiKey = String(keyBytes, Charsets.UTF_8),
                        systemPrompt = agent.systemPrompt,
                        temperature = agent.temperature,
                        maxDepth = agent.maxDepth,
                    )
                }
        return ConfigTomlBuilder.buildAgentsToml(entries)
    }

    /**
     * Extracts a value from the AIEOS identity JSON blob.
     *
     * The JSON structure is nested: `{"identity": {"names": {"first": "..."}, "user_name": "...", ...}}`.
     * This helper navigates into the `identity` object to find the requested key.
     * Agent name is special-cased because it lives under `identity.names.first`.
     *
     * @param json Full AIEOS identity JSON string.
     * @param key Logical key to extract (one of the `IDENTITY_KEY_*` constants).
     * @return The extracted string, or empty string on any parse failure.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun extractFromIdentity(
        json: String,
        key: String,
    ): String {
        if (json.isBlank()) return ""
        return try {
            val identity = JSONObject(json).optJSONObject("identity") ?: return ""
            if (key == IDENTITY_KEY_AGENT_NAME) {
                identity.optJSONObject("names")?.optString("first", "") ?: ""
            } else {
                identity.optString(key, "")
            }
        } catch (_: Exception) {
            ""
        }
    }

    /** Constants for [SetupViewModel]. */
    companion object {
        private const val TAG = "SetupViewModel"
        private const val IDENTITY_KEY_AGENT_NAME = "agentName"
        private const val IDENTITY_KEY_USER_NAME = "user_name"
        private const val IDENTITY_KEY_TIMEZONE = "timezone"
        private const val IDENTITY_KEY_COMMUNICATION_STYLE = "communication_style"
        private val VALID_PORT_RANGE = 1..65535
    }
}

/**
 * Splits a comma-separated string into a trimmed, non-blank list.
 *
 * @param csv Comma-separated string (may be blank).
 * @return List of trimmed non-blank tokens; empty list if [csv] is blank.
 */
private fun splitCsv(csv: String): List<String> = csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
