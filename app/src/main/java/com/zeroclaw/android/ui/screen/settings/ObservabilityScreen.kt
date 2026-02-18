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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.ui.component.SectionHeader

/** Available observability backends matching upstream options. */
private val OBS_BACKENDS = listOf("none", "log", "otel")

/**
 * Observability backend configuration screen.
 *
 * Maps to the upstream `[observability]` TOML section: backend selection
 * and OpenTelemetry collector endpoint/service name for distributed tracing.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param settingsViewModel The shared [SettingsViewModel].
 * @param modifier Modifier applied to the root layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservabilityScreen(
    edgeMargin: Dp,
    settingsViewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    var backendExpanded by remember { mutableStateOf(false) }
    val isOtel = settings.observabilityBackend == "otel"

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SectionHeader(title = "Observability Backend")

        ExposedDropdownMenuBox(
            expanded = backendExpanded,
            onExpandedChange = { backendExpanded = it },
        ) {
            OutlinedTextField(
                value = settings.observabilityBackend,
                onValueChange = {},
                readOnly = true,
                label = { Text("Backend") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(backendExpanded) },
                modifier =
                    Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = backendExpanded,
                onDismissRequest = { backendExpanded = false },
            ) {
                for (backend in OBS_BACKENDS) {
                    DropdownMenuItem(
                        text = { Text(backend) },
                        onClick = {
                            settingsViewModel.updateObservabilityBackend(backend)
                            backendExpanded = false
                        },
                    )
                }
            }
        }

        Text(
            text =
                when (settings.observabilityBackend) {
                    "log" -> "Events are written to the daemon log output."
                    "otel" -> "Events are exported to an OpenTelemetry collector."
                    else -> "No observability backend is active."
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (isOtel) {
            SectionHeader(title = "OpenTelemetry")

            OutlinedTextField(
                value = settings.observabilityOtelEndpoint,
                onValueChange = { settingsViewModel.updateObservabilityOtelEndpoint(it) },
                label = { Text("Collector endpoint") },
                supportingText = { Text("OTLP HTTP endpoint (e.g. http://localhost:4318)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = settings.observabilityOtelServiceName,
                onValueChange = { settingsViewModel.updateObservabilityOtelServiceName(it) },
                label = { Text("Service name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
