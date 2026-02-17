/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.model.ProviderCategory
import com.zeroclaw.android.model.ProviderInfo

/** Spacing between the provider icon and display name in dropdown items. */
private const val ICON_NAME_SPACING_DP = 12

/** Vertical padding for category section headers. */
private const val SECTION_HEADER_PADDING_DP = 8

/**
 * Material 3 dropdown for selecting an AI provider from the [ProviderRegistry].
 *
 * Items are grouped by [ProviderCategory] with section headers. A filter/search
 * field allows quick navigation among 27+ entries. Unknown provider IDs from
 * legacy data display as raw strings with a fallback icon.
 *
 * @param selectedProviderId Currently selected provider ID, or empty string.
 * @param onProviderSelected Callback invoked when the user selects a provider.
 * @param modifier Modifier applied to the root layout.
 * @param label Text label for the dropdown field.
 * @param enabled Whether the dropdown is interactive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDropdown(
    selectedProviderId: String,
    onProviderSelected: (ProviderInfo) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Provider",
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var filterText by remember { mutableStateOf("") }
    val displayText = ProviderRegistry.findById(selectedProviderId)?.displayName
        ?: selectedProviderId.ifEmpty { "" }

    val grouped = remember { ProviderRegistry.allByCategory() }
    val filteredGrouped = remember(filterText) {
        if (filterText.isBlank()) {
            grouped
        } else {
            val query = filterText.lowercase()
            grouped.mapValues { (_, providers) ->
                providers.filter { provider ->
                    provider.displayName.lowercase().contains(query) ||
                        provider.id.contains(query) ||
                        provider.aliases.any { it.contains(query) }
                }
            }.filterValues { it.isNotEmpty() }
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = if (expanded) filterText else displayText,
            onValueChange = { filterText = it },
            readOnly = !expanded,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .semantics { contentDescription = "$label dropdown" },
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                filterText = ""
            },
        ) {
            filteredGrouped.forEach { (category, providers) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = categoryLabel(category),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = SECTION_HEADER_PADDING_DP.dp),
                        )
                    },
                    onClick = {},
                    enabled = false,
                )

                providers.forEach { provider ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProviderIcon(provider = provider.id)
                                Spacer(modifier = Modifier.width(ICON_NAME_SPACING_DP.dp))
                                Text(provider.displayName)
                            }
                        },
                        onClick = {
                            onProviderSelected(provider)
                            expanded = false
                            filterText = ""
                        },
                    )
                }
            }
        }
    }
}

/**
 * Returns a human-readable label for a [ProviderCategory].
 *
 * @param category The category to label.
 * @return Display string for section headers.
 */
private fun categoryLabel(category: ProviderCategory): String =
    when (category) {
        ProviderCategory.PRIMARY -> "Popular"
        ProviderCategory.ECOSYSTEM -> "More Providers"
        ProviderCategory.CUSTOM -> "Custom Endpoints"
    }
