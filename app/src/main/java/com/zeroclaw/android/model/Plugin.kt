/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * Represents a plugin that extends daemon functionality.
 *
 * @property id Unique identifier for the plugin.
 * @property name Human-readable display name.
 * @property description Short description of the plugin's purpose.
 * @property version Current version string.
 * @property author Plugin author or organization.
 * @property category Functional category of the plugin.
 * @property isInstalled Whether the plugin is installed locally.
 * @property isEnabled Whether the plugin is active (only relevant if installed).
 * @property configFields Map of configuration keys to their current values.
 */
data class Plugin(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    val category: PluginCategory,
    val isInstalled: Boolean = false,
    val isEnabled: Boolean = false,
    val configFields: Map<String, String> = emptyMap(),
)

/**
 * Functional category for plugins.
 */
enum class PluginCategory {
    /** Plugins for connecting to channels (HTTP, MQTT, etc.). */
    CHANNEL,

    /** Plugins for memory and context management. */
    MEMORY,

    /** Plugins that provide tools and capabilities to agents. */
    TOOL,

    /** Plugins for monitoring and observability. */
    OBSERVER,

    /** Plugins for security and access control. */
    SECURITY,

    /** Plugins that don't fit other categories. */
    OTHER,
}
