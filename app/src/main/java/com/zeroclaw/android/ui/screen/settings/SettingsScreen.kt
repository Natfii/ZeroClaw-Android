/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.ui.component.SectionHeader

/**
 * Root settings screen displaying a sectioned list of configuration options.
 *
 * Each item navigates to a dedicated sub-screen when tapped.
 *
 * @param onNavigateToServiceConfig Navigate to service configuration.
 * @param onNavigateToBattery Navigate to battery settings.
 * @param onNavigateToApiKeys Navigate to API key management.
 * @param onNavigateToLogViewer Navigate to log viewer.
 * @param onNavigateToAbout Navigate to about screen.
 * @param onNavigateToUpdates Navigate to updates screen.
 * @param onRerunWizard Callback to reset onboarding and navigate to the setup wizard.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun SettingsScreen(
    onNavigateToServiceConfig: () -> Unit,
    onNavigateToBattery: () -> Unit,
    onNavigateToApiKeys: () -> Unit,
    onNavigateToLogViewer: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToUpdates: () -> Unit,
    onRerunWizard: () -> Unit,
    edgeMargin: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    var showRerunDialog by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SectionHeader(title = "Service")
        SettingsItem(
            icon = Icons.Outlined.Settings,
            title = "Service Configuration",
            subtitle = "Host, port, auto-start",
            onClick = onNavigateToServiceConfig,
        )
        SettingsItem(
            icon = Icons.Outlined.BatteryAlert,
            title = "Battery Settings",
            subtitle = "Optimization exemptions",
            onClick = onNavigateToBattery,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader(title = "Security")
        SettingsItem(
            icon = Icons.Outlined.Key,
            title = "API Keys",
            subtitle = "Manage provider credentials",
            onClick = onNavigateToApiKeys,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader(title = "Diagnostics")
        SettingsItem(
            icon = Icons.AutoMirrored.Outlined.Subject,
            title = "Log Viewer",
            subtitle = "View daemon and service logs",
            onClick = onNavigateToLogViewer,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader(title = "App")
        SettingsItem(
            icon = Icons.Outlined.Refresh,
            title = "Re-run Setup Wizard",
            subtitle = "Walk through initial configuration again",
            onClick = { showRerunDialog = true },
        )
        SettingsItem(
            icon = Icons.Outlined.SystemUpdate,
            title = "Updates",
            subtitle = "Check for new versions",
            onClick = onNavigateToUpdates,
        )
        SettingsItem(
            icon = Icons.Outlined.Info,
            title = "About",
            subtitle = "Version, licenses, links",
            onClick = onNavigateToAbout,
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showRerunDialog) {
        RerunWizardDialog(
            onConfirm = {
                showRerunDialog = false
                onRerunWizard()
            },
            onDismiss = { showRerunDialog = false },
        )
    }
}

/**
 * Confirmation dialog shown before re-running the setup wizard.
 *
 * @param onConfirm Called when the user confirms.
 * @param onDismiss Called when the user cancels.
 */
@Composable
private fun RerunWizardDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Re-run Setup Wizard?") },
        text = {
            Text(
                "This will open the initial setup wizard again. " +
                    "Your existing settings and API keys will be preserved.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Single settings list item with icon, title, and subtitle.
 *
 * @param icon Leading icon for the item.
 * @param title Primary text label.
 * @param subtitle Secondary descriptive text.
 * @param onClick Callback invoked when the item is tapped.
 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .semantics { role = Role.Button },
    )
}
