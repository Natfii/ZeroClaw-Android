/*
 * Copyright 2026 ZeroClaw Contributors
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Shared [Json] instance configured with lenient parsing for entity mappers.
 *
 * Uses `ignoreUnknownKeys` to tolerate schema evolution in JSON columns.
 */
private val mapperJson = Json { ignoreUnknownKeys = true }

/**
 * Converts an [AgentEntity] to its domain [Agent] model.
 *
 * @receiver The Room entity to convert.
 * @return The equivalent domain model with deserialized channel configs.
 */
fun AgentEntity.toModel(): Agent =
    Agent(
        id = id,
        name = name,
        provider = provider,
        modelName = modelName,
        isEnabled = isEnabled,
        systemPrompt = systemPrompt,
        channels = deserializeChannels(channelsJson),
    )

/**
 * Converts an [Agent] domain model to its [AgentEntity] for persistence.
 *
 * @receiver The domain model to convert.
 * @return The equivalent Room entity with serialized channel configs.
 */
fun Agent.toEntity(): AgentEntity =
    AgentEntity(
        id = id,
        name = name,
        provider = provider,
        modelName = modelName,
        isEnabled = isEnabled,
        systemPrompt = systemPrompt,
        channelsJson = mapperJson.encodeToString(channels),
    )

/**
 * Converts a [PluginEntity] to its domain [Plugin] model.
 *
 * @receiver The Room entity to convert.
 * @return The equivalent domain model with deserialized config fields.
 */
fun PluginEntity.toModel(): Plugin =
    Plugin(
        id = id,
        name = name,
        description = description,
        version = version,
        author = author,
        category = deserializeCategory(category),
        isInstalled = isInstalled,
        isEnabled = isEnabled,
        configFields = deserializeConfigMap(configJson),
    )

/**
 * Converts a [Plugin] domain model to its [PluginEntity] for persistence.
 *
 * @receiver The domain model to convert.
 * @return The equivalent Room entity with serialized config fields.
 */
fun Plugin.toEntity(): PluginEntity =
    PluginEntity(
        id = id,
        name = name,
        description = description,
        version = version,
        author = author,
        category = category.name,
        isInstalled = isInstalled,
        isEnabled = isEnabled,
        configJson = mapperJson.encodeToString(configFields),
    )

/**
 * Converts a [LogEntryEntity] to its domain [LogEntry] model.
 *
 * @receiver The Room entity to convert.
 * @return The equivalent domain model.
 */
fun LogEntryEntity.toModel(): LogEntry =
    LogEntry(
        id = id,
        timestamp = timestamp,
        severity = deserializeSeverity(severity),
        tag = tag,
        message = message,
    )

/**
 * Converts a [LogEntry] domain model to its [LogEntryEntity] for persistence.
 *
 * @receiver The domain model to convert.
 * @return The equivalent Room entity.
 */
fun LogEntry.toEntity(): LogEntryEntity =
    LogEntryEntity(
        id = id,
        timestamp = timestamp,
        severity = severity.name,
        tag = tag,
        message = message,
    )

/**
 * Converts an [ActivityEventEntity] to its domain [ActivityEvent] model.
 *
 * @receiver The Room entity to convert.
 * @return The equivalent domain model.
 */
fun ActivityEventEntity.toModel(): ActivityEvent =
    ActivityEvent(
        id = id,
        timestamp = timestamp,
        type = deserializeActivityType(type),
        message = message,
    )

/**
 * Converts an [ActivityEvent] domain model to its [ActivityEventEntity] for persistence.
 *
 * @receiver The domain model to convert.
 * @return The equivalent Room entity.
 */
fun ActivityEvent.toEntity(): ActivityEventEntity =
    ActivityEventEntity(
        id = id,
        timestamp = timestamp,
        type = type.name,
        message = message,
    )

/**
 * Deserializes a JSON string to a list of [ChannelConfig].
 *
 * @param json JSON-encoded channel configuration list.
 * @return Deserialized list, or empty list if JSON is blank or invalid.
 */
private fun deserializeChannels(json: String): List<ChannelConfig> =
    if (json.isBlank()) {
        emptyList()
    } else {
        runCatching { mapperJson.decodeFromString<List<ChannelConfig>>(json) }
            .getOrDefault(emptyList())
    }

/**
 * Deserializes a category name to a [PluginCategory] enum value.
 *
 * @param name Category name string.
 * @return Matching [PluginCategory], or [PluginCategory.OTHER] if unknown.
 */
private fun deserializeCategory(name: String): PluginCategory =
    runCatching { PluginCategory.valueOf(name) }
        .getOrDefault(PluginCategory.OTHER)

/**
 * Deserializes a JSON string to a config map.
 *
 * @param json JSON-encoded configuration map.
 * @return Deserialized map, or empty map if JSON is blank or invalid.
 */
private fun deserializeConfigMap(json: String): Map<String, String> =
    if (json.isBlank()) {
        emptyMap()
    } else {
        runCatching { mapperJson.decodeFromString<Map<String, String>>(json) }
            .getOrDefault(emptyMap())
    }

/**
 * Deserializes a severity name to a [LogSeverity] enum value.
 *
 * @param name Severity name string.
 * @return Matching [LogSeverity], or [LogSeverity.INFO] if unknown.
 */
private fun deserializeSeverity(name: String): LogSeverity =
    runCatching { LogSeverity.valueOf(name) }
        .getOrDefault(LogSeverity.INFO)

/**
 * Deserializes an activity type name to an [ActivityType] enum value.
 *
 * @param name Activity type name string.
 * @return Matching [ActivityType], or [ActivityType.CONFIG_CHANGE] if unknown.
 */
private fun deserializeActivityType(name: String): ActivityType =
    runCatching { ActivityType.valueOf(name) }
        .getOrDefault(ActivityType.CONFIG_CHANGE)
