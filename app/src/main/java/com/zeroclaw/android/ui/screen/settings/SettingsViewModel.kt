/*
 * Copyright 2026 ZeroClaw Contributors
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

    /**
     * Updates the gateway host address.
     *
     * @param host New bind address.
     */
    fun updateHost(host: String) {
        viewModelScope.launch { repository.setHost(host) }
    }

    /**
     * Updates the gateway port.
     *
     * @param port New bind port.
     */
    fun updatePort(port: Int) {
        viewModelScope.launch { repository.setPort(port) }
    }

    /**
     * Toggles the auto-start on boot setting.
     *
     * @param enabled Whether to start the daemon on boot.
     */
    fun updateAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoStartOnBoot(enabled) }
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
