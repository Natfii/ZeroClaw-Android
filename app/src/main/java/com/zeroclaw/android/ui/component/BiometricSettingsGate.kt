/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.zeroclaw.android.util.AuthResult
import com.zeroclaw.android.util.BiometricGatekeeper

/**
 * Gate composable that wraps content behind biometric authentication.
 *
 * When [requireBiometric] is true and the user has not yet authenticated
 * during this composition lifetime, a lock overlay is shown instead of
 * [content]. The user can tap "Authenticate to modify" to trigger a
 * biometric prompt. On success, the content is revealed.
 *
 * When [requireBiometric] is false, [content] is shown directly.
 *
 * @param requireBiometric Whether biometric auth is needed to view the content.
 * @param modifier Modifier applied to the root layout.
 * @param content The protected content composable.
 */
@Composable
fun BiometricSettingsGate(
    requireBiometric: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var isUnlocked by remember { mutableStateOf(false) }

    if (!requireBiometric || isUnlocked) {
        content()
    } else {
        BiometricLockOverlay(
            onUnlocked = { isUnlocked = true },
            modifier = modifier,
        )
    }
}

/**
 * Lock overlay shown when biometric authentication is required.
 *
 * Displays a lock icon, status text, and an authenticate button.
 * The status text uses [LiveRegionMode.Polite] so screen readers
 * announce lock/unlock transitions.
 *
 * @param onUnlocked Callback when authentication succeeds.
 * @param modifier Modifier applied to the overlay container.
 */
@Composable
private fun BiometricLockOverlay(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier =
                Modifier.semantics {
                    contentDescription = "Settings locked. Authenticate to modify."
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(LOCK_ICON_SIZE),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(SPACING_MEDIUM))
            Text(
                text = "Settings locked",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(SPACING_SMALL))
            Text(
                text = "Biometric authentication required",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(SPACING_LARGE))
            FilledTonalButton(
                onClick = {
                    if (activity != null) {
                        BiometricGatekeeper.authenticate(
                            activity = activity,
                            title = "Unlock Settings",
                            subtitle = "Authenticate to modify sensitive settings",
                            allowDeviceCredential = true,
                        ) { result ->
                            if (result is AuthResult.Success) {
                                onUnlocked()
                            }
                        }
                    } else {
                        onUnlocked()
                    }
                },
                modifier =
                    Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Authenticate to modify settings"
                    },
            ) {
                Text("Authenticate to modify")
            }
        }
    }
}

/** Size of the lock icon (48dp, meeting touch target minimum). */
private val LOCK_ICON_SIZE = 48.dp

/** Small spacing (8dp). */
private val SPACING_SMALL = 8.dp

/** Medium spacing (12dp). */
private val SPACING_MEDIUM = 12.dp

/** Large spacing (24dp). */
private val SPACING_LARGE = 24.dp
