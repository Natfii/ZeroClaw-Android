/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.plugins

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.ui.component.CategoryBadge
import com.zeroclaw.android.ui.component.CollapsibleSection

/**
 * Plugin detail screen showing full information, install/enable controls,
 * and configuration fields.
 *
 * @param pluginId Unique identifier of the plugin to display.
 * @param onBack Callback to navigate back.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param detailViewModel The [PluginDetailViewModel] for plugin state.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun PluginDetailScreen(
    pluginId: String,
    onBack: () -> Unit,
    edgeMargin: Dp,
    detailViewModel: PluginDetailViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(pluginId) {
        detailViewModel.loadPlugin(pluginId)
    }

    val plugin by detailViewModel.plugin.collectAsStateWithLifecycle()
    val loadedPlugin = plugin ?: return

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = loadedPlugin.name,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(category = loadedPlugin.category)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "v${loadedPlugin.version} by ${loadedPlugin.author}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = loadedPlugin.description,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (loadedPlugin.isInstalled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Enabled",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = loadedPlugin.isEnabled,
                    onCheckedChange = { detailViewModel.toggleEnabled(pluginId) },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "${loadedPlugin.name} " +
                                    if (loadedPlugin.isEnabled) "enabled" else "disabled"
                        },
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (loadedPlugin.isInstalled && loadedPlugin.configFields.isNotEmpty()) {
            CollapsibleSection(
                title = "Configuration",
                initiallyExpanded = true,
            ) {
                loadedPlugin.configFields.forEach { (key, value) ->
                    var fieldValue by remember(key, value) { mutableStateOf(value) }
                    OutlinedTextField(
                        value = fieldValue,
                        onValueChange = {
                            fieldValue = it
                            detailViewModel.updateConfig(pluginId, key, it)
                        },
                        label = { Text(key) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (loadedPlugin.isInstalled) {
            OutlinedButton(
                onClick = {
                    detailViewModel.uninstall(pluginId)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Uninstall",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            FilledTonalButton(
                onClick = { detailViewModel.install(pluginId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Install Plugin")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
