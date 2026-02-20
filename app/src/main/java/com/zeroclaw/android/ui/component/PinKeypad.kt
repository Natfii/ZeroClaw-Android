/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Stateless 4x3 numeric keypad for PIN entry.
 *
 * Renders digits 1-9, an empty cell, 0, and a backspace button.
 * Each button meets the 48x48dp minimum touch target.
 *
 * @param onDigit Callback invoked when a digit button is tapped.
 * @param onBackspace Callback invoked when the backspace button is tapped.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun PinKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KEYPAD_ROW_SPACING),
    ) {
        KeypadRow(digits = charArrayOf('1', '2', '3'), onDigit = onDigit)
        KeypadRow(digits = charArrayOf('4', '5', '6'), onDigit = onDigit)
        KeypadRow(digits = charArrayOf('7', '8', '9'), onDigit = onDigit)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Box(modifier = Modifier.size(BUTTON_SIZE))
            DigitButton(digit = '0', onClick = { onDigit('0') })
            IconButton(
                onClick = onBackspace,
                modifier =
                    Modifier
                        .size(BUTTON_SIZE)
                        .semantics { contentDescription = "Delete last digit" },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun KeypadRow(
    digits: CharArray,
    onDigit: (Char) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        digits.forEach { digit ->
            DigitButton(digit = digit, onClick = { onDigit(digit) })
        }
    }
}

@Composable
private fun DigitButton(
    digit: Char,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier =
            Modifier
                .size(BUTTON_SIZE)
                .semantics { contentDescription = "Digit $digit" },
    ) {
        Text(
            text = digit.toString(),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

/** Minimum button size meeting touch target requirements (64dp). */
private val BUTTON_SIZE = 64.dp

/** Vertical spacing between keypad rows (8dp). */
private val KEYPAD_ROW_SPACING = 8.dp
