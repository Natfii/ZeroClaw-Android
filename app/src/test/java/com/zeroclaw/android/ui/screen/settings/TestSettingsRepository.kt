/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings

import com.zeroclaw.android.data.repository.SettingsRepository
import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [SettingsRepository] for unit tests.
 */
internal class TestSettingsRepository : SettingsRepository {
    private val _settings = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = _settings

    override suspend fun setHost(host: String) = _settings.update { it.copy(host = host) }

    override suspend fun setPort(port: Int) = _settings.update { it.copy(port = port) }

    override suspend fun setAutoStartOnBoot(enabled: Boolean) = _settings.update { it.copy(autoStartOnBoot = enabled) }

    override suspend fun setDefaultProvider(provider: String) = _settings.update { it.copy(defaultProvider = provider) }

    override suspend fun setDefaultModel(model: String) = _settings.update { it.copy(defaultModel = model) }

    override suspend fun setDefaultTemperature(temperature: Float) = _settings.update { it.copy(defaultTemperature = temperature) }

    override suspend fun setCompactContext(enabled: Boolean) = _settings.update { it.copy(compactContext = enabled) }

    override suspend fun setCostEnabled(enabled: Boolean) = _settings.update { it.copy(costEnabled = enabled) }

    override suspend fun setDailyLimitUsd(limit: Float) = _settings.update { it.copy(dailyLimitUsd = limit) }

    override suspend fun setMonthlyLimitUsd(limit: Float) = _settings.update { it.copy(monthlyLimitUsd = limit) }

    override suspend fun setCostWarnAtPercent(percent: Int) = _settings.update { it.copy(costWarnAtPercent = percent) }

    override suspend fun setProviderRetries(retries: Int) = _settings.update { it.copy(providerRetries = retries) }

    override suspend fun setFallbackProviders(providers: String) = _settings.update { it.copy(fallbackProviders = providers) }

    override suspend fun setMemoryBackend(backend: String) = _settings.update { it.copy(memoryBackend = backend) }

    override suspend fun setMemoryAutoSave(enabled: Boolean) = _settings.update { it.copy(memoryAutoSave = enabled) }

    override suspend fun setIdentityJson(json: String) = _settings.update { it.copy(identityJson = json) }

    override suspend fun setAutonomyLevel(level: String) = _settings.update { it.copy(autonomyLevel = level) }

    override suspend fun setWorkspaceOnly(enabled: Boolean) = _settings.update { it.copy(workspaceOnly = enabled) }

    override suspend fun setAllowedCommands(commands: String) = _settings.update { it.copy(allowedCommands = commands) }

    override suspend fun setForbiddenPaths(paths: String) = _settings.update { it.copy(forbiddenPaths = paths) }

    override suspend fun setMaxActionsPerHour(max: Int) = _settings.update { it.copy(maxActionsPerHour = max) }

    override suspend fun setMaxCostPerDayCents(cents: Int) = _settings.update { it.copy(maxCostPerDayCents = cents) }

    override suspend fun setRequireApprovalMediumRisk(required: Boolean) = _settings.update { it.copy(requireApprovalMediumRisk = required) }

    override suspend fun setBlockHighRiskCommands(blocked: Boolean) = _settings.update { it.copy(blockHighRiskCommands = blocked) }

    override suspend fun setTunnelProvider(provider: String) = _settings.update { it.copy(tunnelProvider = provider) }

    override suspend fun setTunnelCloudflareToken(token: String) = _settings.update { it.copy(tunnelCloudflareToken = token) }

    override suspend fun setTunnelTailscaleFunnel(enabled: Boolean) = _settings.update { it.copy(tunnelTailscaleFunnel = enabled) }

    override suspend fun setTunnelTailscaleHostname(hostname: String) = _settings.update { it.copy(tunnelTailscaleHostname = hostname) }

    override suspend fun setTunnelNgrokAuthToken(token: String) = _settings.update { it.copy(tunnelNgrokAuthToken = token) }

    override suspend fun setTunnelNgrokDomain(domain: String) = _settings.update { it.copy(tunnelNgrokDomain = domain) }

    override suspend fun setTunnelCustomCommand(command: String) = _settings.update { it.copy(tunnelCustomCommand = command) }

    override suspend fun setTunnelCustomHealthUrl(url: String) = _settings.update { it.copy(tunnelCustomHealthUrl = url) }

    override suspend fun setTunnelCustomUrlPattern(pattern: String) = _settings.update { it.copy(tunnelCustomUrlPattern = pattern) }

    override suspend fun setGatewayRequirePairing(required: Boolean) = _settings.update { it.copy(gatewayRequirePairing = required) }

    override suspend fun setGatewayAllowPublicBind(allowed: Boolean) = _settings.update { it.copy(gatewayAllowPublicBind = allowed) }

    override suspend fun setGatewayPairedTokens(tokens: String) = _settings.update { it.copy(gatewayPairedTokens = tokens) }

    override suspend fun setGatewayPairRateLimit(limit: Int) = _settings.update { it.copy(gatewayPairRateLimit = limit) }

    override suspend fun setGatewayWebhookRateLimit(limit: Int) = _settings.update { it.copy(gatewayWebhookRateLimit = limit) }

    override suspend fun setGatewayIdempotencyTtl(seconds: Int) = _settings.update { it.copy(gatewayIdempotencyTtl = seconds) }

    override suspend fun setSchedulerEnabled(enabled: Boolean) = _settings.update { it.copy(schedulerEnabled = enabled) }

    override suspend fun setSchedulerMaxTasks(max: Int) = _settings.update { it.copy(schedulerMaxTasks = max) }

    override suspend fun setSchedulerMaxConcurrent(max: Int) = _settings.update { it.copy(schedulerMaxConcurrent = max) }

    override suspend fun setHeartbeatEnabled(enabled: Boolean) = _settings.update { it.copy(heartbeatEnabled = enabled) }

    override suspend fun setHeartbeatIntervalMinutes(minutes: Int) = _settings.update { it.copy(heartbeatIntervalMinutes = minutes) }

    override suspend fun setObservabilityBackend(backend: String) = _settings.update { it.copy(observabilityBackend = backend) }

    override suspend fun setObservabilityOtelEndpoint(endpoint: String) = _settings.update { it.copy(observabilityOtelEndpoint = endpoint) }

    override suspend fun setObservabilityOtelServiceName(name: String) = _settings.update { it.copy(observabilityOtelServiceName = name) }

    override suspend fun setModelRoutesJson(json: String) = _settings.update { it.copy(modelRoutesJson = json) }

    override suspend fun setMemoryHygieneEnabled(enabled: Boolean) = _settings.update { it.copy(memoryHygieneEnabled = enabled) }

    override suspend fun setMemoryArchiveAfterDays(days: Int) = _settings.update { it.copy(memoryArchiveAfterDays = days) }

    override suspend fun setMemoryPurgeAfterDays(days: Int) = _settings.update { it.copy(memoryPurgeAfterDays = days) }

    override suspend fun setMemoryEmbeddingProvider(provider: String) = _settings.update { it.copy(memoryEmbeddingProvider = provider) }

    override suspend fun setMemoryEmbeddingModel(model: String) = _settings.update { it.copy(memoryEmbeddingModel = model) }

    override suspend fun setMemoryVectorWeight(weight: Float) = _settings.update { it.copy(memoryVectorWeight = weight) }

    override suspend fun setMemoryKeywordWeight(weight: Float) = _settings.update { it.copy(memoryKeywordWeight = weight) }

    override suspend fun setComposioEnabled(enabled: Boolean) = _settings.update { it.copy(composioEnabled = enabled) }

    override suspend fun setComposioApiKey(key: String) = _settings.update { it.copy(composioApiKey = key) }

    override suspend fun setComposioEntityId(entityId: String) = _settings.update { it.copy(composioEntityId = entityId) }

    override suspend fun setBrowserEnabled(enabled: Boolean) = _settings.update { it.copy(browserEnabled = enabled) }

    override suspend fun setBrowserAllowedDomains(domains: String) = _settings.update { it.copy(browserAllowedDomains = domains) }

    override suspend fun setHttpRequestEnabled(enabled: Boolean) = _settings.update { it.copy(httpRequestEnabled = enabled) }

    override suspend fun setHttpRequestAllowedDomains(domains: String) = _settings.update { it.copy(httpRequestAllowedDomains = domains) }

    override suspend fun setLockEnabled(enabled: Boolean) = _settings.update { it.copy(lockEnabled = enabled) }

    override suspend fun setLockTimeoutMinutes(minutes: Int) = _settings.update { it.copy(lockTimeoutMinutes = minutes) }

    override suspend fun setPinHash(hash: String) = _settings.update { it.copy(pinHash = hash) }

    override suspend fun setBiometricUnlockEnabled(enabled: Boolean) = _settings.update { it.copy(biometricUnlockEnabled = enabled) }

    override suspend fun setPluginRegistryUrl(url: String) = _settings.update { it.copy(pluginRegistryUrl = url) }

    override suspend fun setPluginSyncEnabled(enabled: Boolean) = _settings.update { it.copy(pluginSyncEnabled = enabled) }

    override suspend fun setPluginSyncIntervalHours(hours: Int) = _settings.update { it.copy(pluginSyncIntervalHours = hours) }

    override suspend fun setLastPluginSyncTimestamp(timestamp: Long) = _settings.update { it.copy(lastPluginSyncTimestamp = timestamp) }

    override suspend fun setStripThinkingTags(enabled: Boolean) = _settings.update { it.copy(stripThinkingTags = enabled) }

    override suspend fun setTheme(theme: ThemeMode) = _settings.update { it.copy(theme = theme) }
}
