/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.terminal

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.ui.theme.TerminalTypography
import com.zeroclaw.android.util.LocalPowerSaveMode
import kotlinx.coroutines.delay

/** Braille spinner animation frames matching the Claude Code CLI thinking indicator. */
private val BRAILLE_FRAMES =
    charArrayOf(
        '\u280B',
        '\u2819',
        '\u2839',
        '\u2838',
        '\u283C',
        '\u2834',
        '\u2826',
        '\u2827',
        '\u2807',
        '\u280F',
    )

/** Interval between spinner frame advances in milliseconds. */
private const val SPINNER_INTERVAL_MS = 80L

/** Spacing between the spinner character and the label text. */
private const val SPINNER_LABEL_SPACING_DP = 8

/**
 * Braille dot spinner inspired by the Claude Code CLI thinking indicator.
 *
 * Cycles through ten braille-pattern characters at 80 ms intervals to
 * convey an in-progress operation. When [LocalPowerSaveMode] is active,
 * the animation is replaced with a static ellipsis to conserve battery.
 *
 * Accessibility: the container [Row] is announced as a polite live region
 * with a content description matching [label], so screen readers announce
 * the status without interrupting the user.
 *
 * @param label Descriptive text displayed beside the spinner (e.g. "Thinking").
 * @param modifier Modifier applied to the container [Row].
 */
@Composable
fun BrailleSpinner(
    label: String,
    modifier: Modifier = Modifier,
) {
    val isPowerSave = LocalPowerSaveMode.current
    var frameIndex by remember { mutableIntStateOf(0) }

    if (!isPowerSave) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(SPINNER_INTERVAL_MS)
                frameIndex = (frameIndex + 1) % BRAILLE_FRAMES.size
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier.semantics(mergeDescendants = true) {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Text(
            text = if (isPowerSave) "\u2026" else BRAILLE_FRAMES[frameIndex].toString(),
            style = TerminalTypography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(SPINNER_LABEL_SPACING_DP.dp))
        Text(
            text = label,
            style = TerminalTypography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
