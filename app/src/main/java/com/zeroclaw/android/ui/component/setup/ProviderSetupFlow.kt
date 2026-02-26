/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component.setup

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.data.validation.ValidationResult
import com.zeroclaw.android.model.DiscoveredServer
import com.zeroclaw.android.ui.component.ModelSuggestionField
import com.zeroclaw.android.ui.component.ProviderCredentialForm
import com.zeroclaw.android.ui.theme.ZeroClawTheme
import com.zeroclaw.android.util.DeepLinkTarget
import com.zeroclaw.android.util.ExternalAppLauncher

/** Standard spacing between form fields. */
private val FieldSpacing = 16.dp

/** Spacing after the title. */
private val TitleSpacing = 8.dp

/** Spacing between the deep-link button and the validate button in the action row. */
private val ActionRowSpacing = 8.dp

/** Spacing before the skip hint text. */
private val HintSpacing = 8.dp

/** Spacing between the validate button icon and label. */
private val ButtonIconSpacing = 4.dp

/**
 * Reusable provider setup form combining credential entry, validation, and model selection.
 *
 * Composes a scrollable vertical layout containing:
 * 1. Provider dropdown via [ProviderCredentialForm]
 * 2. An action row with a [DeepLinkButton] to the provider's API-key console
 *    (when available) and a "Validate" [FilledTonalButton]
 * 3. A [ValidationIndicator] showing the current validation state
 * 4. A [ModelSuggestionField] for selecting a model (when a provider is chosen)
 * 5. An optional skip hint (only during onboarding)
 *
 * This composable is intentionally state-hoisted: all form values and callbacks
 * are provided by the parent. The "Validate" button invokes [onValidate] without
 * managing validation state internally.
 *
 * Used by
 * [ProviderStep][com.zeroclaw.android.ui.screen.onboarding.steps.ProviderStep]
 * during onboarding and by settings screens for provider re-configuration.
 *
 * @param selectedProvider Currently selected provider ID, or empty string.
 * @param apiKey Current API key input value.
 * @param baseUrl Current base URL input value.
 * @param selectedModel Current model name input value.
 * @param availableModels Live model names fetched from the provider API.
 * @param validationResult Current state of the validation operation.
 * @param onProviderChanged Callback when provider selection changes (receives provider ID).
 * @param onApiKeyChanged Callback when API key text changes.
 * @param onBaseUrlChanged Callback when base URL text changes.
 * @param onModelChanged Callback when model text changes.
 * @param onValidate Callback to trigger credential validation.
 * @param showSkipHint Whether to display a "skip this step" hint at the bottom.
 * @param modifier Modifier applied to the root scrollable [Column].
 * @param isLoadingModels Whether live model data is currently being fetched.
 * @param isLiveModelData Whether [availableModels] represents real-time data.
 * @param onServerSelected Optional callback invoked when a server is picked from
 *   the network scan sheet for local providers.
 */
@Composable
fun ProviderSetupFlow(
    selectedProvider: String,
    apiKey: String,
    baseUrl: String,
    selectedModel: String,
    availableModels: List<String> = emptyList(),
    validationResult: ValidationResult = ValidationResult.Idle,
    onProviderChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onValidate: () -> Unit = {},
    showSkipHint: Boolean = false,
    modifier: Modifier = Modifier,
    isLoadingModels: Boolean = false,
    isLiveModelData: Boolean = false,
    onServerSelected: ((DiscoveredServer) -> Unit)? = null,
) {
    val providerInfo = ProviderRegistry.findById(selectedProvider)
    val suggestedModels = providerInfo?.suggestedModels.orEmpty()
    val consoleTarget = ExternalAppLauncher.providerConsoleTarget(selectedProvider)
    val validateEnabled =
        selectedProvider.isNotBlank() &&
            (apiKey.isNotBlank() || baseUrl.isNotBlank())

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        ProviderCredentialForm(
            selectedProviderId = selectedProvider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            onProviderChanged = onProviderChanged,
            onApiKeyChanged = onApiKeyChanged,
            onBaseUrlChanged = onBaseUrlChanged,
            onServerSelected = onServerSelected,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(FieldSpacing))

        ActionRow(
            consoleTarget = consoleTarget,
            validateEnabled = validateEnabled,
            validationResult = validationResult,
            onValidate = onValidate,
        )

        Spacer(modifier = Modifier.height(TitleSpacing))

        ValidationIndicator(
            result = validationResult,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(FieldSpacing))

        if (selectedProvider.isNotBlank()) {
            ModelSuggestionField(
                value = selectedModel,
                onValueChanged = onModelChanged,
                suggestions = suggestedModels,
                liveSuggestions = availableModels,
                isLoadingLive = isLoadingModels,
                isLiveData = isLiveModelData,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (showSkipHint) {
            Spacer(modifier = Modifier.height(HintSpacing))
            Text(
                text = "You can add keys later in Settings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Horizontal row containing the optional deep-link button and the validate button.
 *
 * The deep-link button is only rendered when [consoleTarget] is non-null. Both
 * buttons meet the 48x48dp minimum touch target through their default Material 3
 * sizing.
 *
 * @param consoleTarget Optional deep-link target for the provider's API-key console.
 * @param validateEnabled Whether the validate button is interactive.
 * @param validationResult Current validation state, used to disable during loading.
 * @param onValidate Callback invoked when the validate button is clicked.
 */
@Composable
private fun ActionRow(
    consoleTarget: DeepLinkTarget?,
    validateEnabled: Boolean,
    validationResult: ValidationResult,
    onValidate: () -> Unit,
) {
    val isLoading = validationResult is ValidationResult.Loading

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (consoleTarget != null) {
            DeepLinkButton(
                target = consoleTarget,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(ActionRowSpacing))
        }

        FilledTonalButton(
            onClick = onValidate,
            enabled = validateEnabled && !isLoading,
            modifier =
                if (consoleTarget != null) {
                    Modifier.weight(1f)
                } else {
                    Modifier.fillMaxWidth()
                }.semantics {
                    contentDescription = "Validate provider credentials"
                },
        ) {
            Icon(
                imageVector = Icons.Filled.Verified,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(ButtonIconSpacing))
            Text(text = if (isLoading) "Validating\u2026" else "Validate")
        }
    }
}

@Preview(name = "Provider Setup - Empty")
@Composable
private fun PreviewEmpty() {
    ZeroClawTheme {
        Surface {
            ProviderSetupFlow(
                selectedProvider = "",
                apiKey = "",
                baseUrl = "",
                selectedModel = "",
                onProviderChanged = {},
                onApiKeyChanged = {},
                onBaseUrlChanged = {},
                onModelChanged = {},
            )
        }
    }
}

@Preview(name = "Provider Setup - With Provider")
@Composable
private fun PreviewWithProvider() {
    ZeroClawTheme {
        Surface {
            ProviderSetupFlow(
                selectedProvider = "openai",
                apiKey = "sk-test1234",
                baseUrl = "",
                selectedModel = "gpt-4o",
                validationResult =
                    ValidationResult.Success(details = "3 models available"),
                onProviderChanged = {},
                onApiKeyChanged = {},
                onBaseUrlChanged = {},
                onModelChanged = {},
                showSkipHint = true,
            )
        }
    }
}

@Preview(name = "Provider Setup - Loading")
@Composable
private fun PreviewLoading() {
    ZeroClawTheme {
        Surface {
            ProviderSetupFlow(
                selectedProvider = "anthropic",
                apiKey = "sk-ant-test",
                baseUrl = "",
                selectedModel = "",
                validationResult = ValidationResult.Loading,
                onProviderChanged = {},
                onApiKeyChanged = {},
                onBaseUrlChanged = {},
                onModelChanged = {},
            )
        }
    }
}

@Preview(name = "Provider Setup - Failure")
@Composable
private fun PreviewFailure() {
    ZeroClawTheme {
        Surface {
            ProviderSetupFlow(
                selectedProvider = "openai",
                apiKey = "sk-bad-key",
                baseUrl = "",
                selectedModel = "",
                validationResult =
                    ValidationResult.Failure(message = "Invalid API key"),
                onProviderChanged = {},
                onApiKeyChanged = {},
                onBaseUrlChanged = {},
                onModelChanged = {},
            )
        }
    }
}

@Preview(name = "Provider Setup - Local Provider")
@Composable
private fun PreviewLocalProvider() {
    ZeroClawTheme {
        Surface {
            ProviderSetupFlow(
                selectedProvider = "ollama",
                apiKey = "",
                baseUrl = "http://192.168.1.100:11434",
                selectedModel = "llama3.3",
                validationResult =
                    ValidationResult.Success(details = "Connected \u2014 6 models available"),
                onProviderChanged = {},
                onApiKeyChanged = {},
                onBaseUrlChanged = {},
                onModelChanged = {},
                showSkipHint = true,
            )
        }
    }
}

@Preview(
    name = "Provider Setup - Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PreviewDark() {
    ZeroClawTheme {
        Surface {
            ProviderSetupFlow(
                selectedProvider = "openai",
                apiKey = "sk-test1234",
                baseUrl = "",
                selectedModel = "gpt-4o",
                validationResult =
                    ValidationResult.Success(details = "3 models available"),
                onProviderChanged = {},
                onApiKeyChanged = {},
                onBaseUrlChanged = {},
                onModelChanged = {},
                showSkipHint = true,
            )
        }
    }
}
