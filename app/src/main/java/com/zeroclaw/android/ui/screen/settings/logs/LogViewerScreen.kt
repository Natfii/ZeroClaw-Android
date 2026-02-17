/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings.logs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.model.LogEntry
import com.zeroclaw.android.model.LogSeverity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log viewer screen with severity filter chips, pause/resume, and clear.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param logViewerViewModel The [LogViewerViewModel] for log state.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun LogViewerScreen(
    edgeMargin: Dp,
    logViewerViewModel: LogViewerViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val entries by logViewerViewModel.filteredEntries.collectAsStateWithLifecycle()
    val selectedSeverities by logViewerViewModel.selectedSeverities.collectAsStateWithLifecycle()
    val isPaused by logViewerViewModel.isPaused.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogSeverity.entries.forEach { severity ->
                    FilterChip(
                        selected = severity in selectedSeverities,
                        onClick = { logViewerViewModel.toggleSeverity(severity) },
                        label = { Text(severity.name) },
                    )
                }
            }
            Row {
                IconButton(
                    onClick = {
                        if (isPaused) logViewerViewModel.resume() else logViewerViewModel.pause()
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription = if (isPaused) "Resume logs" else "Pause logs"
                        },
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = null,
                    )
                }
                IconButton(
                    onClick = { logViewerViewModel.clearLogs() },
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Clear logs"
                        },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                    )
                }
            }
        }

        if (isPaused) {
            Text(
                text = "Log stream paused",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(items = entries, key = { it.id }) { entry ->
                LogEntryRow(entry = entry)
            }
        }
    }
}

/**
 * Single log entry row with timestamp, severity badge, tag, and message.
 *
 * @param entry The log entry to display.
 */
@Composable
private fun LogEntryRow(entry: LogEntry) {
    val severityColor =
        when (entry.severity) {
            LogSeverity.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
            LogSeverity.INFO -> MaterialTheme.colorScheme.primary
            LogSeverity.WARN -> MaterialTheme.colorScheme.tertiary
            LogSeverity.ERROR -> MaterialTheme.colorScheme.error
        }

    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatTimestamp(entry.timestamp),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = entry.severity.name,
                style = MaterialTheme.typography.labelSmall,
                color = severityColor,
            )
            Text(
                text = entry.tag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

private fun formatTimestamp(epochMs: Long): String =
    timeFormat.format(Date(epochMs))
