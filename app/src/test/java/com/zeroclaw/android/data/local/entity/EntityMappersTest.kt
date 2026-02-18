/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local.entity

import com.zeroclaw.android.model.ActivityEvent
import com.zeroclaw.android.model.ActivityType
import com.zeroclaw.android.model.Agent
import com.zeroclaw.android.model.ChannelConfig
import com.zeroclaw.android.model.LogEntry
import com.zeroclaw.android.model.LogSeverity
import com.zeroclaw.android.model.Plugin
import com.zeroclaw.android.model.PluginCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EntityMappersTest {
    @Test
    fun `agent round-trip preserves all fields`() {
        val agent =
            Agent(
                id = "a1",
                name = "Test Agent",
                provider = "Anthropic",
                modelName = "claude-sonnet-4-5-20250929",
                isEnabled = true,
                systemPrompt = "Hello",
                channels =
                    listOf(
                        ChannelConfig(type = "http", endpoint = "http://localhost:8080"),
                        ChannelConfig(type = "mqtt", endpoint = "mqtt://broker:1883"),
                    ),
            )

        val roundTripped = agent.toEntity().toModel()
        assertEquals(agent, roundTripped)
    }

    @Test
    fun `agent with empty channels round-trips correctly`() {
        val agent =
            Agent(
                id = "a2",
                name = "No Channels",
                provider = "OpenAI",
                modelName = "gpt-4o",
                channels = emptyList(),
            )

        val roundTripped = agent.toEntity().toModel()
        assertEquals(agent, roundTripped)
    }

    @Test
    fun `agent entity with blank channelsJson deserializes to empty list`() {
        val entity =
            AgentEntity(
                id = "a3",
                name = "Blank",
                provider = "Test",
                modelName = "model",
                isEnabled = true,
                systemPrompt = "",
                channelsJson = "",
            )

        assertTrue(entity.toModel().channels.isEmpty())
    }

    @Test
    fun `agent entity with invalid channelsJson deserializes to empty list`() {
        val entity =
            AgentEntity(
                id = "a4",
                name = "Invalid",
                provider = "Test",
                modelName = "model",
                isEnabled = true,
                systemPrompt = "",
                channelsJson = "not-json",
            )

        assertTrue(entity.toModel().channels.isEmpty())
    }

    @Test
    fun `agent with temperature round-trips correctly`() {
        val agent =
            Agent(
                id = "a5",
                name = "Warm Agent",
                provider = "OpenAI",
                modelName = "gpt-4o",
                temperature = 1.5f,
            )

        val roundTripped = agent.toEntity().toModel()
        assertEquals(1.5f, roundTripped.temperature)
    }

    @Test
    fun `agent with null temperature round-trips correctly`() {
        val agent =
            Agent(
                id = "a6",
                name = "Default Agent",
                provider = "OpenAI",
                modelName = "gpt-4o",
                temperature = null,
            )

        val roundTripped = agent.toEntity().toModel()
        assertEquals(null, roundTripped.temperature)
    }

    @Test
    fun `agent with custom maxDepth round-trips correctly`() {
        val agent =
            Agent(
                id = "a7",
                name = "Deep Agent",
                provider = "OpenAI",
                modelName = "gpt-4o",
                maxDepth = 7,
            )

        val roundTripped = agent.toEntity().toModel()
        assertEquals(7, roundTripped.maxDepth)
    }

    @Test
    fun `plugin round-trip preserves all fields`() {
        val plugin =
            Plugin(
                id = "p1",
                name = "HTTP Channel",
                description = "REST API",
                version = "1.0.0",
                author = "ZeroClaw",
                category = PluginCategory.CHANNEL,
                isInstalled = true,
                isEnabled = true,
                configFields = mapOf("port" to "8080", "host" to "0.0.0.0"),
            )

        val roundTripped = plugin.toEntity().toModel()
        assertEquals(plugin, roundTripped)
    }

    @Test
    fun `plugin with empty config round-trips correctly`() {
        val plugin =
            Plugin(
                id = "p2",
                name = "Simple",
                description = "Desc",
                version = "0.1.0",
                author = "Test",
                category = PluginCategory.TOOL,
                configFields = emptyMap(),
            )

        val roundTripped = plugin.toEntity().toModel()
        assertEquals(plugin, roundTripped)
    }

    @Test
    fun `plugin entity with unknown category defaults to OTHER`() {
        val entity =
            PluginEntity(
                id = "p3",
                name = "Unknown",
                description = "Desc",
                version = "1.0.0",
                author = "Test",
                category = "NONEXISTENT",
                isInstalled = false,
                isEnabled = false,
                configJson = "{}",
            )

        assertEquals(PluginCategory.OTHER, entity.toModel().category)
    }

    @Test
    fun `log entry round-trip preserves all fields`() {
        val entry =
            LogEntry(
                id = 42,
                timestamp = 1700000000000,
                severity = LogSeverity.WARN,
                tag = "FFI",
                message = "Something happened",
            )

        val roundTripped = entry.toEntity().toModel()
        assertEquals(entry, roundTripped)
    }

    @Test
    fun `log entry entity with unknown severity defaults to INFO`() {
        val entity =
            LogEntryEntity(
                id = 1,
                timestamp = 1700000000000,
                severity = "UNKNOWN_LEVEL",
                tag = "Test",
                message = "msg",
            )

        assertEquals(LogSeverity.INFO, entity.toModel().severity)
    }

    @Test
    fun `activity event round-trip preserves all fields`() {
        val event =
            ActivityEvent(
                id = 7,
                timestamp = 1700000000000,
                type = ActivityType.DAEMON_STARTED,
                message = "Daemon started",
            )

        val roundTripped = event.toEntity().toModel()
        assertEquals(event, roundTripped)
    }

    @Test
    fun `activity event entity with unknown type defaults to CONFIG_CHANGE`() {
        val entity =
            ActivityEventEntity(
                id = 1,
                timestamp = 1700000000000,
                type = "UNKNOWN_TYPE",
                message = "msg",
            )

        assertEquals(ActivityType.CONFIG_CHANGE, entity.toModel().type)
    }
}
