/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.data.remote.ModelFetcher
import com.zeroclaw.android.data.validation.ValidationResult
import com.zeroclaw.android.model.ModelListFormat
import com.zeroclaw.android.model.ProviderAuthType
import com.zeroclaw.android.ui.component.setup.ProviderSetupFlow

/** Standard spacing between form fields. */
private const val FIELD_SPACING_DP = 16

/** Spacing after the section description. */
private const val DESCRIPTION_SPACING_DP = 24

/**
 * Onboarding step for selecting a provider and entering credentials.
 *
 * Acts as a thin wrapper around [ProviderSetupFlow], adding onboarding-specific
 * title text, a description, and a [LaunchedEffect] for live model fetching
 * (which depends on ViewModel-level state outside the reusable component).
 *
 * For local providers (Ollama, LM Studio, vLLM, LocalAI), a "Scan Network"
 * button allows automatic discovery of running servers on the LAN. Discovered
 * servers auto-fill the base URL and can pre-populate the model field.
 *
 * @param selectedProvider Currently selected provider ID.
 * @param apiKey Current API key input value.
 * @param baseUrl Current base URL input value.
 * @param selectedModel Current model name input value.
 * @param validationResult Current state of the validation operation.
 * @param onProviderChanged Callback when provider selection changes.
 * @param onApiKeyChanged Callback when API key text changes.
 * @param onBaseUrlChanged Callback when base URL text changes.
 * @param onModelChanged Callback when model text changes.
 * @param onValidate Callback to trigger credential validation.
 */
@Composable
fun ProviderStep(
    selectedProvider: String,
    apiKey: String,
    baseUrl: String,
    selectedModel: String,
    validationResult: ValidationResult = ValidationResult.Idle,
    onProviderChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onValidate: () -> Unit = {},
) {
    val providerInfo = ProviderRegistry.findById(selectedProvider)
    val authType = providerInfo?.authType
    val isLocalProvider =
        authType == ProviderAuthType.URL_ONLY ||
            authType == ProviderAuthType.URL_AND_OPTIONAL_KEY

    var liveModels by remember { mutableStateOf(emptyList<String>()) }
    var isLoadingLive by remember { mutableStateOf(false) }
    var isLiveData by remember { mutableStateOf(false) }

    LaunchedEffect(selectedProvider, apiKey, baseUrl) {
        liveModels = emptyList()
        isLiveData = false
        if (providerInfo == null ||
            providerInfo.modelListFormat == ModelListFormat.NONE
        ) {
            return@LaunchedEffect
        }

        if (!isLocalProvider && apiKey.isBlank()) return@LaunchedEffect

        isLoadingLive = true
        val result = ModelFetcher.fetchModels(providerInfo, apiKey, baseUrl)
        isLoadingLive = false
        result.onSuccess { models ->
            liveModels = models
            isLiveData = true
        }
    }

    Column {
        Text(
            text = "API Provider",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))
        Text(
            text =
                "Select your AI provider and enter credentials. " +
                    "You can add more keys later in Settings.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(DESCRIPTION_SPACING_DP.dp))

        ProviderSetupFlow(
            selectedProvider = selectedProvider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            selectedModel = selectedModel,
            availableModels = liveModels,
            validationResult = validationResult,
            onProviderChanged = onProviderChanged,
            onApiKeyChanged = onApiKeyChanged,
            onBaseUrlChanged = onBaseUrlChanged,
            onModelChanged = onModelChanged,
            onValidate = onValidate,
            showSkipHint = true,
            isLoadingModels = isLoadingLive,
            isLiveModelData = isLiveData,
            onServerSelected = { server ->
                if (server.models.isNotEmpty() && selectedModel.isBlank()) {
                    onModelChanged(server.models.first())
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
