/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.model.CheckStatus
import com.zeroclaw.android.model.DiagnosticCategory
import com.zeroclaw.android.model.DiagnosticCheck
import com.zeroclaw.android.model.DoctorSummary
import com.zeroclaw.android.ui.component.CollapsibleSection

/**
 * ZeroClaw Doctor diagnostics screen.
 *
 * Displays a summary banner with pass/warn/fail counts and a button
 * to run diagnostics. Results are organized by category in collapsible
 * sections, each showing individual check results with colored status
 * dots and optional action buttons.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param onNavigateToRoute Callback invoked when a diagnostic action button is tapped,
 *   receiving the route string from [DiagnosticCheck.actionRoute].
 * @param doctorViewModel The [DoctorViewModel] for diagnostic state.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun DoctorScreen(
    edgeMargin: Dp,
    onNavigateToRoute: (String) -> Unit = {},
    doctorViewModel: DoctorViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val checks by doctorViewModel.checks.collectAsStateWithLifecycle()
    val isRunning by doctorViewModel.isRunning.collectAsStateWithLifecycle()
    val summary by doctorViewModel.summary.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SummaryBanner(
            summary = summary,
            isRunning = isRunning,
            onRunDiagnostics = { doctorViewModel.runAllChecks() },
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (checks.isEmpty() && !isRunning) {
            Text(
                text = "Tap \"Run Diagnostics\" to check your configuration.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            CheckResultsList(checks = checks, onAction = onNavigateToRoute)
        }
    }
}

/**
 * Summary card showing pass/warn/fail counts and the run button.
 *
 * @param summary Aggregated check summary, null before first run.
 * @param isRunning Whether checks are currently executing.
 * @param onRunDiagnostics Callback to start diagnostic checks.
 */
@Composable
private fun SummaryBanner(
    summary: DoctorSummary?,
    isRunning: Boolean,
    onRunDiagnostics: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = summary?.let {
                        "${it.passCount} passed, ${it.warnCount} warnings, ${it.failCount} failed"
                    } ?: "No diagnostics run yet"
                },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "ZeroClaw Doctor",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (summary != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SummaryChip(
                        label = "Pass",
                        count = summary.passCount,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    SummaryChip(
                        label = "Warn",
                        count = summary.warnCount,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    SummaryChip(
                        label = "Fail",
                        count = summary.failCount,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = onRunDiagnostics,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Running...")
                } else {
                    Text(if (summary != null) "Re-run Diagnostics" else "Run Diagnostics")
                }
            }
        }
    }
}

/**
 * Small count chip for the summary banner.
 *
 * @param label Category label (e.g. "Pass").
 * @param count Number of checks in this category.
 * @param color Color for the count text and dot.
 */
@Composable
private fun SummaryChip(
    label: String,
    count: Int,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
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
            text = "$count $label",
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/**
 * Scrollable list of check results organized by category.
 *
 * @param checks All diagnostic check results to display.
 * @param onAction Callback invoked with the action route when a check's action button is tapped.
 */
@Composable
private fun CheckResultsList(
    checks: List<DiagnosticCheck>,
    onAction: (String) -> Unit,
) {
    val grouped = checks.groupBy { it.category }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (category in DiagnosticCategory.entries) {
            val categoryChecks = grouped[category] ?: continue
            item(key = "section-${category.name}") {
                CollapsibleSection(
                    title = categoryDisplayName(category),
                    initiallyExpanded = categoryChecks.any { it.status == CheckStatus.FAIL },
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        categoryChecks.forEach { check ->
                            CheckResultRow(check = check, onAction = onAction)
                        }
                    }
                }
            }
        }

        item(key = "bottom-spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Single check result row with status dot, title, detail, and optional action.
 *
 * @param check The diagnostic check result to display.
 * @param onAction Callback invoked with the action route when the action button is tapped.
 */
@Composable
private fun CheckResultRow(
    check: DiagnosticCheck,
    onAction: (String) -> Unit,
) {
    val statusColor =
        when (check.status) {
            CheckStatus.PASS -> MaterialTheme.colorScheme.primary
            CheckStatus.WARN -> MaterialTheme.colorScheme.tertiary
            CheckStatus.FAIL -> MaterialTheme.colorScheme.error
            CheckStatus.RUNNING -> MaterialTheme.colorScheme.outline
        }
    val statusLabel =
        when (check.status) {
            CheckStatus.PASS -> "Passed"
            CheckStatus.WARN -> "Warning"
            CheckStatus.FAIL -> "Failed"
            CheckStatus.RUNNING -> "Running"
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${check.title}: $statusLabel. ${check.detail}"
                },
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = check.title,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (check.detail.isNotEmpty()) {
                Text(
                    text = check.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (check.actionLabel != null && check.actionRoute != null) {
            TextButton(onClick = { onAction(check.actionRoute) }) {
                Text(check.actionLabel)
            }
        }
    }
}

/**
 * Maps a [DiagnosticCategory] to its human-readable display name.
 *
 * @param category The category to map.
 * @return Human-readable section title.
 */
private fun categoryDisplayName(category: DiagnosticCategory): String =
    when (category) {
        DiagnosticCategory.CONFIG -> "Configuration"
        DiagnosticCategory.API_KEYS -> "API Keys"
        DiagnosticCategory.CONNECTIVITY -> "Connectivity"
        DiagnosticCategory.DAEMON_HEALTH -> "Daemon Health"
        DiagnosticCategory.SYSTEM -> "System"
    }
