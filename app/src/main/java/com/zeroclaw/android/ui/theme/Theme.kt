/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.zeroclaw.android.util.LocalPowerSaveMode
import com.zeroclaw.android.util.rememberPowerSaveMode

private val DarkColorScheme = darkColorScheme()

private val LightColorScheme = lightColorScheme()

/**
 * Application theme for ZeroClaw.
 *
 * Uses Material You dynamic colour on Android 12+ and falls back to
 * the default Material 3 colour scheme on older API levels. Provides
 * [LocalPowerSaveMode] for battery-conscious rendering and
 * [ZeroClawTypography] for explicit sp-based line heights.
 *
 * @param darkTheme Whether to apply the dark variant of the theme.
 * @param dynamicColor Whether to use platform dynamic colour (Material You).
 * @param content The composable content to theme.
 */
@Composable
fun ZeroClawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val isPowerSave = rememberPowerSaveMode()

    CompositionLocalProvider(LocalPowerSaveMode provides isPowerSave) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ZeroClawTypography,
            content = content,
        )
    }
}
