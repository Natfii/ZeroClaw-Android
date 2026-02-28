/*
 * Copyright 2026 ZeroClaw Community
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.data.ProviderKeyValidator
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.data.validation.ValidationResult
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.ProviderAuthType
import com.zeroclaw.android.model.ProviderInfo
import com.zeroclaw.android.ui.component.LoadingIndicator
import com.zeroclaw.android.ui.component.ModelSuggestionField
import com.zeroclaw.android.ui.component.ProviderCredentialForm
import com.zeroclaw.android.ui.component.SectionHeader
import com.zeroclaw.android.ui.component.setup.ProviderSetupFlow

/** Standard vertical spacing between form fields. */
private const val FIELD_SPACING_DP = 16

/** Top padding for the form. */
private const val TOP_SPACING_DP = 8

/** Bottom padding for the form. */
private const val BOTTOM_SPACING_DP = 16

/** Spacer width between save button and loading indicator. */
private const val BUTTON_INDICATOR_SPACING_DP = 12

/**
 * Add or edit API key form screen.
 *
 * For new keys, delegates to [ProviderSetupFlow] which provides the full
 * onboarding-style experience including OAuth login, deep-link buttons,
 * and credential validation. For existing keys, uses a simpler form with
 * [ProviderCredentialForm] and a locked provider dropdown.
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
    val availableModels by apiKeysViewModel.availableModels.collectAsStateWithLifecycle()
    val isLoadingModels by apiKeysViewModel.isLoadingModels.collectAsStateWithLifecycle()
    val oauthInProgress by apiKeysViewModel.oauthInProgress.collectAsStateWithLifecycle()
    val existingKey = remember(keyId, keys) { keys.find { it.id == keyId } }
    val isNewKey = keyId == null

    var providerId by remember(existingKey) {
        mutableStateOf(existingKey?.provider.orEmpty())
    }
    var key by remember(existingKey) {
        mutableStateOf(existingKey?.key.orEmpty())
    }
    var baseUrl by remember(existingKey) {
        mutableStateOf(existingKey?.baseUrl.orEmpty())
    }
    var model by remember(existingKey) {
        mutableStateOf("")
    }

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
        apiKeysViewModel.scheduleFetchModels(providerId, key, baseUrl)
    }

    val providerInfo = ProviderRegistry.findById(providerId)
    val authType = providerInfo?.authType
    val needsKey = authType == ProviderAuthType.API_KEY_ONLY
    val isSaving = saveState is SaveState.Saving
    val isTesting = connectionTestState is ConnectionTestState.Testing

    val providerAlreadyExists =
        remember(providerId, keys, keyId) {
            if (keyId != null || providerId.isBlank()) {
                false
            } else {
                val targetId = providerInfo?.id ?: providerId.lowercase()
                keys.any { existing ->
                    val existingId =
                        ProviderRegistry.findById(existing.provider)?.id
                            ?: existing.provider.lowercase()
                    existingId == targetId
                }
            }
        }

    val prefixValid =
        run {
            val warning =
                if (providerInfo != null) {
                    ProviderKeyValidator.validateKeyFormat(providerInfo, key)
                } else {
                    null
                }
            warning == null || providerInfo?.keyPrefix.isNullOrEmpty()
        }
    val saveEnabled =
        providerId.isNotBlank() &&
            (key.isNotBlank() || !needsKey) &&
            !isSaving &&
            prefixValid &&
            !providerAlreadyExists

    LaunchedEffect(providerId) {
        if (existingKey == null && providerInfo?.defaultBaseUrl?.isNotEmpty() == true) {
            baseUrl = providerInfo.defaultBaseUrl
        }
        if (existingKey == null) {
            model = providerInfo?.suggestedModels?.firstOrNull().orEmpty()
        }
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
            title = if (isNewKey) "Add API Key" else "Edit API Key",
        )

        if (isNewKey) {
            NewKeyForm(
                providerId = providerId,
                apiKey = key,
                baseUrl = baseUrl,
                model = model,
                availableModels = availableModels,
                isLoadingModels = isLoadingModels,
                connectionTestState = connectionTestState,
                oauthInProgress = oauthInProgress,
                onProviderChanged = { providerId = it },
                onApiKeyChanged = { key = it },
                onBaseUrlChanged = { baseUrl = it },
                onModelChanged = { model = it },
                onValidate = {
                    apiKeysViewModel.testConnection(providerId, key, baseUrl)
                },
                onOAuthLogin = { context ->
                    apiKeysViewModel.startOAuthLogin(context)
                },
            )
        } else {
            EditKeyForm(
                providerId = providerId,
                apiKey = key,
                baseUrl = baseUrl,
                model = model,
                availableModels = availableModels,
                isLoadingModels = isLoadingModels,
                isSaving = isSaving,
                needsKey = needsKey,
                providerInfo = providerInfo,
                onProviderChanged = { providerId = it },
                onApiKeyChanged = { key = it },
                onBaseUrlChanged = { baseUrl = it },
                onModelChanged = { model = it },
                onNavigateToQrScanner = onNavigateToQrScanner,
            )
        }

        if (!isNewKey) {
            EditKeyActions(
                existingKey = existingKey,
                providerId = providerId,
                key = key,
                baseUrl = baseUrl,
                saveEnabled = saveEnabled,
                isSaving = isSaving,
                isTesting = isTesting,
                connectionTestState = connectionTestState,
                onSave = { apiKeysViewModel.updateKey(it, model = model) },
                onTest = {
                    apiKeysViewModel.testConnection(providerId, key, baseUrl)
                },
            )
        } else {
            NewKeySaveRow(
                providerId = providerId,
                key = key,
                baseUrl = baseUrl,
                model = model,
                saveEnabled = saveEnabled,
                isSaving = isSaving,
                onSave = { p, k, u, m ->
                    apiKeysViewModel.addKey(
                        provider = p,
                        key = k,
                        baseUrl = u,
                        model = m,
                    )
                },
            )
        }

        if (providerAlreadyExists) {
            Text(
                text = "A key for ${providerInfo?.displayName ?: providerId} already exists",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
            )
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

/**
 * New key form using the shared [ProviderSetupFlow] component.
 *
 * Provides the full onboarding-style experience including OAuth login
 * buttons, deep-link buttons to provider consoles, and credential
 * validation with the [ValidationIndicator][com.zeroclaw.android.ui.component.setup.ValidationIndicator].
 *
 * @param providerId Currently selected provider ID.
 * @param apiKey Current API key input value.
 * @param baseUrl Current base URL input value.
 * @param model Current model name input value.
 * @param availableModels Live model names from the provider API.
 * @param isLoadingModels Whether model data is currently being fetched.
 * @param connectionTestState Current connection test state, mapped to [ValidationResult].
 * @param oauthInProgress Whether an OAuth login flow is in progress.
 * @param onProviderChanged Callback when provider selection changes.
 * @param onApiKeyChanged Callback when API key text changes.
 * @param onBaseUrlChanged Callback when base URL text changes.
 * @param onModelChanged Callback when model text changes.
 * @param onValidate Callback to trigger credential validation.
 * @param onOAuthLogin Callback to initiate the OAuth login flow.
 */
@Composable
private fun NewKeyForm(
    providerId: String,
    apiKey: String,
    baseUrl: String,
    model: String,
    availableModels: List<String>,
    isLoadingModels: Boolean,
    connectionTestState: ConnectionTestState,
    oauthInProgress: Boolean,
    onProviderChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onValidate: () -> Unit,
    onOAuthLogin: (android.content.Context) -> Unit,
) {
    val context = LocalContext.current
    val validationResult = connectionTestState.toValidationResult()

    ProviderSetupFlow(
        selectedProvider = providerId,
        apiKey = apiKey,
        baseUrl = baseUrl,
        selectedModel = model,
        availableModels = availableModels,
        validationResult = validationResult,
        onProviderChanged = onProviderChanged,
        onApiKeyChanged = onApiKeyChanged,
        onBaseUrlChanged = onBaseUrlChanged,
        onModelChanged = onModelChanged,
        onValidate = onValidate,
        isLoadingModels = isLoadingModels,
        isLiveModelData = availableModels.isNotEmpty(),
        isOAuthInProgress = oauthInProgress,
        onOAuthLogin = { onOAuthLogin(context) },
        scrollable = false,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Edit key form using the basic [ProviderCredentialForm] with a locked
 * provider dropdown and QR scanner trailing icon.
 *
 * @param providerId Currently selected provider ID (locked).
 * @param apiKey Current API key input value.
 * @param baseUrl Current base URL input value.
 * @param model Current model name input value.
 * @param availableModels Live model names from the provider API.
 * @param isLoadingModels Whether model data is currently being fetched.
 * @param isSaving Whether a save operation is in progress.
 * @param needsKey Whether the provider requires an API key.
 * @param providerInfo Provider metadata from the registry.
 * @param onProviderChanged Callback when provider selection changes.
 * @param onApiKeyChanged Callback when API key text changes.
 * @param onBaseUrlChanged Callback when base URL text changes.
 * @param onModelChanged Callback when model text changes.
 * @param onNavigateToQrScanner Callback to open the QR code scanner.
 */
@Composable
private fun EditKeyForm(
    providerId: String,
    apiKey: String,
    baseUrl: String,
    model: String,
    availableModels: List<String>,
    isLoadingModels: Boolean,
    isSaving: Boolean,
    needsKey: Boolean,
    providerInfo: ProviderInfo?,
    onProviderChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onNavigateToQrScanner: () -> Unit,
) {
    ProviderCredentialForm(
        selectedProviderId = providerId,
        apiKey = apiKey,
        baseUrl = baseUrl,
        onProviderChanged = onProviderChanged,
        onApiKeyChanged = onApiKeyChanged,
        onBaseUrlChanged = onBaseUrlChanged,
        enabled = !isSaving,
        providerDropdownEnabled = false,
        showApiKeyWhenBlank = true,
        baseUrlKeyboardType = KeyboardType.Uri,
        baseUrlImeAction = if (needsKey) ImeAction.Next else ImeAction.Done,
        apiKeyImeAction = ImeAction.Done,
        apiKeyTrailingIcon = {
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
        modifier = Modifier.fillMaxWidth(),
    )

    if (providerId.isNotBlank()) {
        ModelSuggestionField(
            value = model,
            onValueChanged = onModelChanged,
            suggestions = providerInfo?.suggestedModels.orEmpty(),
            liveSuggestions = availableModels,
            isLoadingLive = isLoadingModels,
            isLiveData = availableModels.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Save button row for new key creation.
 *
 * @param providerId Selected provider ID.
 * @param key API key value.
 * @param baseUrl Base URL value.
 * @param model Selected model name.
 * @param saveEnabled Whether the save button is enabled.
 * @param isSaving Whether a save operation is in progress.
 * @param onSave Callback to save the new key.
 */
@Composable
private fun NewKeySaveRow(
    providerId: String,
    key: String,
    baseUrl: String,
    model: String,
    saveEnabled: Boolean,
    isSaving: Boolean,
    onSave: (String, String, String, String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        FilledTonalButton(
            onClick = { onSave(providerId, key, baseUrl, model) },
            enabled = saveEnabled,
        ) {
            Text(text = "Save")
        }
        if (isSaving) {
            Spacer(modifier = Modifier.width(BUTTON_INDICATOR_SPACING_DP.dp))
            LoadingIndicator()
        }
    }
}

/**
 * Action buttons and test result display for the edit key form.
 *
 * @param existingKey The key being edited, or null if not yet loaded.
 * @param providerId Current provider ID.
 * @param key Current API key value.
 * @param baseUrl Current base URL value.
 * @param saveEnabled Whether the save button is enabled.
 * @param isSaving Whether a save operation is in progress.
 * @param isTesting Whether a connection test is in progress.
 * @param connectionTestState Current connection test state.
 * @param onSave Callback to save the updated key.
 * @param onTest Callback to test the connection.
 */
@Composable
private fun EditKeyActions(
    existingKey: ApiKey?,
    providerId: String,
    key: String,
    baseUrl: String,
    saveEnabled: Boolean,
    isSaving: Boolean,
    isTesting: Boolean,
    connectionTestState: ConnectionTestState,
    onSave: (ApiKey) -> Unit,
    onTest: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        FilledTonalButton(
            onClick = {
                if (existingKey != null) {
                    onSave(
                        existingKey.copy(
                            provider = providerId,
                            key = key,
                            baseUrl = baseUrl,
                        ),
                    )
                }
            },
            enabled = saveEnabled,
        ) {
            Text(text = "Update")
        }
        if (isSaving) {
            Spacer(modifier = Modifier.width(BUTTON_INDICATOR_SPACING_DP.dp))
            LoadingIndicator()
        }
        Spacer(modifier = Modifier.width(BUTTON_INDICATOR_SPACING_DP.dp))
        TextButton(
            onClick = onTest,
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
}

/**
 * Maps a [ConnectionTestState] to a [ValidationResult] for use with [ProviderSetupFlow].
 *
 * @return The corresponding [ValidationResult].
 */
private fun ConnectionTestState.toValidationResult(): ValidationResult =
    when (this) {
        is ConnectionTestState.Idle -> ValidationResult.Idle
        is ConnectionTestState.Testing -> ValidationResult.Loading
        is ConnectionTestState.Success ->
            ValidationResult.Success("Connection verified")
        is ConnectionTestState.Failure ->
            ValidationResult.Failure(message)
    }
