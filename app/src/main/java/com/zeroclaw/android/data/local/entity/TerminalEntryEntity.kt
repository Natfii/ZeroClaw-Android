/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single entry in the terminal REPL history.
 *
 * @property id Auto-generated primary key.
 * @property content The text content of the entry.
 * @property entryType The kind of entry: "input", "response", "error", or "system".
 * @property timestamp Epoch milliseconds when the entry was created.
 * @property imageUris JSON array of image content URIs attached to this entry.
 */
@Entity(tableName = "terminal_entries")
data class TerminalEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    @ColumnInfo(name = "entry_type")
    val entryType: String,
    val timestamp: Long,
    @ColumnInfo(name = "image_uris", defaultValue = "[]")
    val imageUris: String = "[]",
)
