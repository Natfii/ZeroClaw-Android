/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component.setup

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.ui.theme.ZeroClawTheme

/** Spacing after the title text. */
private val TitleSpacing = 8.dp

/** Spacing between the description and the card group. */
private val DescriptionSpacing = 16.dp

/** Vertical spacing between tunnel option cards. */
private val CardSpacing = 8.dp

/** Internal padding for each tunnel option card. */
private val CardPadding = 16.dp

/** Border width for the selected tunnel card. */
private val SelectedBorderWidth = 2.dp

/** Spacing between the card group and additional fields. */
private val FieldSpacing = 16.dp

/** Spacing before the skip hint text. */
private val HintSpacing = 8.dp

/**
 * Describes a tunnel option for display in the selector.
 *
 * @property id Machine-readable identifier matching upstream TOML values.
 * @property title Human-readable option name.
 * @property description Brief explanation of the tunnel behaviour.
 */
private data class TunnelOption(
    val id: String,
    val title: String,
    val description: String,
)

/** Available tunnel options presented to the user. */
private val TUNNEL_OPTIONS =
    listOf(
        TunnelOption(
            id = "none",
            title = "Local Only",
            description = "Agent accessible only on this device and local network",
        ),
        TunnelOption(
            id = "ngrok",
            title = "Ngrok",
            description = "Tunnel via ngrok for public HTTPS endpoint",
        ),
        TunnelOption(
            id = "cloudflare",
            title = "Cloudflare",
            description = "Tunnel via Cloudflare for public endpoint",
        ),
        TunnelOption(
            id = "tailscale",
            title = "Tailscale",
            description = "Tunnel via Tailscale for private mesh network access",
        ),
        TunnelOption(
            id = "custom",
            title = "Custom",
            description = "Specify your own public endpoint URL",
        ),
    )

/**
 * Tunnel configuration form for selecting and configuring a tunnel provider.
 *
 * Presents five selectable cards for tunnel type selection (Local Only, Ngrok,
 * Cloudflare, Tailscale, Custom) and conditionally renders additional fields based on the
 * chosen type:
 * - **Ngrok** or **Cloudflare**: a password-masked token field.
 * - **Custom**: a URL text field for the public endpoint.
 *
 * Field names align with [GlobalTomlConfig][com.zeroclaw.android.service.GlobalTomlConfig]:
 * `tunnelProvider`, `tunnelNgrokAuthToken`, `tunnelCloudflareToken`, and
 * `tunnelCustomCommand`.
 *
 * @param tunnelType Current tunnel type identifier: "none", "ngrok", "cloudflare", "tailscale", or "custom".
 * @param tunnelToken Authentication token for ngrok or Cloudflare tunnels.
 * @param customEndpoint Custom public endpoint URL when tunnel type is "custom".
 * @param onTunnelTypeChanged Callback when the user selects a different tunnel type.
 * @param onTunnelTokenChanged Callback when the token field value changes.
 * @param onCustomEndpointChanged Callback when the custom endpoint field value changes.
 * @param showSkipHint Whether to display a hint that this step can be skipped.
 * @param modifier Modifier applied to the root scrollable [Column].
 */
@Composable
fun TunnelConfigFlow(
    tunnelType: String,
    tunnelToken: String,
    customEndpoint: String,
    onTunnelTypeChanged: (String) -> Unit,
    onTunnelTokenChanged: (String) -> Unit,
    onCustomEndpointChanged: (String) -> Unit,
    showSkipHint: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Tunnel Configuration",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(TitleSpacing))

        Text(
            text = "Expose your agent to the internet for webhooks and external access.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(DescriptionSpacing))

        TUNNEL_OPTIONS.forEach { option ->
            val isSelected = tunnelType == option.id

            TunnelOptionCard(
                option = option,
                isSelected = isSelected,
                onClick = { onTunnelTypeChanged(option.id) },
            )

            Spacer(modifier = Modifier.height(CardSpacing))
        }

        if (tunnelType == "ngrok" || tunnelType == "cloudflare") {
            Spacer(modifier = Modifier.height(FieldSpacing))
            val label =
                if (tunnelType == "ngrok") "Ngrok Auth Token" else "Cloudflare Token"
            OutlinedTextField(
                value = tunnelToken,
                onValueChange = onTunnelTokenChanged,
                label = { Text(label) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = label
                        },
            )
        }

        if (tunnelType == "tailscale") {
            Spacer(modifier = Modifier.height(FieldSpacing))
            OutlinedTextField(
                value = customEndpoint,
                onValueChange = onCustomEndpointChanged,
                label = { Text("Tailscale Hostname") },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Tailscale Hostname"
                        },
            )
        }

        if (tunnelType == "custom") {
            Spacer(modifier = Modifier.height(FieldSpacing))
            OutlinedTextField(
                value = customEndpoint,
                onValueChange = onCustomEndpointChanged,
                label = { Text("Public Endpoint URL") },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Public Endpoint URL"
                        },
            )
        }

        if (showSkipHint) {
            Spacer(modifier = Modifier.height(HintSpacing))
            Text(
                text = "You can configure a tunnel later in Settings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A single selectable tunnel option card.
 *
 * Displays the option title and description inside an [Card]. When
 * selected, applies a primary-coloured border and primary-container background.
 * Accessibility semantics include the option name, selection state, and a
 * radio-button role.
 *
 * @param option The tunnel option to display.
 * @param isSelected Whether this option is currently selected.
 * @param onClick Callback invoked when the card is tapped.
 */
@Composable
private fun TunnelOptionCard(
    option: TunnelOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors =
            if (isSelected) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            } else {
                CardDefaults.cardColors()
            },
        border =
            if (isSelected) {
                BorderStroke(
                    SelectedBorderWidth,
                    MaterialTheme.colorScheme.primary,
                )
            } else {
                null
            },
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "${option.title}, ${if (isSelected) "selected" else "not selected"}"
                    role = Role.RadioButton
                    selected = isSelected
                },
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
        ) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = option.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(name = "Tunnel Config - None")
@Composable
private fun PreviewNone() {
    ZeroClawTheme {
        Surface {
            TunnelConfigFlow(
                tunnelType = "none",
                tunnelToken = "",
                customEndpoint = "",
                onTunnelTypeChanged = {},
                onTunnelTokenChanged = {},
                onCustomEndpointChanged = {},
                showSkipHint = true,
            )
        }
    }
}

@Preview(name = "Tunnel Config - Ngrok")
@Composable
private fun PreviewNgrok() {
    ZeroClawTheme {
        Surface {
            TunnelConfigFlow(
                tunnelType = "ngrok",
                tunnelToken = "2abc123def",
                customEndpoint = "",
                onTunnelTypeChanged = {},
                onTunnelTokenChanged = {},
                onCustomEndpointChanged = {},
            )
        }
    }
}

@Preview(name = "Tunnel Config - Custom")
@Composable
private fun PreviewCustom() {
    ZeroClawTheme {
        Surface {
            TunnelConfigFlow(
                tunnelType = "custom",
                tunnelToken = "",
                customEndpoint = "https://my-agent.example.com",
                onTunnelTypeChanged = {},
                onTunnelTokenChanged = {},
                onCustomEndpointChanged = {},
            )
        }
    }
}

@Preview(
    name = "Tunnel Config - Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PreviewDark() {
    ZeroClawTheme {
        Surface {
            TunnelConfigFlow(
                tunnelType = "cloudflare",
                tunnelToken = "cf-token-value",
                customEndpoint = "",
                onTunnelTypeChanged = {},
                onTunnelTokenChanged = {},
                onCustomEndpointChanged = {},
                showSkipHint = true,
            )
        }
    }
}
