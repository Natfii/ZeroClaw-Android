/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local

import com.zeroclaw.android.data.local.entity.PluginEntity
import com.zeroclaw.android.model.OfficialPlugins
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
     * Returns all seed plugin entities: official built-in plugins plus
     * community sample plugins.
     *
     * @return List of pre-configured [PluginEntity] instances.
     */
    @Suppress("LongMethod")
    fun seedPlugins(): List<PluginEntity> = officialPluginEntities() + communityPluginEntities()

    @Suppress("LongMethod")
    private fun officialPluginEntities(): List<PluginEntity> =
        listOf(
            PluginEntity(
                id = OfficialPlugins.WEB_SEARCH,
                name = "Web Search",
                description = "Search the web via DuckDuckGo or Brave.",
                version = "1.0.0",
                author = "ZeroClaw",
                category = PluginCategory.TOOL.name,
                isInstalled = true,
                isEnabled = false,
                configJson = "{}",
            ),
            PluginEntity(
                id = OfficialPlugins.WEB_FETCH,
                name = "Web Fetch",
                description = "Fetch and read web page content.",
                version = "1.0.0",
                author = "ZeroClaw",
                category = PluginCategory.TOOL.name,
                isInstalled = true,
                isEnabled = false,
                configJson = "{}",
            ),
            PluginEntity(
                id = OfficialPlugins.HTTP_REQUEST,
                name = "HTTP Request",
                description = "Make HTTP calls to external APIs.",
                version = "1.0.0",
                author = "ZeroClaw",
                category = PluginCategory.TOOL.name,
                isInstalled = true,
                isEnabled = false,
                configJson = "{}",
            ),
            PluginEntity(
                id = OfficialPlugins.BROWSER,
                name = "Browser",
                description = "Browse and interact with web pages.",
                version = "1.0.0",
                author = "ZeroClaw",
                category = PluginCategory.TOOL.name,
                isInstalled = true,
                isEnabled = false,
                configJson = "{}",
            ),
            PluginEntity(
                id = OfficialPlugins.COMPOSIO,
                name = "Composio",
                description = "Third-party tool integrations via Composio.",
                version = "1.0.0",
                author = "ZeroClaw",
                category = PluginCategory.TOOL.name,
                isInstalled = true,
                isEnabled = false,
                configJson = "{}",
            ),
            PluginEntity(
                id = OfficialPlugins.VISION,
                name = "Vision",
                description = "Process images for multimodal queries.",
                version = "1.0.0",
                author = "ZeroClaw",
                category = PluginCategory.TOOL.name,
                isInstalled = true,
                isEnabled = true,
                configJson = "{}",
            ),
            PluginEntity(
                id = OfficialPlugins.TRANSCRIPTION,
                name = "Transcription",
                description = "Transcribe audio via Whisper-compatible API.",
                version = "1.0.0",
                author = "ZeroClaw",
                category = PluginCategory.TOOL.name,
                isInstalled = true,
                isEnabled = false,
                configJson = "{}",
            ),
            PluginEntity(
                id = OfficialPlugins.QUERY_CLASSIFICATION,
                name = "Query Classification",
                description = "Classify queries for intelligent model routing.",
                version = "1.0.0",
                author = "ZeroClaw",
                category = PluginCategory.OTHER.name,
                isInstalled = true,
                isEnabled = false,
                configJson = "{}",
            ),
        )

    private fun communityPluginEntities(): List<PluginEntity> =
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
                description = "MQTT message broker channel for IoT communication.",
                version = "1.0.0",
                author = "ZeroClaw",
                category = PluginCategory.CHANNEL.name,
                isInstalled = true,
                isEnabled = false,
                configJson = "{}",
            ),
        )
}
