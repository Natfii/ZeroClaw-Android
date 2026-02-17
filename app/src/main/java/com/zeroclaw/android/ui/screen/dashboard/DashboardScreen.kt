/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.model.ServiceState
import com.zeroclaw.android.ui.component.LoadingIndicator
import com.zeroclaw.android.ZeroClawApplication
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

        SectionHeader(title = "Recent Activity")
        val app = context.applicationContext as ZeroClawApplication
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
private fun KeyRejectionBanner(
    onDismiss: () -> Unit,
) {
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
                text = "An API key may be invalid or expired. " +
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

private fun serviceStateDescription(state: ServiceState): String =
    when (state) {
        ServiceState.STOPPED -> "The daemon is not running."
        ServiceState.STARTING -> "The daemon is starting up\u2026"
        ServiceState.RUNNING -> "The daemon is running and healthy."
        ServiceState.STOPPING -> "The daemon is shutting down\u2026"
        ServiceState.ERROR -> "The daemon encountered an error."
    }
