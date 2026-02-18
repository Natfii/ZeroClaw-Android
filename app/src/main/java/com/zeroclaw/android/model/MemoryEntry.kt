// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.model

/**
 * A memory entry from the daemon's memory backend.
 *
 * Maps to the Rust `FfiMemoryEntry` record transferred across the FFI boundary.
 *
 * @property id Unique identifier of this memory entry.
 * @property key Key under which the memory is stored.
 * @property content Content text of the memory entry.
 * @property category Category string: "core", "daily", "conversation", or a custom name.
 * @property timestamp RFC 3339 timestamp of when the entry was created.
 * @property score Relevance score from a recall query, if applicable.
 */
data class MemoryEntry(
    val id: String,
    val key: String,
    val content: String,
    val category: String,
    val timestamp: String,
    val score: Double?,
)
