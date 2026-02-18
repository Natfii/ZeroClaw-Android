/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings

import com.zeroclaw.android.data.repository.SettingsRepository
import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.model.LogLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SettingsRepository] contract validation.
 *
 * Tests verify that the repository correctly stores defaults, applies
 * individual updates, and validates port input.
 */
@DisplayName("SettingsViewModel")
class SettingsViewModelTest {
    @Test
    @DisplayName("default values are correct")
    fun `default values are correct`() =
        runTest {
            val repo = TestSettingsRepository()
            val settings = repo.settings.first()
            assertEquals(AppSettings.DEFAULT_HOST, settings.host)
            assertEquals(AppSettings.DEFAULT_PORT, settings.port)
            assertEquals(false, settings.autoStartOnBoot)
            assertEquals(LogLevel.INFO, settings.logLevel)
        }

    @Test
    @DisplayName("toggle auto-start updates repository")
    fun `toggle auto-start updates repository`() =
        runTest {
            val repo = TestSettingsRepository()
            repo.setAutoStartOnBoot(true)
            assertEquals(true, repo.settings.first().autoStartOnBoot)
            repo.setAutoStartOnBoot(false)
            assertEquals(false, repo.settings.first().autoStartOnBoot)
        }

    @Test
    @DisplayName("log level update round-trips correctly")
    fun `log level update round-trips correctly`() =
        runTest {
            val repo = TestSettingsRepository()
            LogLevel.entries.forEach { level ->
                repo.setLogLevel(level)
                assertEquals(level, repo.settings.first().logLevel)
            }
        }
}

/**
 * In-memory [SettingsRepository] for ViewModel tests.
 */
private class TestSettingsRepository : SettingsRepository {
    private val _settings = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = _settings

    override suspend fun setHost(host: String) {
        _settings.update { it.copy(host = host) }
    }

    override suspend fun setPort(port: Int) {
        _settings.update { it.copy(port = port) }
    }

    override suspend fun setAutoStartOnBoot(enabled: Boolean) {
        _settings.update { it.copy(autoStartOnBoot = enabled) }
    }

    override suspend fun setLogLevel(level: LogLevel) {
        _settings.update { it.copy(logLevel = level) }
    }

    override suspend fun setDefaultProvider(provider: String) {
        _settings.update { it.copy(defaultProvider = provider) }
    }

    override suspend fun setDefaultModel(model: String) {
        _settings.update { it.copy(defaultModel = model) }
    }

    override suspend fun setDefaultTemperature(temperature: Float) {
        _settings.update { it.copy(defaultTemperature = temperature) }
    }

    override suspend fun setCompactContext(enabled: Boolean) {
        _settings.update { it.copy(compactContext = enabled) }
    }

    override suspend fun setCostEnabled(enabled: Boolean) {
        _settings.update { it.copy(costEnabled = enabled) }
    }

    override suspend fun setDailyLimitUsd(limit: Float) {
        _settings.update { it.copy(dailyLimitUsd = limit) }
    }

    override suspend fun setMonthlyLimitUsd(limit: Float) {
        _settings.update { it.copy(monthlyLimitUsd = limit) }
    }

    override suspend fun setCostWarnAtPercent(percent: Int) {
        _settings.update { it.copy(costWarnAtPercent = percent) }
    }

    override suspend fun setProviderRetries(retries: Int) {
        _settings.update { it.copy(providerRetries = retries) }
    }

    override suspend fun setFallbackProviders(providers: String) {
        _settings.update { it.copy(fallbackProviders = providers) }
    }

    override suspend fun setMemoryBackend(backend: String) {
        _settings.update { it.copy(memoryBackend = backend) }
    }

    override suspend fun setMemoryAutoSave(enabled: Boolean) {
        _settings.update { it.copy(memoryAutoSave = enabled) }
    }

    override suspend fun setIdentityJson(json: String) {
        _settings.update { it.copy(identityJson = json) }
    }

    override suspend fun setAutonomyLevel(level: String) { /* no-op */ }

    override suspend fun setWorkspaceOnly(enabled: Boolean) { /* no-op */ }

    override suspend fun setAllowedCommands(commands: String) { /* no-op */ }

    override suspend fun setForbiddenPaths(paths: String) { /* no-op */ }

    override suspend fun setMaxActionsPerHour(max: Int) { /* no-op */ }

    override suspend fun setMaxCostPerDayCents(cents: Int) { /* no-op */ }

    override suspend fun setRequireApprovalMediumRisk(required: Boolean) { /* no-op */ }

    override suspend fun setBlockHighRiskCommands(blocked: Boolean) { /* no-op */ }

    override suspend fun setTunnelProvider(provider: String) { /* no-op */ }

    override suspend fun setTunnelCloudflareToken(token: String) { /* no-op */ }

    override suspend fun setTunnelTailscaleFunnel(enabled: Boolean) { /* no-op */ }

    override suspend fun setTunnelTailscaleHostname(hostname: String) { /* no-op */ }

    override suspend fun setTunnelNgrokAuthToken(token: String) { /* no-op */ }

    override suspend fun setTunnelNgrokDomain(domain: String) { /* no-op */ }

    override suspend fun setTunnelCustomCommand(command: String) { /* no-op */ }

    override suspend fun setTunnelCustomHealthUrl(url: String) { /* no-op */ }

    override suspend fun setTunnelCustomUrlPattern(pattern: String) { /* no-op */ }

    override suspend fun setGatewayRequirePairing(required: Boolean) { /* no-op */ }

    override suspend fun setGatewayAllowPublicBind(allowed: Boolean) { /* no-op */ }

    override suspend fun setGatewayPairedTokens(tokens: String) { /* no-op */ }

    override suspend fun setGatewayPairRateLimit(limit: Int) { /* no-op */ }

    override suspend fun setGatewayWebhookRateLimit(limit: Int) { /* no-op */ }

    override suspend fun setGatewayIdempotencyTtl(seconds: Int) { /* no-op */ }

    override suspend fun setSchedulerEnabled(enabled: Boolean) { /* no-op */ }

    override suspend fun setSchedulerMaxTasks(max: Int) { /* no-op */ }

    override suspend fun setSchedulerMaxConcurrent(max: Int) { /* no-op */ }

    override suspend fun setHeartbeatEnabled(enabled: Boolean) { /* no-op */ }

    override suspend fun setHeartbeatIntervalMinutes(minutes: Int) { /* no-op */ }

    override suspend fun setObservabilityBackend(backend: String) { /* no-op */ }

    override suspend fun setObservabilityOtelEndpoint(endpoint: String) { /* no-op */ }

    override suspend fun setObservabilityOtelServiceName(name: String) { /* no-op */ }

    override suspend fun setModelRoutesJson(json: String) { /* no-op */ }

    override suspend fun setMemoryHygieneEnabled(enabled: Boolean) { /* no-op */ }

    override suspend fun setMemoryArchiveAfterDays(days: Int) { /* no-op */ }

    override suspend fun setMemoryPurgeAfterDays(days: Int) { /* no-op */ }

    override suspend fun setMemoryEmbeddingProvider(provider: String) { /* no-op */ }

    override suspend fun setMemoryEmbeddingModel(model: String) { /* no-op */ }

    override suspend fun setMemoryVectorWeight(weight: Float) { /* no-op */ }

    override suspend fun setMemoryKeywordWeight(weight: Float) { /* no-op */ }

    override suspend fun setComposioEnabled(enabled: Boolean) { /* no-op */ }

    override suspend fun setComposioApiKey(key: String) { /* no-op */ }

    override suspend fun setComposioEntityId(entityId: String) { /* no-op */ }

    override suspend fun setBrowserEnabled(enabled: Boolean) { /* no-op */ }

    override suspend fun setBrowserAllowedDomains(domains: String) { /* no-op */ }

    override suspend fun setHttpRequestEnabled(enabled: Boolean) { /* no-op */ }

    override suspend fun setHttpRequestAllowedDomains(domains: String) { /* no-op */ }

    override suspend fun setBiometricForService(enabled: Boolean) { /* no-op */ }

    override suspend fun setBiometricForSettings(enabled: Boolean) { /* no-op */ }

    override suspend fun setPluginRegistryUrl(url: String) { /* no-op */ }

    override suspend fun setPluginSyncEnabled(enabled: Boolean) { /* no-op */ }

    override suspend fun setPluginSyncIntervalHours(hours: Int) { /* no-op */ }

    override suspend fun setLastPluginSyncTimestamp(timestamp: Long) { /* no-op */ }

    override suspend fun setStripThinkingTags(enabled: Boolean) { /* no-op */ }
}
