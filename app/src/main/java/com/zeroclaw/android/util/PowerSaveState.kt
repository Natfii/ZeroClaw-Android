/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Composition local that provides the current power-save state.
 *
 * `true` when the system power saver is active or the global
 * animator duration scale is zero (Samsung Battery Guardian).
 * Composables should check this to disable or simplify animations.
 */
val LocalPowerSaveMode =
    staticCompositionLocalOf { false }

/**
 * Remembers the current power-save mode and updates when it changes.
 *
 * Registers a [BroadcastReceiver] for
 * [PowerManager.ACTION_POWER_SAVE_MODE_CHANGED] and reads the initial
 * state from [PowerManager.isPowerSaveMode]. Also checks
 * [Settings.Global.ANIMATOR_DURATION_SCALE] for Samsung Battery Guardian
 * which sets the scale to zero.
 *
 * @return `true` if the device is in power-save mode.
 */
@Composable
fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    var isPowerSave by remember { mutableStateOf(checkPowerSave(context)) }

    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context,
                    intent: Intent?,
                ) {
                    isPowerSave = checkPowerSave(ctx)
                }
            }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        context.registerReceiver(receiver, filter)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    return isPowerSave
}

private fun checkPowerSave(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (pm.isPowerSaveMode) return true
    val scale =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    return scale == 0f
}
