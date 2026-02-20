/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding.steps

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zeroclaw.android.util.BatteryOptimization
import com.zeroclaw.android.util.BiometricGatekeeper

/**
 * Onboarding step for requesting necessary permissions.
 *
 * Guides the user through notification permission (Android 13+),
 * battery optimization exemption, and optional biometric protection
 * for service control and sensitive settings.
 *
 * @param biometricForService Whether biometric is required for service start/stop.
 * @param biometricForSettings Whether biometric is required for sensitive settings.
 * @param onBiometricForServiceChanged Callback when the service toggle changes.
 * @param onBiometricForSettingsChanged Callback when the settings toggle changes.
 */
@Suppress("MagicNumber")
@Composable
fun PermissionsStep(
    biometricForService: Boolean,
    biometricForSettings: Boolean,
    onBiometricForServiceChanged: (Boolean) -> Unit,
    onBiometricForSettingsChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val biometricAvailable = BiometricGatekeeper.isAvailable(context)
    var isExempt by rememberSaveable {
        mutableStateOf(BatteryOptimization.isExempt(context))
    }

    var notificationGranted by rememberSaveable {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }

    val notificationLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            notificationGranted = granted
        }

    LifecycleResumeEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
        isExempt = BatteryOptimization.isExempt(context)
        onPauseOrDispose {}
    }

    Column {
        Text(
            text = "Permissions",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text =
                "ZeroClaw needs a few permissions to run " +
                    "reliably in the background.",
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
                    "Required to show the foreground service " +
                        "notification.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (notificationGranted) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Permission granted",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Permission granted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = {
                        notificationLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Grant Notification Permission")
                }
            }
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
                    "Exempt the app from battery optimization " +
                        "so the daemon is not killed by the system."
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!isExempt) {
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(
                onClick = {
                    context.startActivity(
                        BatteryOptimization.requestExemptionIntent(
                            context,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Request Exemption")
            }
        }

        if (biometricAvailable) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Biometric Protection",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text =
                    "Require fingerprint or face unlock to control " +
                        "the daemon or modify sensitive settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Protect service start/stop",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = biometricForService,
                    onCheckedChange = onBiometricForServiceChanged,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Protect sensitive settings",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = biometricForSettings,
                    onCheckedChange = onBiometricForSettingsChanged,
                )
            }
        }
    }
}
