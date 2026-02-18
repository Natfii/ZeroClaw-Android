// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.ui.screen.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.model.CronJob

/** Number of milliseconds in one second, used for relative time. */
private const val MS_PER_SECOND = 1000L

/** Number of seconds in one minute. */
private const val SECONDS_PER_MINUTE = 60L

/** Number of seconds in one hour. */
private const val SECONDS_PER_HOUR = 3600L

/** Number of seconds in one day. */
private const val SECONDS_PER_DAY = 86400L

/** Maximum characters for the displayed command preview. */
private const val COMMAND_PREVIEW_LENGTH = 50

/**
 * Dashboard summary card showing the count of active cron jobs and
 * the next upcoming job.
 *
 * Tapping the card navigates to the full cron jobs management screen.
 *
 * @param cronJobs Current list of cron jobs from the daemon.
 * @param onClick Callback invoked when the card is tapped.
 * @param modifier Modifier applied to the root card.
 */
@Composable
fun CronSummaryCard(
    cronJobs: List<CronJob>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeCount = cronJobs.count { !it.paused }
    val pausedCount = cronJobs.count { it.paused }
    val nextJob = cronJobs.filter { !it.paused }.minByOrNull { it.nextRunMs }
    val nextRunLabel =
        nextJob?.let { formatRelativeTime(it.nextRunMs) } ?: "none"
    val nextCommandPreview =
        nextJob?.command?.take(COMMAND_PREVIEW_LENGTH) ?: ""

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable(onClick = onClick)
                .semantics {
                    role = Role.Button
                    contentDescription =
                        "$activeCount active jobs, $pausedCount paused. " +
                        "Next run: $nextRunLabel. Tap for details."
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Scheduled Tasks",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CronMetric(label = "Active", value = activeCount.toString())
                CronMetric(label = "Paused", value = pausedCount.toString())
                CronMetric(label = "Next Run", value = nextRunLabel)
            }
            if (nextCommandPreview.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = nextCommandPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Small column displaying a cron metric label and value.
 *
 * @param label Short heading (e.g. "Active").
 * @param value The metric value (e.g. "3").
 * @param modifier Modifier applied to the column.
 */
@Composable
private fun CronMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Formats an epoch-millisecond timestamp as a relative time string.
 *
 * @param epochMs Epoch milliseconds of the target time.
 * @return Human-readable relative time (e.g. "in 5m", "in 2h").
 */
private fun formatRelativeTime(epochMs: Long): String {
    val nowMs = System.currentTimeMillis()
    val diffSeconds = (epochMs - nowMs) / MS_PER_SECOND
    return when {
        diffSeconds < 0 -> "overdue"
        diffSeconds < SECONDS_PER_MINUTE -> "in ${diffSeconds}s"
        diffSeconds < SECONDS_PER_HOUR -> "in ${diffSeconds / SECONDS_PER_MINUTE}m"
        diffSeconds < SECONDS_PER_DAY -> "in ${diffSeconds / SECONDS_PER_HOUR}h"
        else -> "in ${diffSeconds / SECONDS_PER_DAY}d"
    }
}
