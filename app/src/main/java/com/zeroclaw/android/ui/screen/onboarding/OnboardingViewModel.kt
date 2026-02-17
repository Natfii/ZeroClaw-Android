/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.model.ApiKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Total number of onboarding steps. */
private const val TOTAL_STEPS = 4

/**
 * ViewModel for the onboarding wizard.
 *
 * Tracks the current step, provider/API key input state, and handles
 * completion persistence including saving the API key and default
 * provider/model to their respective repositories.
 *
 * @param application Application context for accessing repositories.
 */
class OnboardingViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = application as ZeroClawApplication
    private val onboardingRepository = app.onboardingRepository
    private val apiKeyRepository = app.apiKeyRepository
    private val settingsRepository = app.settingsRepository

    private val _currentStep = MutableStateFlow(0)

    /** Zero-based index of the current onboarding step. */
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    /** Total number of steps in the wizard. */
    val totalSteps: Int = TOTAL_STEPS

    private val _selectedProvider = MutableStateFlow("")

    /** Canonical provider ID selected in the provider step. */
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _apiKey = MutableStateFlow("")

    /** API key entered in the provider step. */
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _baseUrl = MutableStateFlow("")

    /** Base URL entered in the provider step (pre-filled from registry). */
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _selectedModel = MutableStateFlow("")

    /** Model name selected or typed in the provider step. */
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    /** Advances to the next step. */
    fun nextStep() {
        if (_currentStep.value < TOTAL_STEPS - 1) {
            _currentStep.value++
        }
    }

    /** Returns to the previous step. */
    fun previousStep() {
        if (_currentStep.value > 0) {
            _currentStep.value--
        }
    }

    /**
     * Sets the selected provider and auto-populates base URL and model.
     *
     * @param id Canonical provider ID from the registry.
     */
    fun setProvider(id: String) {
        _selectedProvider.value = id
        val info = ProviderRegistry.findById(id)
        _baseUrl.value = info?.defaultBaseUrl.orEmpty()
        _selectedModel.value = info?.suggestedModels?.firstOrNull().orEmpty()
    }

    /**
     * Updates the API key value.
     *
     * @param key The API key string.
     */
    fun setApiKey(key: String) {
        _apiKey.value = key
    }

    /**
     * Updates the base URL value.
     *
     * @param url The base URL string.
     */
    fun setBaseUrl(url: String) {
        _baseUrl.value = url
    }

    /**
     * Updates the selected model name.
     *
     * @param model The model name string.
     */
    fun setModel(model: String) {
        _selectedModel.value = model
    }

    /**
     * Marks onboarding as completed and persists provider configuration.
     *
     * Saves the API key (if non-blank) and default provider/model to
     * their respective repositories before marking onboarding complete.
     */
    fun complete() {
        viewModelScope.launch {
            val provider = _selectedProvider.value
            val key = _apiKey.value
            val model = _selectedModel.value

            if (provider.isNotBlank() && key.isNotBlank()) {
                apiKeyRepository.save(
                    ApiKey(
                        id = UUID.randomUUID().toString(),
                        provider = provider,
                        key = key,
                    ),
                )
            }

            if (provider.isNotBlank()) {
                settingsRepository.setDefaultProvider(provider)
            }
            if (model.isNotBlank()) {
                settingsRepository.setDefaultModel(model)
            }

            onboardingRepository.markComplete()
        }
    }
}
