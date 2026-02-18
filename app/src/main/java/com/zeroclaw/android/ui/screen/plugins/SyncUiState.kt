/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.plugins

/**
 * UI state representing the plugin registry sync operation.
 */
sealed interface SyncUiState {
    /** No sync operation is in progress. */
    data object Idle : SyncUiState

    /** A sync operation is currently running. */
    data object Syncing : SyncUiState

    /**
     * Sync completed successfully.
     *
     * @property count Number of plugins received from the registry.
     */
    data class Success(
        val count: Int,
    ) : SyncUiState

    /**
     * Sync failed with an error.
     *
     * @property message Human-readable error description.
     */
    data class Error(
        val message: String,
    ) : SyncUiState
}
