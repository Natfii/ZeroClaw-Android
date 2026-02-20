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
import com.zeroclaw.android.model.ActivityEvent
import com.zeroclaw.android.model.CostSummary
import com.zeroclaw.android.model.CronJob
import com.zeroclaw.android.model.DaemonStatus
import com.zeroclaw.android.model.HealthDetail
import com.zeroclaw.android.model.KeyRejectionEvent
import com.zeroclaw.android.model.ServiceState
import com.zeroclaw.android.service.CostBridge
import com.zeroclaw.android.service.CronBridge
import com.zeroclaw.android.service.DaemonServiceBridge
import com.zeroclaw.android.service.HealthBridge
import com.zeroclaw.android.service.ZeroClawDaemonService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    private val healthBridge: HealthBridge = app.healthBridge
    private val costBridge: CostBridge = app.costBridge
    private val cronBridge: CronBridge = app.cronBridge

    /**
     * Count of enabled agents, derived from the agent repository flow.
     *
     * Scoped with [SharingStarted.WhileSubscribed] so collection stops
     * when no UI is observing, saving database query overhead.
     */
    val enabledAgentCount: StateFlow<Int> =
        app.agentRepository.agents
            .map { list -> list.count { it.isEnabled } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    /**
     * Count of installed plugins, derived from the plugin repository flow.
     */
    val installedPluginCount: StateFlow<Int> =
        app.pluginRepository.plugins
            .map { list -> list.count { it.isInstalled } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    /**
     * Recent activity events for the dashboard feed.
     */
    val activityEvents: StateFlow<List<ActivityEvent>> =
        app.activityRepository.events
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

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

    private val _healthDetail = MutableStateFlow<HealthDetail?>(null)

    /**
     * Structured health detail snapshot fetched periodically while the daemon is running.
     *
     * Includes per-component status, restart counts, and last-error messages.
     * Returns `null` when the daemon is not running or no health poll has completed.
     */
    val healthDetail: StateFlow<HealthDetail?> = _healthDetail.asStateFlow()

    private val _costSummary = MutableStateFlow<CostSummary?>(null)

    /**
     * Aggregated cost summary fetched periodically while the daemon is running.
     *
     * Includes session, daily, and monthly costs plus token usage figures.
     * Returns `null` when the daemon is not running or no cost poll has completed.
     */
    val costSummary: StateFlow<CostSummary?> = _costSummary.asStateFlow()

    private val _cronJobs = MutableStateFlow<List<CronJob>>(emptyList())

    /**
     * List of cron jobs fetched periodically while the daemon is running.
     *
     * Empty when the daemon is not running or no cron poll has completed.
     */
    val cronJobs: StateFlow<List<CronJob>> = _cronJobs.asStateFlow()

    private var healthPollJob: Job? = null
    private var costPollJob: Job? = null
    private var cronPollJob: Job? = null

    init {
        viewModelScope.launch {
            bridge.serviceState.collect { state ->
                when (state) {
                    ServiceState.RUNNING -> {
                        startHealthPolling()
                        startCostPolling()
                        startCronPolling()
                    }
                    ServiceState.STOPPED -> {
                        stopAllPolling()
                        _statusState.value = DaemonUiState.Idle
                        _healthDetail.value = null
                        _costSummary.value = null
                        _cronJobs.value = emptyList()
                    }
                    ServiceState.ERROR -> {
                        stopAllPolling()
                        _statusState.value =
                            DaemonUiState.Error(
                                detail = bridge.lastError.value ?: "Unknown daemon error",
                                retry = { requestStart() },
                            )
                        _healthDetail.value = null
                        _costSummary.value = null
                        _cronJobs.value = emptyList()
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
     */
    fun requestStart() {
        performStart()
    }

    /**
     * Requests the daemon to stop.
     */
    fun requestStop() {
        performStop()
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

    @Suppress("TooGenericExceptionCaught")
    private fun startHealthPolling() {
        stopHealthPolling()
        healthPollJob =
            viewModelScope.launch {
                while (true) {
                    delay(HEALTH_POLL_INTERVAL_MS)
                    try {
                        _healthDetail.value = healthBridge.getHealthDetail()
                    } catch (_: Exception) {
                        // health poll failure is non-fatal
                    }
                }
            }
    }

    private fun stopHealthPolling() {
        healthPollJob?.cancel()
        healthPollJob = null
    }

    @Suppress("TooGenericExceptionCaught")
    private fun startCostPolling() {
        stopCostPolling()
        costPollJob =
            viewModelScope.launch {
                while (true) {
                    delay(COST_POLL_INTERVAL_MS)
                    try {
                        _costSummary.value = costBridge.getCostSummary()
                    } catch (_: Exception) {
                        // cost poll failure is non-fatal
                    }
                }
            }
    }

    private fun stopCostPolling() {
        costPollJob?.cancel()
        costPollJob = null
    }

    @Suppress("TooGenericExceptionCaught")
    private fun startCronPolling() {
        stopCronPolling()
        cronPollJob =
            viewModelScope.launch {
                while (true) {
                    delay(CRON_POLL_INTERVAL_MS)
                    try {
                        _cronJobs.value = cronBridge.listJobs()
                    } catch (_: Exception) {
                        /** Cron poll failure is non-fatal. */
                    }
                }
            }
    }

    private fun stopCronPolling() {
        cronPollJob?.cancel()
        cronPollJob = null
    }

    private fun stopAllPolling() {
        stopHealthPolling()
        stopCostPolling()
        stopCronPolling()
    }

    /** Constants for [DaemonViewModel]. */
    companion object {
        private const val HEALTH_POLL_INTERVAL_MS = 15_000L
        private const val COST_POLL_INTERVAL_MS = 30_000L
        private const val CRON_POLL_INTERVAL_MS = 30_000L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
