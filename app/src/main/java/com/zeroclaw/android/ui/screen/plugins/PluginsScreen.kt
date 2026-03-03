/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress("MatchingDeclarationName")

package com.zeroclaw.android.ui.screen.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.zeroclaw.android.model.Plugin
import com.zeroclaw.android.ui.component.CategoryBadge
import com.zeroclaw.android.ui.component.EmptyState
import com.zeroclaw.android.ui.component.OfficialPluginBadge
import com.zeroclaw.android.ui.component.PluginSectionHeader

/**
 * Aggregated state for the plugins content composable.
 *
 * @property plugins Filtered list of plugins for the current tab.
 * @property selectedTab Currently selected tab index.
 * @property searchQuery Current search query text.
 * @property syncState Current sync operation state.
 */
data class PluginsState(
    val plugins: List<Plugin>,
    val selectedTab: Int,
    val searchQuery: String,
    val syncState: SyncUiState,
)

/**
 * Plugin and skills management screen with Installed/Available/Skills tabs.
 *
 * Thin stateful wrapper that collects ViewModel flows and delegates
 * rendering to [PluginsContent].
 *
 * @param onNavigateToDetail Callback to navigate to plugin detail.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param pluginsViewModel The [PluginsViewModel] for plugin list state.
 * @param skillsViewModel The [SkillsViewModel] for skills list state.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun PluginsScreen(
    onNavigateToDetail: (String) -> Unit,
    edgeMargin: Dp,
    pluginsViewModel: PluginsViewModel = viewModel(),
    skillsViewModel: SkillsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val plugins by pluginsViewModel.plugins.collectAsStateWithLifecycle()
    val selectedTab by pluginsViewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by pluginsViewModel.searchQuery.collectAsStateWithLifecycle()
    val syncState by pluginsViewModel.syncState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        pluginsViewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    PluginsContent(
        state =
            PluginsState(
                plugins = plugins,
                selectedTab = selectedTab,
                searchQuery = searchQuery,
                syncState = syncState,
            ),
        edgeMargin = edgeMargin,
        snackbarHostState = snackbarHostState,
        onNavigateToDetail = onNavigateToDetail,
        onSelectTab = pluginsViewModel::selectTab,
        onSyncNow = pluginsViewModel::syncNow,
        onSearchChange = pluginsViewModel::updateSearch,
        onToggle = pluginsViewModel::togglePlugin,
        onInstall = pluginsViewModel::installPlugin,
        skillsTabContent = { SkillsTab(skillsViewModel = skillsViewModel) },
        toolsTabContent = { ToolsTab() },
        onRestoreDefaults = pluginsViewModel::restoreDefaults,
        modifier = modifier,
    )
}

/**
 * Stateless plugins content composable for testing.
 *
 * @param state Aggregated plugins state snapshot.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param snackbarHostState Snackbar host state for messages.
 * @param onNavigateToDetail Callback to navigate to plugin detail.
 * @param onSelectTab Callback when a tab is selected.
 * @param onSyncNow Callback to trigger a manual registry sync.
 * @param onSearchChange Callback when search text changes.
 * @param onToggle Callback when a plugin's enable switch is toggled.
 * @param onInstall Callback when a plugin's Install button is tapped.
 * @param skillsTabContent Slot for the skills tab content.
 * @param toolsTabContent Slot for the tools tab content.
 * @param onRestoreDefaults Callback to reset official plugins to defaults.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
internal fun PluginsContent(
    state: PluginsState,
    edgeMargin: Dp,
    snackbarHostState: SnackbarHostState,
    onNavigateToDetail: (String) -> Unit,
    onSelectTab: (Int) -> Unit,
    onSyncNow: () -> Unit,
    onSearchChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onInstall: (String) -> Unit,
    skillsTabContent: @Composable () -> Unit,
    toolsTabContent: @Composable () -> Unit,
    onRestoreDefaults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = edgeMargin),
        ) {
            PrimaryScrollableTabRow(
                selectedTabIndex = state.selectedTab,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 0.dp,
            ) {
                Tab(
                    selected = state.selectedTab == TAB_INSTALLED,
                    onClick = { onSelectTab(TAB_INSTALLED) },
                    text = {
                        Text(
                            "Installed",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                Tab(
                    selected = state.selectedTab == TAB_AVAILABLE,
                    onClick = { onSelectTab(TAB_AVAILABLE) },
                    text = {
                        Text(
                            "Hub",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                Tab(
                    selected = state.selectedTab == TAB_SKILLS,
                    onClick = { onSelectTab(TAB_SKILLS) },
                    text = {
                        Text(
                            "Skills",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                Tab(
                    selected = state.selectedTab == TAB_TOOLS,
                    onClick = { onSelectTab(TAB_TOOLS) },
                    text = {
                        Text(
                            "Tools",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
            if (state.selectedTab == TAB_INSTALLED ||
                state.selectedTab == TAB_AVAILABLE
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.selectedTab == TAB_INSTALLED) {
                        IconButton(
                            onClick = onRestoreDefaults,
                            modifier =
                                Modifier.semantics {
                                    contentDescription =
                                        "Restore official plugins to defaults"
                                },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = null,
                            )
                        }
                    }
                    IconButton(
                        onClick = onSyncNow,
                        enabled = state.syncState !is SyncUiState.Syncing,
                        modifier =
                            Modifier.semantics {
                                contentDescription = "Sync plugin registry"
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                        )
                    }
                }
            }

            if (state.syncState is SyncUiState.Syncing &&
                (state.selectedTab == TAB_INSTALLED || state.selectedTab == TAB_AVAILABLE)
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(modifier = Modifier.height(12.dp))

            when (state.selectedTab) {
                TAB_SKILLS -> skillsTabContent()
                TAB_TOOLS -> toolsTabContent()
                else ->
                    PluginTabContent(
                        plugins = state.plugins,
                        searchQuery = state.searchQuery,
                        selectedTab = state.selectedTab,
                        onSearchChange = onSearchChange,
                        onToggle = onToggle,
                        onInstall = onInstall,
                        onNavigateToDetail = onNavigateToDetail,
                    )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Content for the plugin tabs (Installed/Available).
 *
 * @param plugins Filtered plugin list for the current tab.
 * @param searchQuery Current search query text.
 * @param selectedTab Currently selected tab index.
 * @param onSearchChange Callback when search text changes.
 * @param onToggle Callback when a plugin's enable switch is toggled.
 * @param onInstall Callback when a plugin's Install button is tapped.
 * @param onNavigateToDetail Callback to navigate to plugin detail.
 */
@Composable
private fun PluginTabContent(
    plugins: List<Plugin>,
    searchQuery: String,
    selectedTab: Int,
    onSearchChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onInstall: (String) -> Unit,
    onNavigateToDetail: (String) -> Unit,
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
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
    } else if (selectedTab == TAB_INSTALLED) {
        val officialPlugins by remember(plugins) {
            derivedStateOf { plugins.filter { it.isOfficial } }
        }
        val communityPlugins by remember(plugins) {
            derivedStateOf { plugins.filter { !it.isOfficial } }
        }
        InstalledTabContent(
            officialPlugins = officialPlugins,
            communityPlugins = communityPlugins,
            onToggle = onToggle,
            onNavigateToDetail = onNavigateToDetail,
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
                val onInstallItem =
                    remember(plugin.id) {
                        { onInstall(plugin.id) }
                    }
                val onClickItem =
                    remember(plugin.id) {
                        { onNavigateToDetail(plugin.id) }
                    }
                PluginListItem(
                    plugin = plugin,
                    onToggle = {},
                    onInstall = onInstallItem,
                    onClick = onClickItem,
                )
            }
        }
    }
}

/**
 * Content for the Installed tab with two sections: Official Tools and
 * Installed Plugins.
 *
 * Uses [PluginSectionHeader] to separate the sections and
 * [OfficialPluginBadge] on official plugin items.
 *
 * @param officialPlugins Official built-in plugins.
 * @param communityPlugins Community-installed plugins.
 * @param onToggle Callback when a plugin's enable switch is toggled.
 * @param onNavigateToDetail Callback to navigate to plugin detail.
 */
@Composable
private fun InstalledTabContent(
    officialPlugins: List<Plugin>,
    communityPlugins: List<Plugin>,
    onToggle: (String) -> Unit,
    onNavigateToDetail: (String) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (officialPlugins.isNotEmpty()) {
            item(key = "header-official", contentType = "section-header") {
                PluginSectionHeader(
                    title = "Official Tools",
                    count = officialPlugins.size,
                )
            }
            items(
                items = officialPlugins,
                key = { it.id },
                contentType = { "official-plugin" },
            ) { plugin ->
                val onToggleItem = remember(plugin.id) { { onToggle(plugin.id) } }
                val onClickItem = remember(plugin.id) { { onNavigateToDetail(plugin.id) } }
                PluginListItem(
                    plugin = plugin,
                    onToggle = onToggleItem,
                    onInstall = {},
                    onClick = onClickItem,
                )
            }
        }
        if (communityPlugins.isNotEmpty()) {
            item(key = "header-community", contentType = "section-header") {
                PluginSectionHeader(
                    title = "Installed Plugins",
                    count = communityPlugins.size,
                )
            }
            items(
                items = communityPlugins,
                key = { it.id },
                contentType = { "community-plugin" },
            ) { plugin ->
                val onToggleItem = remember(plugin.id) { { onToggle(plugin.id) } }
                val onClickItem = remember(plugin.id) { { onNavigateToDetail(plugin.id) } }
                PluginListItem(
                    plugin = plugin,
                    onToggle = onToggleItem,
                    onInstall = {},
                    onClick = onClickItem,
                )
            }
        }
    }
}

/**
 * Single plugin row in the list.
 *
 * Shows an "Update available" badge when the plugin is installed and
 * a newer remote version exists. Shows [OfficialPluginBadge] for
 * official built-in plugins.
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
    val hasUpdate =
        plugin.isInstalled &&
            plugin.remoteVersion != null &&
            plugin.remoteVersion != plugin.version

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
                    if (plugin.isOfficial) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OfficialPluginBadge()
                    }
                    if (hasUpdate) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier =
                                Modifier.semantics {
                                    contentDescription =
                                        "Update available: ${plugin.remoteVersion}"
                                },
                        ) {
                            Badge {
                                Text("Update")
                            }
                        }
                    }
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
