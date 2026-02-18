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
     * Updates the default inference temperature.
     *
     * @param temperature Temperature value (0.0–2.0).
     */
    fun updateDefaultTemperature(temperature: Float) {
        viewModelScope.launch { repository.setDefaultTemperature(temperature) }
    }

    /**
     * Toggles compact context mode.
     *
     * @param enabled Whether compact context is active.
     */
    fun updateCompactContext(enabled: Boolean) {
        viewModelScope.launch { repository.setCompactContext(enabled) }
    }

    /**
     * Toggles cost limit enforcement.
     *
     * @param enabled Whether spending limits are enforced.
     */
    fun updateCostEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setCostEnabled(enabled) }
    }

    /**
     * Updates the daily cost limit.
     *
     * @param limit Maximum daily spend in USD.
     */
    fun updateDailyLimitUsd(limit: Float) {
        viewModelScope.launch { repository.setDailyLimitUsd(limit) }
    }

    /**
     * Updates the monthly cost limit.
     *
     * @param limit Maximum monthly spend in USD.
     */
    fun updateMonthlyLimitUsd(limit: Float) {
        viewModelScope.launch { repository.setMonthlyLimitUsd(limit) }
    }

    /**
     * Updates the cost warning threshold.
     *
     * @param percent Percentage of limit at which to warn.
     */
    fun updateCostWarnAtPercent(percent: Int) {
        viewModelScope.launch { repository.setCostWarnAtPercent(percent) }
    }

    /**
     * Updates the number of provider retries.
     *
     * @param retries Retry count.
     */
    fun updateProviderRetries(retries: Int) {
        viewModelScope.launch { repository.setProviderRetries(retries) }
    }

    /**
     * Updates the fallback providers list.
     *
     * @param providers Comma-separated provider IDs.
     */
    fun updateFallbackProviders(providers: String) {
        viewModelScope.launch { repository.setFallbackProviders(providers) }
    }

    /**
     * Updates the memory backend.
     *
     * @param backend Backend name.
     */
    fun updateMemoryBackend(backend: String) {
        viewModelScope.launch { repository.setMemoryBackend(backend) }
    }

    /**
     * Toggles the memory auto-save setting.
     *
     * @param enabled Whether the memory backend auto-saves context.
     */
    fun updateMemoryAutoSave(enabled: Boolean) {
        viewModelScope.launch { repository.setMemoryAutoSave(enabled) }
    }

    /**
     * Updates the AIEOS identity JSON.
     *
     * @param json AIEOS v1.1 JSON string.
     */
    fun updateIdentityJson(json: String) {
        viewModelScope.launch { repository.setIdentityJson(json) }
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
