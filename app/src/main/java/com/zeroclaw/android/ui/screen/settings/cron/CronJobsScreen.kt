// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.ui.screen.settings.cron

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.model.CronJob
import com.zeroclaw.android.ui.component.EmptyState
import com.zeroclaw.android.ui.component.ErrorCard
import com.zeroclaw.android.ui.component.LoadingIndicator

/** Maximum characters to display for a truncated command string. */
private const val COMMAND_MAX_LENGTH = 80

/**
 * Screen for managing scheduled cron jobs.
 *
 * Displays a list of all cron jobs registered with the daemon, with
 * controls to pause, resume, and delete jobs. A floating action button
 * opens the [AddCronJobDialog] for creating new jobs.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param cronJobsViewModel ViewModel providing cron job state and actions.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun CronJobsScreen(
    edgeMargin: Dp,
    cronJobsViewModel: CronJobsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by cronJobsViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarMessage by cronJobsViewModel.snackbarMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            cronJobsViewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier =
                    Modifier.semantics {
                        contentDescription = "Add scheduled job"
                    },
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = edgeMargin),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            when (val state = uiState) {
                is CronJobsUiState.Loading -> {
                    LoadingIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                is CronJobsUiState.Error -> {
                    ErrorCard(
                        message = state.detail,
                        onRetry = { cronJobsViewModel.loadJobs() },
                    )
                }
                is CronJobsUiState.Content -> {
                    if (state.data.isEmpty()) {
                        EmptyState(
                            icon = Icons.Outlined.Schedule,
                            message = "No scheduled jobs. Tap + to add a cron job or one-shot task.",
                        )
                    } else {
                        CronJobsList(
                            jobs = state.data,
                            onPause = { cronJobsViewModel.pauseJob(it) },
                            onResume = { cronJobsViewModel.resumeJob(it) },
                            onRemove = { cronJobsViewModel.removeJob(it) },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCronJobDialog(
            onAddRecurring = { expression, command ->
                cronJobsViewModel.addJob(expression, command)
            },
            onAddOneShot = { delay, command ->
                cronJobsViewModel.addOneShot(delay, command)
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

/**
 * Lazy column of cron job cards.
 *
 * @param jobs List of cron jobs to display.
 * @param onPause Callback to pause a job by its ID.
 * @param onResume Callback to resume a job by its ID.
 * @param onRemove Callback to remove a job by its ID.
 */
@Composable
private fun CronJobsList(
    jobs: List<CronJob>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = jobs,
            key = { it.id },
            contentType = { "cron_job" },
        ) { job ->
            CronJobCard(
                job = job,
                onPause = { onPause(job.id) },
                onResume = { onResume(job.id) },
                onRemove = { onRemove(job.id) },
            )
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

/**
 * Card displaying a single cron job with status and action controls.
 *
 * Shows the cron expression (or "One-shot" label), the command truncated
 * to [COMMAND_MAX_LENGTH] characters, a status chip, and the next run time.
 * Action buttons allow pausing, resuming, and deleting the job.
 *
 * @param job The cron job to display.
 * @param onPause Callback to pause this job.
 * @param onResume Callback to resume this job.
 * @param onRemove Callback to remove this job.
 * @param modifier Modifier applied to the card.
 */
@Composable
private fun CronJobCard(
    job: CronJob,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusText = formatJobStatus(job)
    val statusColor =
        when {
            job.paused -> MaterialTheme.colorScheme.onSurfaceVariant
            job.lastStatus == "ok" -> MaterialTheme.colorScheme.primary
            job.lastStatus != null -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val scheduleLabel =
        if (job.oneShot) "One-shot" else job.expression
    val nextRunFormatted = formatNextRun(job.nextRunMs)

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "Job: ${job.command.take(COMMAND_MAX_LENGTH)}, " +
                        "schedule: $scheduleLabel, status: $statusText"
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = scheduleLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = job.command,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Next: $nextRunFormatted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row {
                    if (job.paused) {
                        IconButton(
                            onClick = onResume,
                            modifier =
                                Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics { contentDescription = "Resume job" },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onPause,
                            modifier =
                                Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics { contentDescription = "Pause job" },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Pause,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onRemove,
                        modifier =
                            Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics { contentDescription = "Delete job" },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** Number of milliseconds in one second. */
private const val MS_PER_SECOND = 1000L

/** Number of seconds in one minute. */
private const val SECONDS_PER_MINUTE = 60L

/** Number of seconds in one hour. */
private const val SECONDS_PER_HOUR = 3600L

/** Number of seconds in one day. */
private const val SECONDS_PER_DAY = 86400L

/**
 * Formats the status of a cron job for display.
 *
 * @param job The cron job.
 * @return A short status label.
 */
private fun formatJobStatus(job: CronJob): String =
    when {
        job.paused -> "Paused"
        job.lastStatus == "ok" -> "OK"
        job.lastStatus != null -> "Error"
        else -> "Pending"
    }

/**
 * Formats a next-run epoch-millisecond timestamp as a relative time string.
 *
 * @param nextRunMs Epoch milliseconds of the next scheduled run.
 * @return Human-readable relative time (e.g. "in 5m", "in 2h").
 */
private fun formatNextRun(nextRunMs: Long): String {
    val nowMs = System.currentTimeMillis()
    val diffSeconds = (nextRunMs - nowMs) / MS_PER_SECOND
    return when {
        diffSeconds < 0 -> "overdue"
        diffSeconds < SECONDS_PER_MINUTE -> "in ${diffSeconds}s"
        diffSeconds < SECONDS_PER_HOUR -> "in ${diffSeconds / SECONDS_PER_MINUTE}m"
        diffSeconds < SECONDS_PER_DAY -> "in ${diffSeconds / SECONDS_PER_HOUR}h"
        else -> "in ${diffSeconds / SECONDS_PER_DAY}d"
    }
}
