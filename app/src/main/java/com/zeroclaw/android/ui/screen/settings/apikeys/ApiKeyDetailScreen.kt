/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings.apikeys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.model.ProviderAuthType
import com.zeroclaw.android.ui.component.LoadingIndicator
import com.zeroclaw.android.ui.component.ProviderDropdown
import com.zeroclaw.android.ui.component.SectionHeader

/** Standard vertical spacing between form fields. */
private const val FIELD_SPACING_DP = 16

/** Top padding for the form. */
private const val TOP_SPACING_DP = 8

/** Bottom padding for the form. */
private const val BOTTOM_SPACING_DP = 16

/**
 * Add or edit API key form screen.
 *
 * When adding a new key, the provider field is a [ProviderDropdown].
 * When editing an existing key, the provider is shown as a read-only dropdown.
 * Dynamically shows a base URL field for providers that require one.
 *
 * Navigation only occurs after the save operation completes successfully,
 * preventing data loss from optimistic navigation.
 *
 * @param keyId Identifier of the key to edit, or null for a new key.
 * @param onSaved Callback invoked after successfully saving.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param apiKeysViewModel The [ApiKeysViewModel] for key management.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun ApiKeyDetailScreen(
    keyId: String?,
    onSaved: () -> Unit,
    edgeMargin: Dp,
    apiKeysViewModel: ApiKeysViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val keys by apiKeysViewModel.keys.collectAsStateWithLifecycle()
    val saveState by apiKeysViewModel.saveState.collectAsStateWithLifecycle()
    val existingKey = remember(keyId, keys) { keys.find { it.id == keyId } }

    var providerId by remember(existingKey) {
        mutableStateOf(existingKey?.provider ?: "")
    }
    var key by remember(existingKey) {
        mutableStateOf(existingKey?.key ?: "")
    }

    LaunchedEffect(saveState) {
        if (saveState is SaveState.Saved) {
            apiKeysViewModel.resetSaveState()
            onSaved()
        }
    }

    val providerInfo = ProviderRegistry.findById(providerId)
    val authType = providerInfo?.authType
    val needsKey = authType != ProviderAuthType.URL_ONLY && authType != ProviderAuthType.NONE
    val isSaving = saveState is SaveState.Saving
    val saveEnabled = providerId.isNotBlank() && (key.isNotBlank() || !needsKey) && !isSaving

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

        if (needsKey || providerId.isBlank()) {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("API Key") },
                singleLine = true,
                enabled = !isSaving,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
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
                            ),
                        )
                    } else {
                        apiKeysViewModel.addKey(provider = providerId, key = key)
                    }
                },
                enabled = saveEnabled,
            ) {
                Text(text = if (keyId != null) "Update" else "Save")
            }
            if (isSaving) {
                Spacer(modifier = Modifier.width(12.dp))
                LoadingIndicator()
            }
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
