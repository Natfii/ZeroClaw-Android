/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.model.ServiceState

/**
 * Small colored circle with text label indicating daemon service state.
 *
 * Uses distinct colors for each state. The text label ensures that color
 * is never the sole differentiator (WCAG 2.2 AA compliance).
 *
 * @param state Current [ServiceState].
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun StatusDot(
    state: ServiceState,
    modifier: Modifier = Modifier,
) {
    val color =
        when (state) {
            ServiceState.RUNNING -> MaterialTheme.colorScheme.primary
            ServiceState.STARTING,
            ServiceState.STOPPING,
            -> MaterialTheme.colorScheme.tertiary
            ServiceState.ERROR -> MaterialTheme.colorScheme.error
            ServiceState.STOPPED -> MaterialTheme.colorScheme.outline
        }
    val label =
        when (state) {
            ServiceState.RUNNING -> "Running"
            ServiceState.STARTING -> "Starting"
            ServiceState.STOPPING -> "Stopping"
            ServiceState.ERROR -> "Error"
            ServiceState.STOPPED -> "Stopped"
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier.semantics(mergeDescendants = true) {
                contentDescription = "Daemon status: $label"
            },
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
