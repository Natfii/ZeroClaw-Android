/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

import kotlinx.serialization.Serializable

/**
 * Represents a configured AI agent.
 *
 * @property id Unique identifier for the agent.
 * @property name Human-readable display name.
 * @property provider AI provider name (e.g. "OpenAI", "Anthropic").
 * @property modelName The model to use (e.g. "gpt-4o", "claude-sonnet-4-5-20250929").
 * @property isEnabled Whether the agent is active and available.
 * @property systemPrompt Optional system prompt for the agent.
 * @property channels Communication channels configured for this agent.
 * @property temperature Per-agent temperature override; null inherits the global default.
 * @property maxDepth Maximum reasoning depth for the agent.
 */
data class Agent(
    val id: String,
    val name: String,
    val provider: String,
    val modelName: String,
    val isEnabled: Boolean = true,
    val systemPrompt: String = "",
    val channels: List<ChannelConfig> = emptyList(),
    val temperature: Float? = null,
    val maxDepth: Int = DEFAULT_MAX_DEPTH,
) {
    /** Constants for [Agent]. */
    companion object {
        /** Default maximum reasoning depth. */
        const val DEFAULT_MAX_DEPTH = 3
    }
}

/**
 * Configuration for a single communication channel on an agent.
 *
 * @property type Channel type identifier (e.g. "http", "websocket", "mqtt").
 * @property endpoint Endpoint address for the channel.
 */
@Serializable
data class ChannelConfig(
    val type: String,
    val endpoint: String,
)
