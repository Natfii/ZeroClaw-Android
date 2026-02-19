/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.model.ThemeMode
import com.zeroclaw.android.navigation.ZeroClawAppShell
import com.zeroclaw.android.ui.theme.ZeroClawTheme

/**
 * Main entry point for the ZeroClaw Android application.
 *
 * Sets up edge-to-edge display and delegates all UI to
 * [ZeroClawAppShell] which manages navigation, the adaptive
 * navigation bar, and all screens.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as ZeroClawApplication
            val settings by app.settingsRepository.settings
                .collectAsStateWithLifecycle(
                    initialValue = AppSettings(),
                )
            val darkTheme =
                when (settings.theme) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            ZeroClawTheme(darkTheme = darkTheme) {
                @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
                val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
                ZeroClawAppShell(
                    windowWidthSizeClass = windowSizeClass.widthSizeClass,
                )
            }
        }
    }
}
