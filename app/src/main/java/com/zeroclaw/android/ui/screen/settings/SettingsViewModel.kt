/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the settings screen hierarchy.
 *
 * Exposes the current [AppSettings] as a [StateFlow] and provides
 * methods for updating individual settings via the repository.
 *
 * @param application Application context for accessing the settings repository.
 */
@Suppress("TooManyFunctions")
class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = (application as ZeroClawApplication).settingsRepository
    private val onboardingRepository = (application as ZeroClawApplication).onboardingRepository

    /** Current application settings, collected as state. */
    val settings: StateFlow<AppSettings> =
        repository.settings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AppSettings(),
        )

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setHost */
    fun updateHost(host: String) {
        viewModelScope.launch { repository.setHost(host) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setPort */
    fun updatePort(port: Int) {
        viewModelScope.launch { repository.setPort(port) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setAutoStartOnBoot */
    fun updateAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoStartOnBoot(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setDefaultTemperature */
    fun updateDefaultTemperature(temperature: Float) {
        viewModelScope.launch { repository.setDefaultTemperature(temperature) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setCompactContext */
    fun updateCompactContext(enabled: Boolean) {
        viewModelScope.launch { repository.setCompactContext(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setCostEnabled */
    fun updateCostEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setCostEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setDailyLimitUsd */
    fun updateDailyLimitUsd(limit: Float) {
        viewModelScope.launch { repository.setDailyLimitUsd(limit) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMonthlyLimitUsd */
    fun updateMonthlyLimitUsd(limit: Float) {
        viewModelScope.launch { repository.setMonthlyLimitUsd(limit) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setCostWarnAtPercent */
    fun updateCostWarnAtPercent(percent: Int) {
        viewModelScope.launch { repository.setCostWarnAtPercent(percent) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setProviderRetries */
    fun updateProviderRetries(retries: Int) {
        viewModelScope.launch { repository.setProviderRetries(retries) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setFallbackProviders */
    fun updateFallbackProviders(providers: String) {
        viewModelScope.launch { repository.setFallbackProviders(providers) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryBackend */
    fun updateMemoryBackend(backend: String) {
        viewModelScope.launch { repository.setMemoryBackend(backend) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryAutoSave */
    fun updateMemoryAutoSave(enabled: Boolean) {
        viewModelScope.launch { repository.setMemoryAutoSave(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setIdentityJson */
    fun updateIdentityJson(json: String) {
        viewModelScope.launch { repository.setIdentityJson(json) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setAutonomyLevel */
    fun updateAutonomyLevel(level: String) {
        viewModelScope.launch { repository.setAutonomyLevel(level) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setWorkspaceOnly */
    fun updateWorkspaceOnly(enabled: Boolean) {
        viewModelScope.launch { repository.setWorkspaceOnly(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setAllowedCommands */
    fun updateAllowedCommands(commands: String) {
        viewModelScope.launch { repository.setAllowedCommands(commands) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setForbiddenPaths */
    fun updateForbiddenPaths(paths: String) {
        viewModelScope.launch { repository.setForbiddenPaths(paths) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMaxActionsPerHour */
    fun updateMaxActionsPerHour(max: Int) {
        viewModelScope.launch { repository.setMaxActionsPerHour(max) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMaxCostPerDayCents */
    fun updateMaxCostPerDayCents(cents: Int) {
        viewModelScope.launch { repository.setMaxCostPerDayCents(cents) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setRequireApprovalMediumRisk */
    fun updateRequireApprovalMediumRisk(required: Boolean) {
        viewModelScope.launch { repository.setRequireApprovalMediumRisk(required) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setBlockHighRiskCommands */
    fun updateBlockHighRiskCommands(blocked: Boolean) {
        viewModelScope.launch { repository.setBlockHighRiskCommands(blocked) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelProvider */
    fun updateTunnelProvider(provider: String) {
        viewModelScope.launch { repository.setTunnelProvider(provider) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelCloudflareToken */
    fun updateTunnelCloudflareToken(token: String) {
        viewModelScope.launch { repository.setTunnelCloudflareToken(token) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelTailscaleFunnel */
    fun updateTunnelTailscaleFunnel(enabled: Boolean) {
        viewModelScope.launch { repository.setTunnelTailscaleFunnel(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelTailscaleHostname */
    fun updateTunnelTailscaleHostname(hostname: String) {
        viewModelScope.launch { repository.setTunnelTailscaleHostname(hostname) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelNgrokAuthToken */
    fun updateTunnelNgrokAuthToken(token: String) {
        viewModelScope.launch { repository.setTunnelNgrokAuthToken(token) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelNgrokDomain */
    fun updateTunnelNgrokDomain(domain: String) {
        viewModelScope.launch { repository.setTunnelNgrokDomain(domain) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelCustomCommand */
    fun updateTunnelCustomCommand(command: String) {
        viewModelScope.launch { repository.setTunnelCustomCommand(command) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelCustomHealthUrl */
    fun updateTunnelCustomHealthUrl(url: String) {
        viewModelScope.launch { repository.setTunnelCustomHealthUrl(url) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelCustomUrlPattern */
    fun updateTunnelCustomUrlPattern(pattern: String) {
        viewModelScope.launch { repository.setTunnelCustomUrlPattern(pattern) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setGatewayRequirePairing */
    fun updateGatewayRequirePairing(required: Boolean) {
        viewModelScope.launch { repository.setGatewayRequirePairing(required) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setGatewayAllowPublicBind */
    fun updateGatewayAllowPublicBind(allowed: Boolean) {
        viewModelScope.launch { repository.setGatewayAllowPublicBind(allowed) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setGatewayPairedTokens */
    fun updateGatewayPairedTokens(tokens: String) {
        viewModelScope.launch { repository.setGatewayPairedTokens(tokens) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setGatewayPairRateLimit */
    fun updateGatewayPairRateLimit(limit: Int) {
        viewModelScope.launch { repository.setGatewayPairRateLimit(limit) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setGatewayWebhookRateLimit */
    fun updateGatewayWebhookRateLimit(limit: Int) {
        viewModelScope.launch { repository.setGatewayWebhookRateLimit(limit) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setGatewayIdempotencyTtl */
    fun updateGatewayIdempotencyTtl(seconds: Int) {
        viewModelScope.launch { repository.setGatewayIdempotencyTtl(seconds) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSchedulerEnabled */
    fun updateSchedulerEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSchedulerEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSchedulerMaxTasks */
    fun updateSchedulerMaxTasks(max: Int) {
        viewModelScope.launch { repository.setSchedulerMaxTasks(max) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSchedulerMaxConcurrent */
    fun updateSchedulerMaxConcurrent(max: Int) {
        viewModelScope.launch { repository.setSchedulerMaxConcurrent(max) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setHeartbeatEnabled */
    fun updateHeartbeatEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setHeartbeatEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setHeartbeatIntervalMinutes */
    fun updateHeartbeatIntervalMinutes(minutes: Int) {
        viewModelScope.launch { repository.setHeartbeatIntervalMinutes(minutes) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setObservabilityBackend */
    fun updateObservabilityBackend(backend: String) {
        viewModelScope.launch { repository.setObservabilityBackend(backend) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setObservabilityOtelEndpoint */
    fun updateObservabilityOtelEndpoint(endpoint: String) {
        viewModelScope.launch { repository.setObservabilityOtelEndpoint(endpoint) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setObservabilityOtelServiceName */
    fun updateObservabilityOtelServiceName(name: String) {
        viewModelScope.launch { repository.setObservabilityOtelServiceName(name) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setModelRoutesJson */
    fun updateModelRoutesJson(json: String) {
        viewModelScope.launch { repository.setModelRoutesJson(json) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryHygieneEnabled */
    fun updateMemoryHygieneEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setMemoryHygieneEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryArchiveAfterDays */
    fun updateMemoryArchiveAfterDays(days: Int) {
        viewModelScope.launch { repository.setMemoryArchiveAfterDays(days) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryPurgeAfterDays */
    fun updateMemoryPurgeAfterDays(days: Int) {
        viewModelScope.launch { repository.setMemoryPurgeAfterDays(days) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryEmbeddingProvider */
    fun updateMemoryEmbeddingProvider(provider: String) {
        viewModelScope.launch { repository.setMemoryEmbeddingProvider(provider) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryEmbeddingModel */
    fun updateMemoryEmbeddingModel(model: String) {
        viewModelScope.launch { repository.setMemoryEmbeddingModel(model) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryVectorWeight */
    fun updateMemoryVectorWeight(weight: Float) {
        viewModelScope.launch { repository.setMemoryVectorWeight(weight) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryKeywordWeight */
    fun updateMemoryKeywordWeight(weight: Float) {
        viewModelScope.launch { repository.setMemoryKeywordWeight(weight) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setComposioEnabled */
    fun updateComposioEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setComposioEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setComposioApiKey */
    fun updateComposioApiKey(key: String) {
        viewModelScope.launch { repository.setComposioApiKey(key) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setComposioEntityId */
    fun updateComposioEntityId(entityId: String) {
        viewModelScope.launch { repository.setComposioEntityId(entityId) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setBrowserEnabled */
    fun updateBrowserEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setBrowserEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setBrowserAllowedDomains */
    fun updateBrowserAllowedDomains(domains: String) {
        viewModelScope.launch { repository.setBrowserAllowedDomains(domains) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setHttpRequestEnabled */
    fun updateHttpRequestEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setHttpRequestEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setHttpRequestAllowedDomains */
    fun updateHttpRequestAllowedDomains(domains: String) {
        viewModelScope.launch { repository.setHttpRequestAllowedDomains(domains) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setBiometricForService */
    fun updateBiometricForService(enabled: Boolean) {
        viewModelScope.launch { repository.setBiometricForService(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setBiometricForSettings */
    fun updateBiometricForSettings(enabled: Boolean) {
        viewModelScope.launch { repository.setBiometricForSettings(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setPluginRegistryUrl */
    fun updatePluginRegistryUrl(url: String) {
        viewModelScope.launch { repository.setPluginRegistryUrl(url) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setPluginSyncEnabled */
    fun updatePluginSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setPluginSyncEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setPluginSyncIntervalHours */
    fun updatePluginSyncIntervalHours(hours: Int) {
        viewModelScope.launch { repository.setPluginSyncIntervalHours(hours) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setLastPluginSyncTimestamp */
    fun updateLastPluginSyncTimestamp(timestamp: Long) {
        viewModelScope.launch { repository.setLastPluginSyncTimestamp(timestamp) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setStripThinkingTags */
    fun updateStripThinkingTags(enabled: Boolean) {
        viewModelScope.launch { repository.setStripThinkingTags(enabled) }
    }

    /**
     * Resets onboarding completion state so the setup wizard is shown again.
     *
     * Existing settings and API keys are preserved.
     */
    fun resetOnboarding() {
        viewModelScope.launch { onboardingRepository.reset() }
    }

    /** Constants for [SettingsViewModel]. */
    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
