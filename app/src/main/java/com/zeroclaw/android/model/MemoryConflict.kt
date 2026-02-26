/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * Result of checking the workspace for stale memory backend artifacts.
 *
 * Produced by [DaemonServiceBridge.detectMemoryConflict][com.zeroclaw.android.service.DaemonServiceBridge]
 * at daemon startup to decide whether cleanup is needed.
 */
sealed interface MemoryConflict {
    /** No conflicting artifacts found. */
    data object None : MemoryConflict

    /**
     * Stale data from a previously configured memory backend was found.
     *
     * @property currentBackend The currently configured backend (e.g. "sqlite").
     * @property staleBackend The backend whose leftover artifacts were found (e.g. "markdown").
     * @property staleFileCount Number of stale files detected.
     * @property staleSizeBytes Total size of stale files in bytes.
     */
    data class StaleData(
        val currentBackend: String,
        val staleBackend: String,
        val staleFileCount: Int,
        val staleSizeBytes: Long,
    ) : MemoryConflict
}

/**
 * Result of a post-startup health probe for the configured memory backend.
 */
sealed interface MemoryHealthResult {
    /** The backend storage is readable and writable. */
    data object Healthy : MemoryHealthResult

    /**
     * The backend storage is not functional.
     *
     * @property reason Human-readable description of the failure.
     */
    data class Unhealthy(
        val reason: String,
    ) : MemoryHealthResult
}
