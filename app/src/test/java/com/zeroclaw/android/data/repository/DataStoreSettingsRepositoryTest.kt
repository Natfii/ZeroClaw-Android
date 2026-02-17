/*
 * Copyright 2026 ZeroClaw Contributors
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
}
