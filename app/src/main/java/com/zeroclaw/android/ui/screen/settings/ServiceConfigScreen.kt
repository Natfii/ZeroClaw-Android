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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.ui.component.SectionHeader
import com.zeroclaw.android.ui.component.SettingsToggleRow

/** Maximum slider value for temperature. */
private const val TEMPERATURE_MAX = 2.0f

/** Number of steps for temperature slider. */
private const val TEMPERATURE_STEPS = 20

/** Minimum valid port number. */
private const val PORT_MIN = 1

/** Maximum valid port number. */
private const val PORT_MAX = 65535

/** Maximum provider retry count. */
private const val RETRIES_MAX = 10

/** Maximum cost warning threshold percentage. */
private const val WARN_PERCENT_MAX = 100

/** Available memory backend options. */
private val MEMORY_BACKENDS = listOf("sqlite", "none", "markdown", "lucid")

/**
 * Service configuration sub-screen for host, port, auto-start,
 * inference, memory, reliability, and cost settings.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param settingsViewModel The shared [SettingsViewModel].
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun ServiceConfigScreen(
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

        SectionHeader(title = "Network")

        OutlinedTextField(
            value = settings.host,
            onValueChange = { settingsViewModel.updateHost(it) },
            label = { Text("Host") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val portText = settings.port.toString()
        val portError = settings.port !in PORT_MIN..PORT_MAX

        OutlinedTextField(
            value = portText,
            onValueChange = { value ->
                value.toIntOrNull()?.let { settingsViewModel.updatePort(it) }
            },
            label = { Text("Port") },
            singleLine = true,
            isError = portError,
            supportingText =
                if (portError) {
                    { Text("Port must be between $PORT_MIN and $PORT_MAX") }
                } else {
                    null
                },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeader(title = "Startup")

        SettingsToggleRow(
            title = "Auto-start on boot",
            subtitle = "Start the daemon automatically after device reboot",
            checked = settings.autoStartOnBoot,
            onCheckedChange = { settingsViewModel.updateAutoStartOnBoot(it) },
            contentDescription = "Auto-start on boot",
        )

        InferenceSection(settings = settings, viewModel = settingsViewModel)
        MemorySection(settings = settings, viewModel = settingsViewModel)
        ReliabilitySection(settings = settings, viewModel = settingsViewModel)
        CostLimitsSection(settings = settings, viewModel = settingsViewModel)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Inference settings section: temperature slider and compact context toggle.
 *
 * @param settings Current application settings.
 * @param viewModel The [SettingsViewModel] for persisting changes.
 */
@Composable
private fun InferenceSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    SectionHeader(title = "Inference")

    Text(
        text = "Temperature: ${"%.1f".format(settings.defaultTemperature)}",
        style = MaterialTheme.typography.bodyLarge,
    )
    Slider(
        value = settings.defaultTemperature,
        onValueChange = { viewModel.updateDefaultTemperature(it) },
        valueRange = 0f..TEMPERATURE_MAX,
        steps = TEMPERATURE_STEPS - 1,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Temperature slider" },
    )

    SettingsToggleRow(
        title = "Compact context",
        subtitle = "Reduce token usage by compressing conversation context",
        checked = settings.compactContext,
        onCheckedChange = { viewModel.updateCompactContext(it) },
        contentDescription = "Compact context",
    )

    SettingsToggleRow(
        title = "Strip thinking tags",
        subtitle = "Client-side only — strips thinking tags from console display without affecting daemon behavior",
        checked = settings.stripThinkingTags,
        onCheckedChange = { viewModel.updateStripThinkingTags(it) },
        contentDescription = "Strip thinking tags from responses",
    )
}

/**
 * Memory backend dropdown section.
 *
 * @param settings Current application settings.
 * @param viewModel The [SettingsViewModel] for persisting changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemorySection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    SectionHeader(title = "Memory")

    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = settings.memoryBackend,
            onValueChange = {},
            readOnly = true,
            label = { Text("Backend") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier =
                Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            for (backend in MEMORY_BACKENDS) {
                DropdownMenuItem(
                    text = { Text(backend) },
                    onClick = {
                        viewModel.updateMemoryBackend(backend)
                        expanded = false
                    },
                )
            }
        }
    }

    SettingsToggleRow(
        title = "Auto-save",
        subtitle = "Automatically save conversation context to memory",
        checked = settings.memoryAutoSave,
        onCheckedChange = { viewModel.updateMemoryAutoSave(it) },
        contentDescription = "Memory auto-save",
    )
}

/**
 * Reliability settings section: retries and fallback providers.
 *
 * @param settings Current application settings.
 * @param viewModel The [SettingsViewModel] for persisting changes.
 */
@Composable
private fun ReliabilitySection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    SectionHeader(title = "Reliability")

    val retriesError = settings.providerRetries !in 0..RETRIES_MAX

    OutlinedTextField(
        value = settings.providerRetries.toString(),
        onValueChange = { value ->
            value.toIntOrNull()?.let { viewModel.updateProviderRetries(it) }
        },
        label = { Text("Provider retries") },
        singleLine = true,
        isError = retriesError,
        supportingText =
            if (retriesError) {
                { Text("Must be between 0 and $RETRIES_MAX") }
            } else {
                null
            },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = settings.fallbackProviders,
        onValueChange = { viewModel.updateFallbackProviders(it) },
        label = { Text("Fallback providers") },
        supportingText = { Text("Comma-separated (e.g. groq, anthropic)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Cost limits settings section: enable toggle, daily/monthly limits, and
 * warning threshold.
 *
 * @param settings Current application settings.
 * @param viewModel The [SettingsViewModel] for persisting changes.
 */
@Composable
private fun CostLimitsSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    SectionHeader(title = "Cost Limits")

    SettingsToggleRow(
        title = "Enable cost limits",
        subtitle = "Enforce daily and monthly spending caps",
        checked = settings.costEnabled,
        onCheckedChange = { viewModel.updateCostEnabled(it) },
        contentDescription = "Enable cost limits",
    )

    val dailyError = settings.costEnabled && settings.dailyLimitUsd < 0f

    OutlinedTextField(
        value = settings.dailyLimitUsd.toString(),
        onValueChange = { value ->
            value.toFloatOrNull()?.let { viewModel.updateDailyLimitUsd(it) }
        },
        label = { Text("Daily limit (USD)") },
        singleLine = true,
        isError = dailyError,
        supportingText =
            if (dailyError) {
                { Text("Must be a positive amount") }
            } else {
                null
            },
        enabled = settings.costEnabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )

    val monthlyError = settings.costEnabled && settings.monthlyLimitUsd < 0f

    OutlinedTextField(
        value = settings.monthlyLimitUsd.toString(),
        onValueChange = { value ->
            value.toFloatOrNull()?.let { viewModel.updateMonthlyLimitUsd(it) }
        },
        label = { Text("Monthly limit (USD)") },
        singleLine = true,
        isError = monthlyError,
        supportingText =
            if (monthlyError) {
                { Text("Must be a positive amount") }
            } else {
                null
            },
        enabled = settings.costEnabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )

    val warnError = settings.costEnabled && settings.costWarnAtPercent !in 0..WARN_PERCENT_MAX

    OutlinedTextField(
        value = settings.costWarnAtPercent.toString(),
        onValueChange = { value ->
            value.toIntOrNull()?.let { viewModel.updateCostWarnAtPercent(it) }
        },
        label = { Text("Warn at (%)") },
        singleLine = true,
        isError = warnError,
        supportingText =
            if (warnError) {
                { Text("Must be between 0 and $WARN_PERCENT_MAX") }
            } else {
                null
            },
        enabled = settings.costEnabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
