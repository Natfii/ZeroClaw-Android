/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings

import com.zeroclaw.android.model.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SettingsRepository][com.zeroclaw.android.data.repository.SettingsRepository]
 * contract validation.
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
    @DisplayName("lock enabled defaults to false")
    fun `lock enabled defaults to false`() =
        runTest {
            val repo = TestSettingsRepository()
            assertEquals(false, repo.settings.first().lockEnabled)
        }

    @Test
    @DisplayName("setLockEnabled updates repository")
    fun `setLockEnabled updates repository`() =
        runTest {
            val repo = TestSettingsRepository()
            repo.setLockEnabled(true)
            assertEquals(true, repo.settings.first().lockEnabled)
            repo.setLockEnabled(false)
            assertEquals(false, repo.settings.first().lockEnabled)
        }

    @Test
    @DisplayName("setLockTimeoutMinutes updates repository")
    fun `setLockTimeoutMinutes updates repository`() =
        runTest {
            val repo = TestSettingsRepository()
            val timeout = 30
            repo.setLockTimeoutMinutes(timeout)
            assertEquals(timeout, repo.settings.first().lockTimeoutMinutes)
        }

    @Test
    @DisplayName("setPinHash updates repository")
    fun `setPinHash updates repository`() =
        runTest {
            val repo = TestSettingsRepository()
            val hash = "test-pin-hash-value"
            repo.setPinHash(hash)
            assertEquals(hash, repo.settings.first().pinHash)
        }

    @Test
    @DisplayName("pinHash defaults to empty string")
    fun `pinHash defaults to empty string`() =
        runTest {
            val repo = TestSettingsRepository()
            assertEquals("", repo.settings.first().pinHash)
        }

    @Test
    @DisplayName("lockTimeoutMinutes defaults to DEFAULT_LOCK_TIMEOUT")
    fun `lockTimeoutMinutes defaults to DEFAULT_LOCK_TIMEOUT`() =
        runTest {
            val repo = TestSettingsRepository()
            assertEquals(
                AppSettings.DEFAULT_LOCK_TIMEOUT,
                repo.settings.first().lockTimeoutMinutes,
            )
        }
}
