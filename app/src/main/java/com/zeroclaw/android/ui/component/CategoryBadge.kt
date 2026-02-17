/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zeroclaw.android.model.PluginCategory

/**
 * Compact chip displaying a [PluginCategory] label.
 *
 * @param category The plugin category to display.
 * @param modifier Modifier applied to the chip.
 */
@Composable
fun CategoryBadge(
    category: PluginCategory,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = { },
        label = {
            Text(
                text = categoryLabel(category),
                style = MaterialTheme.typography.labelSmall,
            )
        },
        modifier = modifier,
    )
}

private fun categoryLabel(category: PluginCategory): String =
    when (category) {
        PluginCategory.CHANNEL -> "Channel"
        PluginCategory.MEMORY -> "Memory"
        PluginCategory.TOOL -> "Tool"
        PluginCategory.OBSERVER -> "Observer"
        PluginCategory.SECURITY -> "Security"
        PluginCategory.OTHER -> "Other"
    }
