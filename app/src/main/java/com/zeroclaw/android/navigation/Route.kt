/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe route definitions for the application navigation graph.
 *
 * Each route is a [Serializable] object or data class that the Navigation
 * Compose library uses for type-safe argument passing between destinations.
 */

/** Dashboard home screen showing daemon status overview. */
@Serializable
data object DashboardRoute

/** Agent list and management screen. */
@Serializable
data object AgentsRoute

/** Agent detail screen. */
@Serializable
data class AgentDetailRoute(
    /** Unique identifier of the agent to display. */
    val agentId: String,
)

/** Add new agent wizard screen. */
@Serializable
data object AddAgentRoute

/** Plugin list and management screen. */
@Serializable
data object PluginsRoute

/** Plugin detail screen. */
@Serializable
data class PluginDetailRoute(
    /** Unique identifier of the plugin to display. */
    val pluginId: String,
)

/** Root settings screen. */
@Serializable
data object SettingsRoute

/** Service configuration sub-screen. */
@Serializable
data object ServiceConfigRoute

/** Battery settings sub-screen. */
@Serializable
data object BatterySettingsRoute

/** About information sub-screen. */
@Serializable
data object AboutRoute

/** Updates check sub-screen. */
@Serializable
data object UpdatesRoute

/** API key management sub-screen. */
@Serializable
data object ApiKeysRoute

/**
 * API key detail sub-screen.
 *
 * @property keyId Identifier of the key to edit, or null for adding a new key.
 */
@Serializable
data class ApiKeyDetailRoute(
    val keyId: String? = null,
)

/** Log viewer sub-screen. */
@Serializable
data object LogViewerRoute

/** First-run onboarding wizard. */
@Serializable
data object OnboardingRoute
