/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Vertical padding around the banner card. */
private val BANNER_VERTICAL_PADDING = 8.dp

/** Internal padding inside the banner card. */
private val BANNER_INNER_PADDING = 16.dp

/**
 * Banner prompting the user to restart the daemon after a configuration change.
 *
 * Displayed when [DaemonServiceBridge.restartRequired][com.zeroclaw.android.service.DaemonServiceBridge]
 * is `true`. The banner uses a tertiary container colour to draw attention
 * without signalling an error.
 *
 * @param edgeMargin Horizontal padding matching the screen's edge margin.
 * @param onRestartDaemon Callback invoked when the user taps the restart button.
 */
@Composable
fun RestartRequiredBanner(
    edgeMargin: Dp,
    onRestartDaemon: () -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = edgeMargin, vertical = BANNER_VERTICAL_PADDING),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(BANNER_INNER_PADDING),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Restart daemon to apply configuration changes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = onRestartDaemon) {
                Text("Restart")
            }
        }
    }
}
