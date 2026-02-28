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
import com.zeroclaw.android.data.repository.SettingsRepository
import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.model.ThemeMode
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
    private val daemonBridge = (application as ZeroClawApplication).daemonBridge

    /** Current application settings, collected as state. */
    val settings: StateFlow<AppSettings> =
        repository.settings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AppSettings(),
        )

    /** Whether a daemon restart is required to apply settings changes. */
    val restartRequired: StateFlow<Boolean> = daemonBridge.restartRequired

    /**
     * Updates a daemon-affecting setting and marks a restart as required
     * if the daemon is currently running.
     */
    private fun updateDaemonSetting(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch {
            repository.block()
            daemonBridge.markRestartRequired()
        }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setHost */
    fun updateHost(host: String) {
        updateDaemonSetting { setHost(host) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setPort */
    fun updatePort(port: Int) {
        updateDaemonSetting { setPort(port) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setAutoStartOnBoot */
    fun updateAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoStartOnBoot(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setDefaultProvider */
    fun updateDefaultProvider(provider: String) {
        updateDaemonSetting { setDefaultProvider(provider) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setDefaultModel */
    fun updateDefaultModel(model: String) {
        updateDaemonSetting { setDefaultModel(model) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setDefaultTemperature */
    fun updateDefaultTemperature(temperature: Float) {
        updateDaemonSetting { setDefaultTemperature(temperature) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setCompactContext */
    fun updateCompactContext(enabled: Boolean) {
        updateDaemonSetting { setCompactContext(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setCostEnabled */
    fun updateCostEnabled(enabled: Boolean) {
        updateDaemonSetting { setCostEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setDailyLimitUsd */
    fun updateDailyLimitUsd(limit: Float) {
        updateDaemonSetting { setDailyLimitUsd(limit) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMonthlyLimitUsd */
    fun updateMonthlyLimitUsd(limit: Float) {
        updateDaemonSetting { setMonthlyLimitUsd(limit) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setCostWarnAtPercent */
    fun updateCostWarnAtPercent(percent: Int) {
        updateDaemonSetting { setCostWarnAtPercent(percent) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setProviderRetries */
    fun updateProviderRetries(retries: Int) {
        updateDaemonSetting { setProviderRetries(retries) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setFallbackProviders */
    fun updateFallbackProviders(providers: String) {
        updateDaemonSetting { setFallbackProviders(providers) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryBackend */
    fun updateMemoryBackend(backend: String) {
        updateDaemonSetting { setMemoryBackend(backend) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryAutoSave */
    fun updateMemoryAutoSave(enabled: Boolean) {
        updateDaemonSetting { setMemoryAutoSave(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setIdentityJson */
    fun updateIdentityJson(json: String) {
        updateDaemonSetting { setIdentityJson(json) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setAutonomyLevel */
    fun updateAutonomyLevel(level: String) {
        updateDaemonSetting { setAutonomyLevel(level) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setWorkspaceOnly */
    fun updateWorkspaceOnly(enabled: Boolean) {
        updateDaemonSetting { setWorkspaceOnly(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setAllowedCommands */
    fun updateAllowedCommands(commands: String) {
        updateDaemonSetting { setAllowedCommands(commands) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setForbiddenPaths */
    fun updateForbiddenPaths(paths: String) {
        updateDaemonSetting { setForbiddenPaths(paths) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMaxActionsPerHour */
    fun updateMaxActionsPerHour(max: Int) {
        updateDaemonSetting { setMaxActionsPerHour(max) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMaxCostPerDayCents */
    fun updateMaxCostPerDayCents(cents: Int) {
        updateDaemonSetting { setMaxCostPerDayCents(cents) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setRequireApprovalMediumRisk */
    fun updateRequireApprovalMediumRisk(required: Boolean) {
        updateDaemonSetting { setRequireApprovalMediumRisk(required) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setBlockHighRiskCommands */
    fun updateBlockHighRiskCommands(blocked: Boolean) {
        updateDaemonSetting { setBlockHighRiskCommands(blocked) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelProvider */
    fun updateTunnelProvider(provider: String) {
        updateDaemonSetting { setTunnelProvider(provider) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelCloudflareToken */
    fun updateTunnelCloudflareToken(token: String) {
        updateDaemonSetting { setTunnelCloudflareToken(token) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelTailscaleFunnel */
    fun updateTunnelTailscaleFunnel(enabled: Boolean) {
        updateDaemonSetting { setTunnelTailscaleFunnel(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelTailscaleHostname */
    fun updateTunnelTailscaleHostname(hostname: String) {
        updateDaemonSetting { setTunnelTailscaleHostname(hostname) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelNgrokAuthToken */
    fun updateTunnelNgrokAuthToken(token: String) {
        updateDaemonSetting { setTunnelNgrokAuthToken(token) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelNgrokDomain */
    fun updateTunnelNgrokDomain(domain: String) {
        updateDaemonSetting { setTunnelNgrokDomain(domain) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelCustomCommand */
    fun updateTunnelCustomCommand(command: String) {
        updateDaemonSetting { setTunnelCustomCommand(command) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelCustomHealthUrl */
    fun updateTunnelCustomHealthUrl(url: String) {
        updateDaemonSetting { setTunnelCustomHealthUrl(url) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTunnelCustomUrlPattern */
    fun updateTunnelCustomUrlPattern(pattern: String) {
        updateDaemonSetting { setTunnelCustomUrlPattern(pattern) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setGatewayRequirePairing */
    fun updateGatewayRequirePairing(required: Boolean) {
        updateDaemonSetting { setGatewayRequirePairing(required) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setGatewayAllowPublicBind */
    fun updateGatewayAllowPublicBind(allowed: Boolean) {
        updateDaemonSetting { setGatewayAllowPublicBind(allowed) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setGatewayPairedTokens */
    fun updateGatewayPairedTokens(tokens: String) {
        updateDaemonSetting { setGatewayPairedTokens(tokens) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setGatewayPairRateLimit */
    fun updateGatewayPairRateLimit(limit: Int) {
        updateDaemonSetting { setGatewayPairRateLimit(limit) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setGatewayWebhookRateLimit */
    fun updateGatewayWebhookRateLimit(limit: Int) {
        updateDaemonSetting { setGatewayWebhookRateLimit(limit) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setGatewayIdempotencyTtl */
    fun updateGatewayIdempotencyTtl(seconds: Int) {
        updateDaemonSetting { setGatewayIdempotencyTtl(seconds) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSchedulerEnabled */
    fun updateSchedulerEnabled(enabled: Boolean) {
        updateDaemonSetting { setSchedulerEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSchedulerMaxTasks */
    fun updateSchedulerMaxTasks(max: Int) {
        updateDaemonSetting { setSchedulerMaxTasks(max) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSchedulerMaxConcurrent */
    fun updateSchedulerMaxConcurrent(max: Int) {
        updateDaemonSetting { setSchedulerMaxConcurrent(max) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setHeartbeatEnabled */
    fun updateHeartbeatEnabled(enabled: Boolean) {
        updateDaemonSetting { setHeartbeatEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setHeartbeatIntervalMinutes */
    fun updateHeartbeatIntervalMinutes(minutes: Int) {
        updateDaemonSetting { setHeartbeatIntervalMinutes(minutes) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setObservabilityBackend */
    fun updateObservabilityBackend(backend: String) {
        updateDaemonSetting { setObservabilityBackend(backend) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setObservabilityOtelEndpoint */
    fun updateObservabilityOtelEndpoint(endpoint: String) {
        updateDaemonSetting { setObservabilityOtelEndpoint(endpoint) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setObservabilityOtelServiceName */
    fun updateObservabilityOtelServiceName(name: String) {
        updateDaemonSetting { setObservabilityOtelServiceName(name) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setModelRoutesJson */
    fun updateModelRoutesJson(json: String) {
        updateDaemonSetting { setModelRoutesJson(json) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryHygieneEnabled */
    fun updateMemoryHygieneEnabled(enabled: Boolean) {
        updateDaemonSetting { setMemoryHygieneEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryArchiveAfterDays */
    fun updateMemoryArchiveAfterDays(days: Int) {
        updateDaemonSetting { setMemoryArchiveAfterDays(days) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryPurgeAfterDays */
    fun updateMemoryPurgeAfterDays(days: Int) {
        updateDaemonSetting { setMemoryPurgeAfterDays(days) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryEmbeddingProvider */
    fun updateMemoryEmbeddingProvider(provider: String) {
        updateDaemonSetting { setMemoryEmbeddingProvider(provider) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryEmbeddingModel */
    fun updateMemoryEmbeddingModel(model: String) {
        updateDaemonSetting { setMemoryEmbeddingModel(model) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryVectorWeight */
    fun updateMemoryVectorWeight(weight: Float) {
        updateDaemonSetting { setMemoryVectorWeight(weight) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryKeywordWeight */
    fun updateMemoryKeywordWeight(weight: Float) {
        updateDaemonSetting { setMemoryKeywordWeight(weight) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setComposioEnabled */
    fun updateComposioEnabled(enabled: Boolean) {
        updateDaemonSetting { setComposioEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setComposioApiKey */
    fun updateComposioApiKey(key: String) {
        updateDaemonSetting { setComposioApiKey(key) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setComposioEntityId */
    fun updateComposioEntityId(entityId: String) {
        updateDaemonSetting { setComposioEntityId(entityId) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setBrowserEnabled */
    fun updateBrowserEnabled(enabled: Boolean) {
        updateDaemonSetting { setBrowserEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setBrowserAllowedDomains */
    fun updateBrowserAllowedDomains(domains: String) {
        updateDaemonSetting { setBrowserAllowedDomains(domains) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setHttpRequestEnabled */
    fun updateHttpRequestEnabled(enabled: Boolean) {
        updateDaemonSetting { setHttpRequestEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setHttpRequestAllowedDomains */
    fun updateHttpRequestAllowedDomains(domains: String) {
        updateDaemonSetting { setHttpRequestAllowedDomains(domains) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setWebFetchEnabled */
    fun updateWebFetchEnabled(enabled: Boolean) {
        updateDaemonSetting { setWebFetchEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setWebFetchAllowedDomains */
    fun updateWebFetchAllowedDomains(domains: String) {
        updateDaemonSetting { setWebFetchAllowedDomains(domains) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setWebFetchBlockedDomains */
    fun updateWebFetchBlockedDomains(domains: String) {
        updateDaemonSetting { setWebFetchBlockedDomains(domains) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setWebFetchMaxResponseSize */
    fun updateWebFetchMaxResponseSize(size: Int) {
        updateDaemonSetting { setWebFetchMaxResponseSize(size) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setWebFetchTimeoutSecs */
    fun updateWebFetchTimeoutSecs(secs: Int) {
        updateDaemonSetting { setWebFetchTimeoutSecs(secs) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setWebSearchEnabled */
    fun updateWebSearchEnabled(enabled: Boolean) {
        updateDaemonSetting { setWebSearchEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setWebSearchProvider */
    fun updateWebSearchProvider(provider: String) {
        updateDaemonSetting { setWebSearchProvider(provider) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setWebSearchBraveApiKey */
    fun updateWebSearchBraveApiKey(key: String) {
        updateDaemonSetting { setWebSearchBraveApiKey(key) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setWebSearchMaxResults */
    fun updateWebSearchMaxResults(max: Int) {
        updateDaemonSetting { setWebSearchMaxResults(max) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setWebSearchTimeoutSecs */
    fun updateWebSearchTimeoutSecs(secs: Int) {
        updateDaemonSetting { setWebSearchTimeoutSecs(secs) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSecuritySandboxEnabled */
    fun updateSecuritySandboxEnabled(enabled: String) {
        updateDaemonSetting { setSecuritySandboxEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSecuritySandboxBackend */
    fun updateSecuritySandboxBackend(backend: String) {
        updateDaemonSetting { setSecuritySandboxBackend(backend) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSecuritySandboxFirejailArgs */
    fun updateSecuritySandboxFirejailArgs(args: String) {
        updateDaemonSetting { setSecuritySandboxFirejailArgs(args) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSecurityResourcesMaxMemoryMb */
    fun updateSecurityResourcesMaxMemoryMb(mb: Int) {
        updateDaemonSetting { setSecurityResourcesMaxMemoryMb(mb) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSecurityResourcesMaxCpuTimeSecs */
    fun updateSecurityResourcesMaxCpuTimeSecs(secs: Int) {
        updateDaemonSetting { setSecurityResourcesMaxCpuTimeSecs(secs) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSecurityResourcesMaxSubprocesses */
    fun updateSecurityResourcesMaxSubprocesses(max: Int) {
        updateDaemonSetting { setSecurityResourcesMaxSubprocesses(max) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSecurityResourcesMemoryMonitoring */
    fun updateSecurityResourcesMemoryMonitoring(enabled: Boolean) {
        updateDaemonSetting { setSecurityResourcesMemoryMonitoring(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSecurityAuditEnabled */
    fun updateSecurityAuditEnabled(enabled: Boolean) {
        updateDaemonSetting { setSecurityAuditEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSecurityOtpEnabled */
    fun updateSecurityOtpEnabled(enabled: Boolean) {
        updateDaemonSetting { setSecurityOtpEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSecurityOtpMethod */
    fun updateSecurityOtpMethod(method: String) {
        updateDaemonSetting { setSecurityOtpMethod(method) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSecurityOtpTokenTtlSecs */
    fun updateSecurityOtpTokenTtlSecs(secs: Int) {
        updateDaemonSetting { setSecurityOtpTokenTtlSecs(secs) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSecurityOtpCacheValidSecs */
    fun updateSecurityOtpCacheValidSecs(secs: Int) {
        updateDaemonSetting { setSecurityOtpCacheValidSecs(secs) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSecurityOtpGatedActions */
    fun updateSecurityOtpGatedActions(actions: String) {
        updateDaemonSetting { setSecurityOtpGatedActions(actions) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSecurityOtpGatedDomains */
    fun updateSecurityOtpGatedDomains(domains: String) {
        updateDaemonSetting { setSecurityOtpGatedDomains(domains) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSecurityOtpGatedDomainCategories */
    fun updateSecurityOtpGatedDomainCategories(categories: String) {
        updateDaemonSetting { setSecurityOtpGatedDomainCategories(categories) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSecurityEstopEnabled */
    fun updateSecurityEstopEnabled(enabled: Boolean) {
        updateDaemonSetting { setSecurityEstopEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setSecurityEstopRequireOtpToResume */
    fun updateSecurityEstopRequireOtpToResume(required: Boolean) {
        updateDaemonSetting { setSecurityEstopRequireOtpToResume(required) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryQdrantUrl */
    fun updateMemoryQdrantUrl(url: String) {
        updateDaemonSetting { setMemoryQdrantUrl(url) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryQdrantCollection */
    fun updateMemoryQdrantCollection(collection: String) {
        updateDaemonSetting { setMemoryQdrantCollection(collection) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setMemoryQdrantApiKey */
    fun updateMemoryQdrantApiKey(key: String) {
        updateDaemonSetting { setMemoryQdrantApiKey(key) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setEmbeddingRoutesJson */
    fun updateEmbeddingRoutesJson(json: String) {
        updateDaemonSetting { setEmbeddingRoutesJson(json) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setQueryClassificationEnabled */
    fun updateQueryClassificationEnabled(enabled: Boolean) {
        updateDaemonSetting { setQueryClassificationEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setProxyEnabled */
    fun updateProxyEnabled(enabled: Boolean) {
        updateDaemonSetting { setProxyEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setProxyHttpProxy */
    fun updateProxyHttpProxy(proxy: String) {
        updateDaemonSetting { setProxyHttpProxy(proxy) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setProxyHttpsProxy */
    fun updateProxyHttpsProxy(proxy: String) {
        updateDaemonSetting { setProxyHttpsProxy(proxy) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setProxyAllProxy */
    fun updateProxyAllProxy(proxy: String) {
        updateDaemonSetting { setProxyAllProxy(proxy) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setProxyNoProxy */
    fun updateProxyNoProxy(noProxy: String) {
        updateDaemonSetting { setProxyNoProxy(noProxy) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setProxyScope */
    fun updateProxyScope(scope: String) {
        updateDaemonSetting { setProxyScope(scope) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setProxyServiceSelectors */
    fun updateProxyServiceSelectors(selectors: String) {
        updateDaemonSetting { setProxyServiceSelectors(selectors) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setReliabilityBackoffMs */
    fun updateReliabilityBackoffMs(ms: Int) {
        updateDaemonSetting { setReliabilityBackoffMs(ms) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setReliabilityApiKeysJson */
    fun updateReliabilityApiKeysJson(json: String) {
        updateDaemonSetting { setReliabilityApiKeysJson(json) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setLockEnabled */
    fun updateLockEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setLockEnabled(enabled) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setLockTimeoutMinutes */
    fun updateLockTimeoutMinutes(minutes: Int) {
        viewModelScope.launch { repository.setLockTimeoutMinutes(minutes) }
    }

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setPinHash */
    fun updatePinHash(hash: String) {
        viewModelScope.launch { repository.setPinHash(hash) }
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

    /** @see com.zeroclaw.android.data.repository.SettingsRepository.setTheme */
    fun updateTheme(theme: ThemeMode) {
        viewModelScope.launch { repository.setTheme(theme) }
    }

    /**
     * Resets onboarding completion state so the setup wizard is shown again.
     *
     * Clears the AIEOS identity JSON so the wizard generates a fresh
     * identity document. Existing API keys and other settings are preserved.
     */
    fun resetOnboarding() {
        viewModelScope.launch {
            repository.setIdentityJson("")
            daemonBridge.markRestartRequired()
            onboardingRepository.reset()
        }
    }

    /** Constants for [SettingsViewModel]. */
    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
