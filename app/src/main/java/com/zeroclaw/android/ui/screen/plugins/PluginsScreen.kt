/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.plugins

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
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.zeroclaw.android.model.Plugin
import com.zeroclaw.android.ui.component.CategoryBadge
import com.zeroclaw.android.ui.component.EmptyState

/**
 * Plugin list and management screen with Installed/Available tabs.
 *
 * @param onNavigateToDetail Callback to navigate to plugin detail.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param pluginsViewModel The [PluginsViewModel] for plugin list state.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun PluginsScreen(
    onNavigateToDetail: (String) -> Unit,
    edgeMargin: Dp,
    pluginsViewModel: PluginsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val plugins by pluginsViewModel.plugins.collectAsStateWithLifecycle()
    val selectedTab by pluginsViewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by pluginsViewModel.searchQuery.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin),
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == TAB_INSTALLED,
                onClick = { pluginsViewModel.selectTab(TAB_INSTALLED) },
                text = { Text("Installed") },
            )
            Tab(
                selected = selectedTab == TAB_AVAILABLE,
                onClick = { pluginsViewModel.selectTab(TAB_AVAILABLE) },
                text = { Text("Available") },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { pluginsViewModel.updateSearch(it) },
            label = { Text("Search plugins") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (plugins.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Extension,
                message =
                    if (searchQuery.isBlank()) {
                        if (selectedTab == TAB_INSTALLED) {
                            "No plugins installed yet"
                        } else {
                            "All plugins are installed"
                        }
                    } else {
                        "No plugins match your search"
                    },
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = plugins,
                    key = { it.id },
                    contentType = { "plugin" },
                ) { plugin ->
                    PluginListItem(
                        plugin = plugin,
                        onToggle = { pluginsViewModel.togglePlugin(plugin.id) },
                        onInstall = { pluginsViewModel.installPlugin(plugin.id) },
                        onClick = { onNavigateToDetail(plugin.id) },
                    )
                }
            }
        }
    }
}

/**
 * Single plugin row in the list.
 *
 * @param plugin The plugin to display.
 * @param onToggle Callback when the enable switch is toggled.
 * @param onInstall Callback when the Install button is tapped.
 * @param onClick Callback when the row is tapped.
 */
@Composable
private fun PluginListItem(
    plugin: Plugin,
    onToggle: () -> Unit,
    onInstall: () -> Unit,
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
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    CategoryBadge(category = plugin.category)
                }
                Text(
                    text = plugin.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                Text(
                    text = "v${plugin.version} \u2022 ${plugin.author}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (plugin.isInstalled) {
                Switch(
                    checked = plugin.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "${plugin.name} ${if (plugin.isEnabled) "enabled" else "disabled"}"
                        },
                )
            } else {
                FilledTonalButton(onClick = onInstall) {
                    Text("Install")
                }
            }
        }
    }
}
