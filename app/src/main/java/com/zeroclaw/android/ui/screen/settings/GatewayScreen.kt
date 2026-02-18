/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.ui.component.SectionHeader

/**
 * Gateway and pairing configuration screen.
 *
 * Maps to the upstream `[gateway]` TOML section: pairing requirement,
 * public bind, paired tokens, and rate limiting settings.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param settingsViewModel The shared [SettingsViewModel].
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun GatewayScreen(
    edgeMargin: Dp,
    settingsViewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SectionHeader(title = "Access Control")

        GatewayToggle(
            title = "Require pairing",
            subtitle = "Enforce token-based pairing for gateway connections",
            checked = settings.gatewayRequirePairing,
            onCheckedChange = { settingsViewModel.updateGatewayRequirePairing(it) },
            description = "Require pairing",
        )

        GatewayToggle(
            title = "Allow public bind",
            subtitle = "Bind the gateway to 0.0.0.0 instead of localhost only",
            checked = settings.gatewayAllowPublicBind,
            onCheckedChange = { settingsViewModel.updateGatewayAllowPublicBind(it) },
            description = "Allow public bind",
        )

        OutlinedTextField(
            value = settings.gatewayPairedTokens,
            onValueChange = { settingsViewModel.updateGatewayPairedTokens(it) },
            label = { Text("Paired tokens") },
            supportingText = { Text("Comma-separated authorized tokens") },
            enabled = settings.gatewayRequirePairing,
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeader(title = "Rate Limits")

        OutlinedTextField(
            value = settings.gatewayPairRateLimit.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.let { settingsViewModel.updateGatewayPairRateLimit(it) }
            },
            label = { Text("Pair rate limit (per minute)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = settings.gatewayWebhookRateLimit.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.let { settingsViewModel.updateGatewayWebhookRateLimit(it) }
            },
            label = { Text("Webhook rate limit (per minute)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = settings.gatewayIdempotencyTtl.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.let { settingsViewModel.updateGatewayIdempotencyTtl(it) }
            },
            label = { Text("Idempotency TTL (seconds)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Toggle row used on the gateway screen.
 *
 * @param title Primary label text.
 * @param subtitle Descriptive text below the title.
 * @param checked Current toggle state.
 * @param onCheckedChange Callback for state changes.
 * @param description Accessibility content description.
 */
@Composable
private fun GatewayToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}
