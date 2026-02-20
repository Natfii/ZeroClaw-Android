/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding

import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.data.repository.ApiKeyRepository
import com.zeroclaw.android.data.repository.OnboardingRepository
import com.zeroclaw.android.data.repository.SettingsRepository
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for provider-related logic extracted from [OnboardingViewModel].
 *
 * Uses test doubles that mirror the ViewModel's behavior to avoid
 * requiring [android.app.Application].
 */
@DisplayName("OnboardingViewModel provider logic")
class OnboardingViewModelProviderTest {
    @Test
    @DisplayName("setProvider updates selectedProvider state")
    fun `setProvider updates selectedProvider state`() =
        runTest {
            val state = TestProviderState()
            state.setProvider("anthropic")
            assertEquals("anthropic", state.selectedProvider)
        }

    @Test
    @DisplayName("setProvider auto-populates baseUrl from registry")
    fun `setProvider auto-populates baseUrl from registry`() =
        runTest {
            val state = TestProviderState()
            state.setProvider("ollama")
            assertEquals("http://localhost:11434", state.baseUrl)
        }

    @Test
    @DisplayName("setProvider auto-populates first suggested model")
    fun `setProvider auto-populates first suggested model`() =
        runTest {
            val state = TestProviderState()
            state.setProvider("openai")
            val expected = ProviderRegistry.findById("openai")!!.suggestedModels.first()
            assertEquals(expected, state.selectedModel)
        }

    @Test
    @DisplayName("setProvider clears baseUrl when provider has no default URL")
    fun `setProvider clears baseUrl when provider has no default URL`() =
        runTest {
            val state = TestProviderState()
            state.setProvider("ollama")
            assertTrue(state.baseUrl.isNotBlank())
            state.setProvider("openai")
            assertEquals("", state.baseUrl)
        }

    @Test
    @DisplayName("complete saves API key when provided")
    fun `complete saves API key when provided`() =
        runTest {
            val apiKeyRepo = TestApiKeyRepository()
            val settingsRepo = TestSettingsRepository()
            val onboardingRepo = FakeOnboardingRepository()

            val completer = TestCompleter(apiKeyRepo, settingsRepo, onboardingRepo)
            completer.selectedProvider = "anthropic"
            completer.apiKey = "sk-ant-test-key"
            completer.selectedModel = "claude-sonnet-4-5-20250929"

            completer.complete()

            val savedKeys = apiKeyRepo.keys.first()
            assertEquals(1, savedKeys.size)
            assertEquals("anthropic", savedKeys[0].provider)
            assertEquals("sk-ant-test-key", savedKeys[0].key)
        }

    @Test
    @DisplayName("complete saves default provider and model")
    fun `complete saves default provider and model`() =
        runTest {
            val apiKeyRepo = TestApiKeyRepository()
            val settingsRepo = TestSettingsRepository()
            val onboardingRepo = FakeOnboardingRepository()

            val completer = TestCompleter(apiKeyRepo, settingsRepo, onboardingRepo)
            completer.selectedProvider = "openai"
            completer.apiKey = "sk-test"
            completer.selectedModel = "gpt-4o"

            completer.complete()

            val settings = settingsRepo.settings.first()
            assertEquals("openai", settings.defaultProvider)
            assertEquals("gpt-4o", settings.defaultModel)
        }

    @Test
    @DisplayName("complete does not save blank API key")
    fun `complete does not save blank API key`() =
        runTest {
            val apiKeyRepo = TestApiKeyRepository()
            val settingsRepo = TestSettingsRepository()
            val onboardingRepo = FakeOnboardingRepository()

            val completer = TestCompleter(apiKeyRepo, settingsRepo, onboardingRepo)
            completer.selectedProvider = "openai"
            completer.apiKey = ""
            completer.selectedModel = "gpt-4o"

            completer.complete()

            val savedKeys = apiKeyRepo.keys.first()
            assertTrue(savedKeys.isEmpty())
        }

    @Test
    @DisplayName("complete marks onboarding as completed")
    fun `complete marks onboarding as completed`() =
        runTest {
            val onboardingRepo = FakeOnboardingRepository()
            val completer =
                TestCompleter(TestApiKeyRepository(), TestSettingsRepository(), onboardingRepo)
            completer.selectedProvider = "openai"
            completer.apiKey = ""
            completer.selectedModel = ""

            completer.complete()

            assertTrue(onboardingRepo.isCompleted.first())
        }
}

/**
 * Mirrors the provider state management from [OnboardingViewModel].
 */
private class TestProviderState {
    var selectedProvider: String = ""
        private set
    var baseUrl: String = ""
        private set
    var selectedModel: String = ""
        private set

    /** Sets the provider and auto-populates base URL and model. */
    fun setProvider(id: String) {
        selectedProvider = id
        val info = ProviderRegistry.findById(id)
        baseUrl = info?.defaultBaseUrl.orEmpty()
        selectedModel = info?.suggestedModels?.firstOrNull().orEmpty()
    }
}

/**
 * Mirrors the completion logic from [OnboardingViewModel].
 */
private class TestCompleter(
    private val apiKeyRepo: TestApiKeyRepository,
    private val settingsRepo: TestSettingsRepository,
    private val onboardingRepo: FakeOnboardingRepository,
) {
    var selectedProvider: String = ""
    var apiKey: String = ""
    var selectedModel: String = ""

    /** Executes the completion flow. */
    suspend fun complete() {
        if (selectedProvider.isNotBlank() && apiKey.isNotBlank()) {
            apiKeyRepo.save(
                ApiKey(
                    id = "test-id",
                    provider = selectedProvider,
                    key = apiKey,
                ),
            )
        }
        if (selectedProvider.isNotBlank()) {
            settingsRepo.setDefaultProvider(selectedProvider)
        }
        if (selectedModel.isNotBlank()) {
            settingsRepo.setDefaultModel(selectedModel)
        }
        onboardingRepo.markComplete()
    }
}

/**
 * Test double for [ApiKeyRepository].
 */
private class TestApiKeyRepository : ApiKeyRepository {
    private val _keys = MutableStateFlow<List<ApiKey>>(emptyList())
    override val keys: Flow<List<ApiKey>> = _keys

    override suspend fun getById(id: String): ApiKey? = _keys.value.find { it.id == id }

    override suspend fun save(apiKey: ApiKey) {
        _keys.value = _keys.value.filter { it.id != apiKey.id } + apiKey
    }

    override suspend fun delete(id: String) {
        _keys.value = _keys.value.filter { it.id != id }
    }

    override suspend fun exportAll(passphrase: String): String = ""

    override suspend fun importFrom(
        encryptedPayload: String,
        passphrase: String,
    ): Int = 0

    override suspend fun getByProvider(provider: String): ApiKey? = _keys.value.find { it.provider == provider }
}

/**
 * Test double for [SettingsRepository].
 */
private class TestSettingsRepository : SettingsRepository {
    private val _settings = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = _settings

    override suspend fun setHost(host: String) {
        _settings.value = _settings.value.copy(host = host)
    }

    override suspend fun setPort(port: Int) {
        _settings.value = _settings.value.copy(port = port)
    }

    override suspend fun setAutoStartOnBoot(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoStartOnBoot = enabled)
    }

    override suspend fun setDefaultProvider(provider: String) {
        _settings.value = _settings.value.copy(defaultProvider = provider)
    }

    override suspend fun setDefaultModel(model: String) {
        _settings.value = _settings.value.copy(defaultModel = model)
    }

    override suspend fun setDefaultTemperature(temperature: Float) {
        _settings.value = _settings.value.copy(defaultTemperature = temperature)
    }

    override suspend fun setCompactContext(enabled: Boolean) {
        _settings.value = _settings.value.copy(compactContext = enabled)
    }

    override suspend fun setCostEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(costEnabled = enabled)
    }

    override suspend fun setDailyLimitUsd(limit: Float) {
        _settings.value = _settings.value.copy(dailyLimitUsd = limit)
    }

    override suspend fun setMonthlyLimitUsd(limit: Float) {
        _settings.value = _settings.value.copy(monthlyLimitUsd = limit)
    }

    override suspend fun setCostWarnAtPercent(percent: Int) {
        _settings.value = _settings.value.copy(costWarnAtPercent = percent)
    }

    override suspend fun setProviderRetries(retries: Int) {
        _settings.value = _settings.value.copy(providerRetries = retries)
    }

    override suspend fun setFallbackProviders(providers: String) {
        _settings.value = _settings.value.copy(fallbackProviders = providers)
    }

    override suspend fun setMemoryBackend(backend: String) {
        _settings.value = _settings.value.copy(memoryBackend = backend)
    }

    override suspend fun setMemoryAutoSave(enabled: Boolean) {
        _settings.value = _settings.value.copy(memoryAutoSave = enabled)
    }

    override suspend fun setIdentityJson(json: String) {
        _settings.value = _settings.value.copy(identityJson = json)
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

    override suspend fun setLockEnabled(enabled: Boolean) { /* no-op */ }

    override suspend fun setLockTimeoutMinutes(minutes: Int) { /* no-op */ }

    override suspend fun setPinHash(hash: String) { /* no-op */ }

    override suspend fun setPluginRegistryUrl(url: String) { /* no-op */ }

    override suspend fun setPluginSyncEnabled(enabled: Boolean) { /* no-op */ }

    override suspend fun setPluginSyncIntervalHours(hours: Int) { /* no-op */ }

    override suspend fun setLastPluginSyncTimestamp(timestamp: Long) { /* no-op */ }

    override suspend fun setStripThinkingTags(enabled: Boolean) { /* no-op */ }

    override suspend fun setTheme(theme: com.zeroclaw.android.model.ThemeMode) { /* no-op */ }
}

/**
 * Test double for [OnboardingRepository].
 */
private class FakeOnboardingRepository : OnboardingRepository {
    private val _isCompleted = MutableStateFlow(false)
    override val isCompleted: Flow<Boolean> = _isCompleted

    override suspend fun markComplete() {
        _isCompleted.value = true
    }

    override suspend fun reset() {
        _isCompleted.value = false
    }
}
