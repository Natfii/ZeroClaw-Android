/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription

/** Number of trailing characters shown unmasked. */
private const val VISIBLE_SUFFIX_LENGTH = 4

/** Mask placeholder string (bullet character). */
private const val MASK_STRING = "\u2022"

/** Minimum number of mask characters to display for short or empty keys. */
private const val MINIMUM_MASK_LENGTH = 4

/**
 * Displays text with all but the last [VISIBLE_SUFFIX_LENGTH] characters
 * masked as bullet dots.
 *
 * Uses [clearAndSetSemantics] to replace the entire semantic tree node
 * with a safe description, preventing screen readers and accessibility
 * services from reading the actual secret text in either state.
 *
 * @param text The full secret text to mask.
 * @param revealed Whether to show the full text unmasked.
 * @param modifier Modifier applied to the [Text].
 */
@Composable
fun MaskedText(
    text: String,
    revealed: Boolean,
    modifier: Modifier = Modifier,
) {
    val suffix =
        if (text.length > VISIBLE_SUFFIX_LENGTH) {
            ", ending in ${text.takeLast(VISIBLE_SUFFIX_LENGTH)}"
        } else {
            ""
        }

    if (revealed) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                modifier.clearAndSetSemantics {
                    contentDescription =
                        "API key revealed$suffix. Tap hide to conceal credential."
                },
        )
    } else {
        Text(
            text = maskText(text),
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                modifier.clearAndSetSemantics {
                    contentDescription = "API key hidden$suffix"
                },
        )
    }
}

/**
 * Masks the input text, showing only the last [VISIBLE_SUFFIX_LENGTH] characters.
 *
 * Keys that are empty or shorter than [VISIBLE_SUFFIX_LENGTH] are fully masked
 * with at least [MINIMUM_MASK_LENGTH] mask characters to avoid leaking key
 * length or content.
 *
 * @param text Full secret text.
 * @return Masked representation.
 */
internal fun maskText(text: String): String {
    if (text.isEmpty()) return MASK_STRING.repeat(MINIMUM_MASK_LENGTH)
    if (text.length <= VISIBLE_SUFFIX_LENGTH) {
        return MASK_STRING.repeat(MINIMUM_MASK_LENGTH)
    }
    val maskLength = text.length - VISIBLE_SUFFIX_LENGTH
    return MASK_STRING.repeat(maskLength) +
        text.takeLast(VISIBLE_SUFFIX_LENGTH)
}
