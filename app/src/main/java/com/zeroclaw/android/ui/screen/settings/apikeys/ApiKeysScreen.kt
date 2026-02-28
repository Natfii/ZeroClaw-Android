/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

@file:Suppress("MatchingDeclarationName")

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
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.data.StorageHealth
import com.zeroclaw.android.data.validation.ProviderValidator
import com.zeroclaw.android.data.validation.ValidationResult
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.KeyStatus
import com.zeroclaw.android.model.isOAuthToken
import com.zeroclaw.android.ui.component.EmptyState
import com.zeroclaw.android.ui.component.ErrorCard
import com.zeroclaw.android.ui.component.MaskedText
import com.zeroclaw.android.ui.component.SetupBottomSheet
import com.zeroclaw.android.ui.component.setup.ValidationIndicator
import kotlinx.coroutines.launch

/** Minimum passphrase length required for export/import operations. */
private const val MIN_PASSPHRASE_LENGTH = 8

/**
 * Aggregated state for the API keys content composable.
 *
 * @property keys List of stored API keys.
 * @property revealedKeyId ID of the currently revealed key, or null.
 * @property corruptCount Number of corrupted keys detected.
 * @property unusedKeyIds Set of key IDs not used by any agent.
 * @property unreachableKeyIds Set of key IDs whose base URL failed a reachability probe.
 * @property storageHealth Current encrypted storage health status.
 */
data class ApiKeysState(
    val keys: List<ApiKey>,
    val revealedKeyId: String?,
    val corruptCount: Int,
    val unusedKeyIds: Set<String>,
    val unreachableKeyIds: Set<String>,
    val storageHealth: StorageHealth,
)

/**
 * API key list screen with masked display and action buttons.
 *
 * Thin stateful wrapper that collects ViewModel flows and delegates
 * rendering to [ApiKeysContent].
 *
 * @param onNavigateToDetail Navigate to the key detail/add screen.
 * @param onRequestBiometric Callback to request PIN authentication
 *   for revealing a key.
 * @param onExportResult Callback invoked with the encrypted export payload
 *   so the caller can share or save it.
 * @param onImportCredentials Callback to open the file picker for
 *   importing a Claude Code `.credentials.json` file.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param apiKeysViewModel The [ApiKeysViewModel] for key management.
 * @param modifier Modifier applied to the root layout.
 */
@Suppress("LongParameterList")
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
    val unusedKeyIds by apiKeysViewModel.unusedKeyIds.collectAsStateWithLifecycle()
    val unreachableKeyIds by apiKeysViewModel.unreachableKeyIds.collectAsStateWithLifecycle()
    val showSheet by apiKeysViewModel.showHotReloadSheet.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        apiKeysViewModel.probeStoredConnections()
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            apiKeysViewModel.dismissSnackbar()
        }
    }

    if (showSheet) {
        SetupBottomSheet(
            progressFlow = apiKeysViewModel.hotReloadProgress,
            onDismiss = apiKeysViewModel::dismissHotReloadSheet,
        )
    }

    ApiKeysContent(
        state =
            ApiKeysState(
                keys = keys,
                revealedKeyId = revealedKeyId,
                corruptCount = corruptCount,
                unusedKeyIds = unusedKeyIds,
                unreachableKeyIds = unreachableKeyIds,
                storageHealth = apiKeysViewModel.storageHealth,
            ),
        snackbarHostState = snackbarHostState,
        edgeMargin = edgeMargin,
        onNavigateToDetail = onNavigateToDetail,
        onRequestBiometric = onRequestBiometric,
        onHideRevealedKey = apiKeysViewModel::hideRevealedKey,
        onDeleteKey = apiKeysViewModel::deleteKey,
        onCountAgentsForKey = apiKeysViewModel::countAgentsForKey,
        onRotateKey = apiKeysViewModel::rotateKey,
        onExportKeys = apiKeysViewModel::exportKeys,
        onImportKeys = apiKeysViewModel::importKeys,
        onShowSnackbar = apiKeysViewModel::showSnackbar,
        onExportResult = onExportResult,
        onImportCredentials = onImportCredentials,
        modifier = modifier,
    )
}

/**
 * Stateless API keys content composable for testing.
 *
 * @param state Aggregated API keys state snapshot.
 * @param snackbarHostState Snackbar host state for messages.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param onNavigateToDetail Navigate to key detail screen.
 * @param onRequestBiometric Request PIN auth for a key.
 * @param onHideRevealedKey Callback to hide the currently revealed key.
 * @param onDeleteKey Callback to delete a key by ID with optional agent cascade.
 * @param onCountAgentsForKey Suspend function returning the number of agents using a key's provider.
 * @param onRotateKey Callback to rotate a key with a new value.
 * @param onExportKeys Callback to export keys with a passphrase.
 * @param onImportKeys Callback to import keys from encrypted payload.
 * @param onShowSnackbar Callback to show a snackbar message.
 * @param onExportResult Callback with the encrypted export payload.
 * @param onImportCredentials Callback to open the credentials file picker.
 * @param modifier Modifier applied to the root layout.
 */
@Suppress("CognitiveComplexMethod", "LongMethod", "LongParameterList")
@Composable
internal fun ApiKeysContent(
    state: ApiKeysState,
    snackbarHostState: SnackbarHostState,
    edgeMargin: Dp,
    onNavigateToDetail: (String?) -> Unit,
    onRequestBiometric: (keyId: String) -> Unit,
    onHideRevealedKey: () -> Unit,
    onDeleteKey: (String, Boolean) -> Unit,
    onCountAgentsForKey: suspend (String) -> Int,
    onRotateKey: (String, String) -> Unit,
    onExportKeys: (String, (String) -> Unit) -> Unit,
    onImportKeys: (String, String, (Int) -> Unit) -> Unit,
    onShowSnackbar: (String) -> Unit,
    onExportResult: (String) -> Unit,
    onImportCredentials: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteTarget by remember { mutableStateOf<ApiKey?>(null) }
    var deleteTargetAgentCount by remember { mutableStateOf(0) }
    var rotatingKeyId by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    val validationResults = remember { mutableStateMapOf<String, ValidationResult>() }
    val validationScope = rememberCoroutineScope()

    LaunchedEffect(deleteTarget) {
        deleteTargetAgentCount = deleteTarget?.let { onCountAgentsForKey(it.id) } ?: 0
    }

    if (deleteTarget != null) {
        ApiKeyDeleteDialog(
            providerName = deleteTarget?.provider ?: "",
            agentCount = deleteTargetAgentCount,
            onConfirm = { alsoDeleteAgents ->
                deleteTarget?.let { onDeleteKey(it.id, alsoDeleteAgents) }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    if (rotatingKeyId != null) {
        val rotatingKey = state.keys.find { it.id == rotatingKeyId }
        if (rotatingKey != null) {
            KeyRotateDialog(
                providerName = rotatingKey.provider,
                onConfirm = { newKey ->
                    onRotateKey(rotatingKey.id, newKey)
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
                onExportKeys(passphrase) { result ->
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
                onImportKeys(payload, passphrase) { count ->
                    onShowSnackbar(
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
        if (state.keys.isEmpty() &&
            state.corruptCount == 0 &&
            state.storageHealth is StorageHealth.Healthy
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

                if (state.storageHealth is StorageHealth.Degraded) {
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

                if (state.storageHealth is StorageHealth.Recovered) {
                    item {
                        ErrorCard(
                            message =
                                "Encrypted storage was corrupted and has " +
                                    "been reset. Previously stored keys were lost.",
                            onRetry = null,
                        )
                    }
                }

                if (state.corruptCount > 0) {
                    item {
                        ErrorCard(
                            message =
                                "${state.corruptCount} stored key(s) could not " +
                                    "be read and may be corrupted.",
                            onRetry = null,
                        )
                    }
                }

                item {
                    ExportImportRow(
                        hasKeys = state.keys.isNotEmpty(),
                        onExport = { showExportDialog = true },
                        onImport = { showImportDialog = true },
                        onImportCredentials = onImportCredentials,
                    )
                }

                items(
                    items = state.keys,
                    key = { it.id },
                    contentType = { "api_key" },
                ) { apiKey ->
                    ApiKeyItem(
                        apiKey = apiKey,
                        isRevealed = state.revealedKeyId == apiKey.id,
                        isUnused = apiKey.id in state.unusedKeyIds,
                        isUnreachable = apiKey.id in state.unreachableKeyIds,
                        validationResult =
                            validationResults[apiKey.id]
                                ?: ValidationResult.Idle,
                        onRevealToggle = {
                            if (state.revealedKeyId == apiKey.id) {
                                onHideRevealedKey()
                            } else {
                                onRequestBiometric(apiKey.id)
                            }
                        },
                        onEdit = { onNavigateToDetail(apiKey.id) },
                        onRotate = { rotatingKeyId = apiKey.id },
                        onDelete = { deleteTarget = apiKey },
                        onValidate = {
                            validationScope.launch {
                                validationResults[apiKey.id] =
                                    ValidationResult.Loading
                                validationResults[apiKey.id] =
                                    ProviderValidator.validate(
                                        providerId = apiKey.provider.lowercase(),
                                        apiKey = apiKey.key,
                                        baseUrl = apiKey.baseUrl,
                                    )
                            }
                        },
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
 * Single API key list item with masked value, action buttons, and inline validation.
 *
 * Shows a warning icon when the key status is [KeyStatus.INVALID],
 * an amber "Unused" label when no configured agent references the
 * key's provider, and an error-colored "Offline" label when the
 * key's base URL failed a reachability probe. A "Validate" button
 * triggers a live probe via [ProviderValidator] and displays the
 * result using [ValidationIndicator] below the key.
 *
 * @param apiKey The key to display.
 * @param isRevealed Whether the key value is currently unmasked.
 * @param isUnused Whether no agent currently uses this key's provider.
 * @param isUnreachable Whether the key's base URL failed a reachability probe.
 * @param validationResult Current inline validation state for this key.
 * @param onRevealToggle Callback to toggle reveal state.
 * @param onEdit Callback to navigate to edit screen.
 * @param onRotate Callback to open the key rotation dialog.
 * @param onDelete Callback to delete this key.
 * @param onValidate Callback to trigger credential validation.
 */
@Suppress("LongParameterList")
@Composable
private fun ApiKeyItem(
    apiKey: ApiKey,
    isRevealed: Boolean,
    isUnused: Boolean,
    isUnreachable: Boolean,
    validationResult: ValidationResult,
    onRevealToggle: () -> Unit,
    onEdit: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
    onValidate: () -> Unit,
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
                    if (isUnreachable) {
                        Text(
                            text = "Offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (isUnused) {
                        Text(
                            text = "Unused",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
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
            if (apiKey.isOAuthToken) {
                Text(
                    text = "ChatGPT Login",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MaskedText(
                    text = apiKey.key,
                    revealed = isRevealed,
                )
            }
            if (apiKey.expiresAt > 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                ExpiryLabel(expiresAt = apiKey.expiresAt)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    onClick = onValidate,
                    enabled = validationResult !is ValidationResult.Loading,
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Validate ${apiKey.provider} key"
                        },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(
                        text =
                            if (validationResult is ValidationResult.Loading) {
                                "Validating\u2026"
                            } else {
                                "Validate"
                            },
                    )
                }
            }
            if (validationResult !is ValidationResult.Idle) {
                ValidationIndicator(
                    result = validationResult,
                    modifier = Modifier.fillMaxWidth(),
                )
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

/**
 * Confirmation dialog for API key deletion with optional agent cascade.
 *
 * When [agentCount] is greater than zero, displays a checkbox allowing
 * the user to also delete agents that reference the same provider. This
 * prevents orphaned agent entries that reference a provider with no
 * stored credentials.
 *
 * @param providerName Human-readable provider name shown in the dialog.
 * @param agentCount Number of agents using this provider.
 * @param onConfirm Callback with whether to also cascade-delete agents.
 * @param onDismiss Callback when the user cancels the dialog.
 */
@Composable
private fun ApiKeyDeleteDialog(
    providerName: String,
    agentCount: Int,
    onConfirm: (alsoDeleteAgents: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var alsoDeleteAgents by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        title = { Text("Delete API Key") },
        text = {
            Column {
                Text(
                    text =
                        "Delete the $providerName key? " +
                            "This action cannot be undone.",
                )
                if (agentCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { alsoDeleteAgents = !alsoDeleteAgents },
                    ) {
                        Checkbox(
                            checked = alsoDeleteAgents,
                            onCheckedChange = { alsoDeleteAgents = it },
                        )
                        Text(
                            text =
                                "Also delete $agentCount agent(s) " +
                                    "using this provider",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(alsoDeleteAgents) }) {
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

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
