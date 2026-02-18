/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.zeroclaw.android.R
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.ServiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile that toggles the ZeroClaw daemon on and off.
 *
 * When the tile is visible in the notification shade, this service observes
 * [DaemonServiceBridge.serviceState] and maps each [ServiceState] to the
 * appropriate [Tile] state, label, and icon. Tapping the tile sends an
 * [ACTION_START][ZeroClawDaemonService.ACTION_START] or
 * [ACTION_STOP][ZeroClawDaemonService.ACTION_STOP] intent to the foreground
 * service. Taps during transitional states ([ServiceState.STARTING],
 * [ServiceState.STOPPING]) are ignored to prevent conflicting commands.
 *
 * The tile uses [R.drawable.ic_notification] as its icon across all states
 * so the crab glyph is recognisable regardless of the system tint applied
 * by the launcher.
 *
 * Lifecycle: [onStartListening] creates a coroutine scope that collects the
 * bridge state flow. [onStopListening] cancels the scope to prevent leaks
 * when the shade is dismissed.
 */
class DaemonTileService : TileService() {
    private var scope: CoroutineScope? = null
    private var collectJob: Job? = null

    /**
     * Called when the tile becomes visible in the Quick Settings panel.
     *
     * Creates a [CoroutineScope] tied to [Dispatchers.Main] and begins
     * collecting [DaemonServiceBridge.serviceState] to keep the tile
     * appearance synchronised with the daemon lifecycle. An immediate
     * [refreshTile] call is made to show the correct state before the
     * first flow emission arrives.
     */
    override fun onStartListening() {
        super.onStartListening()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = newScope

        refreshTile(currentServiceState())

        collectJob =
            newScope.launch {
                bridgeOrNull()?.serviceState?.collect { state ->
                    refreshTile(state)
                }
            }
    }

    /**
     * Called when the tile is no longer visible in the Quick Settings panel.
     *
     * Cancels the coroutine scope to stop collecting the state flow and
     * release all associated resources.
     */
    override fun onStopListening() {
        collectJob?.cancel()
        collectJob = null
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    /**
     * Called when the user taps the tile.
     *
     * Toggles the daemon based on the current [ServiceState]:
     * - [ServiceState.STOPPED] or [ServiceState.ERROR]: sends
     *   [ZeroClawDaemonService.ACTION_START] to start the daemon.
     * - [ServiceState.RUNNING]: sends
     *   [ZeroClawDaemonService.ACTION_STOP] to stop the daemon.
     * - [ServiceState.STARTING] or [ServiceState.STOPPING]: no-op,
     *   because the daemon is already transitioning and sending a
     *   conflicting command could leave the service in an inconsistent
     *   state.
     */
    override fun onClick() {
        super.onClick()

        when (currentServiceState()) {
            ServiceState.STOPPED,
            ServiceState.ERROR,
            -> sendDaemonIntent(ZeroClawDaemonService.ACTION_START)

            ServiceState.RUNNING ->
                sendDaemonIntent(ZeroClawDaemonService.ACTION_STOP)

            ServiceState.STARTING,
            ServiceState.STOPPING,
            -> Unit
        }
    }

    /**
     * Updates the [Tile] appearance to reflect the given [ServiceState].
     *
     * Maps each state to a tile state constant, a human-readable label,
     * and the shared notification icon. The tile is updated atomically
     * via [Tile.updateTile].
     *
     * @param state Current daemon lifecycle state.
     */
    private fun refreshTile(state: ServiceState) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)

        when (state) {
            ServiceState.RUNNING -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = LABEL_RUNNING
                tile.contentDescription = LABEL_RUNNING
            }

            ServiceState.STOPPED -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = LABEL_STOPPED
                tile.contentDescription = LABEL_STOPPED
            }

            ServiceState.ERROR -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = LABEL_ERROR
                tile.contentDescription = LABEL_ERROR
            }

            ServiceState.STARTING -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = LABEL_TRANSITIONING
                tile.contentDescription = LABEL_TRANSITIONING
            }

            ServiceState.STOPPING -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = LABEL_TRANSITIONING
                tile.contentDescription = LABEL_TRANSITIONING
            }
        }
        tile.updateTile()
    }

    /**
     * Reads the current [ServiceState] from the [DaemonServiceBridge].
     *
     * Returns [ServiceState.STOPPED] if the application has not yet
     * initialised the bridge (e.g. during early process creation).
     *
     * @return The current daemon lifecycle state.
     */
    private fun currentServiceState(): ServiceState = bridgeOrNull()?.serviceState?.value ?: ServiceState.STOPPED

    /**
     * Returns the shared [DaemonServiceBridge] or `null` if the
     * application context is not yet available.
     *
     * @return The bridge instance, or `null`.
     */
    private fun bridgeOrNull(): DaemonServiceBridge? = (applicationContext as? ZeroClawApplication)?.daemonBridge

    /**
     * Sends an intent to [ZeroClawDaemonService] with the specified action.
     *
     * Uses [startForegroundService] because the daemon service declares a
     * foreground service type and must be started as a foreground service
     * on Android 8.0+.
     *
     * @param action One of [ZeroClawDaemonService.ACTION_START],
     *   [ZeroClawDaemonService.ACTION_STOP], or
     *   [ZeroClawDaemonService.ACTION_RETRY].
     */
    private fun sendDaemonIntent(action: String) {
        val intent =
            Intent(this, ZeroClawDaemonService::class.java).apply {
                this.action = action
            }
        startForegroundService(intent)
    }

    /** Constants for [DaemonTileService]. */
    companion object {
        private const val LABEL_RUNNING = "ZeroClaw: Running"
        private const val LABEL_STOPPED = "ZeroClaw: Stopped"
        private const val LABEL_ERROR = "ZeroClaw: Error"
        private const val LABEL_TRANSITIONING = "ZeroClaw: ..."
    }
}
