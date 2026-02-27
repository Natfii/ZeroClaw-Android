/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.model.TerminalEntry
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for terminal REPL history entries.
 *
 * Implementations persist entries in Room so they survive navigation
 * and app restarts.
 */
interface TerminalEntryRepository {
    /** Observable stream of terminal entries in chronological order. */
    val entries: Flow<List<TerminalEntry>>

    /**
     * Inserts a new entry and returns it with the auto-generated ID.
     *
     * @param content The entry text.
     * @param entryType The kind of entry: "input", "response", "error", or "system".
     * @param imageUris Content URIs of images attached to this entry.
     * @return The persisted [TerminalEntry] with its generated ID.
     */
    suspend fun append(
        content: String,
        entryType: String,
        imageUris: List<String> = emptyList(),
    ): TerminalEntry

    /** Clears all terminal entries (fire-and-forget). */
    fun clear()
}
