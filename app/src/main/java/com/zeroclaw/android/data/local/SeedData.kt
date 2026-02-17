/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local

import com.zeroclaw.android.data.local.entity.PluginEntity
import com.zeroclaw.android.model.PluginCategory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Provides seed data for first-install database population.
 *
 * These functions return the same sample data previously defined in the
 * in-memory repositories, ensuring a seamless migration experience.
 */
object SeedData {
    /**
     * Returns sample plugin entities for first-install seeding.
     *
     * @return List of pre-configured [PluginEntity] instances.
     */
    @Suppress("LongMethod")
    fun seedPlugins(): List<PluginEntity> =
        listOf(
            PluginEntity(
                id = "plugin-http-channel",
                name = "HTTP Channel",
                description = "REST API channel for agent communication.",
                version = "1.0.0",
                author = "ZeroClaw",
                category = PluginCategory.CHANNEL.name,
                isInstalled = true,
                isEnabled = true,
                configJson = Json.encodeToString(mapOf("port" to "8080", "host" to "0.0.0.0")),
            ),
            PluginEntity(
                id = "plugin-mqtt-channel",
                name = "MQTT Channel",
                description = "MQTT message broker channel for IoT integration.",
                version = "0.9.0",
                author = "ZeroClaw",
                category = PluginCategory.CHANNEL.name,
                isInstalled = true,
                isEnabled = false,
                configJson = Json.encodeToString(emptyMap<String, String>()),
            ),
            PluginEntity(
                id = "plugin-sqlite-memory",
                name = "SQLite Memory",
                description = "Persistent agent memory backed by SQLite.",
                version = "1.2.0",
                author = "ZeroClaw",
                category = PluginCategory.MEMORY.name,
                isInstalled = false,
                isEnabled = false,
                configJson = Json.encodeToString(emptyMap<String, String>()),
            ),
            PluginEntity(
                id = "plugin-web-search",
                name = "Web Search Tool",
                description = "Adds web search capability to agents.",
                version = "0.5.0",
                author = "Community",
                category = PluginCategory.TOOL.name,
                isInstalled = false,
                isEnabled = false,
                configJson = Json.encodeToString(emptyMap<String, String>()),
            ),
            PluginEntity(
                id = "plugin-prometheus",
                name = "Prometheus Observer",
                description = "Exports daemon metrics to Prometheus.",
                version = "1.0.0",
                author = "Community",
                category = PluginCategory.OBSERVER.name,
                isInstalled = false,
                isEnabled = false,
                configJson = Json.encodeToString(emptyMap<String, String>()),
            ),
        )
}
