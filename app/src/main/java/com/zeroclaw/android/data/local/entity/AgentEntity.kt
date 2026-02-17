/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a configured AI agent.
 *
 * Channel configurations are stored as a JSON-encoded TEXT column
 * to avoid the complexity of a join table for a small, bounded list.
 *
 * @property id Unique identifier for the agent.
 * @property name Human-readable display name.
 * @property provider AI provider name (e.g. "OpenAI", "Anthropic").
 * @property modelName The model identifier (e.g. "gpt-4o").
 * @property isEnabled Whether the agent is active and available.
 * @property systemPrompt Optional system prompt for the agent.
 * @property channelsJson JSON-serialized list of [com.zeroclaw.android.model.ChannelConfig].
 */
@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val provider: String,
    @ColumnInfo(name = "model_name")
    val modelName: String,
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean,
    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String,
    @ColumnInfo(name = "channels_json")
    val channelsJson: String,
)
