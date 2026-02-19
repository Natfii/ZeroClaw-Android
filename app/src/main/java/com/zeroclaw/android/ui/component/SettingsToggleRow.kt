/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Vertical padding applied to the toggle row container. */
private val TOGGLE_ROW_VERTICAL_PADDING = 8.dp

/**
 * Shared toggle row displaying a title, subtitle, and [Switch].
 *
 * Uses [semantics] with `mergeDescendants = true` so that screen readers
 * announce the row as a single focusable element with a state description
 * derived from [checked].
 *
 * @param title Primary label text displayed above the subtitle.
 * @param subtitle Descriptive text displayed below the title in a smaller style.
 * @param checked Current toggle state of the [Switch].
 * @param onCheckedChange Callback invoked when the user toggles the [Switch].
 * @param contentDescription Accessibility description announced by screen readers.
 * @param enabled Whether the [Switch] is interactive and the title uses full-contrast color.
 *   Defaults to `true`.
 * @param modifier Modifier applied to the outer [Row].
 */
@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val stateText = if (checked) "enabled" else "disabled"
    val a11yDescription = "$contentDescription: $stateText"

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = TOGGLE_ROW_VERTICAL_PADDING)
                .semantics(mergeDescendants = true) {
                    this.contentDescription = a11yDescription
                },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier =
                Modifier.semantics {
                    this.contentDescription = "$contentDescription toggle"
                },
        )
    }
}
