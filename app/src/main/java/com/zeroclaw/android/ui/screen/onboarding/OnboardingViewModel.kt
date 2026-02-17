/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.model.Agent
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.ChannelType
import com.zeroclaw.android.model.ConnectedChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Total number of onboarding steps (including channel setup). */
private const val TOTAL_STEPS = 5

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
    private val agentRepository = app.agentRepository
    private val channelConfigRepository = app.channelConfigRepository

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

    private val _agentName = MutableStateFlow("My Agent")

    /** Name for the first agent configured in onboarding. */
    val agentName: StateFlow<String> = _agentName.asStateFlow()

    private val _selectedChannelType = MutableStateFlow<ChannelType?>(null)

    /** Selected channel type for the channel setup step, or null if skipped. */
    val selectedChannelType: StateFlow<ChannelType?> = _selectedChannelType.asStateFlow()

    private val _channelFieldValues = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Field values entered for the selected channel type. */
    val channelFieldValues: StateFlow<Map<String, String>> = _channelFieldValues.asStateFlow()

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
     * Updates the agent name.
     *
     * @param name The agent name string.
     */
    fun setAgentName(name: String) {
        _agentName.value = name
    }

    /**
     * Sets the selected channel type, clearing field values when changing types.
     *
     * @param type The selected channel type, or null to deselect.
     */
    fun setChannelType(type: ChannelType?) {
        _selectedChannelType.value = type
        _channelFieldValues.value = emptyMap()
    }

    /**
     * Updates a single channel field value.
     *
     * @param key The field key.
     * @param value The field value.
     */
    fun setChannelField(key: String, value: String) {
        _channelFieldValues.value = _channelFieldValues.value + (key to value)
    }

    /**
     * Marks onboarding as completed and persists provider configuration.
     *
     * Saves the API key (if non-blank), the first agent, a connected
     * channel (if configured), and default provider/model to their
     * respective repositories before marking onboarding complete.
     *
     * @param onDone Callback invoked on the main thread after all data
     *   has been persisted. Navigation should happen here rather than
     *   immediately after calling [complete], because the coroutine needs
     *   to finish before the ViewModel scope is cancelled by a route pop.
     */
    @Suppress("CognitiveComplexMethod")
    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            val provider = _selectedProvider.value
            val key = _apiKey.value
            val model = _selectedModel.value
            val name = _agentName.value

            val url = _baseUrl.value
            if (provider.isNotBlank() && (key.isNotBlank() || url.isNotBlank())) {
                apiKeyRepository.save(
                    ApiKey(
                        id = UUID.randomUUID().toString(),
                        provider = provider,
                        key = key,
                        baseUrl = url,
                    ),
                )
            }

            if (name.isNotBlank() && provider.isNotBlank()) {
                agentRepository.save(
                    Agent(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        provider = provider,
                        modelName = model.ifBlank { "default" },
                    ),
                )
            }

            saveChannelIfConfigured()

            if (provider.isNotBlank()) {
                settingsRepository.setDefaultProvider(provider)
            }
            if (model.isNotBlank()) {
                settingsRepository.setDefaultModel(model)
            }

            onboardingRepository.markComplete()
            onDone()
        }
    }

    /**
     * Saves the selected channel type with its field values if all
     * required fields are filled.
     */
    private suspend fun saveChannelIfConfigured() {
        val channelType = _selectedChannelType.value ?: return
        val fields = _channelFieldValues.value
        val requiredFilled = channelType.fields
            .filter { it.isRequired }
            .all { fields[it.key]?.isNotBlank() == true }
        if (!requiredFilled) return

        val secretKeys = channelType.fields
            .filter { it.isSecret }
            .map { it.key }
            .toSet()
        val secrets = fields.filter { it.key in secretKeys }
        val nonSecrets = fields.filter { it.key !in secretKeys }

        val channel = ConnectedChannel(
            id = UUID.randomUUID().toString(),
            type = channelType,
            configValues = nonSecrets,
        )
        channelConfigRepository.save(channel, secrets)
    }
}
