/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings.apikeys

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.data.StorageHealth
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.KeyStatus
import com.zeroclaw.android.ui.component.ConfirmDeleteDialog
import com.zeroclaw.android.ui.component.EmptyState
import com.zeroclaw.android.ui.component.ErrorCard
import com.zeroclaw.android.ui.component.MaskedText

/** Minimum passphrase length required for export/import operations. */
private const val MIN_PASSPHRASE_LENGTH = 8

/**
 * API key list screen with masked display and action buttons.
 *
 * Shows all stored API keys with their provider name and masked key
 * value. Provides reveal, edit, and delete actions per key, plus a
 * FAB for adding new keys. Includes storage health banners, corrupt
 * key warnings, export/import with passphrase-protected encryption,
 * credentials file import, and a confirmation dialog for deletion.
 *
 * @param onNavigateToDetail Navigate to the key detail/add screen.
 * @param onRequestBiometric Callback to request biometric authentication
 *   for revealing a key.
 * @param onExportResult Callback invoked with the encrypted export payload
 *   so the caller can share or save it.
 * @param onImportCredentials Callback to open the file picker for
 *   importing a Claude Code `.credentials.json` file.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param apiKeysViewModel The [ApiKeysViewModel] for key management.
 * @param modifier Modifier applied to the root layout.
 */
@Suppress("CognitiveComplexMethod", "LongMethod", "LongParameterList")
@Composable
fun ApiKeysScreen(
    onNavigateToDetail: (String?) -> Unit,
    onRequestBiometric: (keyId: String) -> Unit,
    onExportResult: (String) -> Unit,
    onImportCredentials: () -> Unit,
    edgeMargin: Dp,
    apiKeysViewModel: ApiKeysViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val keys by apiKeysViewModel.keys.collectAsStateWithLifecycle()
    val revealedKeyId by apiKeysViewModel.revealedKeyId.collectAsStateWithLifecycle()
    val snackbarMessage by apiKeysViewModel.snackbarMessage.collectAsStateWithLifecycle()
    val corruptCount by apiKeysViewModel.corruptKeyCount.collectAsStateWithLifecycle()

    var deleteTarget by remember { mutableStateOf<ApiKey?>(null) }
    var rotatingKeyId by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            apiKeysViewModel.dismissSnackbar()
        }
    }

    if (deleteTarget != null) {
        ConfirmDeleteDialog(
            title = "Delete API Key",
            message =
                "Delete the ${deleteTarget?.provider} key? " +
                    "This action cannot be undone.",
            onConfirm = {
                deleteTarget?.let { apiKeysViewModel.deleteKey(it.id) }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    if (rotatingKeyId != null) {
        val rotatingKey = keys.find { it.id == rotatingKeyId }
        if (rotatingKey != null) {
            KeyRotateDialog(
                providerName = rotatingKey.provider,
                onConfirm = { newKey ->
                    apiKeysViewModel.rotateKey(rotatingKey.id, newKey)
                    rotatingKeyId = null
                },
                onDismiss = { rotatingKeyId = null },
            )
        }
    }

    if (showExportDialog) {
        ExportPassphraseDialog(
            onConfirm = { passphrase ->
                showExportDialog = false
                apiKeysViewModel.exportKeys(passphrase) { result ->
                    onExportResult(result)
                }
            },
            onDismiss = { showExportDialog = false },
        )
    }

    if (showImportDialog) {
        ImportPassphraseDialog(
            onConfirm = { payload, passphrase ->
                showImportDialog = false
                apiKeysViewModel.importKeys(payload, passphrase) { count ->
                    apiKeysViewModel.showSnackbar(
                        if (count > 0) {
                            "$count key(s) imported"
                        } else {
                            "Import failed: wrong passphrase or invalid data"
                        },
                    )
                }
            },
            onDismiss = { showImportDialog = false },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToDetail(null) },
                modifier =
                    Modifier.semantics {
                        contentDescription = "Add API key"
                    },
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add",
                )
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        if (keys.isEmpty() &&
            corruptCount == 0 &&
            apiKeysViewModel.storageHealth is StorageHealth.Healthy
        ) {
            EmptyState(
                icon = Icons.Outlined.Key,
                message = "No API keys stored",
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = edgeMargin),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                if (apiKeysViewModel.storageHealth is StorageHealth.Degraded) {
                    item {
                        ErrorCard(
                            message =
                                "Encrypted storage unavailable. Keys are " +
                                    "stored in memory only and will be lost when " +
                                    "the app restarts.",
                            onRetry = null,
                        )
                    }
                }

                if (apiKeysViewModel.storageHealth is StorageHealth.Recovered) {
                    item {
                        ErrorCard(
                            message =
                                "Encrypted storage was corrupted and has " +
                                    "been reset. Previously stored keys were lost.",
                            onRetry = null,
                        )
                    }
                }

                if (corruptCount > 0) {
                    item {
                        ErrorCard(
                            message =
                                "$corruptCount stored key(s) could not " +
                                    "be read and may be corrupted.",
                            onRetry = null,
                        )
                    }
                }

                item {
                    ExportImportRow(
                        hasKeys = keys.isNotEmpty(),
                        onExport = { showExportDialog = true },
                        onImport = { showImportDialog = true },
                        onImportCredentials = onImportCredentials,
                    )
                }

                items(
                    items = keys,
                    key = { it.id },
                    contentType = { "api_key" },
                ) { apiKey ->
                    ApiKeyItem(
                        apiKey = apiKey,
                        isRevealed = revealedKeyId == apiKey.id,
                        onRevealToggle = {
                            if (revealedKeyId == apiKey.id) {
                                apiKeysViewModel.hideRevealedKey()
                            } else {
                                onRequestBiometric(apiKey.id)
                            }
                        },
                        onEdit = { onNavigateToDetail(apiKey.id) },
                        onRotate = { rotatingKeyId = apiKey.id },
                        onDelete = { deleteTarget = apiKey },
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

/**
 * Row containing Export, Import, and Import Credentials action buttons.
 *
 * The export button is disabled when there are no keys to export.
 *
 * @param hasKeys Whether the key store contains at least one key.
 * @param onExport Callback when the user taps Export.
 * @param onImport Callback when the user taps Import.
 * @param onImportCredentials Callback when the user taps Import Credentials.
 */
@Composable
private fun ExportImportRow(
    hasKeys: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onImportCredentials: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = onExport,
            enabled = hasKeys,
            modifier =
                Modifier.semantics {
                    contentDescription = "Export API keys"
                },
        ) {
            Icon(
                imageVector = Icons.Outlined.FileUpload,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text("Export")
        }
        TextButton(
            onClick = onImport,
            modifier =
                Modifier.semantics {
                    contentDescription = "Import API keys"
                },
        ) {
            Icon(
                imageVector = Icons.Outlined.FileDownload,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text("Import")
        }
        TextButton(
            onClick = onImportCredentials,
            modifier =
                Modifier.semantics {
                    contentDescription = "Import credentials file"
                },
        ) {
            Icon(
                imageVector = Icons.Outlined.Upload,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text("Credentials")
        }
    }
}

/**
 * Dialog for entering and confirming an export passphrase.
 *
 * Requires a minimum of [MIN_PASSPHRASE_LENGTH] characters and both
 * fields must match before the Encrypt button is enabled.
 *
 * @param onConfirm Callback with the confirmed passphrase.
 * @param onDismiss Callback when the dialog is dismissed without action.
 */
@Composable
private fun ExportPassphraseDialog(
    onConfirm: (passphrase: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val matchesAndValid =
        passphrase.length >= MIN_PASSPHRASE_LENGTH &&
            passphrase == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Encrypt Export") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text =
                        "Enter a passphrase to encrypt your API keys. " +
                            "You will need this passphrase to import them later.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        ),
                    supportingText = {
                        if (passphrase.isNotEmpty() &&
                            passphrase.length < MIN_PASSPHRASE_LENGTH
                        ) {
                            Text("At least $MIN_PASSPHRASE_LENGTH characters")
                        }
                    },
                    isError =
                        passphrase.isNotEmpty() &&
                            passphrase.length < MIN_PASSPHRASE_LENGTH,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    supportingText = {
                        if (confirm.isNotEmpty() && passphrase != confirm) {
                            Text("Passphrases do not match")
                        }
                    },
                    isError = confirm.isNotEmpty() && passphrase != confirm,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(passphrase) },
                enabled = matchesAndValid,
            ) {
                Text("Encrypt & Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Dialog for entering an encrypted payload and passphrase for import.
 *
 * The payload field accepts the Base64-encoded string from a previous
 * export. The Import button is enabled only when both fields are non-empty.
 *
 * @param onConfirm Callback with the encrypted payload and passphrase.
 * @param onDismiss Callback when the dialog is dismissed without action.
 */
@Composable
private fun ImportPassphraseDialog(
    onConfirm: (payload: String, passphrase: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var payload by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }

    val importEnabled = payload.isNotBlank() && passphrase.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Keys") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text =
                        "Paste the encrypted export data and enter the " +
                            "passphrase that was used during export.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = { Text("Encrypted data") },
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Next,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(payload.trim(), passphrase) },
                enabled = importEnabled,
            ) {
                Text("Decrypt & Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Single API key list item with masked value and action buttons.
 *
 * Shows a warning icon when the key status is [KeyStatus.INVALID].
 *
 * @param apiKey The key to display.
 * @param isRevealed Whether the key value is currently unmasked.
 * @param onRevealToggle Callback to toggle reveal state.
 * @param onEdit Callback to navigate to edit screen.
 * @param onRotate Callback to open the key rotation dialog.
 * @param onDelete Callback to delete this key.
 */
@Composable
private fun ApiKeyItem(
    apiKey: ApiKey,
    isRevealed: Boolean,
    onRevealToggle: () -> Unit,
    onEdit: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = apiKey.provider,
                        style = MaterialTheme.typography.titleSmall,
                        color =
                            if (apiKey.status == KeyStatus.INVALID) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                    if (apiKey.status == KeyStatus.INVALID) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Key may be invalid",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Row {
                    IconButton(
                        onClick = onRevealToggle,
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    if (isRevealed) "Hide key" else "Reveal key"
                            },
                    ) {
                        Icon(
                            imageVector =
                                if (isRevealed) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                            contentDescription = null,
                        )
                    }
                    IconButton(
                        onClick = onRotate,
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    "Rotate ${apiKey.provider} key"
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = null,
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    "Delete ${apiKey.provider} key"
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            MaskedText(
                text = apiKey.key,
                revealed = isRevealed,
            )
            if (apiKey.expiresAt > 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                ExpiryLabel(expiresAt = apiKey.expiresAt)
            }
        }
    }
}

/**
 * Dialog for entering a new key value during rotation.
 *
 * @param providerName Provider label shown in the dialog title.
 * @param onConfirm Callback with the new key value.
 * @param onDismiss Callback when the dialog is dismissed.
 */
@Composable
private fun KeyRotateDialog(
    providerName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rotate $providerName Key") },
        text = {
            OutlinedTextField(
                value = newKey,
                onValueChange = { newKey = it },
                label = { Text("New key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Enter new API key for $providerName"
                        },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newKey) },
                enabled = newKey.isNotBlank(),
            ) {
                Text("Rotate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Displays a human-readable expiry label for an OAuth token.
 *
 * Shows "Expired" in error color when past expiry, or a relative
 * time like "Expires in 3h 15m" in normal text color. Uses both
 * text and color to satisfy WCAG AA (not color-only).
 *
 * @param expiresAt Epoch milliseconds when the token expires.
 */
@Composable
private fun ExpiryLabel(expiresAt: Long) {
    val expiryText =
        remember(expiresAt) {
            val now = System.currentTimeMillis()
            val remainingMs = expiresAt - now
            if (remainingMs <= 0) {
                null
            } else {
                val totalMinutes = remainingMs / MILLIS_PER_MINUTE
                val hours = totalMinutes / MINUTES_PER_HOUR
                val minutes = totalMinutes % MINUTES_PER_HOUR
                when {
                    hours > 0 -> "Expires in ${hours}h ${minutes}m"
                    else -> "Expires in ${minutes}m"
                }
            }
        }

    if (expiryText == null) {
        Text(
            text = "Expired",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    } else {
        Text(
            text = expiryText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
