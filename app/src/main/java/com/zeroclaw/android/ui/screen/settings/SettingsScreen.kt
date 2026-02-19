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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.DarkMode
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.model.ThemeMode
import com.zeroclaw.android.navigation.SettingsNavAction
import com.zeroclaw.android.ui.component.SectionHeader
import com.zeroclaw.android.ui.component.SettingsListItem

/**
 * Root settings screen displaying a sectioned list of configuration options.
 *
 * Each item navigates to a dedicated sub-screen when tapped. Navigation is
 * dispatched through a single [onNavigate] callback using [SettingsNavAction].
 *
 * @param onNavigate Callback invoked with a [SettingsNavAction] when the user taps a setting.
 * @param onRerunWizard Callback to reset onboarding and navigate to the setup wizard.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param settingsViewModel ViewModel providing current settings for dynamic subtitles.
 * @param restartRequired Whether the daemon needs a restart to apply settings changes.
 * @param onRestartDaemon Callback invoked when the user taps the restart button.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun SettingsScreen(
    onNavigate: (SettingsNavAction) -> Unit,
    onRerunWizard: () -> Unit,
    edgeMargin: Dp,
    settingsViewModel: SettingsViewModel = viewModel(),
    restartRequired: Boolean = false,
    onRestartDaemon: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    var showRerunDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        if (restartRequired) {
            RestartRequiredBanner(
                edgeMargin = edgeMargin,
                onRestartDaemon = onRestartDaemon,
            )
        }

        SectionHeader(title = "Daemon")
        SettingsListItem(
            icon = Icons.Outlined.Settings,
            title = "Service Configuration",
            subtitle =
                "${settings.host}:${settings.port}" +
                    if (settings.autoStartOnBoot) " | auto-start" else "",
            onClick = { onNavigate(SettingsNavAction.ServiceConfig) },
        )
        SettingsListItem(
            icon = Icons.Outlined.BatteryAlert,
            title = "Battery Settings",
            subtitle = "Optimization exemptions",
            onClick = { onNavigate(SettingsNavAction.Battery) },
        )
        SettingsListItem(
            icon = Icons.Outlined.Fingerprint,
            title = "Agent Identity",
            subtitle = if (settings.identityJson.isNotBlank()) "Configured" else "Not set",
            onClick = { onNavigate(SettingsNavAction.Identity) },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader(title = "Security")
        SettingsListItem(
            icon = Icons.Outlined.VerifiedUser,
            title = "Security Overview",
            subtitle = "View current security posture",
            onClick = { onNavigate(SettingsNavAction.SecurityOverview) },
        )
        SettingsListItem(
            icon = Icons.Outlined.Key,
            title = "API Keys",
            subtitle = "Manage provider credentials",
            onClick = { onNavigate(SettingsNavAction.ApiKeys) },
        )
        SettingsListItem(
            icon = Icons.Outlined.Security,
            title = "Autonomy Level",
            subtitle = settings.autonomyLevel,
            onClick = { onNavigate(SettingsNavAction.Autonomy) },
        )
        SettingsListItem(
            icon = Icons.Outlined.Forum,
            title = "Connected Channels",
            subtitle = "Telegram, Discord, Slack, and more",
            onClick = { onNavigate(SettingsNavAction.Channels) },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader(title = "Network")
        SettingsListItem(
            icon = Icons.Outlined.Hub,
            title = "Gateway & Pairing",
            subtitle =
                if (settings.gatewayRequirePairing) "Pairing required" else "Open access",
            onClick = { onNavigate(SettingsNavAction.Gateway) },
        )
        SettingsListItem(
            icon = Icons.Outlined.VpnKey,
            title = "Tunnel",
            subtitle = settings.tunnelProvider,
            onClick = { onNavigate(SettingsNavAction.Tunnel) },
        )
        SettingsListItem(
            icon = Icons.Outlined.Sync,
            title = "Plugin Registry",
            subtitle =
                if (settings.pluginSyncEnabled) "Auto-sync enabled" else "Manual only",
            onClick = { onNavigate(SettingsNavAction.PluginRegistry) },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader(title = "Advanced Configuration")
        SettingsListItem(
            icon = Icons.Outlined.Route,
            title = "Model Routes",
            subtitle = "Hint-based provider routing",
            onClick = { onNavigate(SettingsNavAction.ModelRoutes) },
        )
        SettingsListItem(
            icon = Icons.Outlined.Memory,
            title = "Memory Advanced",
            subtitle = "Embedding, hygiene, recall weights",
            onClick = { onNavigate(SettingsNavAction.MemoryAdvanced) },
        )
        SettingsListItem(
            icon = Icons.Outlined.Tune,
            title = "Tool Management",
            subtitle = "Browser, HTTP, Composio",
            onClick = { onNavigate(SettingsNavAction.ToolManagement) },
        )
        SettingsListItem(
            icon = Icons.Outlined.Schedule,
            title = "Scheduler & Heartbeat",
            subtitle =
                if (settings.schedulerEnabled) "Scheduler on" else "Scheduler off",
            onClick = { onNavigate(SettingsNavAction.Scheduler) },
        )
        SettingsListItem(
            icon = Icons.Outlined.Speed,
            title = "Observability",
            subtitle = settings.observabilityBackend,
            onClick = { onNavigate(SettingsNavAction.Observability) },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader(title = "Diagnostics")
        SettingsListItem(
            icon = Icons.AutoMirrored.Outlined.Subject,
            title = "Log Viewer",
            subtitle = "View daemon and service logs",
            onClick = { onNavigate(SettingsNavAction.LogViewer) },
        )
        SettingsListItem(
            icon = Icons.Outlined.HealthAndSafety,
            title = "ZeroClaw Doctor",
            subtitle = "Validate config, keys, and connectivity",
            onClick = { onNavigate(SettingsNavAction.Doctor) },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader(title = "Inspect & Browse")
        SettingsListItem(
            icon = Icons.Outlined.Token,
            title = "Tools Browser",
            subtitle = "View all available tools",
            onClick = { onNavigate(SettingsNavAction.ToolsBrowser) },
        )
        SettingsListItem(
            icon = Icons.Outlined.Psychology,
            title = "Memory Browser",
            subtitle = "Browse and search memory entries",
            onClick = { onNavigate(SettingsNavAction.MemoryBrowser) },
        )
        SettingsListItem(
            icon = Icons.Outlined.TaskAlt,
            title = "Scheduled Tasks",
            subtitle = "View and manage cron jobs",
            onClick = { onNavigate(SettingsNavAction.CronJobs) },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader(title = "App")
        SettingsListItem(
            icon = Icons.Outlined.DarkMode,
            title = "Theme",
            subtitle =
                when (settings.theme) {
                    ThemeMode.SYSTEM -> "System default"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                },
            onClick = { showThemeDialog = true },
        )
        SettingsListItem(
            icon = Icons.Outlined.Refresh,
            title = "Re-run Setup Wizard",
            subtitle = "Walk through initial configuration again",
            onClick = { showRerunDialog = true },
        )
        SettingsListItem(
            icon = Icons.Outlined.SystemUpdate,
            title = "Updates",
            subtitle = "Check for new versions",
            onClick = { onNavigate(SettingsNavAction.Updates) },
        )
        SettingsListItem(
            icon = Icons.Outlined.Info,
            title = "About",
            subtitle = "Version, licenses, links",
            onClick = { onNavigate(SettingsNavAction.About) },
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showThemeDialog) {
        ThemePickerDialog(
            currentTheme = settings.theme,
            onThemeSelected = { theme ->
                settingsViewModel.updateTheme(theme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
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
 * Dialog for picking the app theme from [ThemeMode] options.
 *
 * Displays three radio-button rows: System, Light, and Dark.
 *
 * @param currentTheme The currently active [ThemeMode].
 * @param onThemeSelected Called with the chosen [ThemeMode] when the user taps an option.
 * @param onDismiss Called when the dialog is dismissed without selection.
 */
@Composable
private fun ThemePickerDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Theme") },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    val label =
                        when (mode) {
                            ThemeMode.SYSTEM -> "System default"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = mode == currentTheme,
                                    onClick = { onThemeSelected(mode) },
                                    role = Role.RadioButton,
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mode == currentTheme,
                            onClick = null,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Banner shown when daemon-affecting settings have changed and a restart is required.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param onRestartDaemon Callback invoked when the user taps the restart button.
 */
@Composable
private fun RestartRequiredBanner(
    edgeMargin: Dp,
    onRestartDaemon: () -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = edgeMargin, vertical = 8.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Restart daemon to apply changes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = onRestartDaemon) {
                Text("Restart")
            }
        }
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
