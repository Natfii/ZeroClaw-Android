/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * Reusable settings row with icon, title, subtitle, and click action.
 *
 * @param icon Leading icon for the item.
 * @param title Primary text label.
 * @param subtitle Secondary descriptive text.
 * @param onClick Callback invoked when the item is tapped.
 * @param modifier Modifier applied to the [ListItem].
 */
@Composable
fun SettingsListItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier =
            modifier
                .clickable(onClick = onClick)
                .semantics { role = Role.Button },
    )
}
