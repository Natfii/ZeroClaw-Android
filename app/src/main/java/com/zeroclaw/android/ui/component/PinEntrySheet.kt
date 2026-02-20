/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.util.PinHasher

/**
 * Modal bottom sheet for PIN setup or change.
 *
 * In [PinEntryMode.SETUP], the user enters a PIN (4-6 digits) then
 * confirms it. In [PinEntryMode.CHANGE], the user first enters their
 * current PIN for verification.
 *
 * @param mode Whether this is a first-time setup or a PIN change.
 * @param currentPinHash Existing hash for verification in [PinEntryMode.CHANGE].
 * @param onPinSet Callback with the new PIN hash on success.
 * @param onDismiss Callback when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CognitiveComplexMethod", "LongMethod")
@Composable
fun PinEntrySheet(
    mode: PinEntryMode,
    currentPinHash: String,
    onPinSet: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var phase by remember {
        mutableStateOf(if (mode == PinEntryMode.CHANGE) Phase.CURRENT else Phase.ENTER)
    }
    var enteredPin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val title =
        when (phase) {
            Phase.CURRENT -> "Enter current PIN"
            Phase.ENTER -> if (mode == PinEntryMode.SETUP) "Create a PIN" else "Enter new PIN"
            Phase.CONFIRM -> "Confirm PIN"
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SHEET_HORIZONTAL_PADDING, vertical = SHEET_VERTICAL_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(SPACING_SMALL))

            Text(
                text =
                    if (phase == Phase.ENTER) {
                        "Enter 4-6 digits, then tap Next"
                    } else {
                        "4-6 digits"
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(SPACING_LARGE))

            PinDots(
                length = enteredPin.length,
                maxLength = MAX_PIN_LENGTH,
            )

            errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(SPACING_SMALL))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(SPACING_LARGE))

            PinKeypad(
                onDigit = { digit ->
                    if (enteredPin.length < MAX_PIN_LENGTH) {
                        enteredPin += digit
                        errorMessage = null
                        if (phase != Phase.ENTER && enteredPin.length >= MIN_PIN_LENGTH) {
                            handlePinEntry(
                                phase = phase,
                                enteredPin = enteredPin,
                                firstPin = firstPin,
                                currentPinHash = currentPinHash,
                                onAdvance = { nextPhase, savedFirst ->
                                    phase = nextPhase
                                    firstPin = savedFirst
                                    enteredPin = ""
                                },
                                onError = { msg ->
                                    errorMessage = msg
                                    enteredPin = ""
                                },
                                onComplete = onPinSet,
                            )
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

            if (phase == Phase.ENTER && enteredPin.length >= MIN_PIN_LENGTH) {
                Spacer(modifier = Modifier.height(SPACING_SMALL))
                FilledTonalButton(
                    onClick = {
                        handlePinEntry(
                            phase = phase,
                            enteredPin = enteredPin,
                            firstPin = firstPin,
                            currentPinHash = currentPinHash,
                            onAdvance = { nextPhase, savedFirst ->
                                phase = nextPhase
                                firstPin = savedFirst
                                enteredPin = ""
                            },
                            onError = { msg ->
                                errorMessage = msg
                                enteredPin = ""
                            },
                            onComplete = onPinSet,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Next")
                }
            }

            Spacer(modifier = Modifier.height(SPACING_LARGE))
        }
    }
}

/**
 * Row of dots indicating PIN entry progress.
 *
 * @param length Number of digits entered so far.
 * @param maxLength Maximum PIN length (determines total dot count).
 */
@Composable
private fun PinDots(
    length: Int,
    maxLength: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DOT_SPACING),
        modifier =
            Modifier.semantics {
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
                        ),
            )
        }
    }
}

private enum class Phase {
    CURRENT,
    ENTER,
    CONFIRM,
}

@Suppress("LongParameterList")
private fun handlePinEntry(
    phase: Phase,
    enteredPin: String,
    firstPin: String,
    currentPinHash: String,
    onAdvance: (Phase, String) -> Unit,
    onError: (String) -> Unit,
    onComplete: (String) -> Unit,
) {
    when (phase) {
        Phase.CURRENT -> {
            if (PinHasher.verify(enteredPin, currentPinHash)) {
                onAdvance(Phase.ENTER, "")
            } else {
                onError("Wrong PIN")
            }
        }
        Phase.ENTER -> {
            onAdvance(Phase.CONFIRM, enteredPin)
        }
        Phase.CONFIRM -> {
            if (enteredPin == firstPin) {
                onComplete(PinHasher.hash(enteredPin))
            } else {
                onError("PINs don't match")
                onAdvance(Phase.ENTER, "")
            }
        }
    }
}

/**
 * Mode of the PIN entry sheet.
 */
enum class PinEntryMode {
    /** First-time setup: enter PIN, then confirm. */
    SETUP,

    /** Change existing PIN: enter current, then new, then confirm. */
    CHANGE,
}

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 6

private val SHEET_HORIZONTAL_PADDING = 24.dp
private val SHEET_VERTICAL_PADDING = 16.dp
private val SPACING_SMALL = 8.dp
private val SPACING_LARGE = 24.dp
private val DOT_SIZE = 16.dp
private val DOT_SPACING = 12.dp
