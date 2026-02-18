// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.ui.screen.settings

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.ui.component.ErrorCard
import com.zeroclaw.android.ui.component.LoadingIndicator
import com.zeroclaw.android.ui.component.SectionHeader
import com.zeroclaw.android.util.BUDGET_WARNING_THRESHOLD
import com.zeroclaw.android.util.DEFAULT_MONTHLY_BUDGET_USD
import com.zeroclaw.android.util.MAX_PROGRESS
import com.zeroclaw.android.util.formatUsd
import java.util.Locale

/**
 * Cost detail screen showing session, daily, and monthly totals with
 * a per-model breakdown table and budget display.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param costDetailViewModel ViewModel providing cost data.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun CostDetailScreen(
    edgeMargin: Dp,
    costDetailViewModel: CostDetailViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by costDetailViewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is CostDetailUiState.Loading -> {
            LoadingIndicator(modifier = modifier.fillMaxSize())
        }
        is CostDetailUiState.Error -> {
            Column(
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(horizontal = edgeMargin),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ErrorCard(
                    message = state.detail,
                    onRetry = { costDetailViewModel.refresh() },
                )
            }
        }
        is CostDetailUiState.Content -> {
            CostDetailContent(
                data = state.data,
                edgeMargin = edgeMargin,
                modifier = modifier,
                onRefresh = { costDetailViewModel.refresh() },
            )
        }
    }
}

/**
 * Content layout for the cost detail screen when data has loaded.
 *
 * @param data Aggregated cost data with model breakdown.
 * @param edgeMargin Horizontal padding.
 * @param onRefresh Callback to reload cost data.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
private fun CostDetailContent(
    data: CostDetailData,
    edgeMargin: Dp,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = data.summary

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item(key = "totals") {
            CostTotalsCard(
                sessionCost = summary.sessionCostUsd,
                dailyCost = summary.dailyCostUsd,
                monthlyCost = summary.monthlyCostUsd,
                totalTokens = summary.totalTokens,
                requestCount = summary.requestCount,
            )
        }

        item(key = "budget") {
            BudgetCard(monthlyCost = summary.monthlyCostUsd)
        }

        item(key = "breakdown_header") {
            SectionHeader(title = "Per-Model Breakdown")
        }

        if (data.modelBreakdown.isEmpty()) {
            item(key = "no_breakdown") {
                Text(
                    text = "No model usage data available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(
                items = data.modelBreakdown,
                key = { it.model },
                contentType = { "model_entry" },
            ) { entry ->
                ModelBreakdownRow(entry = entry)
            }
        }

        item(key = "refresh") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onRefresh) {
                    Text("Refresh")
                }
            }
        }

        item(key = "footer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Card showing session, daily, and monthly cost totals with token
 * and request counts.
 *
 * @param sessionCost Session cost in USD.
 * @param dailyCost Daily cost in USD.
 * @param monthlyCost Monthly cost in USD.
 * @param totalTokens Total tokens used.
 * @param requestCount Total inference requests.
 */
@Composable
private fun CostTotalsCard(
    sessionCost: Double,
    dailyCost: Double,
    monthlyCost: Double,
    totalTokens: Long,
    requestCount: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Cost Summary",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CostRow(label = "Session", value = formatUsd(sessionCost))
            CostRow(label = "Today", value = formatUsd(dailyCost))
            CostRow(label = "This Month", value = formatUsd(monthlyCost))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            CostRow(label = "Total Tokens", value = formatTokens(totalTokens))
            CostRow(label = "Requests", value = requestCount.toString())
        }
    }
}

/**
 * Card showing budget progress with a progress bar.
 *
 * @param monthlyCost Current monthly cost in USD.
 */
@Composable
private fun BudgetCard(monthlyCost: Double) {
    val budgetLimit = DEFAULT_MONTHLY_BUDGET_USD
    val progress =
        (monthlyCost / budgetLimit)
            .coerceIn(0.0, MAX_PROGRESS.toDouble())
            .toFloat()
    val progressColor =
        if (progress >= BUDGET_WARNING_THRESHOLD) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Monthly Budget",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${formatUsd(monthlyCost)} / ${formatUsd(budgetLimit)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Single row in the per-model breakdown table.
 *
 * @param entry Model cost breakdown entry.
 * @param modifier Modifier applied to the row card.
 */
@Composable
private fun ModelBreakdownRow(
    entry: ModelCostEntry,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "${entry.model}: ${formatUsd(entry.costUsd)}, " +
                        "${formatTokens(entry.tokens)} tokens, " +
                        "${entry.requests} requests"
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = entry.model,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatUsd(entry.costUsd),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${formatTokens(entry.tokens)} tokens",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${entry.requests} reqs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Labeled cost row with a label on the left and value on the right.
 *
 * @param label Description text.
 * @param value Formatted cost or count string.
 */
@Composable
private fun CostRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Threshold for switching to "k" suffix. */
private const val KILO_THRESHOLD = 1_000L

/** Threshold for switching to "M" suffix. */
private const val MEGA_THRESHOLD = 1_000_000L

/** Divisor for kilo formatting. */
private const val KILO_DIVISOR = 1_000.0

/** Divisor for mega formatting. */
private const val MEGA_DIVISOR = 1_000_000.0

/**
 * Formats a token count with k/M suffixes for readability.
 *
 * @param tokens Raw token count.
 * @return Formatted string (e.g. "1.2k", "3.4M").
 */
private fun formatTokens(tokens: Long): String =
    when {
        tokens >= MEGA_THRESHOLD ->
            String.format(Locale.US, "%.1fM", tokens / MEGA_DIVISOR)
        tokens >= KILO_THRESHOLD ->
            String.format(Locale.US, "%.1fk", tokens / KILO_DIVISOR)
        else -> tokens.toString()
    }
