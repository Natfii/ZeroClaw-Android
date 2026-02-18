/*
 * Copyright 2026 ZeroClaw Community
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
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Token
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VpnKey
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.ui.component.SectionHeader

/**
 * Root settings screen displaying a sectioned list of configuration options.
 *
 * Each item navigates to a dedicated sub-screen when tapped.
 *
 * @param onNavigateToServiceConfig Navigate to service configuration.
 * @param onNavigateToBattery Navigate to battery settings.
 * @param onNavigateToApiKeys Navigate to API key management.
 * @param onNavigateToChannels Navigate to connected channels management.
 * @param onNavigateToLogViewer Navigate to log viewer.
 * @param onNavigateToDoctor Navigate to ZeroClaw Doctor diagnostics.
 * @param onNavigateToIdentity Navigate to agent identity editor.
 * @param onNavigateToAbout Navigate to about screen.
 * @param onNavigateToUpdates Navigate to updates screen.
 * @param onNavigateToAutonomy Navigate to autonomy level screen.
 * @param onNavigateToTunnel Navigate to tunnel configuration screen.
 * @param onNavigateToGateway Navigate to gateway and pairing screen.
 * @param onNavigateToToolManagement Navigate to tool management screen.
 * @param onNavigateToModelRoutes Navigate to model routes screen.
 * @param onNavigateToMemoryAdvanced Navigate to memory advanced config screen.
 * @param onNavigateToScheduler Navigate to scheduler and heartbeat screen.
 * @param onNavigateToObservability Navigate to observability backend screen.
 * @param onNavigateToSecurityOverview Navigate to security posture overview screen.
 * @param onNavigateToPluginRegistry Navigate to plugin registry sync settings.
 * @param onNavigateToCronJobs Navigate to scheduled cron jobs management screen.
 * @param onNavigateToToolsBrowser Navigate to tools inventory browser screen.
 * @param onNavigateToMemoryBrowser Navigate to memory entries browser screen.
 * @param onRerunWizard Callback to reset onboarding and navigate to the setup wizard.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param settingsViewModel ViewModel providing current settings for dynamic subtitles.
 * @param modifier Modifier applied to the root layout.
 */
@Suppress("LongParameterList")
@Composable
fun SettingsScreen(
    onNavigateToServiceConfig: () -> Unit,
    onNavigateToBattery: () -> Unit,
    onNavigateToApiKeys: () -> Unit,
    onNavigateToChannels: () -> Unit,
    onNavigateToLogViewer: () -> Unit,
    onNavigateToDoctor: () -> Unit,
    onNavigateToIdentity: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToUpdates: () -> Unit,
    onNavigateToAutonomy: () -> Unit,
    onNavigateToTunnel: () -> Unit,
    onNavigateToGateway: () -> Unit,
    onNavigateToToolManagement: () -> Unit,
    onNavigateToModelRoutes: () -> Unit,
    onNavigateToMemoryAdvanced: () -> Unit,
    onNavigateToScheduler: () -> Unit,
    onNavigateToObservability: () -> Unit,
    onNavigateToSecurityOverview: () -> Unit,
    onNavigateToPluginRegistry: () -> Unit = {},
    onNavigateToCronJobs: () -> Unit = {},
    onNavigateToToolsBrowser: () -> Unit = {},
    onNavigateToMemoryBrowser: () -> Unit = {},
    onRerunWizard: () -> Unit,
    edgeMargin: androidx.compose.ui.unit.Dp,
    settingsViewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
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
            subtitle =
                "${settings.host}:${settings.port}" +
                    if (settings.autoStartOnBoot) " | auto-start" else "",
            onClick = onNavigateToServiceConfig,
        )
        SettingsItem(
            icon = Icons.Outlined.BatteryAlert,
            title = "Battery Settings",
            subtitle = "Optimization exemptions",
            onClick = onNavigateToBattery,
        )
        SettingsItem(
            icon = Icons.Outlined.Fingerprint,
            title = "Agent Identity",
            subtitle = if (settings.identityJson.isNotBlank()) "Configured" else "Not set",
            onClick = onNavigateToIdentity,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader(title = "Security")
        SettingsItem(
            icon = Icons.Outlined.VerifiedUser,
            title = "Security Overview",
            subtitle = "View current security posture",
            onClick = onNavigateToSecurityOverview,
        )
        SettingsItem(
            icon = Icons.Outlined.Key,
            title = "API Keys",
            subtitle = "Manage provider credentials",
            onClick = onNavigateToApiKeys,
        )
        SettingsItem(
            icon = Icons.Outlined.Security,
            title = "Autonomy Level",
            subtitle = settings.autonomyLevel,
            onClick = onNavigateToAutonomy,
        )
        SettingsItem(
            icon = Icons.Outlined.Forum,
            title = "Connected Channels",
            subtitle = "Telegram, Discord, Slack, and more",
            onClick = onNavigateToChannels,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader(title = "Network")
        SettingsItem(
            icon = Icons.Outlined.Hub,
            title = "Gateway & Pairing",
            subtitle =
                if (settings.gatewayRequirePairing) "Pairing required" else "Open access",
            onClick = onNavigateToGateway,
        )
        SettingsItem(
            icon = Icons.Outlined.VpnKey,
            title = "Tunnel",
            subtitle = settings.tunnelProvider,
            onClick = onNavigateToTunnel,
        )
        SettingsItem(
            icon = Icons.Outlined.Sync,
            title = "Plugin Registry",
            subtitle =
                if (settings.pluginSyncEnabled) "Auto-sync enabled" else "Manual only",
            onClick = onNavigateToPluginRegistry,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader(title = "Daemon Config")
        SettingsItem(
            icon = Icons.Outlined.Route,
            title = "Model Routes",
            subtitle = "Hint-based provider routing",
            onClick = onNavigateToModelRoutes,
        )
        SettingsItem(
            icon = Icons.Outlined.Memory,
            title = "Memory Advanced",
            subtitle = "Embedding, hygiene, recall weights",
            onClick = onNavigateToMemoryAdvanced,
        )
        SettingsItem(
            icon = Icons.Outlined.Tune,
            title = "Tool Management",
            subtitle = "Browser, HTTP, Composio",
            onClick = onNavigateToToolManagement,
        )
        SettingsItem(
            icon = Icons.Outlined.Token,
            title = "Tools Browser",
            subtitle = "View all available tools",
            onClick = onNavigateToToolsBrowser,
        )
        SettingsItem(
            icon = Icons.Outlined.Psychology,
            title = "Memory Browser",
            subtitle = "Browse and search memory entries",
            onClick = onNavigateToMemoryBrowser,
        )
        SettingsItem(
            icon = Icons.Outlined.Schedule,
            title = "Scheduler & Heartbeat",
            subtitle =
                if (settings.schedulerEnabled) "Scheduler on" else "Scheduler off",
            onClick = onNavigateToScheduler,
        )
        SettingsItem(
            icon = Icons.Outlined.TaskAlt,
            title = "Scheduled Tasks",
            subtitle = "View and manage cron jobs",
            onClick = onNavigateToCronJobs,
        )
        SettingsItem(
            icon = Icons.Outlined.Speed,
            title = "Observability",
            subtitle = settings.observabilityBackend,
            onClick = onNavigateToObservability,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader(title = "Diagnostics")
        SettingsItem(
            icon = Icons.AutoMirrored.Outlined.Subject,
            title = "Log Viewer",
            subtitle = "View daemon and service logs",
            onClick = onNavigateToLogViewer,
        )
        SettingsItem(
            icon = Icons.Outlined.HealthAndSafety,
            title = "ZeroClaw Doctor",
            subtitle = "Validate config, keys, and connectivity",
            onClick = onNavigateToDoctor,
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
