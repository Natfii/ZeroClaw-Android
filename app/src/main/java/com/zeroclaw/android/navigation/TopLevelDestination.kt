/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level navigation destinations displayed in the bottom navigation bar.
 *
 * Each entry defines both selected and unselected icons along with a
 * content description label used for accessibility.
 *
 * @property selectedIcon Icon displayed when this destination is active.
 * @property unselectedIcon Icon displayed when this destination is inactive.
 * @property label Human-readable label for the destination.
 * @property route Navigation route object for this destination.
 */
enum class TopLevelDestination(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String,
    val route: Any,
) {
    /** Dashboard overview with daemon status and activity feed. */
    DASHBOARD(
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard,
        label = "Dashboard",
        route = DashboardRoute,
    ),

    /** Agent list and management. */
    AGENTS(
        selectedIcon = Icons.Filled.SmartToy,
        unselectedIcon = Icons.Outlined.SmartToy,
        label = "Agents",
        route = AgentsRoute,
    ),

    /** Plugin list and management. */
    PLUGINS(
        selectedIcon = Icons.Filled.Extension,
        unselectedIcon = Icons.Outlined.Extension,
        label = "Plugins",
        route = PluginsRoute,
    ),

    /** Global daemon console for sending messages to the gateway. */
    CONSOLE(
        selectedIcon = Icons.Filled.Terminal,
        unselectedIcon = Icons.Outlined.Terminal,
        label = "Console",
        route = ConsoleRoute,
    ),

    /** Application settings and configuration. */
    SETTINGS(
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        label = "Settings",
        route = SettingsRoute,
    ),
}
