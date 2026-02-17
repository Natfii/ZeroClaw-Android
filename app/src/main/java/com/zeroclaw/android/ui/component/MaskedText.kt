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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics

/** Number of trailing characters shown unmasked. */
private const val VISIBLE_SUFFIX_LENGTH = 4

/** Character used as the mask placeholder. */
private const val MASK_CHAR = '\u2022'

/** Minimum number of mask characters to display for short or empty keys. */
private const val MINIMUM_MASK_LENGTH = 4

/**
 * Displays text with all but the last [VISIBLE_SUFFIX_LENGTH] characters
 * masked as bullet dots.
 *
 * The masked portion is hidden from screen readers via
 * [invisibleToUser] semantics to prevent leaking secrets to
 * accessibility services.
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
    if (revealed) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                modifier.semantics {
                    contentDescription =
                        "API key revealed, tap hide to conceal"
                    @Suppress("DEPRECATION")
                    invisibleToUser()
                },
        )
    } else {
        Text(
            text = maskText(text),
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                modifier.semantics {
                    @Suppress("DEPRECATION")
                    invisibleToUser()
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
    if (text.isEmpty()) return MASK_CHAR.toString().repeat(MINIMUM_MASK_LENGTH)
    if (text.length <= VISIBLE_SUFFIX_LENGTH) {
        return MASK_CHAR.toString().repeat(MINIMUM_MASK_LENGTH)
    }
    val maskLength = text.length - VISIBLE_SUFFIX_LENGTH
    return MASK_CHAR.toString().repeat(maskLength) +
        text.takeLast(VISIBLE_SUFFIX_LENGTH)
}
