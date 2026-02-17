/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding.steps

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.util.BatteryOptimization

/**
 * Onboarding step for requesting necessary permissions.
 *
 * Guides the user through notification permission (Android 13+)
 * and battery optimization exemption.
 */
@Composable
fun PermissionsStep() {
    val context = LocalContext.current
    val isExempt = remember { BatteryOptimization.isExempt(context) }

    Column {
        Text(
            text = "Permissions",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "ZeroClaw needs a few permissions to run reliably in the background.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Text(
                text = "Notification Permission",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text =
                    "Required to show the foreground service notification. " +
                        "Grant this from the system dialog when prompted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = "Battery Optimization",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text =
                if (isExempt) {
                    "Already exempt from battery optimization."
                } else {
                    "Exempt the app from battery optimization so the daemon " +
                        "is not killed by the system."
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!isExempt) {
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(
                onClick = {
                    context.startActivity(
                        BatteryOptimization.requestExemptionIntent(context),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Request Exemption")
            }
        }
    }
}
