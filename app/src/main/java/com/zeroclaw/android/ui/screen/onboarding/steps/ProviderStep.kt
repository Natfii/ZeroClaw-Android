/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.model.ProviderAuthType
import com.zeroclaw.android.ui.component.ModelSuggestionField
import com.zeroclaw.android.ui.component.ProviderDropdown

/** Standard spacing between form fields. */
private const val FIELD_SPACING_DP = 16

/** Spacing after the section description. */
private const val DESCRIPTION_SPACING_DP = 24

/** Spacing before the skip hint. */
private const val HINT_SPACING_DP = 8

/**
 * Onboarding step for selecting a provider and entering credentials.
 *
 * Replaces free-text fields with a structured [ProviderDropdown] and
 * dynamic credential fields based on the provider's [ProviderAuthType].
 * Includes a [ModelSuggestionField] with suggested models from the registry.
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
            Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))
        }

        if (selectedProvider.isNotBlank()) {
            ModelSuggestionField(
                value = selectedModel,
                onValueChanged = onModelChanged,
                suggestions = suggestedModels,
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
