/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.navigation

/**
 * Sealed interface representing navigation actions from the settings root screen.
 *
 * Consolidates individual navigation callbacks into a single typed action,
 * reducing the [SettingsScreen][com.zeroclaw.android.ui.screen.settings.SettingsScreen]
 * parameter count from 22 lambdas to one.
 */
sealed interface SettingsNavAction {
    /** Navigate to the service configuration screen. */
    data object ServiceConfig : SettingsNavAction

    /** Navigate to the battery settings screen. */
    data object Battery : SettingsNavAction

    /** Navigate to the API keys management screen. */
    data object ApiKeys : SettingsNavAction

    /** Navigate to the connected channels screen. */
    data object Channels : SettingsNavAction

    /** Navigate to the log viewer screen. */
    data object LogViewer : SettingsNavAction

    /** Navigate to the ZeroClaw Doctor screen. */
    data object Doctor : SettingsNavAction

    /** Navigate to the agent identity screen. */
    data object Identity : SettingsNavAction

    /** Navigate to the about screen. */
    data object About : SettingsNavAction

    /** Navigate to the updates screen. */
    data object Updates : SettingsNavAction

    /** Navigate to the autonomy settings screen. */
    data object Autonomy : SettingsNavAction

    /** Navigate to the tunnel configuration screen. */
    data object Tunnel : SettingsNavAction

    /** Navigate to the gateway and pairing screen. */
    data object Gateway : SettingsNavAction

    /** Navigate to the tool management screen. */
    data object ToolManagement : SettingsNavAction

    /** Navigate to the model routes screen. */
    data object ModelRoutes : SettingsNavAction

    /** Navigate to the memory advanced settings screen. */
    data object MemoryAdvanced : SettingsNavAction

    /** Navigate to the scheduler and heartbeat screen. */
    data object Scheduler : SettingsNavAction

    /** Navigate to the observability settings screen. */
    data object Observability : SettingsNavAction

    /** Navigate to the security overview screen. */
    data object SecurityOverview : SettingsNavAction

    /** Navigate to the plugin registry screen. */
    data object PluginRegistry : SettingsNavAction

    /** Navigate to the scheduled tasks (cron jobs) screen. */
    data object CronJobs : SettingsNavAction

    /** Navigate to the tools browser screen. */
    data object ToolsBrowser : SettingsNavAction

    /** Navigate to the memory browser screen. */
    data object MemoryBrowser : SettingsNavAction
}
