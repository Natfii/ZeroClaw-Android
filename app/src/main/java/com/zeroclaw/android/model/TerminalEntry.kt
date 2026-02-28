/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * A single entry in the terminal REPL history.
 *
 * Entries are persisted in Room and survive navigation and app restarts.
 *
 * @property id Auto-generated primary key from Room (0 for unsaved entries).
 * @property content The text content of the entry.
 * @property entryType The kind of entry: "input", "response", "error", or "system".
 * @property timestamp Epoch milliseconds when the entry was created.
 * @property imageUris Content URIs of images attached to this entry (empty for text-only).
 */
data class TerminalEntry(
    val id: Long = 0,
    val content: String,
    val entryType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUris: List<String> = emptyList(),
)
