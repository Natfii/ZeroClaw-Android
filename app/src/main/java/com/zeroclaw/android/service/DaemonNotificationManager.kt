/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.zeroclaw.android.MainActivity
import com.zeroclaw.android.R
import com.zeroclaw.android.model.ServiceState

/**
 * Manages the notification channel and ongoing notification for
 * [ZeroClawDaemonService].
 *
 * Creates a default-importance channel with sound, vibration, and lights
 * disabled to avoid audible alerts while keeping the notification visible
 * in the shade and status bar. Notification
 * updates are throttled to a maximum of once per [MIN_UPDATE_INTERVAL_MS]
 * to avoid CPU spikes from rapid state changes.
 *
 * @param context Application or service context used for system services.
 */
class DaemonNotificationManager(
    private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Volatile
    private var lastUpdateTimeMs = 0L

    @Volatile
    private var lastState: ServiceState? = null

    /**
     * Creates the notification channel if it does not already exist.
     *
     * Must be called before posting any notifications, typically during
     * [ZeroClawDaemonService.onCreate].
     */
    fun createChannel() {
        notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Builds the foreground service notification for the given [state].
     *
     * Includes a content intent that opens [MainActivity], a stop
     * action button when the daemon is in [ServiceState.RUNNING],
     * and a retry action button when in [ServiceState.ERROR].
     *
     * @param state Current lifecycle state of the service.
     * @param errorDetail Optional error message to display when [state]
     *   is [ServiceState.ERROR]. Truncated to [MAX_ERROR_DISPLAY_LENGTH]
     *   characters for the notification content.
     * @return A built [Notification] ready for
     *   [android.app.Service.startForeground].
     */
    fun buildNotification(
        state: ServiceState,
        errorDetail: String? = null,
    ): Notification {
        val contentIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(statusText(state, errorDetail))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(
                    NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE,
                )

        if (state == ServiceState.RUNNING) {
            val stopIntent =
                PendingIntent.getService(
                    context,
                    REQUEST_CODE_STOP,
                    Intent(context, ZeroClawDaemonService::class.java).apply {
                        action = ZeroClawDaemonService.ACTION_STOP
                    },
                    PendingIntent.FLAG_IMMUTABLE,
                )
            builder.addAction(
                R.drawable.ic_stop,
                context.getString(R.string.notification_action_stop),
                stopIntent,
            )
        }

        if (state == ServiceState.ERROR) {
            val retryIntent =
                PendingIntent.getService(
                    context,
                    REQUEST_CODE_RETRY,
                    Intent(context, ZeroClawDaemonService::class.java).apply {
                        action = ZeroClawDaemonService.ACTION_RETRY
                    },
                    PendingIntent.FLAG_IMMUTABLE,
                )
            builder.addAction(
                R.drawable.ic_retry,
                context.getString(R.string.notification_action_retry),
                retryIntent,
            )
        }

        return builder.build()
    }

    /**
     * Updates the posted notification if at least [MIN_UPDATE_INTERVAL_MS]
     * has elapsed since the last update, or immediately when the [state]
     * differs from the previously posted state.
     *
     * @param state Current lifecycle state of the service.
     * @param errorDetail Optional error message forwarded to [buildNotification].
     */
    fun updateNotification(
        state: ServiceState,
        errorDetail: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val stateChanged = state != lastState
        if (!stateChanged && now - lastUpdateTimeMs < MIN_UPDATE_INTERVAL_MS) return
        lastUpdateTimeMs = now
        lastState = state
        notificationManager.notify(NOTIFICATION_ID, buildNotification(state, errorDetail))
    }

    private fun statusText(
        state: ServiceState,
        errorDetail: String? = null,
    ): String =
        when (state) {
            ServiceState.STOPPED ->
                context.getString(R.string.notification_status_stopped)
            ServiceState.STARTING ->
                context.getString(R.string.notification_status_starting)
            ServiceState.RUNNING ->
                context.getString(R.string.notification_status_running)
            ServiceState.STOPPING ->
                context.getString(R.string.notification_status_stopping)
            ServiceState.ERROR ->
                if (errorDetail != null) {
                    "Error: ${errorDetail.take(MAX_ERROR_DISPLAY_LENGTH)}"
                } else {
                    context.getString(R.string.notification_status_error)
                }
        }

    /** Constants for [DaemonNotificationManager]. */
    companion object {
        /** Notification channel identifier. */
        const val CHANNEL_ID = "zeroclaw_daemon_v2"

        /** Legacy channel identifier, deleted during migration. */
        private const val LEGACY_CHANNEL_ID = "zeroclaw_daemon"

        /** Identifier for the ongoing foreground notification. */
        const val NOTIFICATION_ID = 1

        /** Minimum interval between notification updates in milliseconds. */
        const val MIN_UPDATE_INTERVAL_MS = 30_000L

        private const val REQUEST_CODE_STOP = 100
        private const val REQUEST_CODE_RETRY = 101
        private const val MAX_ERROR_DISPLAY_LENGTH = 50
    }
}
