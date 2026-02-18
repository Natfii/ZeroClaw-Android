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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.ui.component.SectionHeader

/**
 * Tool management screen for configuring built-in tool integrations.
 *
 * Maps to upstream `[browser]`, `[http_request]`, and `[composio]`
 * TOML sections. Each tool can be toggled on/off with domain
 * allowlists where applicable.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param settingsViewModel The shared [SettingsViewModel].
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun ToolManagementScreen(
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

        SectionHeader(title = "Browser Tool")

        ToolToggle(
            title = "Enable browser",
            subtitle = "Allow the agent to browse web pages",
            checked = settings.browserEnabled,
            onCheckedChange = { settingsViewModel.updateBrowserEnabled(it) },
            description = "Enable browser tool",
        )

        OutlinedTextField(
            value = settings.browserAllowedDomains,
            onValueChange = { settingsViewModel.updateBrowserAllowedDomains(it) },
            label = { Text("Allowed domains") },
            supportingText = { Text("Comma-separated (empty = all domains)") },
            enabled = settings.browserEnabled,
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeader(title = "HTTP Request Tool")

        ToolToggle(
            title = "Enable HTTP requests",
            subtitle = "Allow the agent to make HTTP calls to external APIs",
            checked = settings.httpRequestEnabled,
            onCheckedChange = { settingsViewModel.updateHttpRequestEnabled(it) },
            description = "Enable HTTP request tool",
        )

        OutlinedTextField(
            value = settings.httpRequestAllowedDomains,
            onValueChange = { settingsViewModel.updateHttpRequestAllowedDomains(it) },
            label = { Text("Allowed domains") },
            supportingText = { Text("Comma-separated (empty = all domains)") },
            enabled = settings.httpRequestEnabled,
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeader(title = "Composio Integration")

        ToolToggle(
            title = "Enable Composio",
            subtitle = "Connect to Composio for third-party tool integrations",
            checked = settings.composioEnabled,
            onCheckedChange = { settingsViewModel.updateComposioEnabled(it) },
            description = "Enable Composio",
        )

        OutlinedTextField(
            value = settings.composioApiKey,
            onValueChange = { settingsViewModel.updateComposioApiKey(it) },
            label = { Text("API key") },
            singleLine = true,
            enabled = settings.composioEnabled,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = settings.composioEntityId,
            onValueChange = { settingsViewModel.updateComposioEntityId(it) },
            label = { Text("Entity ID") },
            singleLine = true,
            enabled = settings.composioEnabled,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Toggle row used on the tool management screen.
 *
 * @param title Primary label text.
 * @param subtitle Descriptive text below the title.
 * @param checked Current toggle state.
 * @param onCheckedChange Callback for state changes.
 * @param description Accessibility content description.
 */
@Composable
private fun ToolToggle(
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
