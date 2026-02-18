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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.data.remote.ModelFetcher
import com.zeroclaw.android.model.Agent
import com.zeroclaw.android.model.ModelListFormat
import com.zeroclaw.android.model.ProviderAuthType
import com.zeroclaw.android.ui.component.CollapsibleSection
import com.zeroclaw.android.ui.component.ConnectionPickerSection
import com.zeroclaw.android.ui.component.ModelSuggestionField
import com.zeroclaw.android.ui.component.ProviderDropdown
import java.util.UUID

/** Spacing between form fields. */
private const val FIELD_SPACING_DP = 12

/** Standard section spacing. */
private const val SECTION_SPACING_DP = 24

/** Maximum slider value for per-agent temperature. */
private const val AGENT_TEMPERATURE_MAX = 2.0f

/** Number of slider steps for temperature. */
private const val AGENT_TEMPERATURE_STEPS = 20

/** Default temperature value for new agents. */
private const val DEFAULT_AGENT_TEMPERATURE = 0.7f

/**
 * Screen for adding a new agent.
 *
 * Provides form fields for name, connection picker (with provider fallback),
 * model (with suggestions), and system prompt.
 *
 * @param onSaved Callback invoked after the agent is created.
 * @param onNavigateToAddConnection Callback to navigate to the API key add screen.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param detailViewModel The [AgentDetailViewModel] for persisting the agent.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun AddAgentScreen(
    onSaved: () -> Unit,
    onNavigateToAddConnection: () -> Unit,
    edgeMargin: Dp,
    detailViewModel: AgentDetailViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var providerId by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var useGlobalTemperature by remember { mutableStateOf(true) }
    var temperature by remember { mutableStateOf(DEFAULT_AGENT_TEMPERATURE) }
    var maxDepth by remember { mutableStateOf(Agent.DEFAULT_MAX_DEPTH.toString()) }
    var selectedConnectionId by remember { mutableStateOf<String?>(null) }

    val apiKeys by detailViewModel.apiKeys.collectAsStateWithLifecycle()

    val providerInfo = ProviderRegistry.findById(providerId)
    val suggestedModels = providerInfo?.suggestedModels.orEmpty()

    var liveModels by remember { mutableStateOf(emptyList<String>()) }
    var isLoadingLive by remember { mutableStateOf(false) }
    var isLiveData by remember { mutableStateOf(false) }

    val selectedKey = apiKeys.firstOrNull { it.id == selectedConnectionId }

    LaunchedEffect(providerId, selectedConnectionId, apiKeys) {
        liveModels = emptyList()
        isLiveData = false
        val info = ProviderRegistry.findById(providerId) ?: return@LaunchedEffect
        if (info.modelListFormat == ModelListFormat.NONE) return@LaunchedEffect
        val key = selectedKey
        val apiKeyValue = key?.key.orEmpty()
        val baseUrlValue = key?.baseUrl.orEmpty()
        val isLocal =
            info.authType == ProviderAuthType.URL_ONLY ||
                info.authType == ProviderAuthType.URL_AND_OPTIONAL_KEY
        if (!isLocal && apiKeyValue.isBlank()) return@LaunchedEffect
        isLoadingLive = true
        val result = ModelFetcher.fetchModels(info, apiKeyValue, baseUrlValue)
        isLoadingLive = false
        result.onSuccess { models ->
            liveModels = models
            isLiveData = true
        }
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
            text = "Add Connection",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nickname") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))

        ProviderDropdown(
            selectedProviderId = providerId,
            onProviderSelected = {
                providerId = it.id
                selectedConnectionId = null
                modelName = it.suggestedModels.firstOrNull().orEmpty()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))

        ConnectionPickerSection(
            keys = apiKeys,
            selectedKeyId = selectedConnectionId,
            onKeySelected = { key ->
                selectedConnectionId = key.id
                val resolved = ProviderRegistry.findById(key.provider)
                providerId = resolved?.id ?: key.provider
                modelName = resolved?.suggestedModels?.firstOrNull().orEmpty()
            },
            onAddNewConnection = onNavigateToAddConnection,
        )
        Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))

        ModelSuggestionField(
            value = modelName,
            onValueChanged = { modelName = it },
            suggestions = suggestedModels,
            liveSuggestions = liveModels,
            isLoadingLive = isLoadingLive,
            isLiveData = isLiveData,
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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Temperature" },
                )
            }
            Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))

            val maxDepthValue = maxDepth.toIntOrNull()
            val maxDepthError = maxDepth.isNotEmpty() && (maxDepthValue == null || maxDepthValue < 1)

            OutlinedTextField(
                value = maxDepth,
                onValueChange = { maxDepth = it },
                label = { Text("Max depth") },
                singleLine = true,
                isError = maxDepthError,
                supportingText =
                    if (maxDepthError) {
                        { Text("Must be a positive integer") }
                    } else {
                        null
                    },
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
                    .semantics { contentDescription = "Create connection" },
        ) {
            Text("Create Connection")
        }
        Spacer(modifier = Modifier.height(SECTION_SPACING_DP.dp))
    }
}
