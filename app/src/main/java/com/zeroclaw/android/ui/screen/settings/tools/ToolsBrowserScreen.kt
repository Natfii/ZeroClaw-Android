// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.ui.screen.settings.tools

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.model.ToolSpec
import com.zeroclaw.android.ui.component.CategoryBadge
import com.zeroclaw.android.ui.component.EmptyState
import com.zeroclaw.android.ui.component.ErrorCard
import com.zeroclaw.android.ui.component.LoadingIndicator

/**
 * Screen that displays the full tool inventory from the daemon.
 *
 * Tools are listed in a searchable, filterable lazy column. Each tool card
 * shows the name, description, and source (built-in or skill name). A
 * horizontal row of filter chips allows filtering by source.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param toolsBrowserViewModel ViewModel providing tools state and actions.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun ToolsBrowserScreen(
    edgeMargin: Dp,
    toolsBrowserViewModel: ToolsBrowserViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val filteredState by toolsBrowserViewModel.filteredUiState.collectAsStateWithLifecycle()
    val searchQuery by toolsBrowserViewModel.searchQuery.collectAsStateWithLifecycle()
    val sourceFilter by toolsBrowserViewModel.sourceFilter.collectAsStateWithLifecycle()
    val sources by toolsBrowserViewModel.availableSources.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { toolsBrowserViewModel.updateSearch(it) },
            label = { Text("Search tools") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))

        SourceFilterRow(
            sources = sources,
            selected = sourceFilter,
            onSelect = { toolsBrowserViewModel.setSourceFilter(it) },
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (val state = filteredState) {
            is ToolsUiState.Loading -> {
                LoadingIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            is ToolsUiState.Error -> {
                ErrorCard(
                    message = state.detail,
                    onRetry = { toolsBrowserViewModel.loadTools() },
                )
            }
            is ToolsUiState.Content -> {
                if (state.data.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Build,
                        message =
                            if (searchQuery.isBlank() && sourceFilter == SOURCE_ALL) {
                                "No tools available. Start the daemon to see available tools."
                            } else {
                                "No tools match your filters"
                            },
                    )
                } else {
                    ToolsList(tools = state.data)
                }
            }
        }
    }
}

/**
 * Horizontal scrollable row of filter chips for selecting tool source.
 *
 * @param sources Available source filter values.
 * @param selected Currently selected source.
 * @param onSelect Callback when a chip is selected.
 */
@Composable
private fun SourceFilterRow(
    sources: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sources.forEach { source ->
            FilterChip(
                selected = source == selected,
                onClick = { onSelect(source) },
                label = { Text(source) },
                modifier =
                    Modifier.semantics {
                        contentDescription = "Filter by source: $source"
                    },
            )
        }
    }
}

/**
 * Lazy column of tool cards.
 *
 * @param tools List of tools to display.
 */
@Composable
private fun ToolsList(tools: List<ToolSpec>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = tools,
            key = { it.name },
            contentType = { "tool" },
        ) { tool ->
            ToolCard(tool = tool)
        }
    }
}

/**
 * Card displaying a single tool with its name, description, and source.
 *
 * @param tool The tool specification to display.
 * @param modifier Modifier applied to the card.
 */
@Composable
private fun ToolCard(
    tool: ToolSpec,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "${tool.name}: ${tool.description}, source: ${tool.source}"
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                CategoryBadge(category = tool.source)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
