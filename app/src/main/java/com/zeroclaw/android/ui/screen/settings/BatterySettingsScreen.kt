/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.ui.component.SectionHeader
import com.zeroclaw.android.util.BatteryOptimization

/**
 * Battery settings sub-screen showing OEM detection and exemption controls.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun BatterySettingsScreen(
    edgeMargin: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val oemType = remember { BatteryOptimization.detectAggressiveOem() }
    val isExempt = remember { BatteryOptimization.isExempt(context) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SectionHeader(title = "Battery Optimization")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (isExempt) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isExempt) "Exempt from optimization" else "Not exempt",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        if (isExempt) {
                            "The app is exempt from battery optimization. " +
                                "The daemon will run reliably in the background."
                        } else {
                            "The app is subject to battery optimization. " +
                                "The daemon may be stopped by the system."
                        },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!isExempt) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(
                                BatteryOptimization.requestExemptionIntent(context),
                            )
                        },
                    ) {
                        Text("Request Exemption")
                    }
                }
            }
        }

        if (oemType != null) {
            SectionHeader(title = "OEM Battery Management")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Detected: ${oemName(oemType)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text =
                            "This device manufacturer has aggressive battery management. " +
                                "Follow the guide below for instructions.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            val url = BatteryOptimization.getOemInstructionsUrl(oemType)
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                            )
                        },
                    ) {
                        Text("View Instructions")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun oemName(type: BatteryOptimization.OemBatteryType): String =
    when (type) {
        BatteryOptimization.OemBatteryType.XIAOMI -> "Xiaomi"
        BatteryOptimization.OemBatteryType.SAMSUNG -> "Samsung"
        BatteryOptimization.OemBatteryType.HUAWEI -> "Huawei"
        BatteryOptimization.OemBatteryType.ONEPLUS -> "OnePlus"
    }
