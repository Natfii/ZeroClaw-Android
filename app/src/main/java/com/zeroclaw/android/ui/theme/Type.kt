/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.theme

import androidx.compose.material3.Typography

/**
 * Custom typography scale for ZeroClaw.
 *
 * Uses the Material 3 default type scale which already specifies line
 * heights in `sp` (not `dp`), ensuring correct behaviour under Android
 * 14+ nonlinear font scaling. Defined explicitly to provide a stable
 * hook for future customisation.
 */
val ZeroClawTypography = Typography()
