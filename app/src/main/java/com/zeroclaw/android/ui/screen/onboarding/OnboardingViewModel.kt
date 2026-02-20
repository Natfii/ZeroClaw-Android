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
import com.zeroclaw.android.data.remote.ModelFetcher
import com.zeroclaw.android.model.Agent
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.ChannelType
import com.zeroclaw.android.model.ConnectedChannel
import com.zeroclaw.android.model.ModelListFormat
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

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

    init {
        prefillFromExistingIdentity()
        prefillLockFromSettings()
    }

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

    private val _pinHash = MutableStateFlow("")

    /** PBKDF2 hash of the PIN set during onboarding. */
    val pinHash: StateFlow<String> = _pinHash.asStateFlow()

    private val _lockEnabled = MutableStateFlow(false)

    /** Whether the session lock is enabled. */
    val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()

    private val _biometricUnlockEnabled = MutableStateFlow(false)

    /** Whether biometric unlock is enabled on the lock screen. */
    val biometricUnlockEnabled: StateFlow<Boolean> = _biometricUnlockEnabled.asStateFlow()

    private val _completeError = MutableStateFlow<String?>(null)

    /**
     * One-shot error message surfaced when [complete] detects a bad provider
     * connection. Null when no error is pending. Consumers should call
     * [dismissCompleteError] after displaying the message.
     */
    val completeError: StateFlow<String?> = _completeError.asStateFlow()

    private val _isCompleting = MutableStateFlow(false)

    /** Whether [complete] is currently running (probing or persisting). */
    val isCompleting: StateFlow<Boolean> = _isCompleting.asStateFlow()

    /** Clears the pending [completeError] after it has been shown to the user. */
    fun dismissCompleteError() {
        _completeError.value = null
    }

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
    fun setChannelField(
        key: String,
        value: String,
    ) {
        _channelFieldValues.value = _channelFieldValues.value + (key to value)
    }

    /**
     * Stores the PBKDF2 hash of the PIN configured during onboarding.
     *
     * @param hash Base64-encoded salt+hash string.
     */
    fun setPinHash(hash: String) {
        _pinHash.value = hash
        _lockEnabled.value = hash.isNotEmpty()
    }

    /**
     * Toggles the session lock enabled state.
     *
     * @param enabled Whether the lock is active.
     */
    fun setLockEnabled(enabled: Boolean) {
        _lockEnabled.value = enabled
    }

    /**
     * Toggles biometric unlock during onboarding.
     *
     * @param enabled Whether biometric unlock should be active.
     */
    fun setBiometricUnlockEnabled(enabled: Boolean) {
        _biometricUnlockEnabled.value = enabled
    }

    /**
     * Launches the onboarding completion flow.
     *
     * Guards against double-tap by checking [isCompleting]. Sets [isCompleting]
     * to true for the duration so the UI can disable the button and show a
     * spinner.
     *
     * @param onDone Callback invoked after all data has been persisted.
     */
    fun complete(onDone: () -> Unit) {
        if (_isCompleting.value) return
        viewModelScope.launch {
            _isCompleting.value = true
            try {
                completeInternal(onDone)
            } finally {
                _isCompleting.value = false
            }
        }
    }

    /**
     * Persists provider configuration and marks onboarding complete.
     *
     * Before saving, probes the provider endpoint with [ModelFetcher] to detect
     * bad credentials early. A definitive authentication failure (HTTP 401/403)
     * sets [completeError] and aborts; network errors and 5xx responses are
     * allowed through so that offline use and transient provider issues do not
     * block onboarding. Providers with [ModelListFormat.NONE] are skipped because
     * they have no testable endpoint.
     *
     * Saves the API key (if non-blank), the first agent, a connected channel (if
     * configured), and default provider/model to their respective repositories
     * before marking onboarding complete.
     *
     * @param onDone Callback invoked on the main thread after all data has been
     *   persisted. Navigation should happen here rather than immediately after
     *   calling [complete], because the coroutine needs to finish before the
     *   ViewModel scope is cancelled by a route pop.
     */
    @Suppress("CognitiveComplexMethod")
    private suspend fun completeInternal(onDone: () -> Unit) {
        val provider = _selectedProvider.value
        val key = _apiKey.value
        val model = _selectedModel.value
        val name = _agentName.value

        val url = _baseUrl.value

        if (authErrorForProvider(provider, key, url)) {
            _completeError.value =
                "Invalid API key — verify your credentials before starting the daemon"
            return
        }

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
            val existing =
                agentRepository.agents.first().firstOrNull { it.provider == provider }
            if (existing != null) {
                agentRepository.save(
                    existing.copy(
                        name = name,
                        modelName = model.ifBlank { "default" },
                    ),
                )
            } else {
                agentRepository.save(
                    Agent(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        provider = provider,
                        modelName = model.ifBlank { "default" },
                    ),
                )
            }
        }

        saveChannelIfConfigured()
        ensureIdentity(name)
        scaffoldWorkspaceIfNeeded(name)

        if (provider.isNotBlank()) {
            settingsRepository.setDefaultProvider(provider)
        }
        if (model.isNotBlank()) {
            settingsRepository.setDefaultModel(model)
        }

        val hash = _pinHash.value
        if (hash.isNotEmpty()) {
            settingsRepository.setPinHash(hash)
            settingsRepository.setLockEnabled(_lockEnabled.value)
            settingsRepository.setBiometricUnlockEnabled(_biometricUnlockEnabled.value)
        }

        onboardingRepository.markComplete()
        onDone()
    }

    /**
     * Pre-fills lock settings from existing settings on re-runs.
     *
     * Reads the current [pinHash] and [lockEnabled] values so that
     * re-running the wizard preserves the user's choices.
     */
    private fun prefillLockFromSettings() {
        viewModelScope.launch {
            val current = settingsRepository.settings.first()
            _pinHash.value = current.pinHash
            _lockEnabled.value = current.lockEnabled
            _biometricUnlockEnabled.value = current.biometricUnlockEnabled
        }
    }

    /**
     * Pre-fills [_agentName] from the existing identity JSON on re-runs.
     *
     * Extracts `identity.names.first` from the stored identity JSON and
     * uses it as the initial daemon name, falling back to "My Agent" if
     * no identity is configured yet.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun prefillFromExistingIdentity() {
        viewModelScope.launch {
            try {
                val identityJson = settingsRepository.settings.first().identityJson
                if (identityJson.isNotBlank()) {
                    val root = Json.parseToJsonElement(identityJson).jsonObject
                    val firstName =
                        root["identity"]
                            ?.jsonObject
                            ?.get("names")
                            ?.jsonObject
                            ?.get("first")
                            ?.jsonPrimitive
                            ?.content
                    if (!firstName.isNullOrBlank()) {
                        _agentName.value = firstName
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Writes a minimal AIEOS v1.1 identity JSON from [agentName].
     *
     * Always overwrites the stored identity so that re-running the
     * wizard updates the daemon name.
     *
     * @param agentName Name entered during the daemon naming step.
     */
    private suspend fun ensureIdentity(agentName: String) {
        if (agentName.isBlank()) return

        val json =
            buildJsonObject {
                putJsonObject("identity") {
                    putJsonObject("names") {
                        put("first", agentName)
                    }
                }
            }.toString()
        settingsRepository.setIdentityJson(json)
    }

    /**
     * Scaffolds the workspace directory with identity template files.
     *
     * Best-effort: failures are silently ignored because the daemon can
     * still start without workspace files (it just lacks personalisation).
     *
     * @param agentName Name entered during the daemon naming step.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun scaffoldWorkspaceIfNeeded(agentName: String) {
        try {
            app.daemonBridge.ensureWorkspace(
                agentName = agentName,
                userName = "User",
                timezone = TimeZone.getDefault().id,
                communicationStyle = "",
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Returns true when the provider credentials are definitively rejected (HTTP 401/403).
     *
     * Performs a lightweight [ModelFetcher.fetchModels] probe against the provider
     * endpoint. Returns false for all non-auth failures (network errors, 5xx) so
     * that offline use and transient provider issues do not block onboarding. Also
     * returns false for providers with [ModelListFormat.NONE] that have no testable
     * endpoint.
     *
     * @param providerId Canonical provider identifier.
     * @param key API key value (may be empty for URL-only providers).
     * @param url Base URL override (may be empty for cloud providers).
     * @return True only when HTTP 401 or 403 is returned by the provider.
     */
    private suspend fun authErrorForProvider(
        providerId: String,
        key: String,
        url: String,
    ): Boolean {
        if (providerId.isBlank() || (key.isBlank() && url.isBlank())) return false
        val providerInfo = ProviderRegistry.findById(providerId) ?: return false
        if (providerInfo.modelListFormat == ModelListFormat.NONE) return false
        val probeResult = ModelFetcher.fetchModels(providerInfo, key, url)
        return isDefinitiveAuthFailure(probeResult)
    }

    /**
     * Saves the selected channel type with its field values if all
     * required fields are filled.
     */
    private suspend fun saveChannelIfConfigured() {
        val channelType = _selectedChannelType.value ?: return
        val fields = _channelFieldValues.value
        val requiredFilled =
            channelType.fields
                .filter { it.isRequired }
                .all { fields[it.key]?.isNotBlank() == true }
        if (!requiredFilled) return

        val secretKeys =
            channelType.fields
                .filter { it.isSecret }
                .map { it.key }
                .toSet()
        val (secretEntries, nonSecretEntries) =
            fields.entries.partition { it.key in secretKeys }
        val secrets = secretEntries.associate { it.toPair() }
        val nonSecrets = nonSecretEntries.associate { it.toPair() }

        val channel =
            ConnectedChannel(
                id = UUID.randomUUID().toString(),
                type = channelType,
                configValues = nonSecrets,
            )
        channelConfigRepository.save(channel, secrets)
    }
}

/**
 * Returns true when a model-fetch probe result indicates a definitive
 * authentication failure (HTTP 401 or 403).
 *
 * Returns false for successful probes, non-auth failures (network errors,
 * 5xx responses), and any other unexpected error — these are treated as
 * transient issues that should not block onboarding.
 *
 * This is a package-private function so it can be tested independently
 * without requiring an Android context.
 *
 * @param probeResult Result from [ModelFetcher.fetchModels][com.zeroclaw.android.data.remote.ModelFetcher.fetchModels].
 * @return True only when the probe failed with HTTP 401 or 403.
 */
internal fun isDefinitiveAuthFailure(probeResult: Result<*>): Boolean {
    val failure = probeResult.exceptionOrNull() ?: return false
    val msg = failure.message ?: ""
    return "HTTP 401" in msg || "HTTP 403" in msg
}
