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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.model.Agent
import com.zeroclaw.android.ui.component.EmptyState
import com.zeroclaw.android.ui.component.ProviderIcon

/**
 * Agent list and management screen with search and FAB for adding agents.
 *
 * Tapping an agent card navigates to the agent detail (edit) screen.
 *
 * @param onNavigateToDetail Callback to navigate to agent detail for editing.
 * @param onNavigateToAdd Callback to navigate to the add agent screen.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param agentsViewModel The [AgentsViewModel] for agent list state.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun AgentsScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAdd: () -> Unit,
    edgeMargin: Dp,
    agentsViewModel: AgentsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val agents by agentsViewModel.agents.collectAsStateWithLifecycle()
    val searchQuery by agentsViewModel.searchQuery.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                modifier =
                    Modifier.semantics {
                        contentDescription = "Add new connection"
                    },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = edgeMargin),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { agentsViewModel.updateSearch(it) },
                label = { Text("Search connections") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (agents.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.SmartToy,
                    message =
                        if (searchQuery.isBlank()) {
                            "No connections configured yet"
                        } else {
                            "No connections match your search"
                        },
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = agents,
                        key = { it.id },
                        contentType = { "agent" },
                    ) { agent ->
                        AgentListItem(
                            agent = agent,
                            onToggle = { agentsViewModel.toggleAgent(agent.id) },
                            onClick = { onNavigateToDetail(agent.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single agent row in the list with provider icon, name, and enable toggle.
 *
 * Tapping the card navigates to the agent detail (edit) screen.
 *
 * @param agent The agent to display.
 * @param onToggle Callback when the enable switch is toggled.
 * @param onClick Callback when the card is tapped (opens detail).
 */
@Composable
private fun AgentListItem(
    agent: Agent,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderIcon(provider = agent.provider)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${agent.provider} \u2022 ${agent.modelName}",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (agent.name.isNotBlank()) {
                    Text(
                        text = agent.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = agent.isEnabled,
                onCheckedChange = { onToggle() },
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "${agent.name} ${if (agent.isEnabled) "enabled" else "disabled"}"
                    },
            )
        }
    }
}
