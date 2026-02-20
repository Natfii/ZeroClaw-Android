/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.zeroclaw.android.util.AuthResult
import com.zeroclaw.android.util.BiometricGatekeeper
import com.zeroclaw.android.util.LocalPowerSaveMode
import com.zeroclaw.android.util.PinHasher
import kotlinx.coroutines.launch

/**
 * Full-screen lock gate overlay.
 *
 * Displays the app lock screen with a PIN keypad and optional biometric
 * button. Auto-triggers biometric prompt on appear when biometric unlock
 * is enabled. Shakes on wrong PIN (unless power save mode is active).
 *
 * @param pinHash The stored PBKDF2 PIN hash to verify against.
 * @param biometricUnlockEnabled Whether the user has enabled biometric unlock.
 * @param onUnlock Callback invoked when authentication succeeds.
 * @param modifier Modifier applied to the root layout.
 */
@Suppress("LongMethod")
@Composable
fun LockGateScreen(
    pinHash: String,
    biometricUnlockEnabled: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricAvailable =
        biometricUnlockEnabled &&
            BiometricGatekeeper.isAvailable(context, allowDeviceCredential = true)
    val isPowerSave = LocalPowerSaveMode.current
    val scope = rememberCoroutineScope()

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (biometricAvailable && activity != null) {
            BiometricGatekeeper.authenticate(
                activity = activity,
                title = "Unlock ZeroClaw",
                subtitle = "Authenticate to continue",
                allowDeviceCredential = true,
            ) { result ->
                if (result is AuthResult.Success) {
                    onUnlock()
                }
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GATE_HORIZONTAL_PADDING),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(LOCK_ICON_SIZE),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SPACING_MEDIUM))

            Text(
                text = "ZeroClaw is locked",
                style = MaterialTheme.typography.headlineSmall,
                modifier =
                    Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
            )

            Spacer(modifier = Modifier.height(SPACING_LARGE))

            LockPinDots(
                length = enteredPin.length,
                maxLength = MAX_PIN_LENGTH,
                shakeOffset = shakeOffset.value,
            )

            errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(SPACING_SMALL))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier =
                        Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }

            Spacer(modifier = Modifier.height(SPACING_LARGE))

            PinKeypad(
                onDigit = { digit ->
                    if (enteredPin.length < MAX_PIN_LENGTH) {
                        enteredPin += digit
                        errorMessage = null
                        if (enteredPin.length >= MIN_PIN_LENGTH) {
                            if (PinHasher.verify(enteredPin, pinHash)) {
                                onUnlock()
                            } else if (enteredPin.length == MAX_PIN_LENGTH) {
                                errorMessage = "Wrong PIN"
                                enteredPin = ""
                                if (!isPowerSave) {
                                    scope.launch {
                                        shakeOffset.animateTo(
                                            targetValue = SHAKE_OFFSET,
                                            animationSpec = tween(SHAKE_DURATION_MS),
                                        )
                                        shakeOffset.animateTo(
                                            targetValue = -SHAKE_OFFSET,
                                            animationSpec = tween(SHAKE_DURATION_MS),
                                        )
                                        shakeOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(SHAKE_DURATION_MS),
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                onBackspace = {
                    if (enteredPin.isNotEmpty()) {
                        enteredPin = enteredPin.dropLast(1)
                        errorMessage = null
                    }
                },
            )

            Spacer(modifier = Modifier.height(SPACING_MEDIUM))

            if (biometricAvailable) {
                IconButton(
                    onClick = {
                        if (activity != null) {
                            BiometricGatekeeper.authenticate(
                                activity = activity,
                                title = "Unlock ZeroClaw",
                                subtitle = "Authenticate to continue",
                                allowDeviceCredential = true,
                            ) { result ->
                                if (result is AuthResult.Success) {
                                    onUnlock()
                                }
                            }
                        }
                    },
                    modifier =
                        Modifier
                            .size(BIOMETRIC_BUTTON_SIZE)
                            .semantics { contentDescription = "Unlock with biometrics" },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(BIOMETRIC_ICON_SIZE),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(SPACING_SMALL))

            TextButton(onClick = { showForgotDialog = true }) {
                Text("Forgot PIN?")
            }
        }
    }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text("Forgot PIN?") },
            text = {
                Text(
                    "To reset your PIN, clear the app's data " +
                        "from Android Settings > Apps > ZeroClaw > Storage > Clear Data. " +
                        "This will remove all local settings.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showForgotDialog = false }) {
                    Text("OK")
                }
            },
        )
    }
}

/**
 * PIN dot indicators for the lock screen with shake animation support.
 *
 * @param length Number of digits entered.
 * @param maxLength Maximum PIN length.
 * @param shakeOffset Horizontal offset for shake animation.
 */
@Composable
private fun LockPinDots(
    length: Int,
    maxLength: Int,
    shakeOffset: Float,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DOT_SPACING),
        modifier =
            Modifier
                .offset { IntOffset(shakeOffset.toInt(), 0) }
                .semantics(mergeDescendants = true) {
                    contentDescription = "PIN entry, $length of $maxLength digits entered"
                },
    ) {
        for (i in 0 until maxLength) {
            val filled = i < length
            Box(
                modifier =
                    Modifier
                        .size(DOT_SIZE)
                        .clip(CircleShape)
                        .background(
                            if (filled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ).semantics { invisibleToUser() },
            )
        }
    }
}

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 6
private const val SHAKE_OFFSET = 20f
private const val SHAKE_DURATION_MS = 80

private val GATE_HORIZONTAL_PADDING = 32.dp
private val LOCK_ICON_SIZE = 48.dp
private val SPACING_SMALL = 8.dp
private val SPACING_MEDIUM = 16.dp
private val SPACING_LARGE = 24.dp
private val DOT_SIZE = 16.dp
private val DOT_SPACING = 12.dp
private val BIOMETRIC_BUTTON_SIZE = 56.dp
private val BIOMETRIC_ICON_SIZE = 32.dp
