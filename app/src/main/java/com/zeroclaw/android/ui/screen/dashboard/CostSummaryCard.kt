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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.model.CostSummary
import com.zeroclaw.android.util.BUDGET_WARNING_THRESHOLD
import com.zeroclaw.android.util.DEFAULT_MONTHLY_BUDGET_USD
import com.zeroclaw.android.util.MAX_PROGRESS
import com.zeroclaw.android.util.formatUsd

/**
 * Dashboard card displaying a cost summary with daily and monthly totals
 * and a budget progress bar.
 *
 * Tapping the card navigates to the cost detail screen for a full
 * per-model breakdown.
 *
 * @param costSummary Aggregated cost data from the daemon.
 * @param onClick Callback invoked when the card is tapped.
 * @param modifier Modifier applied to the root card.
 */
@Composable
fun CostSummaryCard(
    costSummary: CostSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val budgetLimit = DEFAULT_MONTHLY_BUDGET_USD
    val progress =
        (costSummary.monthlyCostUsd / budgetLimit)
            .coerceIn(0.0, MAX_PROGRESS.toDouble())
            .toFloat()
    val progressColor =
        if (progress >= BUDGET_WARNING_THRESHOLD) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val dailyFormatted = formatUsd(costSummary.dailyCostUsd)
    val monthlyFormatted = formatUsd(costSummary.monthlyCostUsd)
    val budgetFormatted = formatUsd(budgetLimit)

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable(onClick = onClick)
                .semantics {
                    role = Role.Button
                    contentDescription =
                        "Cost summary: $dailyFormatted today, " +
                        "$monthlyFormatted this month. Tap for details."
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Cost Tracking",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CostLabel(label = "Today", value = dailyFormatted)
                CostLabel(label = "Month", value = monthlyFormatted)
                CostLabel(label = "Requests", value = costSummary.requestCount.toString())
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = progressColor,
                trackColor = trackColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$monthlyFormatted / $budgetFormatted monthly budget",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Small column displaying a cost metric label and value.
 *
 * @param label Short heading (e.g. "Today").
 * @param value Formatted cost string (e.g. "$1.23").
 * @param modifier Modifier applied to the column.
 */
@Composable
private fun CostLabel(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
