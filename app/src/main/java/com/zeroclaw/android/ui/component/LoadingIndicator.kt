/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.util.LocalPowerSaveMode

/**
 * Loading indicator that falls back to static text in power-save mode.
 *
 * When [LocalPowerSaveMode] is true (system power saver or Samsung
 * Battery Guardian with `ANIMATOR_DURATION_SCALE = 0`), displays
 * static ellipsis text instead of an animated spinner.
 *
 * @param modifier Modifier applied to the indicator.
 */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    if (LocalPowerSaveMode.current) {
        Text(
            text = "\u2026",
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
    } else {
        CircularProgressIndicator(
            modifier = modifier.size(24.dp),
        )
    }
}
