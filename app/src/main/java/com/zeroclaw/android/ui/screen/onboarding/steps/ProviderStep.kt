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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.data.validation.ValidationResult
import com.zeroclaw.android.ui.component.setup.ProviderSetupFlow

/** Standard spacing between form fields. */
private const val FIELD_SPACING_DP = 16

/** Spacing after the section description. */
private const val DESCRIPTION_SPACING_DP = 24

/**
 * Onboarding step for selecting a provider and entering credentials.
 *
 * Acts as a thin wrapper around [ProviderSetupFlow], adding onboarding-specific
 * title text and description. Model fetching is delegated to the
 * [OnboardingCoordinator][com.zeroclaw.android.ui.screen.onboarding.OnboardingCoordinator]
 * which manages debounced fetching via its own state flows.
 *
 * For local providers (Ollama, LM Studio, vLLM, LocalAI), a "Scan Network"
 * button allows automatic discovery of running servers on the LAN. Discovered
 * servers auto-fill the base URL and can pre-populate the model field.
 *
 * @param selectedProvider Currently selected provider ID.
 * @param apiKey Current API key input value.
 * @param baseUrl Current base URL input value.
 * @param selectedModel Current model name input value.
 * @param availableModels Models fetched by the coordinator from the provider's API.
 * @param isLoadingModels Whether the coordinator is currently fetching models.
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
    availableModels: List<String> = emptyList(),
    isLoadingModels: Boolean = false,
    validationResult: ValidationResult = ValidationResult.Idle,
    onProviderChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onValidate: () -> Unit = {},
) {
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(DESCRIPTION_SPACING_DP.dp))

        ProviderSetupFlow(
            selectedProvider = selectedProvider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            selectedModel = selectedModel,
            availableModels = availableModels,
            validationResult = validationResult,
            onProviderChanged = onProviderChanged,
            onApiKeyChanged = onApiKeyChanged,
            onBaseUrlChanged = onBaseUrlChanged,
            onModelChanged = onModelChanged,
            onValidate = onValidate,
            showSkipHint = true,
            isLoadingModels = isLoadingModels,
            isLiveModelData = availableModels.isNotEmpty(),
            onServerSelected = { server ->
                if (server.models.isNotEmpty() && selectedModel.isBlank()) {
                    onModelChanged(server.models.first())
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
