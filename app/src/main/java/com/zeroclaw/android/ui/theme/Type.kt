/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.zeroclaw.android.R

/**
 * Custom typography scale for ZeroClaw.
 *
 * Uses the Material 3 default type scale which already specifies line
 * heights in `sp` (not `dp`), ensuring correct behaviour under Android
 * 14+ nonlinear font scaling. Defined explicitly to provide a stable
 * hook for future customisation.
 */
val ZeroClawTypography = Typography()

/**
 * JetBrains Mono font family for terminal and code display.
 *
 * Bundles the Regular (400) and Bold (700) weights of JetBrains Mono v2.304,
 * a typeface designed for developers with increased letter height, distinct
 * character forms for ambiguous glyphs (0/O, 1/l/I), and coding ligatures.
 */
val JetBrainsMonoFamily =
    FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    )

/**
 * Base M3 type scale instance used to derive [TerminalTypography] styles.
 *
 * Kept private to avoid exposing a second default-configured [Typography]
 * alongside [ZeroClawTypography].
 */
private val BaseTypography = Typography()

/**
 * Typography scale for terminal and REPL screens.
 *
 * Overrides the body, label, and title styles from the default Material 3
 * type scale with [JetBrainsMonoFamily], preserving all `sp`-based sizes
 * and line heights for Android 14+ nonlinear font scaling. Display and
 * headline styles are omitted because terminal screens do not use them.
 */
val TerminalTypography =
    Typography(
        bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = JetBrainsMonoFamily),
        bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
        bodySmall = BaseTypography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
        labelLarge = BaseTypography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
        labelMedium = BaseTypography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
        labelSmall = BaseTypography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
        titleLarge = BaseTypography.titleLarge.copy(fontFamily = JetBrainsMonoFamily),
        titleMedium = BaseTypography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
        titleSmall = BaseTypography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
    )
