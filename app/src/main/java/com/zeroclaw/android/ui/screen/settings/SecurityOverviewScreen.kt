/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.data.StorageHealth
import com.zeroclaw.android.ui.component.CollapsibleSection
import com.zeroclaw.android.ui.component.SectionHeader
import com.zeroclaw.android.ui.component.SettingsToggleRow
import com.zeroclaw.android.util.BiometricGatekeeper

/**
 * Security overview screen displaying the current security posture.
 *
 * Shows a summary of autonomy settings, API key health, and active
 * security policies. Each section has a colored status indicator
 * (green/amber/red) based on the current configuration.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param settingsViewModel ViewModel providing current settings state.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun SecurityOverviewScreen(
    edgeMargin: Dp,
    settingsViewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val application = LocalContext.current.applicationContext as ZeroClawApplication
    val apiKeys by application.apiKeyRepository.keys.collectAsStateWithLifecycle(initialValue = emptyList())
    val storageHealth = application.apiKeyRepository.storageHealth

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(SPACING_SMALL))

        AutonomyLevelCard(autonomyLevel = settings.autonomyLevel)

        Spacer(modifier = Modifier.height(SPACING_LARGE))

        SectionHeader(title = "Access Controls")
        AccessControlItem(
            label = "Workspace restriction",
            enabled = settings.workspaceOnly,
            enabledDescription = "Enabled",
            disabledDescription = "Disabled",
            disabledSeverity = DisabledSeverity.WARNING,
        )
        AccessControlItem(
            label = "High-risk command blocking",
            enabled = settings.blockHighRiskCommands,
            enabledDescription = "Enabled",
            disabledDescription = "Disabled",
            disabledSeverity = DisabledSeverity.CRITICAL,
        )
        AccessControlItem(
            label = "Medium-risk approval required",
            enabled = settings.requireApprovalMediumRisk,
            enabledDescription = "Enabled",
            disabledDescription = "Disabled",
            disabledSeverity = DisabledSeverity.WARNING,
        )

        Spacer(modifier = Modifier.height(SPACING_LARGE))

        SectionHeader(title = "Rate Limits")
        RateLimitItem(
            label = "Max actions per hour",
            value = settings.maxActionsPerHour.toString(),
        )
        RateLimitItem(
            label = "Max cost per day",
            value = formatCentsToDollars(settings.maxCostPerDayCents),
        )

        Spacer(modifier = Modifier.height(SPACING_LARGE))

        SectionHeader(title = "API Key Health")
        ApiKeyHealthSection(
            keyCount = apiKeys.size,
            storageHealth = storageHealth,
        )

        Spacer(modifier = Modifier.height(SPACING_LARGE))

        BiometricProtectionSection(
            biometricForService = settings.biometricForService,
            biometricForSettings = settings.biometricForSettings,
            onBiometricForServiceChange = { settingsViewModel.updateBiometricForService(it) },
            onBiometricForSettingsChange = { settingsViewModel.updateBiometricForSettings(it) },
        )

        Spacer(modifier = Modifier.height(SPACING_LARGE))

        AllowedForbiddenSection(
            allowedCommands = settings.allowedCommands,
            forbiddenPaths = settings.forbiddenPaths,
        )

        Spacer(modifier = Modifier.height(SPACING_LARGE))
    }
}

/**
 * Prominent card displaying the current autonomy level with a colored indicator.
 *
 * @param autonomyLevel The current autonomy level string: "readonly", "supervised", or "full".
 */
@Composable
private fun AutonomyLevelCard(autonomyLevel: String) {
    val color = autonomyLevelColor(autonomyLevel)
    val description = autonomyLevelDescription(autonomyLevel)
    val displayName = autonomyLevelDisplayName(autonomyLevel)
    val a11yDescription = "Autonomy level: $displayName. $description"

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = a11yDescription
                    liveRegion = LiveRegionMode.Polite
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Row(
            modifier = Modifier.padding(CARD_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(STATUS_DOT_SIZE)
                        .clip(CircleShape)
                        .background(color),
            )
            Spacer(modifier = Modifier.width(SPACING_MEDIUM))
            Column {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                )
                Spacer(modifier = Modifier.height(SPACING_TINY))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Single access control item with a status dot indicating enabled/disabled state.
 *
 * @param label Display name of the access control setting.
 * @param enabled Whether the control is currently active.
 * @param enabledDescription Accessibility text when enabled.
 * @param disabledDescription Accessibility text when disabled.
 * @param disabledSeverity Severity level when the control is disabled.
 */
@Composable
private fun AccessControlItem(
    label: String,
    enabled: Boolean,
    enabledDescription: String,
    disabledDescription: String,
    disabledSeverity: DisabledSeverity,
) {
    val color =
        if (enabled) {
            statusGreen()
        } else {
            when (disabledSeverity) {
                DisabledSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                DisabledSeverity.CRITICAL -> MaterialTheme.colorScheme.error
            }
        }
    val statusText = if (enabled) enabledDescription else disabledDescription
    val a11yDescription = "$label: $statusText"

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = SPACING_SMALL)
                .semantics(mergeDescendants = true) {
                    contentDescription = a11yDescription
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(STATUS_DOT_SIZE_SMALL)
                    .clip(CircleShape)
                    .background(color),
        )
        Spacer(modifier = Modifier.width(SPACING_MEDIUM))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/**
 * Displays a rate limit entry with label and value.
 *
 * @param label Description of the rate limit.
 * @param value Formatted value string to display.
 */
@Composable
private fun RateLimitItem(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = SPACING_SMALL)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$label: $value"
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Section displaying API key storage health and stored key count.
 *
 * @param keyCount Number of API keys currently stored.
 * @param storageHealth Current health state of the encrypted key storage backend.
 */
@Composable
private fun ApiKeyHealthSection(
    keyCount: Int,
    storageHealth: StorageHealth,
) {
    val healthLabel = storageHealthLabel(storageHealth)
    val healthColor = storageHealthColor(storageHealth)
    val a11yDescription = "API key storage: $healthLabel. $keyCount keys stored"

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = a11yDescription
                    liveRegion = LiveRegionMode.Polite
                },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = SPACING_SMALL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(STATUS_DOT_SIZE_SMALL)
                        .clip(CircleShape)
                        .background(healthColor),
            )
            Spacer(modifier = Modifier.width(SPACING_MEDIUM))
            Text(
                text = "Storage: $healthLabel",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = healthLabel,
                style = MaterialTheme.typography.labelMedium,
                color = healthColor,
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = SPACING_SMALL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                modifier = Modifier.width(STATUS_DOT_SIZE_SMALL + SPACING_MEDIUM),
            )
            Text(
                text = "$keyCount keys stored",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Collapsible sections displaying allowed commands and forbidden paths.
 *
 * Each entry is rendered as a suggestion chip for easy scanning.
 *
 * @param allowedCommands Comma-separated list of allowed shell commands.
 * @param forbiddenPaths Comma-separated list of forbidden filesystem paths.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllowedForbiddenSection(
    allowedCommands: String,
    forbiddenPaths: String,
) {
    val commands = remember(allowedCommands) { parseCommaSeparated(allowedCommands) }
    val paths = remember(forbiddenPaths) { parseCommaSeparated(forbiddenPaths) }

    CollapsibleSection(title = "Allowed Commands (${commands.size})") {
        if (commands.isEmpty()) {
            Text(
                text = "No commands configured",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL),
                verticalArrangement = Arrangement.spacedBy(SPACING_TINY),
            ) {
                commands.forEach { command ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(command) },
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(SPACING_MEDIUM))

    CollapsibleSection(title = "Forbidden Paths (${paths.size})") {
        if (paths.isEmpty()) {
            Text(
                text = "No paths configured",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL),
                verticalArrangement = Arrangement.spacedBy(SPACING_TINY),
            ) {
                paths.forEach { path ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(path) },
                    )
                }
            }
        }
    }
}

/**
 * Section displaying biometric protection toggles for service and settings.
 *
 * Toggles are disabled with helper text when biometric hardware is
 * not available or not enrolled on the device.
 *
 * @param biometricForService Whether biometric is required for service control.
 * @param biometricForSettings Whether biometric is required for sensitive settings.
 * @param onBiometricForServiceChange Callback for service toggle changes.
 * @param onBiometricForSettingsChange Callback for settings toggle changes.
 */
@Composable
private fun BiometricProtectionSection(
    biometricForService: Boolean,
    biometricForSettings: Boolean,
    onBiometricForServiceChange: (Boolean) -> Unit,
    onBiometricForSettingsChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val isBiometricAvailable = BiometricGatekeeper.isAvailable(context)

    SectionHeader(title = "Biometric Protection")

    SettingsToggleRow(
        title = "Require biometric for service control",
        subtitle = "Authenticate before starting or stopping the daemon",
        checked = biometricForService,
        onCheckedChange = onBiometricForServiceChange,
        contentDescription = "Require biometric for service control",
        enabled = isBiometricAvailable,
    )

    SettingsToggleRow(
        title = "Require biometric for sensitive settings",
        subtitle = "Authenticate before modifying security-related settings",
        checked = biometricForSettings,
        onCheckedChange = onBiometricForSettingsChange,
        contentDescription = "Require biometric for sensitive settings",
        enabled = isBiometricAvailable,
    )

    if (!isBiometricAvailable) {
        Text(
            text = "Biometric hardware not available or not enrolled",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = SPACING_SMALL),
        )
    }
}

/**
 * Resolves the display color for the given autonomy level.
 *
 * @return Green for "readonly", tertiary/amber for "supervised", error/red for "full".
 */
@Composable
private fun autonomyLevelColor(level: String): Color =
    when (level) {
        AUTONOMY_READONLY -> statusGreen()
        AUTONOMY_SUPERVISED -> MaterialTheme.colorScheme.tertiary
        AUTONOMY_FULL -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

/**
 * Returns a human-readable description of the autonomy level.
 *
 * @return Description string explaining what the level permits.
 */
private fun autonomyLevelDescription(level: String): String =
    when (level) {
        AUTONOMY_READONLY -> "Agent can only read files"
        AUTONOMY_SUPERVISED -> "Agent asks for approval"
        AUTONOMY_FULL -> "Unrestricted access"
        else -> "Unknown autonomy level"
    }

/**
 * Returns a formatted display name for the autonomy level.
 *
 * @return Capitalized level name suitable for UI display.
 */
private fun autonomyLevelDisplayName(level: String): String =
    when (level) {
        AUTONOMY_READONLY -> "Read-only"
        AUTONOMY_SUPERVISED -> "Supervised"
        AUTONOMY_FULL -> "Full"
        else -> level.replaceFirstChar { it.uppercase() }
    }

/**
 * Returns the display label for the given storage health state.
 *
 * @return "Healthy", "Degraded", or "Error" string.
 */
private fun storageHealthLabel(health: StorageHealth): String =
    when (health) {
        is StorageHealth.Healthy -> "Healthy"
        is StorageHealth.Recovered -> "Recovered"
        is StorageHealth.Degraded -> "Degraded"
    }

/**
 * Returns the status color for the given storage health state.
 *
 * @return Green for healthy, tertiary/amber for recovered, error/red for degraded.
 */
@Composable
private fun storageHealthColor(health: StorageHealth): Color =
    when (health) {
        is StorageHealth.Healthy -> statusGreen()
        is StorageHealth.Recovered -> MaterialTheme.colorScheme.tertiary
        is StorageHealth.Degraded -> MaterialTheme.colorScheme.error
    }

/**
 * Returns the Material 3 primary color, used as the "green" status indicator.
 *
 * The primary color in M3 dynamic theming provides sufficient contrast
 * against surface backgrounds while harmonizing with the overall palette.
 *
 * @return The [MaterialTheme] primary color.
 */
@Composable
private fun statusGreen(): Color = MaterialTheme.colorScheme.primary

/**
 * Formats a cent amount as a dollar string.
 *
 * @return Formatted string such as "$5.00".
 */
private fun formatCentsToDollars(cents: Int): String {
    val dollars = cents / CENTS_PER_DOLLAR
    val remainder = cents % CENTS_PER_DOLLAR
    return "$$dollars.${remainder.toString().padStart(DECIMAL_PLACES, '0')}"
}

/**
 * Parses a comma-separated string into a list of trimmed, non-empty entries.
 *
 * @return List of individual entries with leading/trailing whitespace removed.
 */
private fun parseCommaSeparated(value: String): List<String> =
    value
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/**
 * Severity level for a disabled access control, determining the status dot color.
 *
 * [WARNING] uses tertiary/amber to indicate a non-optimal but acceptable state.
 * [CRITICAL] uses error/red to indicate a significant security gap.
 */
private enum class DisabledSeverity {
    /** Non-optimal but acceptable state (tertiary/amber). */
    WARNING,

    /** Significant security gap (error/red). */
    CRITICAL,
}

/** Autonomy level constant for read-only mode. */
private const val AUTONOMY_READONLY = "readonly"

/** Autonomy level constant for supervised mode. */
private const val AUTONOMY_SUPERVISED = "supervised"

/** Autonomy level constant for full/unrestricted mode. */
private const val AUTONOMY_FULL = "full"

/** Number of cents in one dollar for formatting. */
private const val CENTS_PER_DOLLAR = 100

/** Number of decimal places for dollar formatting. */
private const val DECIMAL_PLACES = 2

/** Tiny spacing between closely related elements (4dp). */
private val SPACING_TINY = 4.dp

/** Small spacing for list item vertical padding (8dp). */
private val SPACING_SMALL = 8.dp

/** Medium spacing between grouped elements (12dp). */
private val SPACING_MEDIUM = 12.dp

/** Large spacing between sections (16dp). */
private val SPACING_LARGE = 16.dp

/** Internal padding for card content (16dp). */
private val CARD_PADDING = 16.dp

/** Size of the status dot in the autonomy card (12dp). */
private val STATUS_DOT_SIZE = 12.dp

/** Size of the status dot in list items (8dp). */
private val STATUS_DOT_SIZE_SMALL = 8.dp
