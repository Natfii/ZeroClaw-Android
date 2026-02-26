/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding.steps

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.zeroclaw.android.ui.component.setup.AutonomyPicker
import com.zeroclaw.android.ui.theme.ZeroClawTheme

/**
 * Onboarding step for configuring agent autonomy level.
 *
 * Thin wrapper around [AutonomyPicker] that delegates all rendering to
 * the reusable component. The onboarding screen provides the state and
 * callbacks via the coordinator.
 *
 * @param autonomyLevel Current autonomy level identifier.
 * @param onAutonomyLevelChanged Callback when the user selects a different level.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun SecurityStep(
    autonomyLevel: String,
    onAutonomyLevelChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AutonomyPicker(
        selectedLevel = autonomyLevel,
        onLevelChanged = onAutonomyLevelChanged,
        modifier = modifier,
    )
}

@Preview(name = "Security Step - Supervised")
@Composable
private fun PreviewSupervised() {
    ZeroClawTheme {
        SecurityStep(
            autonomyLevel = "supervised",
            onAutonomyLevelChanged = {},
        )
    }
}

@Preview(
    name = "Security Step - Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PreviewDark() {
    ZeroClawTheme {
        SecurityStep(
            autonomyLevel = "constrained",
            onAutonomyLevelChanged = {},
        )
    }
}
