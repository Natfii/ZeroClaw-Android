/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings.apikeys

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.BuildConfig
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.data.CredentialsJsonParser
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.data.StorageHealth
import com.zeroclaw.android.data.oauth.AuthProfileWriter
import com.zeroclaw.android.data.oauth.OAuthCallbackServer
import com.zeroclaw.android.data.oauth.OAuthTokenResult
import com.zeroclaw.android.data.oauth.OpenAiOAuthManager
import com.zeroclaw.android.data.remote.ConnectionProber
import com.zeroclaw.android.data.remote.ModelFetcher
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.model.KeyStatus
import com.zeroclaw.android.model.ModelListFormat
import com.zeroclaw.android.model.ServiceState
import com.zeroclaw.android.service.AgentTomlEntry
import com.zeroclaw.android.service.ConfigTomlBuilder
import com.zeroclaw.android.service.GlobalTomlConfig
import com.zeroclaw.android.service.SetupOrchestrator
import com.zeroclaw.android.service.ZeroClawDaemonService
import com.zeroclaw.android.ui.screen.setup.SetupProgress
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Persistence state for save/update operations on API keys.
 */
sealed interface SaveState {
    /** No save operation in progress. */
    data object Idle : SaveState

    /** A save operation is in progress. */
    data object Saving : SaveState

    /** Save completed successfully. */
    data object Saved : SaveState

    /**
     * Save failed with an error.
     *
     * @property message Human-readable error description.
     */
    data class Error(
        val message: String,
    ) : SaveState
}

/**
 * Result of a pre-save connection probe for an API key.
 */
sealed interface ConnectionTestState {
    /** No test has been requested. */
    data object Idle : ConnectionTestState

    /** A connection test is in progress. */
    data object Testing : ConnectionTestState

    /** The connection probe succeeded — credentials are accepted by the provider. */
    data object Success : ConnectionTestState

    /**
     * The connection probe failed.
     *
     * @property message Human-readable failure reason.
     */
    data class Failure(
        val message: String,
    ) : ConnectionTestState
}

/**
 * ViewModel for API key management screens.
 *
 * Provides the list of stored keys, CRUD operations with error handling,
 * save-state tracking, and storage health information. The revealed key
 * is cleared after a timeout or when the user navigates away.
 *
 * @param application Application context for accessing the API key repository.
 */
@Suppress("TooManyFunctions", "LargeClass")
class ApiKeysViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = application as ZeroClawApplication
    private val repository = app.apiKeyRepository
    private val agentRepository = app.agentRepository
    private val daemonBridge = app.daemonBridge
    private val settingsRepository = app.settingsRepository
    private val channelConfigRepository = app.channelConfigRepository
    private val setupOrchestrator = SetupOrchestrator(daemonBridge, app.healthBridge)

    /** Hot-reload progress observable for the bottom sheet. */
    val hotReloadProgress: StateFlow<SetupProgress> = setupOrchestrator.progress

    private val _showHotReloadSheet = MutableStateFlow(false)

    /** Whether to show the hot-reload bottom sheet. */
    val showHotReloadSheet: StateFlow<Boolean> = _showHotReloadSheet.asStateFlow()

    /**
     * Dismisses the hot-reload bottom sheet and resets orchestrator progress.
     */
    fun dismissHotReloadSheet() {
        _showHotReloadSheet.value = false
        setupOrchestrator.reset()
    }

    /** All stored API keys, ordered by creation date descending. */
    val keys: StateFlow<List<ApiKey>> =
        repository.keys.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    /**
     * Set of API key identifiers that are not referenced by any agent.
     *
     * A key is considered "unused" when no configured agent's provider
     * resolves to the same canonical provider ID as the key's provider.
     * The UI can use this to display an amber warning indicator next to
     * unused keys.
     */
    val unusedKeyIds: StateFlow<Set<String>> =
        combine(keys, agentRepository.agents) { keyList, agentList ->
            val agentProviderIds =
                agentList
                    .map { agent ->
                        val resolved = ProviderRegistry.findById(agent.provider)
                        resolved?.id ?: agent.provider.lowercase()
                    }.toSet()
            keyList
                .filter { key ->
                    val resolved = ProviderRegistry.findById(key.provider)
                    val keyProviderId = resolved?.id ?: key.provider.lowercase()
                    keyProviderId !in agentProviderIds
                }.map { it.id }
                .toSet()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptySet(),
        )

    private val _revealedKeyId = MutableStateFlow<String?>(null)

    /** Identifier of the currently revealed key, or null if none. */
    val revealedKeyId: StateFlow<String?> = _revealedKeyId.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)

    /** Current state of the most recent save/update operation. */
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)

    /** One-shot message to display in a snackbar, or null if none pending. */
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _connectionTestState =
        MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)

    /** Result of the most recent connection probe, or [ConnectionTestState.Idle] if none. */
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())

    /** Model names fetched from the current provider's API. */
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _isLoadingModels = MutableStateFlow(false)

    /** Whether a model list fetch is in progress. */
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    private val _unreachableKeyIds = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Set of API key identifiers whose [ApiKey.baseUrl] failed a reachability probe.
     *
     * Only keys with a non-empty base URL (self-hosted/local servers) are probed.
     * Cloud-only keys are never included. Updated by [probeStoredConnections].
     */
    val unreachableKeyIds: StateFlow<Set<String>> = _unreachableKeyIds.asStateFlow()

    private val _oauthInProgress = MutableStateFlow(false)

    /** Whether an OAuth login flow is currently in progress. */
    val oauthInProgress: StateFlow<Boolean> = _oauthInProgress.asStateFlow()

    /**
     * Coroutine job for the auto-hide timer that clears the revealed key
     * after [REVEAL_TIMEOUT_MS] milliseconds.
     */
    private var revealJob: Job? = null

    /** Debounce job for model fetching after provider/key/URL changes. */
    private var modelFetchJob: Job? = null

    /** Health state of the underlying encrypted storage backend. */
    val storageHealth: StorageHealth
        get() = repository.storageHealth

    /** Number of stored entries that could not be deserialized. */
    val corruptKeyCount: StateFlow<Int>
        get() = repository.corruptKeyCount

    /**
     * Saves a new API key with the given provider and key value.
     *
     * Updates [saveState] through [SaveState.Saving] to either
     * [SaveState.Saved] on success or [SaveState.Error] on failure.
     * When [model] is non-empty, persists the selected model as the
     * default model in settings.
     *
     * @param provider Provider name (e.g. "OpenAI").
     * @param key The secret key value.
     * @param baseUrl Provider endpoint URL for self-hosted providers, empty for cloud defaults.
     * @param model Selected model name to save as default, empty to skip.
     */
    @Suppress("TooGenericExceptionCaught")
    fun addKey(
        provider: String,
        key: String,
        baseUrl: String = "",
        model: String = "",
    ) {
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                val existing = repository.getByProvider(provider)
                if (existing != null) {
                    val displayName =
                        ProviderRegistry.findById(provider)?.displayName ?: provider
                    _saveState.value =
                        SaveState.Error("A key for $displayName already exists")
                    return@launch
                }
                repository.save(
                    ApiKey(
                        id = UUID.randomUUID().toString(),
                        provider = provider,
                        key = key,
                        baseUrl = baseUrl,
                    ),
                )
                persistModel(provider, model)
                _saveState.value = SaveState.Saved
                triggerHotReload()
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(safeErrorMessage(e))
            }
        }
    }

    /**
     * Updates an existing API key.
     *
     * Updates [saveState] through [SaveState.Saving] to either
     * [SaveState.Saved] on success or [SaveState.Error] on failure.
     * When [model] is non-empty, persists the selected model as the
     * default model in settings.
     *
     * @param apiKey The updated key.
     * @param model Selected model name to save as default, empty to skip.
     */
    @Suppress("TooGenericExceptionCaught")
    fun updateKey(
        apiKey: ApiKey,
        model: String = "",
    ) {
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                repository.save(apiKey)
                persistModel(apiKey.provider, model)
                _saveState.value = SaveState.Saved
                triggerHotReload()
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(safeErrorMessage(e))
            }
        }
    }

    /**
     * Rotates an existing API key by replacing its secret with a new value.
     *
     * Preserves the key's ID, provider, and base URL while resetting the
     * creation timestamp and status to [KeyStatus.ACTIVE]. Updates
     * [saveState] through the standard save lifecycle.
     *
     * @param id Identifier of the key to rotate.
     * @param newKeyValue The new secret key value.
     */
    @Suppress("TooGenericExceptionCaught")
    fun rotateKey(
        id: String,
        newKeyValue: String,
    ) {
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                val existing = repository.getById(id)
                if (existing == null) {
                    _saveState.value = SaveState.Error("Key not found")
                    return@launch
                }
                repository.save(
                    existing.copy(
                        key = newKeyValue,
                        createdAt = System.currentTimeMillis(),
                        status = KeyStatus.ACTIVE,
                    ),
                )
                _snackbarMessage.value = "Key rotated successfully"
                _saveState.value = SaveState.Saved
                triggerHotReload()
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(safeErrorMessage(e))
            }
        }
    }

    /**
     * Deletes an API key and optionally its associated agents.
     *
     * When [alsoDeleteAgents] is true, all agents whose provider matches
     * the deleted key's provider are removed. This prevents orphaned agent
     * entries that reference a provider with no stored credentials.
     *
     * @param id Key identifier to delete.
     * @param alsoDeleteAgents Whether to cascade-delete agents for this provider.
     */
    @Suppress("TooGenericExceptionCaught")
    fun deleteKey(
        id: String,
        alsoDeleteAgents: Boolean = false,
    ) {
        viewModelScope.launch {
            try {
                val deletedKey = repository.getById(id)
                repository.delete(id)
                if (_revealedKeyId.value == id) {
                    revealJob?.cancel()
                    revealJob = null
                    _revealedKeyId.value = null
                }
                if (deletedKey != null) {
                    if (isCodexProvider(deletedKey.provider)) {
                        AuthProfileWriter.removeCodexProfile(getApplication())
                    }
                    if (alsoDeleteAgents) {
                        deleteAgentsForProvider(deletedKey.provider)
                    }
                    clearDefaultProviderIfNeeded(
                        deletedKey = deletedKey,
                        keyRepo = repository,
                        settingsRepo = settingsRepository,
                    )
                    triggerHotReload()
                }
                _snackbarMessage.value = "Key deleted"
            } catch (e: Exception) {
                _snackbarMessage.value = "Delete failed: ${safeErrorMessage(e)}"
            }
        }
    }

    /**
     * Returns the number of agents that reference the given API key's provider.
     *
     * Used by the UI to show how many agents will be affected in the
     * delete confirmation dialog.
     *
     * @param keyId Key identifier to look up.
     * @return Number of agents using this key's provider, or 0 if key not found.
     */
    suspend fun countAgentsForKey(keyId: String): Int {
        val key = repository.getById(keyId) ?: return 0
        val canonical =
            ProviderRegistry.findById(key.provider)?.id ?: key.provider.lowercase()
        return agentRepository.agents.first().count { agent ->
            val agentCanonical =
                ProviderRegistry.findById(agent.provider)?.id ?: agent.provider.lowercase()
            agentCanonical == canonical
        }
    }

    /**
     * Deletes all agents whose provider matches the given provider ID.
     *
     * Resolves aliases via [ProviderRegistry] to ensure "grok" and "xai"
     * are treated as the same provider.
     *
     * @param provider Provider ID or alias.
     */
    private suspend fun deleteAgentsForProvider(provider: String) {
        val canonical =
            ProviderRegistry.findById(provider)?.id ?: provider.lowercase()
        val agents = agentRepository.agents.first()
        agents
            .filter { agent ->
                val agentCanonical =
                    ProviderRegistry.findById(agent.provider)?.id ?: agent.provider.lowercase()
                agentCanonical == canonical
            }.forEach { agent ->
                agentRepository.delete(agent.id)
            }
    }

    /**
     * Reveals the full API key for the given [id].
     *
     * The key is automatically hidden after [REVEAL_TIMEOUT_MS] milliseconds.
     * Any previous reveal timer is cancelled before starting a new one.
     * Callers should gate this behind PIN authentication.
     *
     * @param id Unique identifier of the key to reveal.
     */
    fun revealKey(id: String) {
        revealJob?.cancel()
        _revealedKeyId.value = id
        revealJob =
            viewModelScope.launch {
                delay(REVEAL_TIMEOUT_MS)
                _revealedKeyId.value = null
            }
    }

    /**
     * Hides any currently revealed key and cancels the auto-hide timer.
     */
    fun hideRevealedKey() {
        revealJob?.cancel()
        revealJob = null
        _revealedKeyId.value = null
    }

    /**
     * Exports all keys as an encrypted payload.
     *
     * The export is encrypted with AES-256-GCM using a key derived from
     * [passphrase] via PBKDF2. On success, [onResult] receives the
     * Base64-encoded payload. On failure, [onResult] receives an error
     * message prefixed with "Export failed:".
     *
     * @param passphrase User-provided encryption passphrase.
     * @param onResult Callback with the encrypted payload or error message.
     */
    @Suppress("TooGenericExceptionCaught")
    fun exportKeys(
        passphrase: String,
        onResult: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val encrypted = repository.exportAll(passphrase)
                onResult(encrypted)
            } catch (e: Exception) {
                onResult("Export failed: ${safeErrorMessage(e)}")
            }
        }
    }

    /**
     * Imports keys from an encrypted payload.
     *
     * Decrypts [encryptedPayload] using [passphrase] and upserts the
     * contained keys with fresh UUIDs. On success, [onResult] receives
     * the number of imported keys. On failure, [onResult] receives zero.
     *
     * @param encryptedPayload Base64-encoded encrypted data from [exportKeys].
     * @param passphrase The passphrase used during export.
     * @param onResult Callback with the number of keys imported or zero on error.
     */
    @Suppress("TooGenericExceptionCaught")
    fun importKeys(
        encryptedPayload: String,
        passphrase: String,
        onResult: (Int) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val count = repository.importFrom(encryptedPayload, passphrase)
                onResult(count)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Import failed: ${e.message}", e)
                }
                onResult(0)
            }
        }
    }

    /** Resets [saveState] back to [SaveState.Idle]. */
    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    /**
     * Displays a one-shot snackbar message.
     *
     * @param message The message to display.
     */
    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    /** Clears the pending snackbar message. */
    fun dismissSnackbar() {
        _snackbarMessage.value = null
    }

    /**
     * Probes the provider endpoint to verify that credentials are accepted.
     *
     * For providers with no model-list endpoint ([ModelListFormat.NONE]), the
     * test state is set to [ConnectionTestState.Failure] immediately with an
     * explanatory message rather than attempting a network call. Otherwise,
     * [ModelFetcher.fetchModels] is called and the result is mapped to
     * [ConnectionTestState.Success] or [ConnectionTestState.Failure].
     *
     * @param providerId Canonical provider identifier from the registry.
     * @param key API key value (may be empty for URL-only providers).
     * @param baseUrl Provider endpoint URL override (may be empty for cloud providers).
     */
    @Suppress("TooGenericExceptionCaught")
    fun testConnection(
        providerId: String,
        key: String,
        baseUrl: String,
    ) {
        val providerInfo = ProviderRegistry.findById(providerId)
        if (providerInfo == null) {
            _connectionTestState.value = ConnectionTestState.Failure("Unknown provider")
            return
        }
        if (providerInfo.modelListFormat == ModelListFormat.NONE) {
            _connectionTestState.value =
                ConnectionTestState.Failure("No test endpoint available for this provider")
            return
        }
        _connectionTestState.value = ConnectionTestState.Testing
        viewModelScope.launch {
            val result = ModelFetcher.fetchModels(providerInfo, key, baseUrl)
            _connectionTestState.value =
                result.fold(
                    onSuccess = { ConnectionTestState.Success },
                    onFailure = { e -> ConnectionTestState.Failure(mapConnectionError(e)) },
                )
        }
    }

    /** Resets [connectionTestState] back to [ConnectionTestState.Idle]. */
    fun resetConnectionTestState() {
        _connectionTestState.value = ConnectionTestState.Idle
    }

    /**
     * Schedules a debounced model fetch for the given provider credentials.
     *
     * Cancels any pending fetch and waits [MODEL_FETCH_DEBOUNCE_MS] before
     * starting the network call. This prevents excessive requests while the
     * user is still typing. Also clears stale model data immediately when
     * the provider changes.
     *
     * @param providerId Canonical provider identifier from the registry.
     * @param apiKey API key value (may be empty for URL-only providers).
     * @param baseUrl Provider endpoint URL override (may be empty for cloud providers).
     */
    fun scheduleFetchModels(
        providerId: String,
        apiKey: String,
        baseUrl: String,
    ) {
        modelFetchJob?.cancel()
        _availableModels.value = emptyList()
        modelFetchJob =
            viewModelScope.launch {
                delay(MODEL_FETCH_DEBOUNCE_MS)
                fetchModels(providerId, apiKey, baseUrl)
            }
    }

    /**
     * Fetches available models from the provider's API.
     *
     * Updates [availableModels] and [isLoadingModels]. Silently ignores
     * failures since model listing is a convenience feature.
     *
     * @param providerId Canonical provider identifier.
     * @param apiKey API key value.
     * @param baseUrl Provider endpoint URL override.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun fetchModels(
        providerId: String,
        apiKey: String,
        baseUrl: String,
    ) {
        if (providerId.isBlank()) return
        val info = ProviderRegistry.findById(providerId) ?: return
        if (info.modelListFormat == ModelListFormat.NONE) return
        if (apiKey.isBlank() && baseUrl.isBlank()) return

        _isLoadingModels.value = true
        try {
            val result = ModelFetcher.fetchModels(info, apiKey, baseUrl)
            result.onSuccess { models ->
                _availableModels.value = models
            }
        } catch (_: Exception) {
            // Model listing is best-effort; failures are silently ignored
        } finally {
            _isLoadingModels.value = false
        }
    }

    /**
     * Imports an API key from a Claude Code `.credentials.json` file.
     *
     * Reads the file via [android.content.ContentResolver], parses the
     * OAuth credentials, and saves the resulting [ApiKey] to the
     * repository. Shows a snackbar with the result.
     *
     * @param context Context for resolving the file URI.
     * @param uri Content URI of the selected `.credentials.json` file.
     */
    @Suppress("TooGenericExceptionCaught")
    fun importCredentialsFile(
        context: Context,
        uri: Uri,
    ) {
        viewModelScope.launch {
            try {
                val jsonContent =
                    context.contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader()
                        ?.readText()
                if (jsonContent.isNullOrBlank()) {
                    _snackbarMessage.value = "File is empty"
                    return@launch
                }
                val apiKey = CredentialsJsonParser.parse(jsonContent)
                if (apiKey == null) {
                    _snackbarMessage.value = "No valid OAuth credentials found in file"
                    return@launch
                }
                repository.save(apiKey)
                _snackbarMessage.value = "Anthropic OAuth credentials imported"
                triggerHotReload()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Credentials import failed: ${e.message}", e)
                }
                _snackbarMessage.value = "Import failed: ${safeErrorMessage(e)}"
            }
        }
    }

    /**
     * Initiates the OpenAI OAuth 2.0 login flow using PKCE.
     *
     * Generates PKCE state, starts a loopback callback server, opens a
     * Chrome Custom Tab for the user to authenticate, then exchanges the
     * authorization code for tokens. On success, the resulting API key is
     * saved to the repository with provider "openai", including the refresh
     * token and expiry. On failure, the operation is silently abandoned so
     * the user can retry.
     *
     * Safe to call from the main thread; all blocking work runs on
     * background dispatchers.
     *
     * @param context Activity or application context used to launch the
     *   Chrome Custom Tab.
     */
    @Suppress("TooGenericExceptionCaught")
    fun startOAuthLogin(context: Context) {
        _oauthInProgress.value = true
        viewModelScope.launch {
            val pkce = OpenAiOAuthManager.generatePkceState()
            var server: OAuthCallbackServer? = null
            try {
                holdForegroundForOAuth(context)
                server = OAuthCallbackServer.startWithFallback()
                val port = server.boundPort
                val url = OpenAiOAuthManager.buildAuthorizeUrl(pkce, port)
                if (BuildConfig.DEBUG) Log.d(TAG, "OAuth: starting flow on port $port")
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))

                val callbackResult = server.awaitCallback()
                bringAppToForeground(context)
                if (callbackResult == null) {
                    Log.w(TAG, "OAuth: callback timed out or was cancelled")
                    _snackbarMessage.value = "Login timed out — please try again"
                    return@launch
                }

                if (callbackResult.state != pkce.state) {
                    Log.w(TAG, "OAuth: CSRF state mismatch")
                    _snackbarMessage.value = "Login failed — security check failed"
                    return@launch
                }

                if (BuildConfig.DEBUG) Log.d(TAG, "OAuth: exchanging code for tokens")
                val tokens =
                    OpenAiOAuthManager.exchangeCodeForTokens(
                        code = callbackResult.code,
                        codeVerifier = pkce.codeVerifier,
                        port = port,
                    )
                if (BuildConfig.DEBUG) Log.d(TAG, "OAuth: token exchange successful, writing profile")
                saveOAuthTokens(tokens)
            } catch (e: Exception) {
                Log.e(TAG, "OAuth login failed", e)
                _snackbarMessage.value = "Login failed: ${e.message}"
            } finally {
                server?.stop()
                releaseOAuthHold(context)
                _oauthInProgress.value = false
            }
        }
    }

    /**
     * Starts the daemon service in OAuth-hold mode to prevent process freezing.
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
     * Persists OAuth tokens after a successful token exchange.
     *
     * Writes the profile to `auth-profiles.json` for the Rust daemon, saves
     * the key to the repository, sets the default provider and model, and
     * triggers a daemon restart.
     *
     * @param tokens The token exchange result.
     */
    private suspend fun saveOAuthTokens(tokens: OAuthTokenResult) {
        AuthProfileWriter.writeCodexProfile(
            context = getApplication(),
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresAtMs = tokens.expiresAt,
        )
        cleanupStaleOpenAiEntries()
        repository.save(
            ApiKey(
                id = UUID.randomUUID().toString(),
                provider = CODEX_PROVIDER,
                key = "",
                refreshToken = tokens.refreshToken,
                expiresAt = tokens.expiresAt,
            ),
        )
        migrateAgentsToCodex()
        settingsRepository.setDefaultProvider(CODEX_PROVIDER)
        val defaultModel =
            ProviderRegistry
                .findById(CODEX_PROVIDER)
                ?.suggestedModels
                ?.firstOrNull()
                .orEmpty()
        if (defaultModel.isNotEmpty()) {
            settingsRepository.setDefaultModel(defaultModel)
        }
        triggerHotReload()
        _snackbarMessage.value = "ChatGPT login successful"
        _saveState.value = SaveState.Saved
    }

    /**
     * Removes any existing "openai" API keys that have an empty key value.
     *
     * These are stale entries left over from previous OAuth attempts that
     * created a key under the wrong provider ID. Called before saving the
     * correct "openai-codex" key.
     */
    private suspend fun cleanupStaleOpenAiEntries() {
        val allKeys = repository.keys.first()
        allKeys
            .filter { it.provider == OPENAI_PROVIDER && it.key.isBlank() }
            .forEach { repository.delete(it.id) }
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
     * Triggers a hot-reload of the daemon with updated configuration.
     *
     * When the daemon is currently [ServiceState.RUNNING], shows the hot-reload
     * bottom sheet, builds a fresh TOML config from current settings and keys,
     * and restarts the daemon via [SetupOrchestrator.runHotReload]. When the
     * daemon is not running, falls back to [DaemonServiceBridge.markRestartRequired]
     * so the next manual start picks up the changes.
     *
     * All secret [ByteArray] buffers are zero-filled in a `finally` block
     * regardless of outcome.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun triggerHotReload() {
        if (daemonBridge.serviceState.value != ServiceState.RUNNING) {
            daemonBridge.markRestartRequired()
            return
        }
        _showHotReloadSheet.value = true
        viewModelScope.launch {
            val secretBuffers = mutableListOf<ByteArray>()
            try {
                val settings = settingsRepository.settings.first()
                val effectiveSettings = resolveEffectiveDefaults(settings)
                val apiKey =
                    repository.getByProviderFresh(effectiveSettings.defaultProvider)
                val apiKeyBytes =
                    apiKey?.key?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                secretBuffers.add(apiKeyBytes)

                val configToml =
                    buildConfigToml(effectiveSettings, apiKey, apiKeyBytes, secretBuffers)
                val channels =
                    channelConfigRepository.channels
                        .first()
                        .filter { it.isEnabled }
                        .map { it.type.tomlKey }
                val validPort =
                    if (settings.port in VALID_PORT_RANGE) {
                        settings.port
                    } else {
                        AppSettings.DEFAULT_PORT
                    }

                setupOrchestrator.runHotReload(
                    context = getApplication(),
                    configToml = configToml,
                    expectedChannels = channels,
                    port = validPort.toUShort(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Hot-reload failed", e)
            } finally {
                secretBuffers.forEach { it.fill(0) }
            }
        }
    }

    /**
     * Derives effective default provider and model from the agent list.
     *
     * The first enabled agent with a non-blank provider and model name
     * overrides the DataStore values in [settings]. Mirrors the resolution
     * logic in [ZeroClawDaemonService].
     *
     * @param settings Current application settings (may have stale defaults).
     * @return A copy of [settings] with provider and model overridden by the
     *   primary agent, or unchanged if no qualifying agent exists.
     */
    private suspend fun resolveEffectiveDefaults(settings: AppSettings): AppSettings {
        val agents = agentRepository.agents.first()
        val primary =
            agents.firstOrNull {
                it.isEnabled && it.provider.isNotBlank() && it.modelName.isNotBlank()
            } ?: return settings
        return settings.copy(
            defaultProvider = primary.provider,
            defaultModel = primary.modelName,
        )
    }

    /**
     * Builds the complete TOML configuration string from settings and keys.
     *
     * Combines the global config, channel sections, and per-agent sections
     * into a single TOML document. Mirrors the pattern in [SetupViewModel]
     * and [ZeroClawDaemonService].
     *
     * @param settings Effective application settings with resolved defaults.
     * @param apiKey Default provider API key, or null.
     * @param apiKeyBytes Decrypted API key as a [ByteArray] for secure handling.
     * @param secretBuffers Mutable list for agent key buffer cleanup tracking.
     * @return Complete TOML configuration string.
     */
    private suspend fun buildConfigToml(
        settings: AppSettings,
        apiKey: ApiKey?,
        apiKeyBytes: ByteArray,
        secretBuffers: MutableList<ByteArray>,
    ): String {
        val globalConfig =
            buildGlobalTomlConfig(
                settings,
                apiKey,
                String(apiKeyBytes, Charsets.UTF_8),
            )
        val baseToml = ConfigTomlBuilder.build(globalConfig)
        val channelsToml =
            ConfigTomlBuilder.buildChannelsToml(
                channelConfigRepository.getEnabledWithSecrets(),
            )
        val agentsToml = buildAgentsToml(secretBuffers)
        return baseToml + channelsToml + agentsToml
    }

    /**
     * Converts [AppSettings] and resolved API key into a [GlobalTomlConfig].
     *
     * Comma-separated string fields in [AppSettings] are split into lists
     * for [GlobalTomlConfig] properties that expect `List<String>`. Mirrors
     * the logic in [ZeroClawDaemonService].
     *
     * @param settings Current application settings.
     * @param apiKey Resolved API key for the default provider, or null.
     * @param apiKeyValue Decrypted API key string from the secure buffer.
     * @return A fully populated [GlobalTomlConfig].
     */
    @Suppress("LongMethod")
    private fun buildGlobalTomlConfig(
        settings: AppSettings,
        apiKey: ApiKey?,
        apiKeyValue: String,
    ): GlobalTomlConfig =
        GlobalTomlConfig(
            provider = settings.defaultProvider,
            model = settings.defaultModel,
            apiKey = apiKeyValue,
            baseUrl = apiKey?.baseUrl.orEmpty(),
            temperature = settings.defaultTemperature,
            compactContext = settings.compactContext,
            costEnabled = settings.costEnabled,
            dailyLimitUsd = settings.dailyLimitUsd,
            monthlyLimitUsd = settings.monthlyLimitUsd,
            costWarnAtPercent = settings.costWarnAtPercent,
            providerRetries = settings.providerRetries,
            fallbackProviders = splitCsv(settings.fallbackProviders),
            memoryBackend = settings.memoryBackend,
            memoryAutoSave = settings.memoryAutoSave,
            identityJson = settings.identityJson,
            autonomyLevel = settings.autonomyLevel,
            workspaceOnly = settings.workspaceOnly,
            allowedCommands = splitCsv(settings.allowedCommands),
            forbiddenPaths = splitCsv(settings.forbiddenPaths),
            maxActionsPerHour = settings.maxActionsPerHour,
            maxCostPerDayCents = settings.maxCostPerDayCents,
            requireApprovalMediumRisk = settings.requireApprovalMediumRisk,
            blockHighRiskCommands = settings.blockHighRiskCommands,
            tunnelProvider = settings.tunnelProvider,
            tunnelCloudflareToken = settings.tunnelCloudflareToken,
            tunnelTailscaleFunnel = settings.tunnelTailscaleFunnel,
            tunnelTailscaleHostname = settings.tunnelTailscaleHostname,
            tunnelNgrokAuthToken = settings.tunnelNgrokAuthToken,
            tunnelNgrokDomain = settings.tunnelNgrokDomain,
            tunnelCustomCommand = settings.tunnelCustomCommand,
            tunnelCustomHealthUrl = settings.tunnelCustomHealthUrl,
            tunnelCustomUrlPattern = settings.tunnelCustomUrlPattern,
            gatewayHost = settings.host,
            gatewayPort = settings.port,
            gatewayRequirePairing = settings.gatewayRequirePairing,
            gatewayAllowPublicBind = settings.gatewayAllowPublicBind,
            gatewayPairedTokens = splitCsv(settings.gatewayPairedTokens),
            gatewayPairRateLimit = settings.gatewayPairRateLimit,
            gatewayWebhookRateLimit = settings.gatewayWebhookRateLimit,
            gatewayIdempotencyTtl = settings.gatewayIdempotencyTtl,
            schedulerEnabled = settings.schedulerEnabled,
            schedulerMaxTasks = settings.schedulerMaxTasks,
            schedulerMaxConcurrent = settings.schedulerMaxConcurrent,
            heartbeatEnabled = settings.heartbeatEnabled,
            heartbeatIntervalMinutes = settings.heartbeatIntervalMinutes,
            observabilityBackend = settings.observabilityBackend,
            observabilityOtelEndpoint = settings.observabilityOtelEndpoint,
            observabilityOtelServiceName = settings.observabilityOtelServiceName,
            modelRoutesJson = settings.modelRoutesJson,
            memoryHygieneEnabled = settings.memoryHygieneEnabled,
            memoryArchiveAfterDays = settings.memoryArchiveAfterDays,
            memoryPurgeAfterDays = settings.memoryPurgeAfterDays,
            memoryEmbeddingProvider = settings.memoryEmbeddingProvider,
            memoryEmbeddingModel = settings.memoryEmbeddingModel,
            memoryVectorWeight = settings.memoryVectorWeight,
            memoryKeywordWeight = settings.memoryKeywordWeight,
            composioEnabled = settings.composioEnabled,
            composioApiKey = settings.composioApiKey,
            composioEntityId = settings.composioEntityId,
            browserEnabled = settings.browserEnabled,
            browserAllowedDomains = splitCsv(settings.browserAllowedDomains),
            httpRequestEnabled = settings.httpRequestEnabled,
            httpRequestAllowedDomains = splitCsv(settings.httpRequestAllowedDomains),
            webFetchEnabled = settings.webFetchEnabled,
            webFetchAllowedDomains = splitCsv(settings.webFetchAllowedDomains),
            webFetchBlockedDomains = splitCsv(settings.webFetchBlockedDomains),
            webFetchMaxResponseSize = settings.webFetchMaxResponseSize,
            webFetchTimeoutSecs = settings.webFetchTimeoutSecs,
            webSearchEnabled = settings.webSearchEnabled,
            webSearchProvider = settings.webSearchProvider,
            webSearchBraveApiKey = settings.webSearchBraveApiKey,
            webSearchMaxResults = settings.webSearchMaxResults,
            webSearchTimeoutSecs = settings.webSearchTimeoutSecs,
            securitySandboxEnabled = settings.securitySandboxEnabled,
            securitySandboxBackend = settings.securitySandboxBackend,
            securitySandboxFirejailArgs = splitCsv(settings.securitySandboxFirejailArgs),
            securityResourcesMaxMemoryMb = settings.securityResourcesMaxMemoryMb,
            securityResourcesMaxCpuTimeSecs = settings.securityResourcesMaxCpuTimeSecs,
            securityResourcesMaxSubprocesses = settings.securityResourcesMaxSubprocesses,
            securityResourcesMemoryMonitoring = settings.securityResourcesMemoryMonitoring,
            securityAuditEnabled = settings.securityAuditEnabled,
            securityOtpEnabled = settings.securityOtpEnabled,
            securityOtpMethod = settings.securityOtpMethod,
            securityOtpTokenTtlSecs = settings.securityOtpTokenTtlSecs,
            securityOtpCacheValidSecs = settings.securityOtpCacheValidSecs,
            securityOtpGatedActions = splitCsv(settings.securityOtpGatedActions),
            securityOtpGatedDomains = splitCsv(settings.securityOtpGatedDomains),
            securityOtpGatedDomainCategories =
                splitCsv(settings.securityOtpGatedDomainCategories),
            securityEstopEnabled = settings.securityEstopEnabled,
            securityEstopRequireOtpToResume = settings.securityEstopRequireOtpToResume,
            memoryQdrantUrl = settings.memoryQdrantUrl,
            memoryQdrantCollection = settings.memoryQdrantCollection,
            memoryQdrantApiKey = settings.memoryQdrantApiKey,
            embeddingRoutesJson = settings.embeddingRoutesJson,
            queryClassificationEnabled = settings.queryClassificationEnabled,
            proxyEnabled = settings.proxyEnabled,
            proxyHttpProxy = settings.proxyHttpProxy,
            proxyHttpsProxy = settings.proxyHttpsProxy,
            proxyAllProxy = settings.proxyAllProxy,
            proxyNoProxy = splitCsv(settings.proxyNoProxy),
            proxyScope = settings.proxyScope,
            proxyServiceSelectors = splitCsv(settings.proxyServiceSelectors),
            reliabilityBackoffMs = settings.reliabilityBackoffMs,
            reliabilityApiKeysJson = settings.reliabilityApiKeysJson,
        )

    /**
     * Resolves all enabled agents into [AgentTomlEntry] instances and builds
     * the `[agents.<name>]` TOML sections.
     *
     * Each agent's API key is fetched and added to [secretBuffers] for
     * zero-fill cleanup. Agents without a provider or model are skipped.
     *
     * @param secretBuffers Mutable list to which agent API key buffers are
     *   appended for post-setup cleanup.
     * @return TOML string with per-agent sections, or empty if no agents qualify.
     */
    private suspend fun buildAgentsToml(secretBuffers: MutableList<ByteArray>): String {
        val allAgents = agentRepository.agents.first()
        val entries =
            allAgents
                .filter { it.isEnabled && it.provider.isNotBlank() && it.modelName.isNotBlank() }
                .map { agent ->
                    val agentKey = repository.getByProviderFresh(agent.provider)
                    val keyBytes =
                        agentKey?.key?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                    secretBuffers.add(keyBytes)
                    AgentTomlEntry(
                        name = agent.name,
                        provider =
                            ConfigTomlBuilder.resolveProvider(
                                agent.provider,
                                agentKey?.baseUrl.orEmpty(),
                            ),
                        model = agent.modelName,
                        apiKey = String(keyBytes, Charsets.UTF_8),
                        systemPrompt = agent.systemPrompt,
                        temperature = agent.temperature,
                        maxDepth = agent.maxDepth,
                    )
                }
        return ConfigTomlBuilder.buildAgentsToml(entries)
    }

    /**
     * Probes stored keys with non-empty [ApiKey.baseUrl] for reachability.
     *
     * Iterates all stored keys, sending a lightweight HEAD request to each
     * base URL via [ConnectionProber]. Keys whose server does not respond
     * within the probe timeout are added to [unreachableKeyIds].
     *
     * Safe to call from the UI layer; work is dispatched to IO internally.
     */
    fun probeStoredConnections() {
        viewModelScope.launch {
            val currentKeys = keys.value
            val unreachable = mutableSetOf<String>()
            for (key in currentKeys) {
                if (key.baseUrl.isNotBlank() && !ConnectionProber.isReachable(key.baseUrl)) {
                    unreachable.add(key.id)
                }
            }
            _unreachableKeyIds.value = unreachable
        }
    }

    /**
     * Persists the selected model as the default model in settings.
     *
     * Only updates settings when [model] is non-empty. Also sets the
     * default provider when it is currently blank.
     *
     * @param provider Canonical provider identifier.
     * @param model Selected model name, empty to skip.
     */
    private suspend fun persistModel(
        provider: String,
        model: String,
    ) {
        if (model.isBlank()) return
        val settings = settingsRepository.settings.first()
        if (settings.defaultProvider.isBlank()) {
            settingsRepository.setDefaultProvider(provider)
        }
        settingsRepository.setDefaultModel(model)
    }

    /**
     * Maps an exception to a generic user-facing message that does not
     * leak internal details such as key fragments or file paths.
     *
     * @param e The caught exception.
     * @return A safe, human-readable error description.
     */
    private fun safeErrorMessage(e: Exception): String =
        when (e) {
            is GeneralSecurityException -> "Encrypted storage error"
            is IOException -> "Storage I/O error"
            is org.json.JSONException -> "Invalid data format"
            else -> "Operation failed"
        }

    /** Constants for [ApiKeysViewModel]. */
    companion object {
        private const val TAG = "ApiKeysViewModel"
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Duration in milliseconds before a revealed key is automatically hidden. */
        private const val REVEAL_TIMEOUT_MS = 30_000L

        /** Debounce delay in milliseconds before fetching models after input changes. */
        private const val MODEL_FETCH_DEBOUNCE_MS = 500L

        /** Canonical provider ID for OpenAI API-key access. */
        private const val OPENAI_PROVIDER = "openai"

        /** Canonical provider ID for ChatGPT Codex OAuth access. */
        private const val CODEX_PROVIDER = "openai-codex"

        /** Valid range for gateway port numbers. */
        private val VALID_PORT_RANGE = 1..65535

        /** Provider IDs that use the Codex OAuth auth-profiles store. */
        private val CODEX_PROVIDER_IDS =
            setOf("openai-codex", "openai_codex", "codex")

        /**
         * Returns true if the provider uses the Codex auth-profiles store.
         *
         * @param provider Provider ID to check.
         * @return True if auth-profiles.json cleanup is needed on delete.
         */
        private fun isCodexProvider(provider: String): Boolean = provider.lowercase() in CODEX_PROVIDER_IDS
    }
}

/**
 * Clears or replaces [AppSettings.defaultProvider][com.zeroclaw.android.model.AppSettings.defaultProvider]
 * when the provider key that backs it has been deleted.
 *
 * This is a package-private suspend function so it can be called from
 * [ApiKeysViewModel] and tested independently without requiring an Android
 * context.
 *
 * If the deleted key's canonical provider ID matches the current default
 * provider, the first remaining key in [keyRepo] is selected as the new
 * default. If no keys remain, both default provider and default model
 * are cleared to empty strings.
 *
 * @param deletedKey The key that was just removed from [keyRepo].
 * @param keyRepo Repository to query for remaining keys.
 * @param settingsRepo Repository to update if the default changes.
 */
internal suspend fun clearDefaultProviderIfNeeded(
    deletedKey: ApiKey,
    keyRepo: com.zeroclaw.android.data.repository.ApiKeyRepository,
    settingsRepo: com.zeroclaw.android.data.repository.SettingsRepository,
) {
    val settings = settingsRepo.settings.first()
    val deletedCanonical =
        com.zeroclaw.android.data.ProviderRegistry
            .findById(deletedKey.provider)
            ?.id
            ?: deletedKey.provider.lowercase()
    val defaultCanonical =
        com.zeroclaw.android.data.ProviderRegistry
            .findById(settings.defaultProvider)
            ?.id
            ?: settings.defaultProvider.lowercase()
    if (deletedCanonical != defaultCanonical) return

    val remaining = keyRepo.keys.first()
    val fallback = remaining.firstOrNull()
    settingsRepo.setDefaultProvider(fallback?.provider ?: "")
    settingsRepo.setDefaultModel("")
}

/**
 * Maps a connection probe exception to a user-facing error message.
 *
 * Matches against the `"HTTP {code}"` format produced by
 * [ModelFetcher][com.zeroclaw.android.data.remote.ModelFetcher]'s
 * `executeRequest()` to produce specific guidance for common failure
 * modes. Falls back to a generic message for unexpected errors.
 *
 * This is a package-private function so it can be tested independently
 * without requiring an Android context.
 *
 * @param e Exception thrown during the connection probe.
 * @return Human-readable failure reason.
 */
internal fun mapConnectionError(e: Throwable): String {
    val msg = e.message ?: ""
    return when {
        "HTTP 401" in msg -> "Authentication failed — check your API key"
        "HTTP 403" in msg -> "Access denied — check your API key permissions"
        "HTTP 404" in msg -> "Endpoint not found — check the base URL"
        "HTTP 429" in msg -> "Rate limited — try again shortly"
        "HTTP" in msg -> "Provider returned an error — try again later"
        "timeout" in msg.lowercase() || "timed out" in msg.lowercase() ->
            "Connection timed out — check your network"
        else -> "Connection failed — check credentials and URL"
    }
}

/**
 * Splits a comma-separated string into a trimmed, non-blank list.
 *
 * @param csv Comma-separated string (may be blank).
 * @return List of trimmed non-blank tokens; empty list if [csv] is blank.
 */
private fun splitCsv(csv: String): List<String> = csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
