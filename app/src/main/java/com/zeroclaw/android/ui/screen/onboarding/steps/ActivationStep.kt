/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.util.LocalPowerSaveMode

/**
 * Final onboarding step for activating the daemon.
 *
 * @param onActivate Callback invoked when the user starts the daemon
 *   and finishes onboarding.
 * @param isActivating Whether the activation is in progress (probing
 *   credentials and persisting configuration). When true, the button is
 *   disabled and a progress indicator is shown.
 */
@Composable
fun ActivationStep(
    onActivate: () -> Unit,
    isActivating: Boolean = false,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Ready to Go",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Everything is set up. Start the ZeroClaw daemon to begin.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(32.dp))
        FilledTonalButton(
            onClick = onActivate,
            enabled = !isActivating,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics {
                        contentDescription = "Start daemon and finish setup"
                    },
        ) {
            if (isActivating) {
                if (LocalPowerSaveMode.current) {
                    Text("Verifying\u2026")
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                Text("Start Daemon")
            }
        }
    }
}
