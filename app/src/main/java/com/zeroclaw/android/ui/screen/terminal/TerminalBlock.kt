/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.terminal

/**
 * A single entry in the terminal REPL scrollback buffer.
 *
 * Each variant represents a different type of terminal output with
 * its own rendering style and accessibility semantics. Blocks are
 * mapped from persisted [com.zeroclaw.android.model.TerminalEntry]
 * rows for display in the scrollback list.
 */
sealed interface TerminalBlock {
    /** Auto-generated Room ID. */
    val id: Long

    /** Epoch milliseconds when the entry was created. */
    val timestamp: Long

    /**
     * User-typed input (command or chat message).
     *
     * @property id Auto-generated Room ID.
     * @property timestamp Epoch milliseconds when the entry was created.
     * @property text The raw text the user entered.
     * @property imageNames Display names of images attached to this input.
     */
    data class Input(
        override val id: Long,
        override val timestamp: Long,
        val text: String,
        val imageNames: List<String> = emptyList(),
    ) : TerminalBlock

    /**
     * Agent or gateway response text.
     *
     * @property id Auto-generated Room ID.
     * @property timestamp Epoch milliseconds when the entry was created.
     * @property content The response body (plain text; markdown is a stretch goal).
     */
    data class Response(
        override val id: Long,
        override val timestamp: Long,
        val content: String,
    ) : TerminalBlock

    /**
     * Structured JSON output from a slash command.
     *
     * @property id Auto-generated Room ID.
     * @property timestamp Epoch milliseconds when the entry was created.
     * @property json The raw JSON string returned by the Rhai engine.
     */
    data class Structured(
        override val id: Long,
        override val timestamp: Long,
        val json: String,
    ) : TerminalBlock

    /**
     * Error message from the daemon or Rhai engine.
     *
     * @property id Auto-generated Room ID.
     * @property timestamp Epoch milliseconds when the entry was created.
     * @property message The error description.
     */
    data class Error(
        override val id: Long,
        override val timestamp: Long,
        val message: String,
    ) : TerminalBlock

    /**
     * System message (welcome banner, clear confirmation).
     *
     * @property id Auto-generated Room ID.
     * @property timestamp Epoch milliseconds when the entry was created.
     * @property text The system message content.
     */
    data class System(
        override val id: Long,
        override val timestamp: Long,
        val text: String,
    ) : TerminalBlock
}
