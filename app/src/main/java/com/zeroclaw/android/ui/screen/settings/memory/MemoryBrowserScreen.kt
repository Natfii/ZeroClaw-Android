// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.ui.screen.settings.memory

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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.model.MemoryEntry
import com.zeroclaw.android.ui.component.CategoryBadge
import com.zeroclaw.android.ui.component.EmptyState
import com.zeroclaw.android.ui.component.ErrorCard
import com.zeroclaw.android.ui.component.LoadingIndicator

/** Maximum number of content lines to show in a memory card. */
private const val CONTENT_MAX_LINES = 4

/** Ordered list of category filter options for the filter chip row. */
private val CATEGORY_FILTERS =
    listOf(CATEGORY_ALL, CATEGORY_CORE, CATEGORY_DAILY, CATEGORY_CONVERSATION)

/**
 * Screen for browsing and managing daemon memory entries.
 *
 * Displays a searchable, filterable list of memory entries from the
 * daemon's memory backend. Supports keyword search (recall), category
 * filtering via chips, and deleting entries. Shows a total entry count
 * in the header.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param memoryBrowserViewModel ViewModel providing memory state and actions.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun MemoryBrowserScreen(
    edgeMargin: Dp,
    memoryBrowserViewModel: MemoryBrowserViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by memoryBrowserViewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by memoryBrowserViewModel.searchQuery.collectAsStateWithLifecycle()
    val categoryFilter by memoryBrowserViewModel.categoryFilter.collectAsStateWithLifecycle()
    val totalCount by memoryBrowserViewModel.totalCount.collectAsStateWithLifecycle()
    val snackbarMessage by memoryBrowserViewModel.snackbarMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            memoryBrowserViewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = edgeMargin),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Memory Browser",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "$totalCount entries",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { memoryBrowserViewModel.updateSearch(it) },
                label = { Text("Search memories (keyword recall)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))

            CategoryFilterRow(
                selected = categoryFilter,
                onSelect = { memoryBrowserViewModel.setCategoryFilter(it) },
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (val state = uiState) {
                is MemoryUiState.Loading -> {
                    LoadingIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                is MemoryUiState.Error -> {
                    ErrorCard(
                        message = state.detail,
                        onRetry = { memoryBrowserViewModel.loadMemories() },
                    )
                }
                is MemoryUiState.Content -> {
                    if (state.data.isEmpty()) {
                        EmptyState(
                            icon = Icons.Outlined.Psychology,
                            message =
                                if (searchQuery.isBlank() && categoryFilter == CATEGORY_ALL) {
                                    "No memory entries. The daemon stores memories as it runs."
                                } else if (searchQuery.isNotBlank()) {
                                    "No memories match your search"
                                } else {
                                    "No entries in the \"$categoryFilter\" category"
                                },
                        )
                    } else {
                        MemoryList(
                            entries = state.data,
                            onForget = { memoryBrowserViewModel.forgetMemory(it) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Horizontal scrollable row of category filter chips.
 *
 * @param selected Currently selected category filter.
 * @param onSelect Callback when a chip is selected.
 */
@Composable
private fun CategoryFilterRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CATEGORY_FILTERS.forEach { category ->
            FilterChip(
                selected = category == selected,
                onClick = { onSelect(category) },
                label = {
                    Text(
                        text =
                            category.replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase() else it.toString()
                            },
                    )
                },
                modifier =
                    Modifier.semantics {
                        contentDescription = "Filter by category: $category"
                    },
            )
        }
    }
}

/**
 * Lazy column of memory entry cards.
 *
 * @param entries List of memory entries to display.
 * @param onForget Callback to delete a memory entry by key.
 */
@Composable
private fun MemoryList(
    entries: List<MemoryEntry>,
    onForget: (String) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = entries,
            key = { it.id },
            contentType = { "memory" },
        ) { entry ->
            MemoryCard(
                entry = entry,
                onForget = { onForget(entry.key) },
            )
        }
    }
}

/**
 * Card displaying a single memory entry.
 *
 * Shows the key, content (truncated), category badge, timestamp,
 * optional relevance score, and a delete button.
 *
 * @param entry The memory entry to display.
 * @param onForget Callback to delete this entry.
 * @param modifier Modifier applied to the card.
 */
@Composable
private fun MemoryCard(
    entry: MemoryEntry,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "Memory: ${entry.key}, category: ${entry.category}"
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.key,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    CategoryBadge(category = entry.category)
                }
                IconButton(
                    onClick = onForget,
                    modifier =
                        Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                contentDescription = "Delete memory: ${entry.key}"
                            },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = CONTENT_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.score?.let { score ->
                    Text(
                        text = "Score: %.2f".format(score),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
