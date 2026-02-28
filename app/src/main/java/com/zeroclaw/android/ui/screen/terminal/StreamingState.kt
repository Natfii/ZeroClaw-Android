/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.terminal

/**
 * Phases of a streaming response lifecycle.
 *
 * Transitions follow:
 * [IDLE] -> [THINKING] -> [TOOL_EXECUTING] -> [RESPONDING] -> [COMPACTING] -> [COMPLETE],
 * or any active phase may transition to [CANCELLED] or [ERROR].
 */
enum class StreamingPhase {
    /** No streaming operation in progress. */
    IDLE,

    /** Receiving thinking/reasoning tokens from the model. */
    THINKING,

    /** The agent is executing one or more tools. */
    TOOL_EXECUTING,

    /** Receiving response content tokens from the model. */
    RESPONDING,

    /** Conversation history is being compacted to fit context window. */
    COMPACTING,

    /** Streaming completed successfully. */
    COMPLETE,

    /** Streaming was cancelled by the user. */
    CANCELLED,

    /** An error occurred during streaming. */
    ERROR,
}

/**
 * Represents an in-flight tool execution displayed during [StreamingPhase.TOOL_EXECUTING].
 *
 * @property name Tool identifier (e.g. "memory_recall").
 * @property hint Short argument summary shown alongside the tool name.
 */
data class ToolProgress(
    val name: String,
    val hint: String = "",
)

/**
 * Completed tool execution result for display in the tool activity footer.
 *
 * @property name Tool identifier that was executed.
 * @property success Whether the tool execution succeeded.
 * @property durationSecs Wall-clock execution time in seconds.
 * @property output Full tool output text for expandable display.
 */
data class ToolResultEntry(
    val name: String,
    val success: Boolean,
    val durationSecs: Long,
    val output: String = "",
)

/**
 * Immutable snapshot of the current streaming state.
 *
 * @property phase Current streaming lifecycle phase.
 * @property thinkingText Accumulated thinking/reasoning tokens.
 * @property responseText Accumulated response content tokens.
 * @property errorMessage Error description when [phase] is [StreamingPhase.ERROR].
 * @property activeTools Tools currently executing during [StreamingPhase.TOOL_EXECUTING].
 * @property toolResults Completed tool execution results for the current turn.
 * @property progressMessage Miscellaneous progress status (e.g. "Searching memory...").
 */
data class StreamingState(
    val phase: StreamingPhase = StreamingPhase.IDLE,
    val thinkingText: String = "",
    val responseText: String = "",
    val errorMessage: String? = null,
    val activeTools: List<ToolProgress> = emptyList(),
    val toolResults: List<ToolResultEntry> = emptyList(),
    val progressMessage: String? = null,
) {
    /** Constants and factory methods for [StreamingState]. */
    companion object {
        /** Returns a fresh idle state with no accumulated text. */
        fun idle(): StreamingState = StreamingState()
    }
}
