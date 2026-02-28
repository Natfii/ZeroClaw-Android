/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.zeroclaw.android.ui.screen.setup.SetupProgress
import com.zeroclaw.android.ui.screen.setup.SetupStepStatus
import com.zeroclaw.ffi.FfiException
import com.zeroclaw.ffi.validateConfig
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives the daemon setup pipeline with polling-based health verification.
 *
 * Orchestrates the sequential steps of validating configuration, scaffolding
 * the workspace, starting the foreground service and daemon, and verifying
 * that the daemon and its channels become healthy. Each step's status is
 * published to [progress] so the UI can render real-time feedback.
 *
 * Two entry points are provided:
 * - [runFullSetup] for initial configuration including workspace scaffolding.
 * - [runHotReload] for restarting the daemon after settings changes without
 *   re-scaffolding.
 *
 * Both methods use exponential-backoff polling to wait for the daemon and
 * channels to report healthy status.
 *
 * @param daemonBridge Bridge for daemon lifecycle operations.
 * @param healthBridge Bridge for structured health detail queries.
 */
class SetupOrchestrator(
    private val daemonBridge: DaemonServiceBridge,
    private val healthBridge: HealthBridge,
) {
    private val _progress = MutableStateFlow(SetupProgress())

    /** Observable progress across all setup steps. */
    val progress: StateFlow<SetupProgress> = _progress.asStateFlow()

    /**
     * Executes the full daemon setup pipeline.
     *
     * Runs each step sequentially: config validation, workspace scaffolding,
     * daemon start, daemon health polling, and channel health polling. If any
     * step fails, subsequent steps are skipped and progress reflects the
     * failure point.
     *
     * Safe to call from the main thread; blocking FFI calls are dispatched
     * internally to [Dispatchers.IO].
     *
     * @param context Application or activity context for starting the
     *   foreground service.
     * @param configToml Complete TOML configuration string for the daemon.
     * @param agentName Name for the AI agent identity file.
     * @param userName Name of the human user for identity files.
     * @param timezone IANA timezone ID for identity files.
     * @param communicationStyle Preferred communication tone for identity files.
     * @param expectedChannels List of channel names expected to become healthy.
     * @param host Gateway bind address.
     * @param port Gateway bind port.
     * @throws CancellationException if the calling coroutine is cancelled.
     */
    @Suppress("LongParameterList", "TooGenericExceptionCaught")
    suspend fun runFullSetup(
        context: Context,
        configToml: String,
        agentName: String,
        userName: String,
        timezone: String,
        communicationStyle: String,
        expectedChannels: List<String>,
        host: String = "127.0.0.1",
        port: UShort,
    ) {
        _progress.value =
            SetupProgress(
                channels = expectedChannels.associateWith { SetupStepStatus.Pending },
            )

        if (!stepValidateConfig(configToml)) return
        if (!stepScaffoldWorkspace(agentName, userName, timezone, communicationStyle)) return
        stopDaemonIfRunning()
        if (!stepStartDaemon(context, configToml, host, port)) return
        if (!stepAwaitDaemonHealth()) return
        stepAwaitChannels(expectedChannels)
    }

    /**
     * Restarts the daemon with updated configuration without re-scaffolding.
     *
     * Marks config validation and workspace scaffolding as already successful,
     * stops the current daemon (if running), then starts the daemon and awaits
     * health. Use this after settings changes that do not affect workspace
     * identity files.
     *
     * Safe to call from the main thread; blocking FFI calls are dispatched
     * internally to [Dispatchers.IO].
     *
     * @param context Application or activity context for starting the
     *   foreground service.
     * @param configToml Updated TOML configuration string.
     * @param expectedChannels List of channel names expected to become healthy.
     * @param host Gateway bind address.
     * @param port Gateway bind port.
     * @throws CancellationException if the calling coroutine is cancelled.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun runHotReload(
        context: Context,
        configToml: String,
        expectedChannels: List<String>,
        host: String = "127.0.0.1",
        port: UShort,
    ) {
        _progress.value =
            SetupProgress(
                configValidation = SetupStepStatus.Success,
                workspaceScaffold = SetupStepStatus.Success,
                channels = expectedChannels.associateWith { SetupStepStatus.Pending },
            )

        try {
            daemonBridge.stop()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Stop before hot-reload failed (non-fatal): ${e.message}")
        }

        if (!stepStartDaemon(context, configToml, host, port)) return
        if (!stepAwaitDaemonHealth()) return
        stepAwaitChannels(expectedChannels)
    }

    /**
     * Resets progress to its initial empty state.
     *
     * Call this before starting a new setup attempt so the UI shows all
     * steps as [SetupStepStatus.Pending].
     */
    fun reset() {
        _progress.value = SetupProgress()
    }

    /**
     * Stops the daemon if it is currently running.
     *
     * Called before starting a fresh daemon to avoid a "daemon already
     * running" error when the user re-runs the setup wizard from settings.
     * Failures are logged but not propagated since the daemon may not be
     * running at all.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun stopDaemonIfRunning() {
        try {
            val status = daemonBridge.pollStatus()
            if (status.running) {
                Log.d(TAG, "Daemon already running, stopping before fresh setup")
                daemonBridge.stop()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Pre-setup daemon stop check failed (non-fatal): ${e.message}")
        }
    }

    /**
     * Validates the TOML configuration string via FFI.
     *
     * @return `true` if validation passed, `false` on failure.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun stepValidateConfig(configToml: String): Boolean {
        _progress.value =
            _progress.value.copy(
                configValidation = SetupStepStatus.Running,
            )

        return try {
            val result = withContext(Dispatchers.IO) { validateConfig(configToml) }
            if (result.isEmpty()) {
                _progress.value =
                    _progress.value.copy(
                        configValidation = SetupStepStatus.Success,
                    )
                true
            } else {
                _progress.value =
                    _progress.value.copy(
                        configValidation = SetupStepStatus.Failed(error = result),
                    )
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "Config validation failed"
            Log.e(TAG, "Config validation error: $msg")
            _progress.value =
                _progress.value.copy(
                    configValidation = SetupStepStatus.Failed(error = msg),
                )
            false
        }
    }

    /**
     * Scaffolds the workspace directory with identity template files.
     *
     * @return `true` if scaffolding succeeded, `false` on failure.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun stepScaffoldWorkspace(
        agentName: String,
        userName: String,
        timezone: String,
        communicationStyle: String,
    ): Boolean {
        _progress.value =
            _progress.value.copy(
                workspaceScaffold = SetupStepStatus.Running,
            )

        return try {
            daemonBridge.ensureWorkspace(agentName, userName, timezone, communicationStyle)
            _progress.value =
                _progress.value.copy(
                    workspaceScaffold = SetupStepStatus.Success,
                )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "Workspace scaffolding failed"
            Log.e(TAG, "Workspace scaffold error: $msg")
            _progress.value =
                _progress.value.copy(
                    workspaceScaffold = SetupStepStatus.Failed(error = msg),
                )
            false
        }
    }

    /**
     * Starts the foreground service and daemon.
     *
     * Sends a [startForegroundService] intent first so the system does not
     * kill the process, then calls the bridge to start the native daemon.
     *
     * @return `true` if the daemon started, `false` on failure.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun stepStartDaemon(
        context: Context,
        configToml: String,
        host: String,
        port: UShort,
    ): Boolean {
        _progress.value =
            _progress.value.copy(
                daemonStart = SetupStepStatus.Running,
            )

        return try {
            val intent = Intent(context, ZeroClawDaemonService::class.java)
            context.startForegroundService(intent)
            daemonBridge.start(configToml, host, port)
            _progress.value =
                _progress.value.copy(
                    daemonStart = SetupStepStatus.Success,
                )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "Daemon start failed"
            Log.e(TAG, "Daemon start error: $msg")
            _progress.value =
                _progress.value.copy(
                    daemonStart = SetupStepStatus.Failed(error = msg),
                )
            false
        }
    }

    /**
     * Polls daemon status until it reports as running.
     *
     * Uses [pollWithBackoff] with a [DAEMON_HEALTH_TIMEOUT_MS] timeout.
     *
     * @return `true` if the daemon became healthy, `false` on timeout.
     */
    private suspend fun stepAwaitDaemonHealth(): Boolean {
        _progress.value =
            _progress.value.copy(
                daemonHealth = SetupStepStatus.Running,
            )

        val healthy =
            pollWithBackoff(
                timeoutMs = DAEMON_HEALTH_TIMEOUT_MS,
                label = "daemon health",
            ) {
                try {
                    val status = daemonBridge.pollStatus()
                    status.running
                } catch (e: FfiException) {
                    Log.w(TAG, "Health poll attempt failed: ${e.message}")
                    false
                }
            }

        if (healthy) {
            _progress.value =
                _progress.value.copy(
                    daemonHealth = SetupStepStatus.Success,
                )
        } else {
            _progress.value =
                _progress.value.copy(
                    daemonHealth =
                        SetupStepStatus.Failed(
                            error = "Daemon did not become healthy within ${DAEMON_HEALTH_TIMEOUT_MS / MILLIS_PER_SECOND}s",
                        ),
                )
        }

        return healthy
    }

    /**
     * Polls structured health detail until all expected channels report healthy.
     *
     * Marks each channel as [SetupStepStatus.Running] at the start, then
     * transitions to [SetupStepStatus.Success] or [SetupStepStatus.Failed]
     * based on the "channels" component status in the health detail response.
     * Channels that are still pending at timeout are marked as failed.
     *
     * @param expectedChannels Channel names to wait for.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun stepAwaitChannels(expectedChannels: List<String>) {
        if (expectedChannels.isEmpty()) return

        _progress.value =
            _progress.value.copy(
                channels = expectedChannels.associateWith { SetupStepStatus.Running },
            )

        val resolved =
            pollWithBackoff(
                timeoutMs = CHANNEL_HEALTH_TIMEOUT_MS,
                label = "channel health",
            ) {
                try {
                    pollChannelHealth(expectedChannels)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Channel health poll failed: ${e.message}")
                    false
                }
            }

        if (!resolved) {
            markChannelsTimedOut()
        }
    }

    /**
     * Polls the health bridge for channel status and updates progress.
     *
     * Queries the structured health detail and checks the "channels"
     * component status. Updates [_progress] with per-channel results.
     *
     * @param expectedChannels Channel names to check.
     * @return `true` if channels resolved to a terminal state (ok or error),
     *   `false` if still pending.
     */
    private suspend fun pollChannelHealth(expectedChannels: List<String>): Boolean {
        val detail = healthBridge.getHealthDetail()
        val channelsComponent = detail.components.find { it.name == "channels" }

        return when (channelsComponent?.status) {
            "ok" -> {
                _progress.value =
                    _progress.value.copy(
                        channels = expectedChannels.associateWith { SetupStepStatus.Success },
                    )
                true
            }
            "error" -> {
                val errorMsg =
                    channelsComponent.lastError ?: "Channel component reported error"
                _progress.value =
                    _progress.value.copy(
                        channels =
                            expectedChannels.associateWith {
                                SetupStepStatus.Failed(error = errorMsg)
                            },
                    )
                true
            }
            else -> false
        }
    }

    /**
     * Marks any still-pending channels as failed due to timeout.
     *
     * Reads current channel statuses from [_progress] and transitions
     * any that are still [SetupStepStatus.Running] or [SetupStepStatus.Pending]
     * to [SetupStepStatus.Failed].
     */
    private fun markChannelsTimedOut() {
        val timeoutMsg =
            "Channels did not become healthy within ${CHANNEL_HEALTH_TIMEOUT_MS / MILLIS_PER_SECOND}s"
        val currentChannels = _progress.value.channels
        _progress.value =
            _progress.value.copy(
                channels =
                    currentChannels.mapValues { (_, status) ->
                        if (status is SetupStepStatus.Running || status is SetupStepStatus.Pending) {
                            SetupStepStatus.Failed(error = timeoutMsg)
                        } else {
                            status
                        }
                    },
            )
    }

    /**
     * Polls a condition with exponential backoff until it returns `true` or
     * the timeout elapses.
     *
     * Starts with [INITIAL_BACKOFF_MS] and doubles the delay each iteration,
     * capping at [MAX_BACKOFF_MS]. The entire polling loop is bounded by
     * [timeoutMs] using [withTimeoutOrNull].
     *
     * @param timeoutMs Hard deadline in milliseconds.
     * @param label Descriptive label for log messages.
     * @param check Suspend function that returns `true` when the condition is met.
     * @return `true` if [check] returned `true` before timeout, `false` otherwise.
     * @throws CancellationException if the calling coroutine is cancelled.
     */
    private suspend fun pollWithBackoff(
        timeoutMs: Long,
        label: String,
        check: suspend () -> Boolean,
    ): Boolean {
        var backoffMs = INITIAL_BACKOFF_MS
        return withTimeoutOrNull(timeoutMs) {
            while (true) {
                if (check()) return@withTimeoutOrNull true
                Log.d(TAG, "Polling $label, next attempt in ${backoffMs}ms")
                delay(backoffMs)
                backoffMs =
                    (backoffMs * BACKOFF_MULTIPLIER)
                        .toLong()
                        .coerceAtMost(MAX_BACKOFF_MS)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false
    }

    /** Constants for [SetupOrchestrator]. */
    companion object {
        private const val TAG = "SetupOrchestrator"
        private const val INITIAL_BACKOFF_MS = 500L
        private const val BACKOFF_MULTIPLIER = 2.0
        private const val MAX_BACKOFF_MS = 5_000L
        private const val DAEMON_HEALTH_TIMEOUT_MS = 30_000L
        private const val CHANNEL_HEALTH_TIMEOUT_MS = 60_000L
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
