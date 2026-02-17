/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings.channels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.ChannelType
import com.zeroclaw.android.model.ConnectedChannel
import com.zeroclaw.android.ui.screen.settings.apikeys.SaveState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for connected channels management screens.
 *
 * Provides the list of configured channels, CRUD operations with save-state
 * tracking, and type availability checking for the channel picker.
 *
 * @param application Application context for accessing the channel config repository.
 */
class ChannelsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = (application as ZeroClawApplication).channelConfigRepository

    /** All configured channels. */
    val channels: StateFlow<List<ConnectedChannel>> =
        repository.channels.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)

    /** Current state of the most recent save operation. */
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)

    /** One-shot message to display in a snackbar, or null if none pending. */
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    /**
     * Saves a channel, splitting values into non-secret and secret storage.
     *
     * @param channel The channel model to persist.
     * @param allFieldValues All field values including both secrets and non-secrets.
     */
    @Suppress("TooGenericExceptionCaught")
    fun saveChannel(channel: ConnectedChannel, allFieldValues: Map<String, String>) {
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                val secretKeys = channel.type.fields
                    .filter { it.isSecret }
                    .map { it.key }
                    .toSet()
                val secrets = allFieldValues.filter { it.key in secretKeys }
                val nonSecrets = allFieldValues.filter { it.key !in secretKeys }
                val updated = channel.copy(configValues = nonSecrets)
                repository.save(updated, secrets)
                _saveState.value = SaveState.Saved
            } catch (e: Exception) {
                Log.e(TAG, "Channel save failed", e)
                _saveState.value = SaveState.Error("Save failed")
            }
        }
    }

    /**
     * Deletes a channel and all associated secrets.
     *
     * @param id Unique channel identifier.
     */
    @Suppress("TooGenericExceptionCaught")
    fun deleteChannel(id: String) {
        viewModelScope.launch {
            try {
                repository.delete(id)
                _snackbarMessage.value = "Channel deleted"
            } catch (e: Exception) {
                Log.e(TAG, "Channel delete failed", e)
                _snackbarMessage.value = "Delete failed"
            }
        }
    }

    /**
     * Toggles the enabled state of a channel.
     *
     * @param id Unique channel identifier.
     */
    fun toggleChannel(id: String) {
        viewModelScope.launch {
            repository.toggleEnabled(id)
        }
    }

    /**
     * Loads a channel with its secret values for form editing.
     *
     * @param id Unique channel identifier.
     * @return Pair of channel and merged secrets, or null if not found.
     */
    suspend fun loadChannelWithSecrets(id: String): Pair<ConnectedChannel, Map<String, String>>? {
        val channel = repository.getById(id) ?: return null
        val secrets = repository.getSecrets(id)
        return channel to (channel.configValues + secrets)
    }

    /**
     * Checks whether a channel of the given type is already configured.
     *
     * @param type Channel type to check.
     * @return True if the type is not yet configured and is available.
     */
    suspend fun isTypeAvailable(type: ChannelType): Boolean =
        !repository.existsForType(type)

    /** Resets [saveState] back to [SaveState.Idle]. */
    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    /** Clears the pending snackbar message. */
    fun dismissSnackbar() {
        _snackbarMessage.value = null
    }

    /** Constants for [ChannelsViewModel]. */
    companion object {
        private const val TAG = "ChannelsViewModel"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
