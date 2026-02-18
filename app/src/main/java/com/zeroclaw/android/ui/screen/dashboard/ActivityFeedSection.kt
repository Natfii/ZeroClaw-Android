/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.model.ActivityEvent
import com.zeroclaw.android.model.ActivityType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity feed section for the dashboard showing recent events.
 *
 * @param events List of recent activity events to display.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun ActivityFeedSection(
    events: List<ActivityEvent>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (events.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
            ) {
                Text(
                    text = "No recent activity",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            events.take(MAX_VISIBLE_EVENTS).forEach { event ->
                ActivityEventRow(event = event)
            }
        }
    }
}

/**
 * Single activity event row.
 *
 * @param event The event to display.
 */
@Composable
private fun ActivityEventRow(event: ActivityEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = activityIcon(event.type),
                style = MaterialTheme.typography.bodyMedium,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.message,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = formatTime(event.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun activityIcon(type: ActivityType): String =
    when (type) {
        ActivityType.DAEMON_STARTED -> "\u25B6"
        ActivityType.DAEMON_STOPPED -> "\u25A0"
        ActivityType.DAEMON_ERROR -> "\u26A0"
        ActivityType.FFI_CALL -> "\u2194"
        ActivityType.NETWORK_CHANGE -> "\u2195"
        ActivityType.CONFIG_CHANGE -> "\u2699"
    }

private val activityTimeFormat = SimpleDateFormat("HH:mm", Locale.US)

private fun formatTime(epochMs: Long): String = activityTimeFormat.format(Date(epochMs))

private const val MAX_VISIBLE_EVENTS = 5
