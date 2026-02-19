/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.ui.component.SectionHeader
import com.zeroclaw.android.ui.component.SettingsToggleRow

/** Available autonomy levels matching upstream AutonomyLevel enum. */
private val AUTONOMY_LEVELS = listOf("readonly", "supervised", "full")

/**
 * Autonomy level and security policy configuration screen.
 *
 * Maps to the upstream `[autonomy]` TOML section: level picker,
 * workspace restriction, allowed commands, forbidden paths, action
 * limits, and risk approval toggles.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param settingsViewModel The shared [SettingsViewModel].
 * @param modifier Modifier applied to the root layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutonomyScreen(
    edgeMargin: Dp,
    settingsViewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    var levelExpanded by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SectionHeader(title = "Autonomy Level")

        ExposedDropdownMenuBox(
            expanded = levelExpanded,
            onExpandedChange = { levelExpanded = it },
        ) {
            OutlinedTextField(
                value = settings.autonomyLevel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Level") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(levelExpanded) },
                modifier =
                    Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = levelExpanded,
                onDismissRequest = { levelExpanded = false },
            ) {
                for (level in AUTONOMY_LEVELS) {
                    DropdownMenuItem(
                        text = { Text(level) },
                        onClick = {
                            settingsViewModel.updateAutonomyLevel(level)
                            levelExpanded = false
                        },
                    )
                }
            }
        }

        Text(
            text =
                when (settings.autonomyLevel) {
                    "readonly" -> "Agent can only read files and answer questions."
                    "full" -> "Agent has unrestricted access to tools and commands."
                    else -> "Agent asks for approval on risky actions."
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionHeader(title = "Workspace")

        SettingsToggleRow(
            title = "Workspace only",
            subtitle = "Restrict file access to the project workspace directory",
            checked = settings.workspaceOnly,
            onCheckedChange = { settingsViewModel.updateWorkspaceOnly(it) },
            contentDescription = "Workspace only restriction",
        )

        SectionHeader(title = "Commands")

        OutlinedTextField(
            value = settings.allowedCommands,
            onValueChange = { settingsViewModel.updateAllowedCommands(it) },
            label = { Text("Allowed commands") },
            supportingText = { Text("Comma-separated (e.g. git, npm, cargo)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = settings.forbiddenPaths,
            onValueChange = { settingsViewModel.updateForbiddenPaths(it) },
            label = { Text("Forbidden paths") },
            supportingText = { Text("Comma-separated (e.g. /etc, ~/.ssh)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeader(title = "Limits")

        OutlinedTextField(
            value = settings.maxActionsPerHour.toString(),
            onValueChange = { v -> v.toIntOrNull()?.let { settingsViewModel.updateMaxActionsPerHour(it) } },
            label = { Text("Max actions per hour") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = settings.maxCostPerDayCents.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.let { settingsViewModel.updateMaxCostPerDayCents(it) }
            },
            label = { Text("Max cost per day (cents)") },
            supportingText = {
                Text("Hard limit \u2014 blocks actions when exceeded")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeader(title = "Risk Management")

        SettingsToggleRow(
            title = "Require approval for medium risk",
            subtitle = "Prompt the user before executing medium-risk actions",
            checked = settings.requireApprovalMediumRisk,
            onCheckedChange = { settingsViewModel.updateRequireApprovalMediumRisk(it) },
            contentDescription = "Require approval for medium risk",
        )

        SettingsToggleRow(
            title = "Block high-risk commands",
            subtitle = "Prevent execution of dangerous shell commands entirely",
            checked = settings.blockHighRiskCommands,
            onCheckedChange = { settingsViewModel.updateBlockHighRiskCommands(it) },
            contentDescription = "Block high-risk commands",
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
