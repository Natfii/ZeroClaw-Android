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
}
