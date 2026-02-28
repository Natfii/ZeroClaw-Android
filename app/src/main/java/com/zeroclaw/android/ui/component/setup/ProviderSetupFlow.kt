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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.data.validation.ValidationResult
import com.zeroclaw.android.model.DiscoveredServer
import com.zeroclaw.android.model.ProviderAuthType
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

/** Size of the circular progress indicator inside the OAuth login button. */
private val OAuthProgressSize = 18.dp

/** Size of the ChatGPT logo icon in the OAuth login button. */
private val OAuthLogoSize = 20.dp

/** Pixel size for the ChatGPT logo Coil request (20dp at 4x density). */
private const val OAUTH_LOGO_PX = 80

/** Google Favicon API URL for the ChatGPT logo. */
private const val CHATGPT_FAVICON_URL =
    "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON" +
        "&fallback_opts=TYPE,SIZE,URL&url=https://chatgpt.com&size=128"

/** Stroke width for the OAuth progress indicator. */
private val OAuthProgressStroke = 2.dp

/** Internal padding for the OAuth connected chip. */
private val ChipPadding = 12.dp

/** Size of the check icon inside the OAuth connected chip. */
private val ChipIconSize = 20.dp

/** Spacing between the icon and text columns in the OAuth connected chip. */
private val ChipIconTextSpacing = 8.dp

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
 * @param isOAuthInProgress Whether an OAuth login flow is currently running.
 * @param oauthEmail Display email or label for the connected OAuth session,
 *   or empty string when not connected.
 * @param onOAuthLogin Optional callback to initiate the OAuth login flow.
 *   When non-null and the provider supports [ProviderAuthType.API_KEY_OR_OAUTH],
 *   the "Login with ChatGPT" button is rendered.
 * @param onOAuthDisconnect Optional callback to disconnect the current OAuth session.
 * @param scrollable Whether the root [Column] applies its own vertical scroll. Set to
 *   false when embedding inside an already-scrollable parent to avoid nested scrolling.
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
    isOAuthInProgress: Boolean = false,
    oauthEmail: String = "",
    onOAuthLogin: (() -> Unit)? = null,
    onOAuthDisconnect: (() -> Unit)? = null,
    scrollable: Boolean = true,
) {
    val providerInfo = ProviderRegistry.findById(selectedProvider)
    val suggestedModels = providerInfo?.suggestedModels.orEmpty()
    val consoleTarget = ExternalAppLauncher.providerConsoleTarget(selectedProvider)
    val isOAuthConnected = oauthEmail.isNotEmpty()
    val validateEnabled =
        selectedProvider.isNotBlank() &&
            (apiKey.isNotBlank() || baseUrl.isNotBlank())

    val columnModifier =
        if (scrollable) {
            modifier.verticalScroll(rememberScrollState())
        } else {
            modifier
        }

    Column(
        modifier = columnModifier,
    ) {
        ProviderCredentialForm(
            selectedProviderId = selectedProvider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            onProviderChanged = onProviderChanged,
            onApiKeyChanged = onApiKeyChanged,
            onBaseUrlChanged = onBaseUrlChanged,
            onServerSelected = onServerSelected,
            oauthConnected = isOAuthConnected,
            modifier = Modifier.fillMaxWidth(),
        )

        if (providerInfo?.authType == ProviderAuthType.API_KEY_OR_OAUTH &&
            onOAuthLogin != null
        ) {
            Spacer(modifier = Modifier.height(FieldSpacing))

            if (oauthEmail.isNotEmpty()) {
                OAuthConnectedChip(
                    email = oauthEmail,
                    onDisconnect = onOAuthDisconnect ?: {},
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Button(
                    onClick = onOAuthLogin,
                    enabled = !isOAuthInProgress,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription =
                                    "Login with ChatGPT"
                            },
                ) {
                    if (isOAuthInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(OAuthProgressSize),
                            strokeWidth = OAuthProgressStroke,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(ButtonIconSpacing))
                    } else {
                        val context = LocalContext.current
                        val logoRequest =
                            remember {
                                ImageRequest
                                    .Builder(context)
                                    .data(CHATGPT_FAVICON_URL)
                                    .size(OAUTH_LOGO_PX, OAUTH_LOGO_PX)
                                    .build()
                            }
                        AsyncImage(
                            model = logoRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(OAuthLogoSize)
                                    .clip(CircleShape),
                        )
                        Spacer(modifier = Modifier.width(ButtonIconSpacing))
                    }
                    Text(
                        text =
                            if (isOAuthInProgress) {
                                "Logging in\u2026"
                            } else {
                                "Login with ChatGPT"
                            },
                    )
                }

                Spacer(modifier = Modifier.height(FieldSpacing))

                OAuthDividerRow(modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(modifier = Modifier.height(FieldSpacing))

        if (!isOAuthConnected) {
            ActionRow(
                consoleTarget = consoleTarget,
                validateEnabled = validateEnabled,
                validationResult = validationResult,
                onValidate = onValidate,
            )

            Spacer(modifier = Modifier.height(TitleSpacing))
        }

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

/**
 * Horizontal divider with centered "or use API key" text.
 *
 * Rendered below the "Login with ChatGPT" button to indicate the
 * alternative API-key authentication path.
 *
 * @param modifier Modifier applied to the root [Row].
 */
@Composable
private fun OAuthDividerRow(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = "or use API key",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = FieldSpacing),
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

/**
 * Compact chip showing the connected OAuth session with a disconnect action.
 *
 * Displays a [CheckCircle][Icons.Default.CheckCircle] icon, the connected
 * account label, a "Connected via ChatGPT" subtitle, and a "Disconnect"
 * [TextButton].
 *
 * @param email Display label for the connected OAuth account.
 * @param onDisconnect Callback invoked when the user taps "Disconnect".
 * @param modifier Modifier applied to the root [Surface].
 */
@Composable
private fun OAuthConnectedChip(
    email: String,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(ChipPadding),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(ChipIconSize),
            )
            Spacer(modifier = Modifier.width(ChipIconTextSpacing))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "Connected via ChatGPT",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme.onSecondaryContainer
                            .copy(alpha = 0.7f),
                )
            }
            TextButton(onClick = onDisconnect) {
                Text("Disconnect")
            }
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

@Preview(name = "Provider Setup - OAuth Login")
@Composable
private fun PreviewOAuthLogin() {
    ZeroClawTheme {
        Surface {
            ProviderSetupFlow(
                selectedProvider = "openai",
                apiKey = "",
                baseUrl = "",
                selectedModel = "",
                onProviderChanged = {},
                onApiKeyChanged = {},
                onBaseUrlChanged = {},
                onModelChanged = {},
                onOAuthLogin = {},
                showSkipHint = true,
            )
        }
    }
}

@Preview(name = "Provider Setup - OAuth In Progress")
@Composable
private fun PreviewOAuthInProgress() {
    ZeroClawTheme {
        Surface {
            ProviderSetupFlow(
                selectedProvider = "openai",
                apiKey = "",
                baseUrl = "",
                selectedModel = "",
                onProviderChanged = {},
                onApiKeyChanged = {},
                onBaseUrlChanged = {},
                onModelChanged = {},
                isOAuthInProgress = true,
                onOAuthLogin = {},
                showSkipHint = true,
            )
        }
    }
}

@Preview(name = "Provider Setup - OAuth Connected")
@Composable
private fun PreviewOAuthConnected() {
    ZeroClawTheme {
        Surface {
            ProviderSetupFlow(
                selectedProvider = "openai",
                apiKey = "",
                baseUrl = "",
                selectedModel = "gpt-4o",
                onProviderChanged = {},
                onApiKeyChanged = {},
                onBaseUrlChanged = {},
                onModelChanged = {},
                oauthEmail = "ChatGPT Login",
                onOAuthLogin = {},
                onOAuthDisconnect = {},
                showSkipHint = true,
            )
        }
    }
}
