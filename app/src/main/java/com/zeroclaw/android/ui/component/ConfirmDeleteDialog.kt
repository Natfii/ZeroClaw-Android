/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/**
 * Reusable confirmation dialog for destructive actions.
 *
 * Displays a Material 3 [AlertDialog] with configurable title and message,
 * plus Cancel/Delete action buttons with 48dp minimum touch targets.
 * Uses [LiveRegionMode.Polite] so screen readers announce the dialog.
 *
 * @param title Dialog title (e.g. "Delete API Key").
 * @param message Descriptive body text (e.g. "This action cannot be undone.").
 * @param onConfirm Callback invoked when the user confirms deletion.
 * @param onDismiss Callback invoked when the user cancels or dismisses.
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}
