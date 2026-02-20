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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zeroclaw.android.ui.component.PinEntryMode
import com.zeroclaw.android.ui.component.PinEntrySheet
import com.zeroclaw.android.ui.component.SettingsToggleRow
import com.zeroclaw.android.util.BatteryOptimization
import com.zeroclaw.android.util.BiometricGatekeeper

/**
 * Onboarding step for requesting necessary permissions.
 *
 * Guides the user through notification permission (Android 13+),
 * battery optimization exemption, optional PIN-based app lock, and
 * optional biometric unlock.
 *
 * @param pinHash The current PIN hash (empty if no PIN is set).
 * @param biometricUnlockEnabled Whether biometric unlock is toggled on.
 * @param onPinSet Callback with the new PIN hash after setup.
 * @param onBiometricUnlockEnabledChange Callback when the biometric toggle changes.
 */
@Suppress("MagicNumber")
@Composable
fun PermissionsStep(
    pinHash: String,
    biometricUnlockEnabled: Boolean,
    onPinSet: (String) -> Unit,
    onBiometricUnlockEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
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

        Spacer(modifier = Modifier.height(16.dp))

        var showPinSheet by remember { mutableStateOf(false) }

        Text(
            text = "App Lock",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text =
                "Set up a PIN to lock the app on launch and " +
                    "after a period of inactivity.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (pinHash.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "PIN set",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "PIN is set",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            FilledTonalButton(
                onClick = { showPinSheet = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Change PIN")
            }
        } else {
            FilledTonalButton(
                onClick = { showPinSheet = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Set up a PIN")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "You can set up app lock later in Settings",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (pinHash.isNotEmpty() && BiometricGatekeeper.isAvailable(context)) {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsToggleRow(
                title = "Use fingerprint to unlock",
                subtitle = "Unlock with biometrics instead of PIN",
                checked = biometricUnlockEnabled,
                onCheckedChange = onBiometricUnlockEnabledChange,
                contentDescription = "Use fingerprint to unlock",
            )
        }

        if (showPinSheet) {
            PinEntrySheet(
                mode = if (pinHash.isNotEmpty()) PinEntryMode.CHANGE else PinEntryMode.SETUP,
                currentPinHash = pinHash,
                onPinSet = { hash ->
                    onPinSet(hash)
                    showPinSheet = false
                },
                onDismiss = { showPinSheet = false },
            )
        }
    }
}
