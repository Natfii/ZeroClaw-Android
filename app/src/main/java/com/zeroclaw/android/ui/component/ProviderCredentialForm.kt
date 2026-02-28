/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.data.ProviderKeyValidator
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.model.DiscoveredServer
import com.zeroclaw.android.model.ProviderAuthType

/** Standard spacing between form fields. */
private const val FIELD_SPACING_DP = 16

/** Spacing after help text and link rows. */
private const val HINT_SPACING_DP = 8

/** Size of small inline icons (scan, open-in-new). */
private const val INLINE_ICON_SIZE_DP = 18

/** Spacing between an inline icon and its label. */
private const val INLINE_ICON_LABEL_SPACING_DP = 4

/**
 * Shared credential form for selecting a provider and entering authentication details.
 *
 * Renders a [ProviderDropdown], contextual help text and "Get API Key" link,
 * a base URL field with localhost warning and "Scan Network" button for local
 * providers, and an API key field with prefix validation. Used by both the
 * onboarding [ProviderStep][com.zeroclaw.android.ui.screen.onboarding.steps.ProviderStep]
 * and the settings
 * [ApiKeyDetailScreen][com.zeroclaw.android.ui.screen.settings.apikeys.ApiKeyDetailScreen].
 *
 * When the base URL contains "localhost" or "127.0.0.1", a warning hints the
 * user to enter their computer's LAN IP or use "Scan Network" instead, because
 * `localhost` on Android refers to the phone itself.
 *
 * @param selectedProviderId Currently selected provider ID, or empty string.
 * @param apiKey Current API key input value.
 * @param baseUrl Current base URL input value.
 * @param onProviderChanged Callback when provider selection changes (receives provider ID).
 * @param onApiKeyChanged Callback when API key text changes.
 * @param onBaseUrlChanged Callback when base URL text changes.
 * @param modifier Modifier applied to the root [Column].
 * @param enabled Whether the form fields are interactive.
 * @param providerDropdownEnabled Separate enable flag for the provider dropdown
 *   (e.g. disabled when editing an existing key).
 * @param apiKeyLabel Label for the API key field.
 * @param apiKeyTrailingIcon Optional trailing icon composable for the API key field
 *   (e.g. a QR scanner button).
 * @param showApiKeyWhenBlank When true, shows the API key field even when no
 *   provider is selected (used by the settings screen).
 * @param baseUrlKeyboardType Keyboard type for the base URL field.
 * @param baseUrlImeAction IME action for the base URL field.
 * @param apiKeyImeAction IME action for the API key field.
 * @param onServerSelected Optional callback invoked when a server is picked from
 *   the network scan sheet; allows the caller to pre-fill model fields.
 * @param oauthConnected Whether an OAuth session is connected for this provider.
 *   When true, the API key field and "Get API Key" link are hidden because
 *   authentication is handled by the OAuth token.
 */
@Composable
fun ProviderCredentialForm(
    selectedProviderId: String,
    apiKey: String,
    baseUrl: String,
    onProviderChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    providerDropdownEnabled: Boolean = enabled,
    apiKeyLabel: String = "API Key",
    apiKeyTrailingIcon: @Composable (() -> Unit)? = null,
    showApiKeyWhenBlank: Boolean = false,
    baseUrlKeyboardType: KeyboardType = KeyboardType.Uri,
    baseUrlImeAction: ImeAction = ImeAction.Default,
    apiKeyImeAction: ImeAction = ImeAction.Default,
    onServerSelected: ((DiscoveredServer) -> Unit)? = null,
    oauthConnected: Boolean = false,
) {
    val providerInfo = ProviderRegistry.findById(selectedProviderId)
    val authType = providerInfo?.authType
    val needsUrl =
        authType == ProviderAuthType.URL_ONLY ||
            authType == ProviderAuthType.URL_AND_OPTIONAL_KEY
    val needsKey = authType == ProviderAuthType.API_KEY_ONLY
    val showKeyField =
        !oauthConnected &&
            authType != ProviderAuthType.URL_ONLY && authType != ProviderAuthType.NONE

    var showScanSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val prefixWarning by remember(selectedProviderId, apiKey) {
        derivedStateOf {
            if (providerInfo != null) {
                ProviderKeyValidator.validateKeyFormat(providerInfo, apiKey)
            } else {
                null
            }
        }
    }
    var prefixOverridden by remember { mutableStateOf(false) }

    LaunchedEffect(selectedProviderId, apiKey) {
        prefixOverridden = false
    }

    val isLocalhostUrl =
        remember(baseUrl) {
            val lower = baseUrl.lowercase()
            lower.contains("localhost") || lower.contains("127.0.0.1")
        }

    if (showScanSheet) {
        NetworkScanSheet(
            onDismiss = { showScanSheet = false },
            onServerSelected = { server ->
                onBaseUrlChanged(server.baseUrl)
                onServerSelected?.invoke(server)
                showScanSheet = false
            },
        )
    }

    Column(modifier = modifier) {
        ProviderDropdown(
            selectedProviderId = selectedProviderId,
            onProviderSelected = { onProviderChanged(it.id) },
            enabled = providerDropdownEnabled,
            modifier = Modifier.fillMaxWidth(),
        )

        if (providerInfo?.helpText?.isNotEmpty() == true) {
            Spacer(modifier = Modifier.height(HINT_SPACING_DP.dp))
            Text(
                text = providerInfo.helpText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!oauthConnected && providerInfo?.keyCreationUrl?.isNotEmpty() == true) {
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(providerInfo.keyCreationUrl)),
                    )
                },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(INLINE_ICON_SIZE_DP.dp),
                )
                Spacer(modifier = Modifier.width(INLINE_ICON_LABEL_SPACING_DP.dp))
                Text("Get API Key")
            }
        }

        Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))

        if (needsUrl) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChanged,
                label = { Text("Base URL") },
                singleLine = true,
                enabled = enabled,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = baseUrlKeyboardType,
                        imeAction = baseUrlImeAction,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )

            if (isLocalhostUrl) {
                Spacer(modifier = Modifier.height(HINT_SPACING_DP.dp))
                Text(
                    text =
                        "\"localhost\" refers to the phone itself on Android. " +
                            "Enter your computer\u2019s LAN IP address or use " +
                            "\"Scan Network\" to find servers automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            Spacer(modifier = Modifier.height(HINT_SPACING_DP.dp))
            TextButton(
                onClick = { showScanSheet = true },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.Default.WifiFind,
                    contentDescription = null,
                    modifier = Modifier.size(INLINE_ICON_SIZE_DP.dp),
                )
                Spacer(modifier = Modifier.width(INLINE_ICON_LABEL_SPACING_DP.dp))
                Text("Scan Network for Servers")
            }
            Spacer(modifier = Modifier.height(HINT_SPACING_DP.dp))
        }

        if (showKeyField || showApiKeyWhenBlank) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChanged,
                label = { Text(if (needsKey) apiKeyLabel else "$apiKeyLabel (optional)") },
                singleLine = true,
                enabled = enabled,
                visualTransformation = PasswordVisualTransformation(),
                trailingIcon = apiKeyTrailingIcon,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = apiKeyImeAction,
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
    }
}
