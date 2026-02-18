// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.service

import com.zeroclaw.android.model.MemoryEntry
import com.zeroclaw.ffi.FfiException
import com.zeroclaw.ffi.FfiMemoryEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridge between the Android UI layer and the Rust memory browsing FFI.
 *
 * Wraps the memory-related UniFFI-generated functions in coroutine-safe
 * suspend functions dispatched to [Dispatchers.IO].
 *
 * @param ioDispatcher Dispatcher for blocking FFI calls. Defaults to [Dispatchers.IO].
 */
class MemoryBridge(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Lists memory entries, optionally filtered by category.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @param category Optional category filter (e.g. "core", "daily", "conversation").
     * @param limit Maximum number of entries to return.
     * @return List of [MemoryEntry] instances.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun listMemories(
        category: String? = null,
        limit: UInt = DEFAULT_LIMIT,
    ): List<MemoryEntry> =
        withContext(ioDispatcher) {
            com.zeroclaw.ffi
                .listMemories(category, limit)
                .map { it.toModel() }
        }

    /**
     * Searches memory entries by keyword query.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @param query Search keyword.
     * @param limit Maximum number of results to return.
     * @return List of [MemoryEntry] instances ranked by relevance.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun recallMemory(
        query: String,
        limit: UInt = DEFAULT_LIMIT,
    ): List<MemoryEntry> =
        withContext(ioDispatcher) {
            com.zeroclaw.ffi
                .recallMemory(query, limit)
                .map { it.toModel() }
        }

    /**
     * Deletes a memory entry by key.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @param key The key of the memory entry to delete.
     * @return `true` if the entry was found and deleted, `false` otherwise.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun forgetMemory(key: String): Boolean =
        withContext(ioDispatcher) {
            com.zeroclaw.ffi.forgetMemory(key)
        }

    /**
     * Returns the total number of memory entries.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @return Total count of memory entries.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun memoryCount(): UInt =
        withContext(ioDispatcher) {
            com.zeroclaw.ffi.memoryCount()
        }

    /** Constants for [MemoryBridge]. */
    companion object {
        /** Default maximum number of memory entries to retrieve. */
        private const val DEFAULT_LIMIT_INT = 100

        /** Default limit as [UInt] for FFI calls. */
        val DEFAULT_LIMIT: UInt = DEFAULT_LIMIT_INT.toUInt()
    }
}

/**
 * Converts an FFI memory entry record to the domain model.
 *
 * @receiver FFI-generated [FfiMemoryEntry] record from the native layer.
 * @return Domain [MemoryEntry] model with identical field values.
 */
private fun FfiMemoryEntry.toModel(): MemoryEntry =
    MemoryEntry(
        id = id,
        key = key,
        content = content,
        category = category,
        timestamp = timestamp,
        score = score,
    )
