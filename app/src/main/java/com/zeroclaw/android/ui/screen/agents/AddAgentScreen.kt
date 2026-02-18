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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.model.Agent
import com.zeroclaw.android.model.ProviderAuthType
import com.zeroclaw.android.ui.component.CollapsibleSection
import com.zeroclaw.android.ui.component.ModelSuggestionField
import com.zeroclaw.android.ui.component.ProviderDropdown
import java.util.UUID

/** Spacing between form fields. */
private const val FIELD_SPACING_DP = 12

/** Standard section spacing. */
private const val SECTION_SPACING_DP = 24

/** Padding inside the API key warning card. */
private const val WARNING_CARD_PADDING_DP = 12

/** Maximum slider value for per-agent temperature. */
private const val AGENT_TEMPERATURE_MAX = 2.0f

/** Number of slider steps for temperature. */
private const val AGENT_TEMPERATURE_STEPS = 20

/**
 * Screen for adding a new agent.
 *
 * Provides form fields for name, provider (dropdown), model (with suggestions),
 * and system prompt. Shows a warning card when the selected provider requires
 * an API key that has not been configured.
 *
 * @param onSaved Callback invoked after the agent is created.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param detailViewModel The [AgentDetailViewModel] for persisting the agent.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun AddAgentScreen(
    onSaved: () -> Unit,
    edgeMargin: Dp,
    detailViewModel: AgentDetailViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var providerId by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var useGlobalTemperature by remember { mutableStateOf(true) }
    var temperature by remember { mutableStateOf(0.7f) }
    var maxDepth by remember { mutableStateOf(Agent.DEFAULT_MAX_DEPTH.toString()) }

    val context = LocalContext.current
    val app = context.applicationContext as ZeroClawApplication
    val settings by app.settingsRepository.settings
        .collectAsStateWithLifecycle(
            initialValue = com.zeroclaw.android.model.AppSettings(),
        )
    val apiKeys by app.apiKeyRepository.keys
        .collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(settings.defaultProvider, settings.defaultModel) {
        if (providerId.isBlank() && settings.defaultProvider.isNotBlank()) {
            providerId = settings.defaultProvider
        }
        if (modelName.isBlank() && settings.defaultModel.isNotBlank()) {
            modelName = settings.defaultModel
        }
    }

    val providerInfo = ProviderRegistry.findById(providerId)
    val suggestedModels = providerInfo?.suggestedModels.orEmpty()
    val needsApiKey = providerInfo?.authType == ProviderAuthType.API_KEY_ONLY ||
        providerInfo?.authType == ProviderAuthType.URL_AND_OPTIONAL_KEY
    val hasApiKey = providerId.isBlank() || apiKeys.any { key ->
        val resolved = ProviderRegistry.findById(key.provider)
        resolved?.id == providerInfo?.id
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))

        Text(
            text = "Add Agent",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))

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
            onProviderSelected = {
                providerId = it.id
                if (modelName.isBlank()) {
                    modelName = it.suggestedModels.firstOrNull().orEmpty()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))

        if (needsApiKey && !hasApiKey && providerId.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "No API key found for ${providerInfo?.displayName ?: providerId}. " +
                        "Add one in Settings > API Keys.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(WARNING_CARD_PADDING_DP.dp),
                )
            }
            Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))
        }

        ModelSuggestionField(
            value = modelName,
            onValueChanged = { modelName = it },
            suggestions = suggestedModels,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))

        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it },
            label = { Text("System Prompt (optional)") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
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
                    valueRange = 0f..AGENT_TEMPERATURE_MAX,
                    steps = AGENT_TEMPERATURE_STEPS - 1,
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

        FilledTonalButton(
            onClick = {
                detailViewModel.saveAgent(
                    Agent(
                        id = UUID.randomUUID().toString(),
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
            enabled = name.isNotBlank() && providerId.isNotBlank() && modelName.isNotBlank(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics { contentDescription = "Create agent" },
        ) {
            Text("Create Agent")
        }
        Spacer(modifier = Modifier.height(SECTION_SPACING_DP.dp))
    }
}
