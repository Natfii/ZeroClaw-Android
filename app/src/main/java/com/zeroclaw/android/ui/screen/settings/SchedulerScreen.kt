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
 * Scheduler and heartbeat configuration screen.
 *
 * Maps to upstream `[scheduler]` and `[heartbeat]` TOML sections:
 * task scheduling limits and periodic heartbeat tick configuration.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param settingsViewModel The shared [SettingsViewModel].
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun SchedulerScreen(
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

        SectionHeader(title = "Task Scheduler")

        SchedulerToggle(
            title = "Enable scheduler",
            subtitle = "Allow cron-style scheduled tasks",
            checked = settings.schedulerEnabled,
            onCheckedChange = { settingsViewModel.updateSchedulerEnabled(it) },
            description = "Enable task scheduler",
        )

        OutlinedTextField(
            value = settings.schedulerMaxTasks.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.let { settingsViewModel.updateSchedulerMaxTasks(it) }
            },
            label = { Text("Max tasks") },
            singleLine = true,
            enabled = settings.schedulerEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = settings.schedulerMaxConcurrent.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.let { settingsViewModel.updateSchedulerMaxConcurrent(it) }
            },
            label = { Text("Max concurrent") },
            singleLine = true,
            enabled = settings.schedulerEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeader(title = "Heartbeat")

        SchedulerToggle(
            title = "Enable heartbeat",
            subtitle = "Periodic heartbeat ticks for keep-alive and monitoring",
            checked = settings.heartbeatEnabled,
            onCheckedChange = { settingsViewModel.updateHeartbeatEnabled(it) },
            description = "Enable heartbeat",
        )

        OutlinedTextField(
            value = settings.heartbeatIntervalMinutes.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.let { settingsViewModel.updateHeartbeatIntervalMinutes(it) }
            },
            label = { Text("Interval (minutes)") },
            singleLine = true,
            enabled = settings.heartbeatEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Toggle row used on the scheduler screen.
 *
 * @param title Primary label text.
 * @param subtitle Descriptive text below the title.
 * @param checked Current toggle state.
 * @param onCheckedChange Callback for state changes.
 * @param description Accessibility content description.
 */
@Composable
private fun SchedulerToggle(
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
