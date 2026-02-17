/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings.apikeys

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.BuildConfig
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.data.CredentialsJsonParser
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.data.StorageHealth
import com.zeroclaw.android.model.ApiKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.UUID

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
    data class Error(val message: String) : SaveState
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
class ApiKeysViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = (application as ZeroClawApplication).apiKeyRepository
    private val agentRepository = (application as ZeroClawApplication).agentRepository

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
            val agentProviderIds = agentList.map { agent ->
                val resolved = ProviderRegistry.findById(agent.provider)
                resolved?.id ?: agent.provider.lowercase()
            }.toSet()
            keyList.filter { key ->
                val resolved = ProviderRegistry.findById(key.provider)
                val keyProviderId = resolved?.id ?: key.provider.lowercase()
                keyProviderId !in agentProviderIds
            }.map { it.id }.toSet()
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

    /**
     * Coroutine job for the auto-hide timer that clears the revealed key
     * after [REVEAL_TIMEOUT_MS] milliseconds.
     */
    private var revealJob: Job? = null

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
     *
     * @param provider Provider name (e.g. "OpenAI").
     * @param key The secret key value.
     * @param baseUrl Provider endpoint URL for self-hosted providers, empty for cloud defaults.
     */
    @Suppress("TooGenericExceptionCaught")
    fun addKey(
        provider: String,
        key: String,
        baseUrl: String = "",
    ) {
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                repository.save(
                    ApiKey(
                        id = UUID.randomUUID().toString(),
                        provider = provider,
                        key = key,
                        baseUrl = baseUrl,
                    ),
                )
                _saveState.value = SaveState.Saved
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
     *
     * @param apiKey The updated key.
     */
    @Suppress("TooGenericExceptionCaught")
    fun updateKey(apiKey: ApiKey) {
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                repository.save(apiKey)
                _saveState.value = SaveState.Saved
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(safeErrorMessage(e))
            }
        }
    }

    /**
     * Deletes an API key by its identifier.
     *
     * Emits a snackbar message on success or failure.
     *
     * @param id Key identifier to delete.
     */
    @Suppress("TooGenericExceptionCaught")
    fun deleteKey(id: String) {
        viewModelScope.launch {
            try {
                repository.delete(id)
                if (_revealedKeyId.value == id) {
                    revealJob?.cancel()
                    revealJob = null
                    _revealedKeyId.value = null
                }
                _snackbarMessage.value = "Key deleted"
            } catch (e: Exception) {
                _snackbarMessage.value = "Delete failed: ${safeErrorMessage(e)}"
            }
        }
    }

    /**
     * Reveals the full API key for the given [id].
     *
     * The key is automatically hidden after [REVEAL_TIMEOUT_MS] milliseconds.
     * Any previous reveal timer is cancelled before starting a new one.
     * Callers should gate this behind biometric authentication.
     *
     * @param id Unique identifier of the key to reveal.
     */
    fun revealKey(id: String) {
        revealJob?.cancel()
        _revealedKeyId.value = id
        revealJob = viewModelScope.launch {
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
    fun importCredentialsFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val jsonContent = context.contentResolver.openInputStream(uri)
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
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Credentials import failed: ${e.message}", e)
                }
                _snackbarMessage.value = "Import failed: ${safeErrorMessage(e)}"
            }
        }
    }

    /**
     * Maps an exception to a generic user-facing message that does not
     * leak internal details such as key fragments or file paths.
     *
     * @param e The caught exception.
     * @return A safe, human-readable error description.
     */
    private fun safeErrorMessage(e: Exception): String = when (e) {
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
    }
}
