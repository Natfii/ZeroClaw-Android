/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.data.repository.AgentRepository
import com.zeroclaw.android.data.repository.ApiKeyRepository
import com.zeroclaw.android.data.repository.ChannelConfigRepository
import com.zeroclaw.android.data.repository.LogRepository
import com.zeroclaw.android.data.repository.SettingsRepository
import com.zeroclaw.android.model.LogSeverity
import com.zeroclaw.android.model.ServiceState
import com.zeroclaw.ffi.FfiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Always-on foreground service that manages the ZeroClaw daemon lifecycle.
 *
 * Uses the `specialUse` foreground service type and [START_STICKY] to
 * ensure the daemon remains running across process restarts. Includes
 * a status polling loop that feeds [DaemonServiceBridge.lastStatus],
 * network connectivity monitoring via [NetworkMonitor], and a partial
 * wake lock to keep the CPU active while the daemon processes requests.
 *
 * The service communicates with the Rust native layer exclusively through
 * the shared [DaemonServiceBridge] obtained from [ZeroClawApplication].
 *
 * After a successful start the daemon configuration is persisted via
 * [DaemonPersistence]. When the system restarts the service after process
 * death ([START_STICKY] with a null intent), the persisted configuration
 * is restored and the daemon is restarted automatically.
 *
 * Startup failures are retried with exponential backoff via [RetryPolicy].
 * After all retries are exhausted the service transitions to
 * [ServiceState.ERROR] and the notification offers a manual Retry action.
 *
 * Lifecycle control is performed via [Intent] actions:
 * - [ACTION_START] to start the daemon and enter the foreground.
 * - [ACTION_STOP] to stop the daemon and remove the foreground notification.
 * - [ACTION_RETRY] to reset the retry counter and attempt startup again.
 */
class ZeroClawDaemonService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var bridge: DaemonServiceBridge
    private lateinit var notificationManager: DaemonNotificationManager
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var persistence: DaemonPersistence
    private lateinit var logRepository: LogRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var apiKeyRepository: ApiKeyRepository
    private lateinit var channelConfigRepository: ChannelConfigRepository
    private lateinit var agentRepository: AgentRepository
    private val retryPolicy = RetryPolicy()

    private var statusPollJob: Job? = null
    private var startJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as ZeroClawApplication
        bridge = app.daemonBridge
        logRepository = app.logRepository
        settingsRepository = app.settingsRepository
        apiKeyRepository = app.apiKeyRepository
        channelConfigRepository = app.channelConfigRepository
        agentRepository = app.agentRepository
        notificationManager = DaemonNotificationManager(this)
        networkMonitor = NetworkMonitor(this)
        persistence = DaemonPersistence(this)

        notificationManager.createChannel()
        networkMonitor.register()
        observeServiceState()
        observeNetworkState()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> handleStartFromSettings()
            ACTION_STOP -> handleStop()
            ACTION_RETRY -> handleRetry()
            null -> handleStickyRestart()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Called when the user swipes the app from the recents screen.
     *
     * The service continues running because [START_STICKY] ensures the
     * system will restart it if killed. No additional action is required.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseWakeLock()
        networkMonitor.unregister()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Reads the current user settings and API key, builds a TOML config,
     * then enters the foreground and starts the daemon.
     *
     * Settings are read inside a coroutine because the repositories are
     * flow-based. The foreground notification is posted immediately so the
     * system does not kill the service while waiting for I/O.
     */
    private fun handleStartFromSettings() {
        val notification =
            notificationManager.buildNotification(ServiceState.STARTING)
        startForeground(
            DaemonNotificationManager.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        acquireWakeLock()

        serviceScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.settings.first()
            val apiKey = apiKeyRepository.getByProviderFresh(settings.defaultProvider)

            val baseToml = ConfigTomlBuilder.build(
                provider = settings.defaultProvider,
                model = settings.defaultModel,
                apiKey = apiKey?.key.orEmpty(),
                baseUrl = apiKey?.baseUrl.orEmpty(),
            )
            val channelsToml = ConfigTomlBuilder.buildChannelsToml(
                channelConfigRepository.getEnabledWithSecrets(),
            )
            val agentsToml = buildAgentsToml()
            val configToml = baseToml + channelsToml + agentsToml

            retryPolicy.reset()
            attemptStart(
                configToml = configToml,
                host = settings.host,
                port = settings.port.toUShort(),
            )
        }
    }

    /**
     * Resolves all enabled agents into [AgentTomlEntry] instances and builds
     * the `[agents.<name>]` TOML sections.
     *
     * For each enabled agent, the provider ID is resolved to an upstream
     * factory name and the corresponding API key is fetched (with OAuth
     * refresh if needed). Agents without a provider or model are skipped.
     *
     * @return TOML string with per-agent sections, or empty if no agents qualify.
     */
    private suspend fun buildAgentsToml(): String {
        val allAgents = agentRepository.agents.first()
        val entries = allAgents
            .filter { it.isEnabled && it.provider.isNotBlank() && it.modelName.isNotBlank() }
            .map { agent ->
                val agentKey = apiKeyRepository.getByProviderFresh(agent.provider)
                AgentTomlEntry(
                    name = agent.name,
                    provider = ConfigTomlBuilder.resolveProvider(
                        agent.provider,
                        agentKey?.baseUrl.orEmpty(),
                    ),
                    model = agent.modelName,
                    apiKey = agentKey?.key.orEmpty(),
                    systemPrompt = agent.systemPrompt,
                )
            }
        return ConfigTomlBuilder.buildAgentsToml(entries)
    }

    /**
     * Enters the foreground and attempts to start the daemon with the
     * given configuration, retrying with exponential backoff on failure.
     */
    private fun handleStart(
        configToml: String,
        host: String,
        port: UShort,
    ) {
        val notification =
            notificationManager.buildNotification(
                ServiceState.STARTING,
            )
        startForeground(
            DaemonNotificationManager.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        acquireWakeLock()
        retryPolicy.reset()
        attemptStart(configToml, host, port)
    }

    private fun handleStop() {
        startJob?.cancel()
        statusPollJob?.cancel()
        retryPolicy.reset()
        persistence.recordStopped()
        serviceScope.launch {
            try {
                bridge.stop()
            } catch (e: FfiException) {
                Log.w(TAG, "Daemon stop failed: ${e.message}")
                logRepository.append(LogSeverity.ERROR, TAG, "Stop failed: ${e.message}")
            } finally {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    /**
     * Resets the retry counter and reattempts startup by re-reading
     * current settings and refreshing OAuth tokens if needed.
     */
    private fun handleRetry() {
        retryPolicy.reset()
        handleStartFromSettings()
    }

    /**
     * Handles a [START_STICKY] restart where the system delivers a null
     * intent after process death. Restores the daemon using the persisted
     * configuration if available; otherwise stops the service.
     */
    private fun handleStickyRestart() {
        val saved =
            persistence.restoreConfiguration() ?: run {
                stopSelf()
                return
            }
        Log.i(TAG, "Restoring daemon after process death")
        handleStart(saved.configToml, saved.host, saved.port)
    }

    /**
     * Launches a coroutine that tries to start the daemon, retrying
     * with exponential backoff on failure until [RetryPolicy] is
     * exhausted.
     */
    private fun attemptStart(
        configToml: String,
        host: String,
        port: UShort,
    ) {
        startJob?.cancel()
        startJob =
            serviceScope.launch {
                while (true) {
                    try {
                        bridge.start(
                            configToml = configToml,
                            host = host,
                            port = port,
                        )
                        persistence.recordRunning(configToml, host, port)
                        retryPolicy.reset()
                        startStatusPolling()
                        return@launch
                    } catch (e: FfiException) {
                        val errorMsg = e.message ?: "Unknown error"
                        val delayMs = retryPolicy.nextDelay()
                        if (delayMs != null) {
                            Log.w(TAG, "Daemon start failed: $errorMsg, retrying in ${delayMs}ms")
                            logRepository.append(
                                LogSeverity.WARN,
                                TAG,
                                "Start failed: $errorMsg (retrying)",
                            )
                            delay(delayMs)
                        } else {
                            Log.e(TAG, "Daemon start failed after max retries: $errorMsg")
                            logRepository.append(LogSeverity.ERROR, TAG, "Start failed: $errorMsg")
                            notificationManager.updateNotification(
                                ServiceState.ERROR,
                                errorDetail = errorMsg,
                            )
                            return@launch
                        }
                    }
                }
            }
    }

    private fun startStatusPolling() {
        statusPollJob?.cancel()
        statusPollJob =
            serviceScope.launch {
                while (true) {
                    delay(STATUS_POLL_INTERVAL_MS)
                    try {
                        bridge.pollStatus()
                    } catch (e: FfiException) {
                        Log.w(TAG, "Status poll failed: ${e.message}")
                        logRepository.append(
                            LogSeverity.WARN,
                            TAG,
                            "Status poll failed: ${e.message}",
                        )
                        notificationManager.updateNotification(
                            ServiceState.ERROR,
                            errorDetail = e.message,
                        )
                    }
                }
            }
    }

    private fun observeServiceState() {
        serviceScope.launch {
            bridge.serviceState.collect { state ->
                notificationManager.updateNotification(
                    state,
                    errorDetail = if (state == ServiceState.ERROR) bridge.lastError.value else null,
                )
            }
        }
    }

    /**
     * Logs network connectivity changes while the daemon is running.
     *
     * The daemon operates on localhost so connectivity loss does not
     * require pausing or stopping it. This log aids debugging when
     * outbound channel components fail during network gaps.
     */
    private fun observeNetworkState() {
        serviceScope.launch {
            networkMonitor.isConnected.collect { connected ->
                if (!connected && bridge.serviceState.value == ServiceState.RUNNING) {
                    Log.w(TAG, "Network connectivity lost while daemon running")
                }
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock =
            powerManager
                .newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    WAKE_LOCK_TAG,
                ).apply {
                    acquire(WAKE_LOCK_TIMEOUT_MS)
                }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }

    /** Constants for [ZeroClawDaemonService]. */
    companion object {
        /** Intent action to start the daemon and enter the foreground. */
        const val ACTION_START = "com.zeroclaw.android.action.START_DAEMON"

        /** Intent action to stop the daemon and leave the foreground. */
        const val ACTION_STOP = "com.zeroclaw.android.action.STOP_DAEMON"

        /** Intent action to retry daemon startup after a failure. */
        const val ACTION_RETRY = "com.zeroclaw.android.action.RETRY_DAEMON"

        private const val TAG = "ZeroClawDaemonService"
        private const val STATUS_POLL_INTERVAL_MS = 5_000L
        private const val WAKE_LOCK_TAG = "zeroclaw:daemon"
        private const val WAKE_LOCK_TIMEOUT_MS = 4L * 60 * 60 * 1000
    }
}
