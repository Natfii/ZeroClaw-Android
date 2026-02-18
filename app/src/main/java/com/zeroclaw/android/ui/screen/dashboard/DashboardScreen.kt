// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.ui.screen.dashboard

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.Agent
import com.zeroclaw.android.model.DaemonStatus
import com.zeroclaw.android.model.Plugin
import com.zeroclaw.android.model.ServiceState
import com.zeroclaw.android.ui.component.LoadingIndicator
import com.zeroclaw.android.ui.component.SectionHeader
import com.zeroclaw.android.util.BatteryOptimization
import com.zeroclaw.android.viewmodel.DaemonUiState
import com.zeroclaw.android.viewmodel.DaemonViewModel

/**
 * Dashboard home screen displaying daemon status, toggle control,
 * and an activity feed placeholder.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param viewModel The [DaemonViewModel] for daemon state and actions.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun DashboardScreen(
    edgeMargin: androidx.compose.ui.unit.Dp,
    viewModel: DaemonViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val statusState by viewModel.statusState.collectAsStateWithLifecycle()
    val keyRejection by viewModel.keyRejectionEvent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val oemType = remember { BatteryOptimization.detectAggressiveOem() }
    val isExempt = remember { BatteryOptimization.isExempt(context) }
    var bannerDismissed by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        if (oemType != null && !isExempt && !bannerDismissed) {
            BatteryOptimizationBanner(
                oemType = oemType,
                onDismiss = { bannerDismissed = true },
                onLearnMore = {
                    val url = BatteryOptimization.getOemInstructionsUrl(oemType)
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                    )
                },
            )
        }

        if (keyRejection != null) {
            KeyRejectionBanner(
                onDismiss = { viewModel.dismissKeyRejection() },
            )
        }

        StatusHeroCard(
            serviceState = serviceState,
            errorMessage = (statusState as? DaemonUiState.Error)?.detail,
            onStart = { viewModel.requestStart() },
            onStop = { viewModel.requestStop() },
        )

        val app = context.applicationContext as ZeroClawApplication
        val agents by app.agentRepository.agents
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val plugins by app.pluginRepository.plugins
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val lastStatus by app.daemonBridge.lastStatus
            .collectAsStateWithLifecycle()

        SectionHeader(title = "At a Glance")
        MetricCardsRow(
            agents = agents,
            plugins = plugins,
            daemonStatus = lastStatus,
            serviceState = serviceState,
        )

        SectionHeader(title = "Recent Activity")
        val activityEvents by app.activityRepository.events
            .collectAsStateWithLifecycle(initialValue = emptyList())
        ActivityFeedSection(events = activityEvents)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Hero card showing daemon status with start/stop toggle.
 *
 * @param serviceState Current lifecycle state of the daemon.
 * @param errorMessage Optional error detail to display when in [ServiceState.ERROR].
 * @param onStart Callback invoked when the user taps Start.
 * @param onStop Callback invoked when the user taps Stop.
 */
@Composable
private fun StatusHeroCard(
    serviceState: ServiceState,
    errorMessage: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val isTransitioning =
        serviceState == ServiceState.STARTING ||
            serviceState == ServiceState.STOPPING
    val isRunning = serviceState == ServiceState.RUNNING

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Daemon Status",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = serviceStateDescription(serviceState),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = if (isRunning) onStop else onStart,
                    enabled = !isTransitioning,
                    modifier =
                        Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .semantics {
                                contentDescription =
                                    if (isRunning) "Stop daemon" else "Start daemon"
                            },
                ) {
                    Text(text = if (isRunning) "Stop Daemon" else "Start Daemon")
                }
                if (isTransitioning) {
                    Spacer(modifier = Modifier.width(12.dp))
                    LoadingIndicator()
                }
            }
            if (serviceState == ServiceState.ERROR) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage ?: "Daemon encountered an error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * Dismissible banner warning about aggressive OEM battery management.
 *
 * @param oemType Detected OEM battery management type.
 * @param onDismiss Callback when the user dismisses the banner.
 * @param onLearnMore Callback when the user taps "Learn More".
 */
@Composable
private fun BatteryOptimizationBanner(
    oemType: BatteryOptimization.OemBatteryType,
    onDismiss: () -> Unit,
    onLearnMore: () -> Unit,
) {
    val oemName =
        when (oemType) {
            BatteryOptimization.OemBatteryType.XIAOMI -> "Xiaomi"
            BatteryOptimization.OemBatteryType.SAMSUNG -> "Samsung"
            BatteryOptimization.OemBatteryType.HUAWEI -> "Huawei"
            BatteryOptimization.OemBatteryType.ONEPLUS -> "OnePlus"
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Battery optimization",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text =
                    "$oemName devices may stop the daemon in the background. " +
                        "Disable battery optimization for reliable operation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onLearnMore) {
                    Text("Learn More")
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}

/**
 * Dismissible banner shown when an API key rejection has been detected.
 *
 * @param onDismiss Callback when the user dismisses the banner.
 */
@Composable
private fun KeyRejectionBanner(onDismiss: () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text =
                    "An API key may be invalid or expired. " +
                        "Check Settings \u203A API Keys.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

/** Number of seconds in one minute, used for uptime formatting. */
private const val SECONDS_PER_MINUTE = 60

/** Number of seconds in one hour, used for uptime formatting. */
private const val SECONDS_PER_HOUR = 3600L

/**
 * Row of three compact metric cards summarising agent count, plugin count,
 * and daemon uptime. Each card occupies equal width via [Modifier.weight].
 *
 * The cards provide an at-a-glance overview of the system state on the
 * dashboard without requiring the user to navigate to detail screens.
 *
 * @param agents Current list of all agents; only enabled agents are counted.
 * @param plugins Current list of all plugins; only installed plugins are counted.
 * @param daemonStatus Latest daemon health snapshot, used for uptime. May be null
 *   if the daemon has not been polled yet.
 * @param serviceState Current service lifecycle state; used to determine whether
 *   to show uptime or "Offline".
 */
@Composable
private fun MetricCardsRow(
    agents: List<Agent>,
    plugins: List<Plugin>,
    daemonStatus: DaemonStatus?,
    serviceState: ServiceState,
) {
    val enabledAgentCount = agents.count { it.isEnabled }
    val installedPluginCount = plugins.count { it.isInstalled }
    val uptimeText = formatUptime(daemonStatus, serviceState)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetricCard(
            label = "Connections",
            value = enabledAgentCount.toString(),
            description = "enabled",
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = "Plugins",
            value = installedPluginCount.toString(),
            description = "installed",
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = "Uptime",
            value = uptimeText,
            description = if (serviceState == ServiceState.RUNNING) "running" else "",
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Compact metric card displaying a label, a prominent value, and an optional
 * description line beneath.
 *
 * Styled with [MaterialTheme.colorScheme.surfaceContainerLow] background and
 * centered text alignment for visual consistency in a multi-card row.
 *
 * @param label Short heading displayed above the value (e.g. "Agents").
 * @param value The primary metric value displayed prominently (e.g. "3").
 * @param description Optional secondary text below the value (e.g. "enabled").
 * @param modifier Modifier applied to the root [Card] layout.
 */
@Composable
private fun MetricCard(
    label: String,
    value: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Formats daemon uptime into a human-readable string.
 *
 * When the daemon is running and a status snapshot is available, formats the
 * [DaemonStatus.uptimeSeconds] as "Xh Ym" (e.g. "2h 15m") or "Xm" for
 * durations under one hour. Returns "Offline" when the daemon is not running
 * or no status has been received.
 *
 * @param status Latest daemon health snapshot, or null if unavailable.
 * @param serviceState Current service lifecycle state.
 * @return Formatted uptime string.
 */
private fun formatUptime(
    status: DaemonStatus?,
    serviceState: ServiceState,
): String {
    if (serviceState != ServiceState.RUNNING || status == null) {
        return "Offline"
    }
    val totalSeconds = status.uptimeSeconds
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

private fun serviceStateDescription(state: ServiceState): String =
    when (state) {
        ServiceState.STOPPED -> "The daemon is not running."
        ServiceState.STARTING -> "The daemon is starting up\u2026"
        ServiceState.RUNNING -> "The daemon is running and healthy."
        ServiceState.STOPPING -> "The daemon is shutting down\u2026"
        ServiceState.ERROR -> "The daemon encountered an error."
    }
