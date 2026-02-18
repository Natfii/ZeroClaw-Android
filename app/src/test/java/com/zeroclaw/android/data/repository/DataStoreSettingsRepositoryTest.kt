/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.model.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SettingsRepository] contract.
 *
 * Uses an in-memory implementation to verify round-trip persistence
 * of each settings field without requiring Android DataStore.
 */
@DisplayName("SettingsRepository")
class DataStoreSettingsRepositoryTest {
    @Test
    @DisplayName("default settings are returned initially")
    fun `default settings are returned initially`() = runTest {
        val repo = InMemorySettingsRepository()
        val settings = repo.settings.first()
        assertEquals(AppSettings.DEFAULT_HOST, settings.host)
        assertEquals(AppSettings.DEFAULT_PORT, settings.port)
        assertEquals(false, settings.autoStartOnBoot)
        assertEquals(LogLevel.INFO, settings.logLevel)
    }

    @Test
    @DisplayName("setHost persists and emits updated value")
    fun `setHost persists and emits updated value`() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setHost("192.168.1.1")
        assertEquals("192.168.1.1", repo.settings.first().host)
    }

    @Test
    @DisplayName("setPort persists and emits updated value")
    fun `setPort persists and emits updated value`() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setPort(9090)
        assertEquals(9090, repo.settings.first().port)
    }

    @Test
    @DisplayName("setAutoStartOnBoot persists and emits updated value")
    fun `setAutoStartOnBoot persists and emits updated value`() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setAutoStartOnBoot(true)
        assertEquals(true, repo.settings.first().autoStartOnBoot)
    }

    @Test
    @DisplayName("setLogLevel persists and emits updated value")
    fun `setLogLevel persists and emits updated value`() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setLogLevel(LogLevel.ERROR)
        assertEquals(LogLevel.ERROR, repo.settings.first().logLevel)
    }

    @Test
    @DisplayName("multiple updates compose correctly")
    fun `multiple updates compose correctly`() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setHost("10.0.0.1")
        repo.setPort(3000)
        repo.setAutoStartOnBoot(true)
        repo.setLogLevel(LogLevel.DEBUG)
        val settings = repo.settings.first()
        assertEquals("10.0.0.1", settings.host)
        assertEquals(3000, settings.port)
        assertEquals(true, settings.autoStartOnBoot)
        assertEquals(LogLevel.DEBUG, settings.logLevel)
    }

    @Test
    @DisplayName("setDefaultTemperature persists and emits updated value")
    fun `setDefaultTemperature persists and emits updated value`() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setDefaultTemperature(1.5f)
        assertEquals(1.5f, repo.settings.first().defaultTemperature)
    }

    @Test
    @DisplayName("setCompactContext persists and emits updated value")
    fun `setCompactContext persists and emits updated value`() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setCompactContext(true)
        assertEquals(true, repo.settings.first().compactContext)
    }

    @Test
    @DisplayName("setCostEnabled persists and emits updated value")
    fun `setCostEnabled persists and emits updated value`() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setCostEnabled(true)
        assertEquals(true, repo.settings.first().costEnabled)
    }

    @Test
    @DisplayName("setDailyLimitUsd persists and emits updated value")
    fun `setDailyLimitUsd persists and emits updated value`() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setDailyLimitUsd(25f)
        assertEquals(25f, repo.settings.first().dailyLimitUsd)
    }

    @Test
    @DisplayName("setMonthlyLimitUsd persists and emits updated value")
    fun `setMonthlyLimitUsd persists and emits updated value`() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setMonthlyLimitUsd(200f)
        assertEquals(200f, repo.settings.first().monthlyLimitUsd)
    }

    @Test
    @DisplayName("setCostWarnAtPercent persists and emits updated value")
    fun `setCostWarnAtPercent persists and emits updated value`() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setCostWarnAtPercent(90)
        assertEquals(90, repo.settings.first().costWarnAtPercent)
    }

    @Test
    @DisplayName("setProviderRetries persists and emits updated value")
    fun `setProviderRetries persists and emits updated value`() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setProviderRetries(5)
        assertEquals(5, repo.settings.first().providerRetries)
    }

    @Test
    @DisplayName("setFallbackProviders persists and emits updated value")
    fun `setFallbackProviders persists and emits updated value`() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setFallbackProviders("groq, anthropic")
        assertEquals("groq, anthropic", repo.settings.first().fallbackProviders)
    }

    @Test
    @DisplayName("setMemoryBackend persists and emits updated value")
    fun `setMemoryBackend persists and emits updated value`() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setMemoryBackend("markdown")
        assertEquals("markdown", repo.settings.first().memoryBackend)
    }

    @Test
    @DisplayName("setMemoryAutoSave persists and emits updated value")
    fun `setMemoryAutoSave persists and emits updated value`() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setMemoryAutoSave(false)
        assertEquals(false, repo.settings.first().memoryAutoSave)
    }

    @Test
    @DisplayName("setIdentityJson persists and emits updated value")
    fun `setIdentityJson persists and emits updated value`() = runTest {
        val repo = InMemorySettingsRepository()
        repo.setIdentityJson("""{"name":"TestBot"}""")
        assertEquals("""{"name":"TestBot"}""", repo.settings.first().identityJson)
    }
}

/**
 * In-memory [SettingsRepository] for testing.
 *
 * Stores settings in a [MutableStateFlow] without requiring
 * Android context or DataStore infrastructure.
 */
private class InMemorySettingsRepository : SettingsRepository {
    private val _settings = MutableStateFlow(AppSettings())
    override val settings = _settings

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
}
