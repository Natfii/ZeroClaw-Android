/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

@file:Suppress("TooManyFunctions")

package com.zeroclaw.android.ui.screen.onboarding

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.data.channel.ChannelSetupSpecs
import com.zeroclaw.android.data.oauth.AuthProfileWriter
import com.zeroclaw.android.data.oauth.OAuthCallbackServer
import com.zeroclaw.android.data.oauth.OpenAiOAuthManager
import com.zeroclaw.android.data.oauth.PkceState
import com.zeroclaw.android.data.remote.ModelFetcher
import com.zeroclaw.android.data.validation.ChannelValidator
import com.zeroclaw.android.data.validation.ProviderValidator
import com.zeroclaw.android.data.validation.ValidationResult
import com.zeroclaw.android.model.Agent
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.ChannelType
import com.zeroclaw.android.model.ConnectedChannel
import com.zeroclaw.android.model.ModelListFormat
import com.zeroclaw.android.model.ServiceState
import com.zeroclaw.android.service.ZeroClawDaemonService
import com.zeroclaw.android.ui.component.setup.ConfigSummary
import com.zeroclaw.android.ui.screen.onboarding.state.ActivationStepState
import com.zeroclaw.android.ui.screen.onboarding.state.ChannelSelectionState
import com.zeroclaw.android.ui.screen.onboarding.state.ChannelSubFlowState
import com.zeroclaw.android.ui.screen.onboarding.state.IdentityStepState
import com.zeroclaw.android.ui.screen.onboarding.state.MemoryStepState
import com.zeroclaw.android.ui.screen.onboarding.state.ProviderStepState
import com.zeroclaw.android.ui.screen.onboarding.state.SecurityStepState
import com.zeroclaw.android.ui.screen.onboarding.state.TunnelStepState
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Debounce delay in milliseconds before fetching models after input changes. */
private const val MODEL_FETCH_DEBOUNCE_MS = 500L

/**
 * Sharing timeout for derived [StateFlow] instances.
 *
 * Keeps the upstream subscription alive for 5 seconds after the last
 * subscriber disconnects, avoiding unnecessary recomputation when the
 * UI is briefly detached (e.g. during configuration changes).
 */
private const val SHARING_TIMEOUT_MS = 5000L

/**
 * Coordinator ViewModel for the 9-step onboarding wizard.
 *
 * Replaces [OnboardingViewModel] with expanded support for multiple channels,
 * tunnel configuration, security/autonomy settings, memory backend selection,
 * and identity customisation. All existing completion logic from the original
 * ViewModel is preserved.
 *
 * Each step has its own typed state class exposed as a [StateFlow]. The
 * coordinator manages step navigation, validation, model fetching, and
 * the final completion flow that persists all configuration.
 *
 * @param application Application context for accessing repositories.
 */
@Suppress("LongParameterList", "CognitiveComplexMethod", "LargeClass")
class OnboardingCoordinator(
    application: Application,
) : AndroidViewModel(application) {
    private val app = application as ZeroClawApplication
    private val onboardingRepository = app.onboardingRepository
    private val apiKeyRepository = app.apiKeyRepository
    private val settingsRepository = app.settingsRepository
    private val agentRepository = app.agentRepository
    private val channelConfigRepository = app.channelConfigRepository

    private val _currentStep = MutableStateFlow(STEP_PERMISSIONS)

    /** Zero-based index of the current onboarding step. */
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    /** Total number of steps in the wizard. */
    val totalSteps: Int = TOTAL_STEPS

    /** Step index and total constants for onboarding navigation. */
    companion object {
        /** Step index: runtime permissions. */
        const val STEP_PERMISSIONS = 0

        /** Step index: welcome screen. */
        const val STEP_WELCOME = 1

        /** Step index: provider and API key configuration. */
        const val STEP_PROVIDER = 2

        /** Step index: channel selection and sub-flow configuration. */
        const val STEP_CHANNELS = 3

        /** Step index: tunnel configuration. */
        const val STEP_TUNNEL = 4

        /** Step index: security/autonomy level. */
        const val STEP_SECURITY = 5

        /** Step index: memory backend configuration. */
        const val STEP_MEMORY = 6

        /** Step index: agent identity configuration. */
        const val STEP_IDENTITY = 7

        /** Step index: activation and completion. */
        const val STEP_ACTIVATION = 8

        /** Total number of onboarding steps. */
        const val TOTAL_STEPS = 9

        /** Canonical provider ID for OpenAI API-key access. */
        private const val OPENAI_PROVIDER = "openai"

        /** Canonical provider ID for ChatGPT Codex OAuth access. */
        private const val CODEX_PROVIDER = "openai-codex"
    }

    private val _pinHash = MutableStateFlow("")

    /** PBKDF2 hash of the PIN set during onboarding. */
    val pinHash: StateFlow<String> = _pinHash.asStateFlow()

    private val _lockEnabled = MutableStateFlow(false)

    /** Whether the session lock is enabled. */
    val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()

    private val _providerState = MutableStateFlow(ProviderStepState())

    /** Observable state for the provider configuration step. */
    val providerState: StateFlow<ProviderStepState> = _providerState.asStateFlow()

    private val _channelSelectionState = MutableStateFlow(ChannelSelectionState())

    /** Observable state for channel type selection. */
    val channelSelectionState: StateFlow<ChannelSelectionState> =
        _channelSelectionState.asStateFlow()

    private val _channelSubFlowStates =
        MutableStateFlow<Map<ChannelType, ChannelSubFlowState>>(emptyMap())

    /** Observable map of per-channel sub-flow states. */
    val channelSubFlowStates: StateFlow<Map<ChannelType, ChannelSubFlowState>> =
        _channelSubFlowStates.asStateFlow()

    private val _tunnelState = MutableStateFlow(TunnelStepState())

    /** Observable state for tunnel configuration. */
    val tunnelState: StateFlow<TunnelStepState> = _tunnelState.asStateFlow()

    private val _securityState = MutableStateFlow(SecurityStepState())

    /** Observable state for security/autonomy configuration. */
    val securityState: StateFlow<SecurityStepState> = _securityState.asStateFlow()

    private val _memoryState = MutableStateFlow(MemoryStepState())

    /** Observable state for memory backend configuration. */
    val memoryState: StateFlow<MemoryStepState> = _memoryState.asStateFlow()

    private val _identityState = MutableStateFlow(IdentityStepState())

    /** Observable state for identity configuration. */
    val identityState: StateFlow<IdentityStepState> = _identityState.asStateFlow()

    private val _activationState = MutableStateFlow(ActivationStepState())

    /** Observable state for the activation/completion step. */
    val activationState: StateFlow<ActivationStepState> = _activationState.asStateFlow()

    /** Debounce job for model fetching after provider/key changes. */
    private var modelFetchJob: Job? = null

    /**
     * Derived configuration summary combining all step states.
     *
     * The summary is recomputed whenever any contributing state changes and
     * is displayed on the activation step as a read-only overview.
     */
    @Suppress("SpreadOperator", "MagicNumber")
    val configSummary: StateFlow<ConfigSummary> =
        combine(
            _providerState,
            _channelSelectionState,
            _tunnelState,
            _securityState,
            _memoryState,
            _identityState,
        ) { states ->
            val provider = states[0] as ProviderStepState
            val channels = states[1] as ChannelSelectionState
            val tunnel = states[2] as TunnelStepState
            val security = states[3] as SecurityStepState
            val memory = states[4] as MemoryStepState
            val identity = states[5] as IdentityStepState
            ConfigSummary(
                provider = provider.providerId,
                model = provider.model,
                autonomy = security.autonomyLevel,
                memoryBackend = memory.backend,
                autoSave = memory.autoSave,
                channels = channels.selectedTypes.map { it.displayName },
                tunnel = tunnel.tunnelType,
                identityFormat = identity.identityFormat,
                agentName = identity.agentName,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(SHARING_TIMEOUT_MS),
            ConfigSummary(),
        )

    init {
        prefillFromExistingIdentity()
        prefillLockFromSettings()
        prefillFromExistingSettings()
    }

    /**
     * Advances to the next step if not at the last step.
     */
    fun nextStep() {
        if (_currentStep.value < TOTAL_STEPS - 1) {
            _currentStep.value++
        }
    }

    /**
     * Returns to the previous step if not at the first step.
     */
    fun previousStep() {
        if (_currentStep.value > 0) {
            _currentStep.value--
        }
    }

    /**
     * Stores the PBKDF2 hash of the PIN configured during onboarding.
     *
     * Also enables the session lock when a non-empty hash is provided.
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
     * Sets the selected provider and auto-populates base URL and model.
     *
     * Triggers a debounced model fetch if the provider has a model listing endpoint.
     *
     * @param id Canonical provider ID from the registry.
     */
    fun setProvider(id: String) {
        val info = ProviderRegistry.findById(id)
        _providerState.value =
            _providerState.value.copy(
                providerId = id,
                baseUrl = info?.defaultBaseUrl.orEmpty(),
                model = info?.suggestedModels?.firstOrNull().orEmpty(),
                validationResult = ValidationResult.Idle,
                availableModels = emptyList(),
            )
        scheduleFetchModels()
    }

    /**
     * Updates the API key value and triggers a debounced model fetch.
     *
     * @param key The API key string.
     */
    fun setApiKey(key: String) {
        _providerState.value =
            _providerState.value.copy(
                apiKey = key,
                validationResult = ValidationResult.Idle,
            )
        scheduleFetchModels()
    }

    /**
     * Updates the base URL value.
     *
     * @param url The base URL string.
     */
    fun setBaseUrl(url: String) {
        _providerState.value = _providerState.value.copy(baseUrl = url)
    }

    /**
     * Updates the selected model name.
     *
     * @param model The model name string.
     */
    fun setModel(model: String) {
        _providerState.value = _providerState.value.copy(model = model)
    }

    /**
     * Launches the full OpenAI OAuth 2.0 PKCE login flow.
     *
     * Generates PKCE state, starts a loopback callback server, opens a Chrome
     * Custom Tab for the user to authenticate, and exchanges the returned
     * authorization code for access and refresh tokens. On success, the
     * provider state is updated with the OAuth tokens and a success
     * validation result. On failure, the validation result is set to an
     * appropriate error state.
     *
     * Safe to call from the main thread; all network operations run on
     * background dispatchers.
     *
     * @param context Activity or application context used to launch the
     *   Chrome Custom Tab.
     */
    @Suppress("TooGenericExceptionCaught")
    fun startOAuthLogin(context: Context) {
        viewModelScope.launch {
            _providerState.update {
                it.copy(isOAuthInProgress = true)
            }
            val pkce = OpenAiOAuthManager.generatePkceState()
            var server: OAuthCallbackServer? = null
            try {
                holdForegroundForOAuth(context)
                server = OAuthCallbackServer.startWithFallback()
                val port = server.boundPort
                val url = OpenAiOAuthManager.buildAuthorizeUrl(pkce, port)
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                handleOAuthCallback(server, pkce, port, context)
            } catch (e: Exception) {
                _providerState.update {
                    it.copy(
                        isOAuthInProgress = false,
                        validationResult =
                            ValidationResult.Offline(
                                e.message ?: "OAuth login failed",
                            ),
                    )
                }
            } finally {
                server?.stop()
                releaseOAuthHold(context)
            }
        }
    }

    /**
     * Processes the OAuth callback from the loopback server.
     *
     * Awaits the callback result, validates the CSRF state nonce, and
     * exchanges the authorization code for tokens. Updates the provider
     * state with the result.
     *
     * @param server The running callback server to await results from.
     * @param pkce The PKCE state containing the expected state nonce and
     *   code verifier for the token exchange.
     * @param port The actual port the callback server is bound to.
     * @param context Context for bringing the app back to the foreground.
     */
    private suspend fun handleOAuthCallback(
        server: OAuthCallbackServer,
        pkce: PkceState,
        port: Int,
        context: Context,
    ) {
        val callbackResult = server.awaitCallback()
        bringAppToForeground(context)
        if (callbackResult == null) {
            _providerState.update {
                it.copy(
                    isOAuthInProgress = false,
                    validationResult =
                        ValidationResult.Failure("Login timed out"),
                )
            }
            return
        }

        if (callbackResult.state != pkce.state) {
            _providerState.update {
                it.copy(
                    isOAuthInProgress = false,
                    validationResult =
                        ValidationResult.Failure("Security validation failed"),
                )
            }
            return
        }

        val tokens =
            OpenAiOAuthManager.exchangeCodeForTokens(
                code = callbackResult.code,
                codeVerifier = pkce.codeVerifier,
                port = port,
            )
        AuthProfileWriter.writeCodexProfile(
            context = getApplication(),
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresAtMs = tokens.expiresAt,
        )
        cleanupStaleOpenAiEntries()
        migrateAgentsToCodex()
        _providerState.update {
            it.copy(
                providerId = "openai-codex",
                oauthRefreshToken = tokens.refreshToken,
                oauthExpiresAt = tokens.expiresAt,
                oauthEmail = "ChatGPT Login",
                validationResult =
                    ValidationResult.Success("OAuth login successful"),
                isOAuthInProgress = false,
            )
        }
    }

    /**
     * Clears all OAuth-related fields from the provider state.
     *
     * Resets the API key, refresh token, expiry, email, and validation
     * result to their defaults. Used when the user disconnects their
     * OAuth session during onboarding.
     */
    fun disconnectOAuth() {
        AuthProfileWriter.removeCodexProfile(getApplication())
        _providerState.update {
            it.copy(
                providerId = "openai",
                apiKey = "",
                oauthRefreshToken = "",
                oauthExpiresAt = 0L,
                oauthEmail = "",
                validationResult = ValidationResult.Idle,
            )
        }
    }

    /**
     * Starts the daemon service in OAuth-hold mode to prevent process freezing.
     *
     * Android 12+ freezes cached-app processes within seconds when no
     * foreground service is active. This hold keeps the process alive while
     * the user authenticates in a Custom Tab or 2FA app.
     *
     * @param context Context for starting the service.
     */
    private fun holdForegroundForOAuth(context: Context) {
        val intent =
            Intent(context, ZeroClawDaemonService::class.java).apply {
                action = ZeroClawDaemonService.ACTION_OAUTH_HOLD
            }
        context.startForegroundService(intent)
    }

    /**
     * Stops the OAuth-hold foreground service if the daemon is not running.
     *
     * @param context Context for stopping the service.
     */
    private fun releaseOAuthHold(context: Context) {
        val app = getApplication<ZeroClawApplication>()
        if (app.daemonBridge.serviceState.value != ServiceState.RUNNING) {
            val intent =
                Intent(context, ZeroClawDaemonService::class.java).apply {
                    action = ZeroClawDaemonService.ACTION_STOP
                }
            context.startService(intent)
        }
    }

    /**
     * Brings the app to the foreground to dismiss the Custom Tab overlay.
     *
     * Uses the package launch intent with [Intent.FLAG_ACTIVITY_SINGLE_TOP]
     * to resume the existing activity rather than creating a new one.
     *
     * @param context Context for launching the intent.
     */
    private fun bringAppToForeground(context: Context) {
        val intent =
            context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                ?: return
        context.startActivity(intent)
    }

    /**
     * Removes any existing "openai" API keys that have an empty key value.
     *
     * These are stale entries left over from previous OAuth attempts that
     * created a key under the wrong provider ID. Called before saving the
     * correct "openai-codex" key.
     */
    private suspend fun cleanupStaleOpenAiEntries() {
        val allKeys = apiKeyRepository.keys.first()
        allKeys
            .filter { it.provider == OPENAI_PROVIDER && it.key.isBlank() }
            .forEach { apiKeyRepository.delete(it.id) }
    }

    /**
     * Migrates any agents using the "openai" provider to "openai-codex".
     *
     * When the user completes ChatGPT OAuth, agents that were created
     * against the "openai" provider need to be re-pointed to "openai-codex"
     * so the daemon uses the correct API endpoint and OAuth tokens.
     */
    private suspend fun migrateAgentsToCodex() {
        val agents = agentRepository.agents.first()
        agents
            .filter { it.provider == OPENAI_PROVIDER }
            .forEach { agent ->
                agentRepository.save(agent.copy(provider = CODEX_PROVIDER))
            }
    }

    /**
     * Validates the current provider credentials by probing the model listing endpoint.
     *
     * Sets [ProviderStepState.validationResult] to [ValidationResult.Loading] immediately,
     * then launches a coroutine that calls [ProviderValidator.validate] and updates the
     * result to a terminal state.
     */
    fun validateProvider() {
        val state = _providerState.value
        _providerState.value = state.copy(validationResult = ValidationResult.Loading)
        viewModelScope.launch {
            val result =
                ProviderValidator.validate(
                    providerId = state.providerId,
                    apiKey = state.apiKey,
                    baseUrl = state.baseUrl,
                )
            _providerState.value = _providerState.value.copy(validationResult = result)
        }
    }

    /**
     * Toggles a channel type in the selection set.
     *
     * Adding a type initialises its sub-flow state. Removing a type preserves
     * the sub-flow state so it can be restored if re-selected.
     *
     * @param type The channel type to toggle.
     */
    fun toggleChannelSelection(type: ChannelType) {
        val current = _channelSelectionState.value.selectedTypes
        val updated =
            if (type in current) {
                current - type
            } else {
                current + type
            }
        _channelSelectionState.value =
            _channelSelectionState.value.copy(selectedTypes = updated)

        if (type !in _channelSubFlowStates.value) {
            _channelSubFlowStates.value =
                _channelSubFlowStates.value + (type to ChannelSubFlowState())
        }
    }

    /**
     * Opens the configuration sub-flow for a specific channel type.
     *
     * @param type The channel type whose sub-flow to display.
     */
    fun startChannelSubFlow(type: ChannelType) {
        _channelSelectionState.value =
            _channelSelectionState.value.copy(activeSubFlowType = type)
        if (type !in _channelSubFlowStates.value) {
            _channelSubFlowStates.value =
                _channelSubFlowStates.value + (type to ChannelSubFlowState())
        }
    }

    /**
     * Exits the current channel sub-flow and marks it as completed.
     */
    fun exitChannelSubFlow() {
        val activeType = _channelSelectionState.value.activeSubFlowType ?: return
        val subFlow = _channelSubFlowStates.value[activeType] ?: return
        _channelSubFlowStates.value =
            _channelSubFlowStates.value + (activeType to subFlow.copy(completed = true))
        _channelSelectionState.value =
            _channelSelectionState.value.copy(activeSubFlowType = null)
    }

    /**
     * Updates a single field value within a channel's sub-flow state.
     *
     * @param type The channel type whose field to update.
     * @param key The field key matching a TOML configuration key.
     * @param value The field value.
     */
    fun setChannelField(
        type: ChannelType,
        key: String,
        value: String,
    ) {
        val subFlow = _channelSubFlowStates.value[type] ?: ChannelSubFlowState()
        _channelSubFlowStates.value =
            _channelSubFlowStates.value +
            (
                type to
                    subFlow.copy(
                        fieldValues = subFlow.fieldValues + (key to value),
                    )
            )
    }

    /**
     * Advances to the next sub-step within a channel's sub-flow.
     *
     * Bounds-checked against the number of steps defined in
     * [ChannelSetupSpecs] for the given channel type.
     *
     * @param type The channel type.
     */
    fun nextChannelSubStep(type: ChannelType) {
        val subFlow = _channelSubFlowStates.value[type] ?: return
        val maxSteps = ChannelSetupSpecs.forType(type)?.steps?.size ?: return
        if (subFlow.currentSubStep < maxSteps - 1) {
            _channelSubFlowStates.value =
                _channelSubFlowStates.value +
                (type to subFlow.copy(currentSubStep = subFlow.currentSubStep + 1))
        }
    }

    /**
     * Returns to the previous sub-step within a channel's sub-flow.
     *
     * @param type The channel type.
     */
    fun previousChannelSubStep(type: ChannelType) {
        val subFlow = _channelSubFlowStates.value[type] ?: return
        if (subFlow.currentSubStep > 0) {
            _channelSubFlowStates.value =
                _channelSubFlowStates.value +
                (type to subFlow.copy(currentSubStep = subFlow.currentSubStep - 1))
        }
    }

    /**
     * Validates the current channel's token or credentials.
     *
     * Sets [ChannelSubFlowState.validationResult] to [ValidationResult.Loading]
     * immediately, then launches a coroutine that calls [ChannelValidator.validate]
     * and updates the result to a terminal state.
     *
     * @param type The channel type to validate.
     */
    fun validateChannel(type: ChannelType) {
        val subFlow = _channelSubFlowStates.value[type] ?: return
        _channelSubFlowStates.value =
            _channelSubFlowStates.value +
            (type to subFlow.copy(validationResult = ValidationResult.Loading))
        viewModelScope.launch {
            val result =
                ChannelValidator.validate(
                    channelType = type,
                    fields = subFlow.fieldValues,
                )
            val current = _channelSubFlowStates.value[type] ?: return@launch
            _channelSubFlowStates.value =
                _channelSubFlowStates.value +
                (type to current.copy(validationResult = result))
        }
    }

    /**
     * Sets the tunnel provider type.
     *
     * @param type One of "none", "cloudflare", "tailscale", "ngrok", "custom".
     */
    fun setTunnelType(type: String) {
        _tunnelState.value = TunnelStepState(tunnelType = type)
    }

    /**
     * Sets the tunnel authentication token.
     *
     * @param token Authentication token for the selected tunnel provider.
     */
    fun setTunnelToken(token: String) {
        _tunnelState.value = _tunnelState.value.copy(tunnelToken = token)
    }

    /**
     * Sets the custom tunnel endpoint URL.
     *
     * @param endpoint User-provided endpoint URL.
     */
    fun setCustomEndpoint(endpoint: String) {
        _tunnelState.value = _tunnelState.value.copy(customEndpoint = endpoint)
    }

    /**
     * Sets the autonomy level.
     *
     * @param level One of "supervised", "constrained", or "unconstrained".
     */
    fun setAutonomyLevel(level: String) {
        _securityState.value = _securityState.value.copy(autonomyLevel = level)
    }

    /**
     * Sets the memory backend.
     *
     * @param backend One of "sqlite", "none", "markdown", or "lucid".
     */
    fun setMemoryBackend(backend: String) {
        _memoryState.value = _memoryState.value.copy(backend = backend)
    }

    /**
     * Toggles the memory auto-save setting.
     *
     * @param enabled Whether auto-save is active.
     */
    fun setAutoSave(enabled: Boolean) {
        _memoryState.value = _memoryState.value.copy(autoSave = enabled)
    }

    /**
     * Sets the embedding provider.
     *
     * @param provider One of "none", "openai", or "custom:URL".
     */
    fun setEmbeddingProvider(provider: String) {
        _memoryState.value = _memoryState.value.copy(embeddingProvider = provider)
    }

    /**
     * Sets the memory retention period.
     *
     * @param days Number of days before memory entries are archived.
     */
    fun setRetentionDays(days: Int) {
        _memoryState.value = _memoryState.value.copy(retentionDays = days)
    }

    /**
     * Sets the agent name.
     *
     * @param name Name for the AI agent.
     */
    fun setAgentName(name: String) {
        _identityState.value = _identityState.value.copy(agentName = name)
    }

    /**
     * Sets the human user's name.
     *
     * @param name User's display name.
     */
    fun setUserName(name: String) {
        _identityState.value = _identityState.value.copy(userName = name)
    }

    /**
     * Sets the timezone.
     *
     * @param tz IANA timezone ID (e.g. "America/New_York").
     */
    fun setTimezone(tz: String) {
        _identityState.value = _identityState.value.copy(timezone = tz)
    }

    /**
     * Sets the communication style.
     *
     * @param style Preferred communication style or tone description.
     */
    fun setCommunicationStyle(style: String) {
        _identityState.value = _identityState.value.copy(communicationStyle = style)
    }

    /**
     * Sets the identity format.
     *
     * @param format One of "openclaw" or "aieos".
     */
    fun setIdentityFormat(format: String) {
        _identityState.value = _identityState.value.copy(identityFormat = format)
    }

    /**
     * Clears the pending completion error after it has been shown to the user.
     */
    fun dismissCompleteError() {
        _activationState.value = _activationState.value.copy(completeError = null)
    }

    /**
     * Launches the onboarding completion flow.
     *
     * Guards against double-tap by checking [ActivationStepState.isCompleting].
     * Sets [ActivationStepState.isCompleting] to true for the duration so the
     * UI can disable the button and show a spinner.
     *
     * @param onDone Callback invoked after all data has been persisted.
     */
    fun complete(onDone: () -> Unit) {
        if (_activationState.value.isCompleting) return
        viewModelScope.launch {
            _activationState.value = _activationState.value.copy(isCompleting = true)
            try {
                completeInternal(onDone)
            } finally {
                _activationState.value = _activationState.value.copy(isCompleting = false)
            }
        }
    }

    /**
     * Schedules a debounced model fetch after provider or API key changes.
     *
     * Cancels any pending fetch and waits [MODEL_FETCH_DEBOUNCE_MS] before
     * starting the network call. This prevents excessive requests while the
     * user is still typing.
     */
    private fun scheduleFetchModels() {
        modelFetchJob?.cancel()
        modelFetchJob =
            viewModelScope.launch {
                delay(MODEL_FETCH_DEBOUNCE_MS)
                fetchModels()
            }
    }

    /**
     * Fetches available models from the current provider's API.
     *
     * Updates [ProviderStepState.isLoadingModels] and [ProviderStepState.availableModels].
     * Silently ignores failures since model listing is a convenience feature.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun fetchModels() {
        val state = _providerState.value
        if (state.providerId.isBlank()) return
        val info = ProviderRegistry.findById(state.providerId) ?: return
        if (info.modelListFormat == ModelListFormat.NONE) return
        if (state.apiKey.isBlank() && state.baseUrl.isBlank()) return

        _providerState.value = state.copy(isLoadingModels = true)
        try {
            val result = ModelFetcher.fetchModels(info, state.apiKey, state.baseUrl)
            result.onSuccess { models ->
                _providerState.value =
                    _providerState.value.copy(
                        availableModels = models,
                        isLoadingModels = false,
                    )
            }
            result.onFailure {
                _providerState.value = _providerState.value.copy(isLoadingModels = false)
            }
        } catch (_: Exception) {
            _providerState.value = _providerState.value.copy(isLoadingModels = false)
        }
    }

    /**
     * Pre-fills lock settings from existing settings on re-runs.
     *
     * Reads the current PIN hash and lock enabled values so that
     * re-running the wizard preserves the user's choices.
     */
    private fun prefillLockFromSettings() {
        viewModelScope.launch {
            val current = settingsRepository.settings.first()
            _pinHash.value = current.pinHash
            _lockEnabled.value = current.lockEnabled
        }
    }

    /**
     * Pre-fills agent name and identity fields from the existing identity JSON.
     *
     * Extracts `identity.names.first` and other identity fields from stored
     * JSON and uses them as initial values. Falls back to defaults if no
     * identity is configured yet.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun prefillFromExistingIdentity() {
        viewModelScope.launch {
            try {
                val identityJson = settingsRepository.settings.first().identityJson
                if (identityJson.isNotBlank()) {
                    val root = Json.parseToJsonElement(identityJson).jsonObject
                    val identity = root["identity"]?.jsonObject
                    val names = identity?.get("names")?.jsonObject
                    val firstName = names?.get("first")?.jsonPrimitive?.content
                    if (!firstName.isNullOrBlank()) {
                        _identityState.value =
                            _identityState.value.copy(agentName = firstName)
                    }
                    val userName = identity?.get("user_name")?.jsonPrimitive?.content
                    if (!userName.isNullOrBlank()) {
                        _identityState.value =
                            _identityState.value.copy(userName = userName)
                    }
                    val tz = identity?.get("timezone")?.jsonPrimitive?.content
                    if (!tz.isNullOrBlank()) {
                        _identityState.value =
                            _identityState.value.copy(timezone = tz)
                    }
                    val style = identity?.get("communication_style")?.jsonPrimitive?.content
                    if (!style.isNullOrBlank()) {
                        _identityState.value =
                            _identityState.value.copy(communicationStyle = style)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Pre-fills tunnel, autonomy, memory, and provider settings from
     * existing [AppSettings][com.zeroclaw.android.model.AppSettings].
     *
     * Allows users who re-run the wizard to see their previously configured
     * values instead of defaults.
     */
    private fun prefillFromExistingSettings() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()

            if (settings.defaultProvider.isNotBlank()) {
                val info = ProviderRegistry.findById(settings.defaultProvider)
                _providerState.value =
                    _providerState.value.copy(
                        providerId = settings.defaultProvider,
                        baseUrl = info?.defaultBaseUrl.orEmpty(),
                        model = settings.defaultModel,
                    )
            }

            _tunnelState.value =
                TunnelStepState(
                    tunnelType = settings.tunnelProvider,
                    tunnelToken =
                        when (settings.tunnelProvider) {
                            "cloudflare" -> settings.tunnelCloudflareToken
                            "ngrok" -> settings.tunnelNgrokAuthToken
                            else -> ""
                        },
                    customEndpoint =
                        when (settings.tunnelProvider) {
                            "custom" -> settings.tunnelCustomHealthUrl
                            "tailscale" -> settings.tunnelTailscaleHostname
                            else -> ""
                        },
                )

            _securityState.value =
                SecurityStepState(autonomyLevel = settings.autonomyLevel)

            _memoryState.value =
                MemoryStepState(
                    backend = settings.memoryBackend,
                    autoSave = settings.memoryAutoSave,
                    embeddingProvider = settings.memoryEmbeddingProvider,
                    retentionDays = settings.memoryArchiveAfterDays,
                )
        }
    }

    /**
     * Persists all onboarding configuration and marks onboarding complete.
     *
     * This method preserves all existing logic from [OnboardingViewModel.completeInternal]
     * and extends it with tunnel, autonomy, memory, and enhanced identity persistence.
     *
     * Steps:
     * 1. Provider auth probing (HTTP 401/403 detection)
     * 2. API key saving
     * 3. Agent saving (create or update)
     * 4. Channel saving for all selected channels with filled required fields
     * 5. Identity JSON writing with expanded fields
     * 6. Workspace scaffolding with user name, timezone, communication style
     * 7. Default provider/model persistence
     * 8. Tunnel configuration persistence
     * 9. Autonomy level persistence
     * 10. Memory configuration persistence
     * 11. PIN hash/lock persistence
     * 12. Onboarding completion flag
     *
     * @param onDone Callback invoked on the main thread after all data has been
     *   persisted. Navigation should happen here rather than immediately after
     *   calling [complete], because the coroutine needs to finish before the
     *   ViewModel scope is cancelled by a route pop.
     */
    @Suppress("CognitiveComplexMethod", "CyclomaticComplexMethod", "LongMethod")
    private suspend fun completeInternal(onDone: () -> Unit) {
        val provider = _providerState.value.providerId
        val key = _providerState.value.apiKey
        val model = _providerState.value.model
        val url = _providerState.value.baseUrl
        val identity = _identityState.value
        val name = identity.agentName

        val isOAuthSession = _providerState.value.oauthEmail.isNotEmpty()
        if (!isOAuthSession && authErrorForProvider(provider, key, url)) {
            _activationState.value =
                _activationState.value.copy(
                    completeError =
                        "Invalid API key \u2014 verify your credentials before " +
                            "starting the daemon",
                )
            return
        }

        val hasCredentials =
            key.isNotBlank() || url.isNotBlank() || isOAuthSession
        if (provider.isNotBlank() && hasCredentials) {
            saveProviderApiKey(provider, key, url)
        }

        if (name.isNotBlank() && provider.isNotBlank()) {
            val canonicalId =
                ProviderRegistry.findById(provider)?.id ?: provider.lowercase()
            val existing =
                agentRepository.agents.first().firstOrNull { agent ->
                    val agentCanonical =
                        ProviderRegistry.findById(agent.provider)?.id
                            ?: agent.provider.lowercase()
                    agentCanonical == canonicalId
                }
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

        saveAllConfiguredChannels()
        ensureIdentity(identity)
        scaffoldWorkspaceIfNeeded(identity)

        if (provider.isNotBlank()) {
            settingsRepository.setDefaultProvider(provider)
        }
        if (model.isNotBlank()) {
            settingsRepository.setDefaultModel(model)
        }

        saveTunnelConfig()
        saveAutonomyLevel()
        saveMemoryConfig()

        val hash = _pinHash.value
        if (hash.isNotEmpty()) {
            settingsRepository.setPinHash(hash)
            settingsRepository.setLockEnabled(_lockEnabled.value)
        }

        onDone()
    }

    /**
     * Persists the provider API key record for the selected provider.
     *
     * For OAuth sessions the key may be blank; the record is still saved
     * so that the settings screen can display the connection and the
     * [ConfigTomlBuilder] can reconstruct the TOML on daemon restart.
     *
     * @param provider Canonical provider ID.
     * @param key Decrypted API key (may be blank for OAuth providers).
     * @param url Base URL (may be blank for cloud providers).
     */
    private suspend fun saveProviderApiKey(
        provider: String,
        key: String,
        url: String,
    ) {
        val existingKey = apiKeyRepository.getByProvider(provider)
        val providerState = _providerState.value
        apiKeyRepository.save(
            ApiKey(
                id = existingKey?.id ?: UUID.randomUUID().toString(),
                provider = provider,
                key = key,
                baseUrl = url,
                refreshToken = providerState.oauthRefreshToken,
                expiresAt = providerState.oauthExpiresAt,
            ),
        )
    }

    /**
     * Saves all selected channels that have their required fields filled.
     *
     * Iterates over the selected channel types and saves each one whose
     * required fields are all present and non-blank.
     */
    private suspend fun saveAllConfiguredChannels() {
        val subFlows = _channelSubFlowStates.value
        _channelSelectionState.value.selectedTypes
            .filter { type -> isChannelReadyToSave(type, subFlows[type]) }
            .forEach { type -> saveChannel(type, subFlows.getValue(type)) }
    }

    /**
     * Checks whether a channel type has all required fields filled.
     *
     * @param type The channel type to check.
     * @param subFlow The sub-flow state for this channel, or null if absent.
     * @return True if the channel has a sub-flow with all required fields filled.
     */
    private fun isChannelReadyToSave(
        type: ChannelType,
        subFlow: ChannelSubFlowState?,
    ): Boolean {
        if (subFlow == null) return false
        val fields = subFlow.fieldValues
        return type.fields
            .filter { it.isRequired }
            .all { fields[it.key]?.isNotBlank() == true }
    }

    /**
     * Persists a single configured channel with its secret and non-secret fields.
     *
     * @param type The channel type to save.
     * @param subFlow The sub-flow state containing field values for this channel.
     */
    private suspend fun saveChannel(
        type: ChannelType,
        subFlow: ChannelSubFlowState,
    ) {
        val fields = subFlow.fieldValues
        val secretKeys =
            type.fields
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
                type = type,
                configValues = nonSecrets,
            )
        channelConfigRepository.save(channel, secrets)
    }

    /**
     * Writes an enhanced AIEOS v1.1 identity JSON with user name, timezone,
     * and communication style.
     *
     * Always overwrites the stored identity so that re-running the wizard
     * updates the configuration.
     *
     * @param identity The identity step state containing all identity fields.
     */
    private suspend fun ensureIdentity(identity: IdentityStepState) {
        if (identity.agentName.isBlank()) return

        val json =
            buildJsonObject {
                putJsonObject("identity") {
                    putJsonObject("names") {
                        put("first", identity.agentName)
                    }
                    if (identity.userName.isNotBlank()) {
                        put("user_name", identity.userName)
                    }
                    if (identity.timezone.isNotBlank()) {
                        put("timezone", identity.timezone)
                    }
                    if (identity.communicationStyle.isNotBlank()) {
                        put("communication_style", identity.communicationStyle)
                    }
                    if (identity.identityFormat.isNotBlank()) {
                        put("format", identity.identityFormat)
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
     * @param identity The identity step state containing user name, timezone,
     *   and communication style.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun scaffoldWorkspaceIfNeeded(identity: IdentityStepState) {
        try {
            app.daemonBridge.ensureWorkspace(
                agentName = identity.agentName,
                userName = identity.userName.ifBlank { "User" },
                timezone = identity.timezone.ifBlank { TimeZone.getDefault().id },
                communicationStyle = identity.communicationStyle,
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Persists tunnel configuration to the settings repository.
     */
    private suspend fun saveTunnelConfig() {
        val tunnel = _tunnelState.value
        settingsRepository.setTunnelProvider(tunnel.tunnelType)
        when (tunnel.tunnelType) {
            "cloudflare" -> settingsRepository.setTunnelCloudflareToken(tunnel.tunnelToken)
            "ngrok" -> settingsRepository.setTunnelNgrokAuthToken(tunnel.tunnelToken)
            "tailscale" ->
                settingsRepository.setTunnelTailscaleHostname(tunnel.customEndpoint)
            "custom" -> settingsRepository.setTunnelCustomHealthUrl(tunnel.customEndpoint)
        }
    }

    /**
     * Persists the autonomy level to the settings repository.
     */
    private suspend fun saveAutonomyLevel() {
        settingsRepository.setAutonomyLevel(_securityState.value.autonomyLevel)
    }

    /**
     * Persists memory configuration to the settings repository.
     */
    private suspend fun saveMemoryConfig() {
        val memory = _memoryState.value
        settingsRepository.setMemoryBackend(memory.backend)
        settingsRepository.setMemoryAutoSave(memory.autoSave)
        if (memory.embeddingProvider.isNotBlank()) {
            settingsRepository.setMemoryEmbeddingProvider(memory.embeddingProvider)
        }
        settingsRepository.setMemoryArchiveAfterDays(memory.retentionDays)
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
        val failure = probeResult.exceptionOrNull() ?: return false
        val msg = failure.message ?: ""
        return "HTTP 401" in msg || "HTTP 403" in msg
    }
}
