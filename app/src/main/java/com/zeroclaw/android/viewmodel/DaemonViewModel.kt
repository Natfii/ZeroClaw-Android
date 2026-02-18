/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.DaemonStatus
import com.zeroclaw.android.model.KeyRejectionEvent
import com.zeroclaw.android.model.ServiceState
import com.zeroclaw.android.service.DaemonServiceBridge
import com.zeroclaw.android.service.ZeroClawDaemonService
import com.zeroclaw.ffi.FfiException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Actions that may require biometric authentication before execution.
 */
sealed interface BiometricAction {
    /** Starting the daemon requires authentication. */
    data object StartDaemon : BiometricAction

    /** Stopping the daemon requires authentication. */
    data object StopDaemon : BiometricAction
}

/**
 * Represents the possible states of an asynchronous daemon UI operation.
 *
 * @param T The type of data held in the [Content] variant.
 */
sealed interface DaemonUiState<out T> {
    /** No operation has been initiated. */
    data object Idle : DaemonUiState<Nothing>

    /** An operation is in progress. */
    data object Loading : DaemonUiState<Nothing>

    /**
     * An operation failed.
     *
     * @property detail Human-readable error description.
     * @property retry Optional callback to retry the failed operation.
     */
    data class Error(
        val detail: String,
        val retry: (() -> Unit)? = null,
    ) : DaemonUiState<Nothing>

    /**
     * An operation completed successfully.
     *
     * @param T The type of the result payload.
     * @property data The result payload.
     */
    data class Content<T>(
        val data: T,
    ) : DaemonUiState<T>
}

/**
 * ViewModel for the daemon control screen.
 *
 * Exposes daemon state as [StateFlow] instances for lifecycle-aware
 * collection in Compose via `collectAsStateWithLifecycle`. Daemon
 * lifecycle control (start/stop) is performed by sending [Intent]
 * actions to [ZeroClawDaemonService], while messaging uses the
 * shared [DaemonServiceBridge] directly.
 *
 * Automatically starts and stops status polling based on
 * [ServiceState] transitions from the bridge.
 *
 * @param application Application context for accessing
 *   [ZeroClawApplication.daemonBridge] and starting the service.
 */
class DaemonViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = application as ZeroClawApplication
    private val bridge: DaemonServiceBridge = app.daemonBridge
    private val settingsRepository = app.settingsRepository

    private val _pendingBiometricAction = MutableStateFlow<BiometricAction?>(null)

    /**
     * Pending action that requires biometric authentication before execution.
     *
     * When non-null, the UI layer should present a biometric prompt and call
     * [confirmBiometricAction] on success or [cancelBiometricAction] on failure.
     */
    val pendingBiometricAction: StateFlow<BiometricAction?> =
        _pendingBiometricAction.asStateFlow()

    /** Current lifecycle state of the daemon service. */
    val serviceState: StateFlow<ServiceState> = bridge.serviceState

    /** Most recently fetched daemon health snapshot. */
    val daemonStatus: StateFlow<DaemonStatus?> = bridge.lastStatus

    private val _statusState =
        MutableStateFlow<DaemonUiState<DaemonStatus>>(DaemonUiState.Idle)

    /** UI state of the daemon status section. */
    val statusState: StateFlow<DaemonUiState<DaemonStatus>> =
        _statusState.asStateFlow()

    private val _keyRejectionEvent = MutableStateFlow<KeyRejectionEvent?>(null)

    /**
     * Most recent API key rejection event detected during a send operation.
     *
     * Non-null when a key rejection has been detected and not yet dismissed
     * by the user via [dismissKeyRejection].
     */
    val keyRejectionEvent: StateFlow<KeyRejectionEvent?> = _keyRejectionEvent.asStateFlow()

    private var statusPollJob: Job? = null

    init {
        viewModelScope.launch {
            bridge.serviceState.collect { state ->
                when (state) {
                    ServiceState.RUNNING -> startStatusPolling()
                    ServiceState.STOPPED -> {
                        stopStatusPolling()
                        _statusState.value = DaemonUiState.Idle
                    }
                    ServiceState.ERROR -> {
                        stopStatusPolling()
                        _statusState.value =
                            DaemonUiState.Error(
                                detail = bridge.lastError.value ?: "Unknown daemon error",
                                retry = { requestStart() },
                            )
                    }
                    ServiceState.STARTING ->
                        _statusState.value = DaemonUiState.Loading
                    ServiceState.STOPPING ->
                        _statusState.value = DaemonUiState.Loading
                }
            }
        }

        viewModelScope.launch {
            bridge.lastStatus.collect { status ->
                if (status != null) {
                    _statusState.value = DaemonUiState.Content(status)
                }
            }
        }

        viewModelScope.launch {
            bridge.keyRejections.collect { event ->
                _keyRejectionEvent.value = event
            }
        }
    }

    /**
     * Requests the daemon to start.
     *
     * Reads the biometric-for-service setting from the repository
     * before deciding whether to gate on authentication. This avoids
     * a race condition where an eagerly initialised [StateFlow] could
     * still hold its `false` default before the data store emits.
     *
     * If biometric auth is required, emits [BiometricAction.StartDaemon]
     * via [pendingBiometricAction] so the UI layer can present a
     * biometric prompt and call [confirmBiometricAction] on success.
     */
    fun requestStart() {
        viewModelScope.launch {
            val requireBiometric =
                settingsRepository.settings.first().biometricForService
            if (requireBiometric) {
                _pendingBiometricAction.value = BiometricAction.StartDaemon
            } else {
                performStart()
            }
        }
    }

    /**
     * Requests the daemon to stop.
     *
     * Reads the biometric-for-service setting from the repository
     * before deciding whether to gate on authentication. This avoids
     * a race condition where an eagerly initialised [StateFlow] could
     * still hold its `false` default before the data store emits.
     *
     * If biometric auth is required, emits [BiometricAction.StopDaemon]
     * via [pendingBiometricAction] so the UI layer can present a
     * biometric prompt and call [confirmBiometricAction] on success.
     */
    fun requestStop() {
        viewModelScope.launch {
            val requireBiometric =
                settingsRepository.settings.first().biometricForService
            if (requireBiometric) {
                _pendingBiometricAction.value = BiometricAction.StopDaemon
            } else {
                performStop()
            }
        }
    }

    /**
     * Confirms the pending biometric action after successful authentication.
     *
     * Executes the action stored in [pendingBiometricAction] and clears it.
     */
    fun confirmBiometricAction() {
        when (_pendingBiometricAction.value) {
            BiometricAction.StartDaemon -> performStart()
            BiometricAction.StopDaemon -> performStop()
            null -> { /* nothing pending */ }
        }
        _pendingBiometricAction.value = null
    }

    /**
     * Cancels the pending biometric action after failed or cancelled authentication.
     */
    fun cancelBiometricAction() {
        _pendingBiometricAction.value = null
    }

    private fun performStart() {
        val intent =
            Intent(
                getApplication(),
                ZeroClawDaemonService::class.java,
            ).apply {
                action = ZeroClawDaemonService.ACTION_START
            }
        getApplication<Application>().startForegroundService(intent)
    }

    private fun performStop() {
        val intent =
            Intent(
                getApplication(),
                ZeroClawDaemonService::class.java,
            ).apply {
                action = ZeroClawDaemonService.ACTION_STOP
            }
        getApplication<Application>().startService(intent)
    }

    /** Clears the current key rejection event after the user has dismissed it. */
    fun dismissKeyRejection() {
        _keyRejectionEvent.value = null
    }

    private fun startStatusPolling() {
        stopStatusPolling()
        statusPollJob =
            viewModelScope.launch {
                while (true) {
                    delay(STATUS_POLL_INTERVAL_MS)
                    try {
                        bridge.pollStatus()
                    } catch (e: FfiException) {
                        _statusState.value =
                            DaemonUiState.Error(
                                detail = e.message ?: "status poll failed",
                                retry = { requestStart() },
                            )
                    }
                }
            }
    }

    private fun stopStatusPolling() {
        statusPollJob?.cancel()
        statusPollJob = null
    }

    /** Constants for [DaemonViewModel]. */
    companion object {
        private const val STATUS_POLL_INTERVAL_MS = 5_000L
    }
}
