/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.data.remote.ModelFetcher
import com.zeroclaw.android.model.ModelListFormat
import com.zeroclaw.android.model.ProviderAuthType
import com.zeroclaw.android.ui.component.ModelSuggestionField
import com.zeroclaw.android.ui.component.NetworkScanSheet
import com.zeroclaw.android.ui.component.ProviderDropdown

/** Standard spacing between form fields. */
private const val FIELD_SPACING_DP = 16

/** Spacing after the section description. */
private const val DESCRIPTION_SPACING_DP = 24

/** Spacing before the skip hint. */
private const val HINT_SPACING_DP = 8

/** Size of the scan button icon. */
private const val SCAN_ICON_SIZE_DP = 18

/** Spacing between the scan icon and label. */
private const val SCAN_ICON_SPACING_DP = 4

/**
 * Onboarding step for selecting a provider and entering credentials.
 *
 * Replaces free-text fields with a structured [ProviderDropdown] and
 * dynamic credential fields based on the provider's [ProviderAuthType].
 * Includes a [ModelSuggestionField] with live model suggestions when an
 * API key is available, or static suggestions from the registry otherwise.
 *
 * For local providers (Ollama, LM Studio, vLLM, LocalAI), a "Scan Network"
 * button allows automatic discovery of running servers on the LAN. Discovered
 * servers auto-fill the base URL and can pre-populate the model field.
 *
 * @param selectedProvider Currently selected provider ID.
 * @param apiKey Current API key input value.
 * @param baseUrl Current base URL input value.
 * @param selectedModel Current model name input value.
 * @param onProviderChanged Callback when provider selection changes.
 * @param onApiKeyChanged Callback when API key text changes.
 * @param onBaseUrlChanged Callback when base URL text changes.
 * @param onModelChanged Callback when model text changes.
 */
@Composable
fun ProviderStep(
    selectedProvider: String,
    apiKey: String,
    baseUrl: String,
    selectedModel: String,
    onProviderChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
) {
    val providerInfo = ProviderRegistry.findById(selectedProvider)
    val authType = providerInfo?.authType
    val suggestedModels = providerInfo?.suggestedModels.orEmpty()
    val isLocalProvider = authType == ProviderAuthType.URL_ONLY ||
        authType == ProviderAuthType.URL_AND_OPTIONAL_KEY

    var liveModels by remember { mutableStateOf(emptyList<String>()) }
    var isLoadingLive by remember { mutableStateOf(false) }
    var isLiveData by remember { mutableStateOf(false) }
    var showScanSheet by remember { mutableStateOf(false) }

    LaunchedEffect(selectedProvider, apiKey, baseUrl) {
        liveModels = emptyList()
        isLiveData = false
        if (providerInfo == null || providerInfo.modelListFormat == ModelListFormat.NONE) return@LaunchedEffect

        if (!isLocalProvider && apiKey.isBlank()) return@LaunchedEffect

        isLoadingLive = true
        val result = ModelFetcher.fetchModels(providerInfo, apiKey, baseUrl)
        isLoadingLive = false
        result.onSuccess { models ->
            liveModels = models
            isLiveData = true
        }
    }

    if (showScanSheet) {
        NetworkScanSheet(
            onDismiss = { showScanSheet = false },
            onServerSelected = { server ->
                onBaseUrlChanged(server.baseUrl)
                if (server.models.isNotEmpty() && selectedModel.isBlank()) {
                    onModelChanged(server.models.first())
                }
                showScanSheet = false
            },
        )
    }

    Column {
        Text(
            text = "API Provider",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))
        Text(
            text = "Select your AI provider and enter credentials. You can add more keys later in Settings.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(DESCRIPTION_SPACING_DP.dp))

        ProviderDropdown(
            selectedProviderId = selectedProvider,
            onProviderSelected = { onProviderChanged(it.id) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))

        if (authType == ProviderAuthType.API_KEY_ONLY ||
            authType == ProviderAuthType.URL_AND_OPTIONAL_KEY
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChanged,
                label = { Text("API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))
        }

        if (authType == ProviderAuthType.URL_ONLY ||
            authType == ProviderAuthType.URL_AND_OPTIONAL_KEY
        ) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChanged,
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(HINT_SPACING_DP.dp))
            TextButton(
                onClick = { showScanSheet = true },
            ) {
                Icon(
                    imageVector = Icons.Default.WifiFind,
                    contentDescription = null,
                    modifier = Modifier.size(SCAN_ICON_SIZE_DP.dp),
                )
                Spacer(modifier = Modifier.width(SCAN_ICON_SPACING_DP.dp))
                Text("Scan Network for Servers")
            }
            Spacer(modifier = Modifier.height(HINT_SPACING_DP.dp))
        }

        if (selectedProvider.isNotBlank()) {
            ModelSuggestionField(
                value = selectedModel,
                onValueChanged = onModelChanged,
                suggestions = suggestedModels,
                liveSuggestions = liveModels,
                isLoadingLive = isLoadingLive,
                isLiveData = isLiveData,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(HINT_SPACING_DP.dp))
        }

        Text(
            text = "You can skip this step and add keys later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
