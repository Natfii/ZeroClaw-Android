/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.data.repository.ActivityRepository
import com.zeroclaw.android.data.repository.AgentRepository
import com.zeroclaw.android.data.repository.ApiKeyRepository
import com.zeroclaw.android.data.repository.ChannelConfigRepository
import com.zeroclaw.android.data.repository.LogRepository
import com.zeroclaw.android.data.repository.SettingsRepository
import com.zeroclaw.android.model.ActivityType
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.model.LogSeverity
import com.zeroclaw.android.model.MemoryConflict
import com.zeroclaw.android.model.MemoryHealthResult
import com.zeroclaw.android.model.ServiceState
import com.zeroclaw.android.util.LogSanitizer
import com.zeroclaw.ffi.FfiException
import kotlinx.coroutines.CoroutineDispatcher
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
 * an adaptive status polling loop that feeds [DaemonServiceBridge.lastStatus]
 * (5 s when the screen is on, 60 s when off), network connectivity
 * monitoring via [NetworkMonitor], and a partial wake lock with a
 * 3-minute safety timeout held only during startup (Rust FFI init and
 * tokio runtime boot). The foreground notification keeps the process alive
 * after startup, so the wake lock is released once
 * [DaemonServiceBridge.start] returns successfully.
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
 * [ServiceState.ERROR], detaches the foreground notification (keeping it
 * visible as an error indicator), and stops itself to avoid a zombie
 * service.
 *
 * Lifecycle control is performed via [Intent] actions:
 * - [ACTION_START] to start the daemon and enter the foreground.
 * - [ACTION_STOP] to stop the daemon and remove the foreground notification.
 * - [ACTION_RETRY] to reset the retry counter and attempt startup again.
 */
@Suppress("TooManyFunctions")
class ZeroClawDaemonService : Service() {
    @Suppress("InjectDispatcher")
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var bridge: DaemonServiceBridge
    private lateinit var notificationManager: DaemonNotificationManager
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var persistence: DaemonPersistence
    private lateinit var logRepository: LogRepository
    private lateinit var activityRepository: ActivityRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var apiKeyRepository: ApiKeyRepository
    private lateinit var channelConfigRepository: ChannelConfigRepository
    private lateinit var agentRepository: AgentRepository
    private val retryPolicy = RetryPolicy()

    private var statusPollJob: Job? = null
    private var startJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var isScreenOn: Boolean = true
    private var screenReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as ZeroClawApplication
        bridge = app.daemonBridge
        logRepository = app.logRepository
        activityRepository = app.activityRepository
        settingsRepository = app.settingsRepository
        apiKeyRepository = app.apiKeyRepository
        channelConfigRepository = app.channelConfigRepository
        agentRepository = app.agentRepository
        notificationManager = DaemonNotificationManager(this)
        networkMonitor = NetworkMonitor(this)
        persistence = DaemonPersistence(this)

        notificationManager.createChannel()
        networkMonitor.register()
        registerScreenReceiver()
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
     * [START_STICKY] ensures the system will eventually restart the service
     * if killed, but some OEM manufacturers (Xiaomi, Samsung, Huawei, Oppo,
     * Vivo) aggressively terminate services after task removal without
     * honouring [START_STICKY]. As a fallback, an [AlarmManager] alarm is
     * scheduled [RESTART_DELAY_MS] (5 seconds) in the future to restart the
     * service via [setExactAndAllowWhileIdle], which fires even in Doze mode.
     *
     * The alarm is only scheduled when the daemon is currently
     * [ServiceState.RUNNING] to avoid restarting a service the user
     * explicitly stopped.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (bridge.serviceState.value == ServiceState.RUNNING) {
            scheduleRestartAlarm()
        }
    }

    /**
     * Schedules an [AlarmManager] alarm to restart this service after a
     * short delay.
     *
     * Uses [AlarmManager.setExactAndAllowWhileIdle] so the alarm fires even
     * when the device is in Doze mode. The [PendingIntent] targets this
     * service without an explicit action, triggering the [START_STICKY] null
     * intent path in [onStartCommand] which restores the daemon from
     * persisted configuration.
     */
    private fun scheduleRestartAlarm() {
        val restartIntent =
            Intent(applicationContext, ZeroClawDaemonService::class.java)
        val pendingIntent =
            PendingIntent.getService(
                applicationContext,
                RESTART_REQUEST_CODE,
                restartIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
            )
        val alarmManager =
            getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + RESTART_DELAY_MS,
            pendingIntent,
        )
        Log.i(TAG, "Scheduled AlarmManager restart fallback in ${RESTART_DELAY_MS}ms")
    }

    override fun onDestroy() {
        releaseWakeLock()
        unregisterScreenReceiver()
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

        serviceScope.launch(ioDispatcher) {
            val settings = settingsRepository.settings.first()
            val effectiveSettings = resolveEffectiveDefaults(settings)
            val apiKey = apiKeyRepository.getByProviderFresh(effectiveSettings.defaultProvider)

            val globalConfig =
                buildGlobalTomlConfig(effectiveSettings, apiKey)
            val baseToml = ConfigTomlBuilder.build(globalConfig)
            val channelsToml =
                ConfigTomlBuilder.buildChannelsToml(
                    channelConfigRepository.getEnabledWithSecrets(),
                )
            val agentsToml = buildAgentsToml()
            val configToml = baseToml + channelsToml + agentsToml

            val conflict = bridge.detectMemoryConflict(effectiveSettings.memoryBackend)
            if (conflict is MemoryConflict.StaleData) {
                val shouldDelete = bridge.awaitConflictResolution(conflict)
                if (shouldDelete) {
                    bridge.cleanupStaleMemory(conflict)
                    logRepository.append(
                        LogSeverity.INFO,
                        TAG,
                        "Cleaned up ${conflict.staleFileCount} stale " +
                            "${conflict.staleBackend} memory files",
                    )
                }
            }

            retryPolicy.reset()
            val validPort =
                if (settings.port in VALID_PORT_RANGE) {
                    settings.port
                } else {
                    AppSettings.DEFAULT_PORT
                }
            attemptStart(
                configToml = configToml,
                host = settings.host,
                port = validPort.toUShort(),
                memoryBackend = effectiveSettings.memoryBackend,
            )
        }
    }

    /**
     * Derives effective default provider and model from the agent list.
     *
     * The first enabled agent with a non-blank provider and model name
     * overrides the DataStore values in [settings]. This ensures that
     * edits or deletions in the Connections tab take effect immediately
     * on the next daemon start, without requiring the DataStore
     * `defaultProvider` / `defaultModel` keys to be kept in sync with
     * every agent mutation.
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
     * Converts [AppSettings] and resolved API key into a [GlobalTomlConfig].
     *
     * Comma-separated string fields in [AppSettings] are split into lists
     * for [GlobalTomlConfig] properties that expect `List<String>`.
     *
     * @param settings Current application settings.
     * @param apiKey Resolved API key for the default provider, or null.
     * @return A fully populated [GlobalTomlConfig].
     */
    @Suppress("LongMethod")
    private fun buildGlobalTomlConfig(
        settings: AppSettings,
        apiKey: ApiKey?,
    ): GlobalTomlConfig =
        GlobalTomlConfig(
            provider = settings.defaultProvider,
            model = settings.defaultModel,
            apiKey = apiKey?.key.orEmpty(),
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
            securityOtpGatedDomainCategories = splitCsv(settings.securityOtpGatedDomainCategories),
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
     * For each enabled agent, the provider ID is resolved to an upstream
     * factory name and the corresponding API key is fetched (with OAuth
     * refresh if needed). Agents without a provider or model are skipped.
     *
     * @return TOML string with per-agent sections, or empty if no agents qualify.
     */
    private suspend fun buildAgentsToml(): String {
        val allAgents = agentRepository.agents.first()
        val entries =
            allAgents
                .filter { it.isEnabled && it.provider.isNotBlank() && it.modelName.isNotBlank() }
                .map { agent ->
                    val agentKey = apiKeyRepository.getByProviderFresh(agent.provider)
                    AgentTomlEntry(
                        name = agent.name,
                        provider =
                            ConfigTomlBuilder.resolveProvider(
                                agent.provider,
                                agentKey?.baseUrl.orEmpty(),
                            ),
                        model = agent.modelName,
                        apiKey = agentKey?.key.orEmpty(),
                        systemPrompt = agent.systemPrompt,
                        temperature = agent.temperature,
                        maxDepth = agent.maxDepth,
                    )
                }
        return ConfigTomlBuilder.buildAgentsToml(entries)
    }

    private fun handleStop() {
        startJob?.cancel()
        statusPollJob?.cancel()
        retryPolicy.reset()
        persistence.recordStopped()
        serviceScope.launch {
            try {
                bridge.stop()
                activityRepository.record(
                    ActivityType.DAEMON_STOPPED,
                    "Daemon stopped by user",
                )
            } catch (e: FfiException) {
                val safeMsg = LogSanitizer.sanitizeLogMessage(e.message ?: "Unknown error")
                Log.w(TAG, "Daemon stop failed: $safeMsg")
                logRepository.append(LogSeverity.ERROR, TAG, "Stop failed: $safeMsg")
                activityRepository.record(
                    ActivityType.DAEMON_ERROR,
                    "Stop failed: $safeMsg",
                )
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
     * intent after process death.
     *
     * On Android 12+ the system requires [startForeground] within a few
     * seconds of [onStartCommand], even if the service intends to stop
     * immediately. This method posts a minimal foreground notification
     * before checking whether the daemon was previously running. If it
     * was not running, the notification is removed and the service stops
     * itself. Otherwise, the daemon configuration is rebuilt fresh from
     * current settings rather than restored from the stale persisted TOML,
     * so any key rotation or deletion that happened since the last start
     * is reflected immediately.
     */
    private fun handleStickyRestart() {
        val notification =
            notificationManager.buildNotification(ServiceState.STARTING)
        startForeground(
            DaemonNotificationManager.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        if (!persistence.wasRunning()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        Log.i(TAG, "Rebuilding daemon config after process death")
        activityRepository.record(
            ActivityType.DAEMON_STARTED,
            "Daemon restored after process death",
        )
        handleStartFromSettings()
    }

    /**
     * Launches a coroutine that tries to start the daemon, retrying
     * with exponential backoff on failure until [RetryPolicy] is
     * exhausted.
     *
     * The retry loop is wrapped in a `try/finally` so that the wake
     * lock is released even if the coroutine is cancelled mid-retry.
     * When retries are exhausted the service records itself as stopped,
     * detaches the foreground notification (keeping it visible as an
     * error indicator), and calls [stopSelf] to avoid a zombie service.
     */
    private fun attemptStart(
        configToml: String,
        host: String,
        port: UShort,
        memoryBackend: String = "none",
    ) {
        startJob?.cancel()
        startJob =
            serviceScope.launch {
                try {
                    while (true) {
                        try {
                            bridge.start(
                                configToml = configToml,
                                host = host,
                                port = port,
                            )
                            releaseWakeLock()
                            persistence.recordRunning(configToml, host, port)
                            retryPolicy.reset()
                            activityRepository.record(
                                ActivityType.DAEMON_STARTED,
                                "Daemon started on $host:$port",
                            )
                            startStatusPolling()
                            runMemoryHealthCheck(memoryBackend)
                            return@launch
                        } catch (e: FfiException) {
                            val errorMsg =
                                LogSanitizer.sanitizeLogMessage(e.message ?: "Unknown error")
                            val delayMs = retryPolicy.nextDelay()
                            if (delayMs != null) {
                                Log.w(
                                    TAG,
                                    "Daemon start failed: $errorMsg, retrying in ${delayMs}ms",
                                )
                                logRepository.append(
                                    LogSeverity.WARN,
                                    TAG,
                                    "Start failed: $errorMsg (retrying)",
                                )
                                delay(delayMs)
                            } else {
                                handleStartupExhausted(errorMsg)
                                return@launch
                            }
                        }
                    }
                } finally {
                    releaseWakeLock()
                }
            }
    }

    /**
     * Handles the case where all startup retry attempts have been exhausted.
     *
     * Logs the final error, updates the notification to [ServiceState.ERROR],
     * clears the persisted "was running" flag to prevent infinite restart
     * loops, releases the wake lock, and stops the service while keeping
     * the error notification visible for the user.
     *
     * @param errorMsg Description of the last startup failure.
     */
    private fun handleStartupExhausted(errorMsg: String) {
        Log.e(TAG, "Daemon start failed after max retries: $errorMsg")
        logRepository.append(LogSeverity.ERROR, TAG, "Start failed: $errorMsg")
        activityRepository.record(
            ActivityType.DAEMON_ERROR,
            "Start failed: $errorMsg",
        )
        notificationManager.updateNotification(
            ServiceState.ERROR,
            errorDetail = errorMsg,
        )
        persistence.recordStopped()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    /**
     * Starts a coroutine that periodically polls the daemon for status.
     *
     * The poll interval adapts to screen state: [POLL_INTERVAL_FOREGROUND_MS]
     * (5 s) when the screen is on, [POLL_INTERVAL_BACKGROUND_MS] (60 s) when
     * the screen is off. This reduces CPU wake-ups and battery drain during
     * Doze and screen-off periods while still providing responsive UI updates
     * when the user is actively viewing the app.
     */
    private fun startStatusPolling() {
        statusPollJob?.cancel()
        statusPollJob =
            serviceScope.launch {
                while (true) {
                    val interval =
                        if (isScreenOn) {
                            POLL_INTERVAL_FOREGROUND_MS
                        } else {
                            POLL_INTERVAL_BACKGROUND_MS
                        }
                    delay(interval)
                    try {
                        bridge.pollStatus()
                    } catch (e: FfiException) {
                        val safeMsg =
                            LogSanitizer.sanitizeLogMessage(e.message ?: "Unknown error")
                        Log.w(TAG, "Status poll failed: $safeMsg")
                        logRepository.append(
                            LogSeverity.WARN,
                            TAG,
                            "Status poll failed: $safeMsg",
                        )
                        notificationManager.updateNotification(
                            ServiceState.ERROR,
                            errorDetail = safeMsg,
                        )
                    }
                }
            }
    }

    /**
     * Runs the memory backend health probe after a successful daemon start.
     *
     * If the probe fails, a warning is surfaced to the UI through the bridge
     * and logged. Called once per daemon start attempt.
     *
     * @param memoryBackend The configured memory backend identifier.
     */
    private fun runMemoryHealthCheck(memoryBackend: String) {
        val healthResult = bridge.checkMemoryHealth(memoryBackend)
        if (healthResult is MemoryHealthResult.Unhealthy) {
            bridge.setMemoryHealthWarning(healthResult.reason)
            logRepository.append(
                LogSeverity.WARN,
                TAG,
                "Memory health check failed: ${healthResult.reason}",
            )
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
                if (bridge.serviceState.value == ServiceState.RUNNING) {
                    if (!connected) {
                        Log.w(TAG, "Network connectivity lost while daemon running")
                        activityRepository.record(
                            ActivityType.NETWORK_CHANGE,
                            "Network connectivity lost",
                        )
                    } else {
                        activityRepository.record(
                            ActivityType.NETWORK_CHANGE,
                            "Network connectivity restored",
                        )
                    }
                }
            }
        }
    }

    /**
     * Registers a [BroadcastReceiver] for [Intent.ACTION_SCREEN_ON] and
     * [Intent.ACTION_SCREEN_OFF] to toggle [isScreenOn].
     *
     * The flag is read by [startStatusPolling] to choose between the
     * foreground and background poll intervals.
     */
    @Suppress("UnspecifiedRegisterReceiverFlag")
    private fun registerScreenReceiver() {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    isScreenOn =
                        intent?.action != Intent.ACTION_SCREEN_OFF
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        screenReceiver = receiver
    }

    /**
     * Unregisters the screen state [BroadcastReceiver] if it was
     * previously registered.
     */
    private fun unregisterScreenReceiver() {
        screenReceiver?.let { unregisterReceiver(it) }
        screenReceiver = null
    }

    /**
     * Acquires a partial wake lock for the startup phase.
     *
     * If a previous wake lock reference exists but is no longer held
     * (e.g. from a timed expiry or prior release), it is discarded and
     * a fresh lock is acquired. The lock is acquired with a
     * [WAKE_LOCK_TIMEOUT_MS] (3-minute) safety timeout so that the CPU
     * is released even if the startup coroutine is cancelled before
     * [releaseWakeLock] is called.
     */
    private fun acquireWakeLock() {
        wakeLock?.let { existing ->
            if (existing.isHeld) return
            wakeLock = null
        }
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

    /**
     * Releases the partial wake lock if it is currently held and clears
     * the reference.
     *
     * Safe to call multiple times; subsequent calls are no-ops when
     * [wakeLock] is already null or released.
     */
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
        private const val POLL_INTERVAL_FOREGROUND_MS = 5_000L
        private const val POLL_INTERVAL_BACKGROUND_MS = 60_000L
        private const val WAKE_LOCK_TAG = "zeroclaw:daemon"
        private const val WAKE_LOCK_TIMEOUT_MS = 180_000L
        private const val RESTART_DELAY_MS = 5_000L
        private const val RESTART_REQUEST_CODE = 42
        private val VALID_PORT_RANGE = 1..65535
    }
}

/**
 * Splits a comma-separated string into a trimmed, non-blank list.
 *
 * @param csv Comma-separated string (may be blank).
 * @return List of trimmed non-blank tokens; empty list if [csv] is blank.
 */
private fun splitCsv(csv: String): List<String> = csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
