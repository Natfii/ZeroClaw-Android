/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings.apikeys

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.zeroclaw.android.data.ProviderKeyValidator
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.model.ProviderAuthType
import com.zeroclaw.android.ui.component.LoadingIndicator
import com.zeroclaw.android.ui.component.NetworkScanSheet
import com.zeroclaw.android.ui.component.ProviderDropdown
import com.zeroclaw.android.ui.component.SectionHeader

/** Standard vertical spacing between form fields. */
private const val FIELD_SPACING_DP = 16

/** Top padding for the form. */
private const val TOP_SPACING_DP = 8

/** Bottom padding for the form. */
private const val BOTTOM_SPACING_DP = 16

/** Size of the scan button icon. */
private const val SCAN_ICON_SIZE_DP = 18

/** Spacing between the scan icon and label. */
private const val SCAN_ICON_SPACING_DP = 4

/** Spacer width between save button and loading indicator. */
private const val BUTTON_INDICATOR_SPACING_DP = 12

/**
 * Add or edit API key form screen.
 *
 * When adding a new key, the provider field is a [ProviderDropdown].
 * When editing an existing key, the provider is shown as a read-only dropdown.
 * Dynamically shows a base URL field for providers that require one, with
 * a "Scan Network" option for local providers to discover servers on the LAN.
 *
 * Navigation only occurs after the save operation completes successfully,
 * preventing data loss from optimistic navigation.
 *
 * @param keyId Identifier of the key to edit, or null for a new key.
 * @param onSaved Callback invoked after successfully saving.
 * @param onNavigateToQrScanner Callback to open the QR code scanner for key input.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param apiKeysViewModel The [ApiKeysViewModel] for key management.
 * @param scannedApiKey API key value scanned via QR code, empty when none.
 * @param onScannedApiKeyConsumed Callback to clear the scanned value after applying it.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun ApiKeyDetailScreen(
    keyId: String?,
    onSaved: () -> Unit,
    onNavigateToQrScanner: () -> Unit,
    edgeMargin: Dp,
    apiKeysViewModel: ApiKeysViewModel = viewModel(),
    scannedApiKey: String = "",
    onScannedApiKeyConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val keys by apiKeysViewModel.keys.collectAsStateWithLifecycle()
    val saveState by apiKeysViewModel.saveState.collectAsStateWithLifecycle()
    val connectionTestState by apiKeysViewModel.connectionTestState.collectAsStateWithLifecycle()
    val existingKey = remember(keyId, keys) { keys.find { it.id == keyId } }

    var providerId by remember(existingKey) {
        mutableStateOf(existingKey?.provider.orEmpty())
    }
    var key by remember(existingKey) {
        mutableStateOf(existingKey?.key.orEmpty())
    }
    var baseUrl by remember(existingKey) {
        mutableStateOf(existingKey?.baseUrl.orEmpty())
    }
    var showScanSheet by remember { mutableStateOf(false) }

    LaunchedEffect(saveState) {
        if (saveState is SaveState.Saved) {
            apiKeysViewModel.resetSaveState()
            onSaved()
        }
    }

    LaunchedEffect(scannedApiKey) {
        if (scannedApiKey.isNotBlank()) {
            key = scannedApiKey
            onScannedApiKeyConsumed()
        }
    }

    LaunchedEffect(providerId, key, baseUrl) {
        apiKeysViewModel.resetConnectionTestState()
    }

    val providerInfo = ProviderRegistry.findById(providerId)
    val authType = providerInfo?.authType
    val needsKey = authType == ProviderAuthType.API_KEY_ONLY
    val showKeyField = authType != ProviderAuthType.URL_ONLY && authType != ProviderAuthType.NONE
    val needsUrl =
        authType == ProviderAuthType.URL_ONLY || authType == ProviderAuthType.URL_AND_OPTIONAL_KEY
    val isSaving = saveState is SaveState.Saving
    val isTesting = connectionTestState is ConnectionTestState.Testing

    val context = LocalContext.current
    val prefixWarning by remember(providerId, key) {
        derivedStateOf {
            if (providerInfo != null) {
                ProviderKeyValidator.validateKeyFormat(providerInfo, key)
            } else {
                null
            }
        }
    }
    var prefixOverridden by remember { mutableStateOf(false) }

    LaunchedEffect(providerId, key) {
        prefixOverridden = false
    }

    val prefixValid = prefixWarning == null || prefixOverridden ||
        providerInfo?.keyPrefix.isNullOrEmpty()
    val saveEnabled = providerId.isNotBlank() && (key.isNotBlank() || !needsKey) && !isSaving && prefixValid

    LaunchedEffect(providerId) {
        if (existingKey == null && providerInfo?.defaultBaseUrl?.isNotEmpty() == true) {
            baseUrl = providerInfo.defaultBaseUrl
        }
    }

    if (showScanSheet) {
        NetworkScanSheet(
            onDismiss = { showScanSheet = false },
            onServerSelected = { server ->
                baseUrl = server.baseUrl
                showScanSheet = false
            },
        )
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(FIELD_SPACING_DP.dp),
    ) {
        Spacer(modifier = Modifier.height(TOP_SPACING_DP.dp))

        SectionHeader(
            title = if (keyId != null) "Edit API Key" else "Add API Key",
        )

        ProviderDropdown(
            selectedProviderId = providerId,
            onProviderSelected = { providerId = it.id },
            enabled = keyId == null && !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )

        if (providerInfo?.helpText?.isNotEmpty() == true) {
            Text(
                text = providerInfo.helpText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (providerInfo?.keyCreationUrl?.isNotEmpty() == true) {
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(providerInfo.keyCreationUrl)),
                    )
                },
                enabled = !isSaving,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(SCAN_ICON_SIZE_DP.dp),
                )
                Spacer(modifier = Modifier.width(SCAN_ICON_SPACING_DP.dp))
                Text("Get API Key")
            }
        }

        if (needsUrl) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                singleLine = true,
                enabled = !isSaving,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = if (needsKey) ImeAction.Next else ImeAction.Done,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )

            TextButton(
                onClick = { showScanSheet = true },
                enabled = !isSaving,
            ) {
                Icon(
                    imageVector = Icons.Default.WifiFind,
                    contentDescription = null,
                    modifier = Modifier.size(SCAN_ICON_SIZE_DP.dp),
                )
                Spacer(modifier = Modifier.width(SCAN_ICON_SPACING_DP.dp))
                Text("Scan Network for Servers")
            }
        }

        if (showKeyField || providerId.isBlank()) {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(if (needsKey) "API Key" else "API Key (optional)") },
                singleLine = true,
                enabled = !isSaving,
                visualTransformation = PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = onNavigateToQrScanner,
                        enabled = !isSaving,
                        modifier =
                            Modifier.semantics {
                                contentDescription = "Scan QR code to fill API key"
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = null,
                        )
                    }
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                isError = prefixWarning != null && !prefixOverridden,
                supportingText =
                    if (prefixWarning != null && !prefixOverridden) {
                        { Text(prefixWarning!!) }
                    } else {
                        null
                    },
                modifier = Modifier.fillMaxWidth(),
            )
            if (prefixWarning != null && !prefixOverridden) {
                TextButton(onClick = { prefixOverridden = true }) {
                    Text("Use this key anyway")
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalButton(
                onClick = {
                    if (existingKey != null) {
                        apiKeysViewModel.updateKey(
                            existingKey.copy(
                                provider = providerId,
                                key = key,
                                baseUrl = baseUrl,
                            ),
                        )
                    } else {
                        apiKeysViewModel.addKey(
                            provider = providerId,
                            key = key,
                            baseUrl = baseUrl,
                        )
                    }
                },
                enabled = saveEnabled,
            ) {
                Text(text = if (keyId != null) "Update" else "Save")
            }
            if (isSaving) {
                Spacer(modifier = Modifier.width(BUTTON_INDICATOR_SPACING_DP.dp))
                LoadingIndicator()
            }
            Spacer(modifier = Modifier.width(BUTTON_INDICATOR_SPACING_DP.dp))
            TextButton(
                onClick = {
                    apiKeysViewModel.testConnection(
                        providerId = providerId,
                        key = key,
                        baseUrl = baseUrl,
                    )
                },
                enabled = saveEnabled && !isTesting,
                modifier =
                    Modifier.semantics {
                        contentDescription = "Test connection to provider"
                    },
            ) {
                if (isTesting) {
                    LoadingIndicator()
                } else {
                    Text("Test")
                }
            }
        }

        when (val testState = connectionTestState) {
            is ConnectionTestState.Success ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier =
                        Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Connection verified",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            is ConnectionTestState.Failure ->
                Text(
                    text = testState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            else -> Unit
        }

        if (saveState is SaveState.Error) {
            Text(
                text = (saveState as SaveState.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(BOTTOM_SPACING_DP.dp))
    }
}
