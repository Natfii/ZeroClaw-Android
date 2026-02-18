/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.agents

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.model.Agent
import com.zeroclaw.android.ui.component.CollapsibleSection
import com.zeroclaw.android.ui.component.ModelSuggestionField
import com.zeroclaw.android.ui.component.ProviderDropdown

/** Spacing between form fields. */
private const val FIELD_SPACING_DP = 12

/** Standard section spacing. */
private const val SECTION_SPACING_DP = 16

/** Spacing after heading. */
private const val HEADING_SPACING_DP = 16

/** Bottom section spacing. */
private const val BOTTOM_SPACING_DP = 24

/** Small vertical spacing. */
private const val SMALL_SPACING_DP = 8

/** Channel item spacing. */
private const val CHANNEL_SPACING_DP = 4

/** Maximum slider value for per-agent temperature. */
private const val DETAIL_TEMPERATURE_MAX = 2.0f

/** Number of slider steps for temperature. */
private const val DETAIL_TEMPERATURE_STEPS = 20

/**
 * Agent detail screen with editable fields and collapsible sections.
 *
 * Uses [ProviderDropdown] for provider selection and [ModelSuggestionField]
 * for model entry with suggestions from the registry.
 *
 * @param agentId Unique identifier of the agent to display.
 * @param onSaved Callback invoked after saving changes.
 * @param onDeleted Callback invoked after deleting the agent.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param detailViewModel The [AgentDetailViewModel] for agent state.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun AgentDetailScreen(
    agentId: String,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    edgeMargin: Dp,
    detailViewModel: AgentDetailViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(agentId) {
        detailViewModel.loadAgent(agentId)
    }

    val agent by detailViewModel.agent.collectAsStateWithLifecycle()
    val loadedAgent = agent ?: return

    var name by remember(loadedAgent) { mutableStateOf(loadedAgent.name) }
    var providerId by remember(loadedAgent) { mutableStateOf(loadedAgent.provider) }
    var modelName by remember(loadedAgent) { mutableStateOf(loadedAgent.modelName) }
    var systemPrompt by remember(loadedAgent) { mutableStateOf(loadedAgent.systemPrompt) }
    var useGlobalTemperature by remember(loadedAgent) {
        mutableStateOf(loadedAgent.temperature == null)
    }
    var temperature by remember(loadedAgent) {
        mutableStateOf(loadedAgent.temperature ?: 0.7f)
    }
    var maxDepth by remember(loadedAgent) {
        mutableStateOf(loadedAgent.maxDepth.toString())
    }

    val providerInfo = ProviderRegistry.findById(providerId)
    val suggestedModels = providerInfo?.suggestedModels.orEmpty()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(HEADING_SPACING_DP.dp))

        Text(
            text = "Agent Details",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(HEADING_SPACING_DP.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))

        ProviderDropdown(
            selectedProviderId = providerId,
            onProviderSelected = { providerId = it.id },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))

        ModelSuggestionField(
            value = modelName,
            onValueChanged = { modelName = it },
            suggestions = suggestedModels,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(SECTION_SPACING_DP.dp))

        CollapsibleSection(title = "System Prompt") {
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System prompt") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(SECTION_SPACING_DP.dp))

        CollapsibleSection(title = "Advanced") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = useGlobalTemperature,
                    onCheckedChange = { useGlobalTemperature = it },
                )
                Text(
                    text = "Use global default temperature",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!useGlobalTemperature) {
                Text(
                    text = "Temperature: ${"%.1f".format(temperature)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0f..DETAIL_TEMPERATURE_MAX,
                    steps = DETAIL_TEMPERATURE_STEPS - 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Agent temperature" },
                )
            }
            Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))
            OutlinedTextField(
                value = maxDepth,
                onValueChange = { maxDepth = it },
                label = { Text("Max depth") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(SECTION_SPACING_DP.dp))

        CollapsibleSection(title = "Channels") {
            if (loadedAgent.channels.isEmpty()) {
                Text(
                    text = "No channels configured.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                loadedAgent.channels.forEach { channel ->
                    Text(
                        text = "${channel.type}: ${channel.endpoint}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(CHANNEL_SPACING_DP.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(BOTTOM_SPACING_DP.dp))

        FilledTonalButton(
            onClick = {
                detailViewModel.saveAgent(
                    loadedAgent.copy(
                        name = name,
                        provider = providerId,
                        modelName = modelName,
                        systemPrompt = systemPrompt,
                        temperature = if (useGlobalTemperature) null else temperature,
                        maxDepth = maxDepth.toIntOrNull() ?: Agent.DEFAULT_MAX_DEPTH,
                    ),
                )
                onSaved()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Changes")
        }
        Spacer(modifier = Modifier.height(SMALL_SPACING_DP.dp))
        OutlinedButton(
            onClick = {
                detailViewModel.deleteAgent(agentId)
                onDeleted()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Delete Agent",
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(BOTTOM_SPACING_DP.dp))
    }
}
