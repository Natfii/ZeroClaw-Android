/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts [ZeroClawDaemonService] after the device finishes booting.
 *
 * Registered in the manifest with [Intent.ACTION_BOOT_COMPLETED]. The
 * service only auto-starts if the user has previously enabled the
 * auto-start preference stored in [PREFS_NAME] under [KEY_AUTO_START].
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean(KEY_AUTO_START, false)
        if (!autoStart) return

        val serviceIntent =
            Intent(
                context,
                ZeroClawDaemonService::class.java,
            ).apply {
                action = ZeroClawDaemonService.ACTION_START
            }
        context.startForegroundService(serviceIntent)
    }

    /** Constants for [BootReceiver]. */
    companion object {
        /** SharedPreferences file for service configuration. */
        const val PREFS_NAME = "zeroclaw_service"

        /** Key that controls whether the daemon starts on boot. */
        const val KEY_AUTO_START = "auto_start_on_boot"
    }
}
