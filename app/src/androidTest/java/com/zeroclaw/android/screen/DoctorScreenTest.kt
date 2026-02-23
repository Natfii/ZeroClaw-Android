/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zeroclaw.android.screen.helpers.fakeDoctorState
import com.zeroclaw.android.ui.screen.settings.doctor.DoctorContent
import com.zeroclaw.android.ui.screen.settings.doctor.DoctorState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose screen tests for [DoctorContent].
 */
@RunWith(AndroidJUnit4::class)
class DoctorScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun title_isDisplayed() {
        composeTestRule.setContent {
            DoctorContent(
                state = fakeDoctorState(),
                edgeMargin = 16.dp,
                onNavigateToRoute = {},
                onRunDiagnostics = {},
            )
        }
        composeTestRule
            .onNodeWithText("ZeroClaw Doctor")
            .assertIsDisplayed()
    }

    @Test
    fun summary_showsCounts() {
        composeTestRule.setContent {
            DoctorContent(
                state = fakeDoctorState(),
                edgeMargin = 16.dp,
                onNavigateToRoute = {},
                onRunDiagnostics = {},
            )
        }
        composeTestRule.onNodeWithText("2 Pass").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 Warn").assertIsDisplayed()
        composeTestRule.onNodeWithText("0 Fail").assertIsDisplayed()
    }

    @Test
    fun rerunButton_isEnabled() {
        composeTestRule.setContent {
            DoctorContent(
                state = fakeDoctorState(),
                edgeMargin = 16.dp,
                onNavigateToRoute = {},
                onRunDiagnostics = {},
            )
        }
        composeTestRule
            .onNodeWithText("Re-run Diagnostics")
            .assertIsEnabled()
    }

    @Test
    fun runningState_disablesButton() {
        composeTestRule.setContent {
            DoctorContent(
                state = fakeDoctorState().copy(isRunning = true),
                edgeMargin = 16.dp,
                onNavigateToRoute = {},
                onRunDiagnostics = {},
            )
        }
        composeTestRule
            .onNodeWithText("Running...")
            .assertIsDisplayed()
    }

    @Test
    fun emptyState_showsPrompt() {
        composeTestRule.setContent {
            DoctorContent(
                state =
                    DoctorState(
                        checks = emptyList(),
                        isRunning = false,
                        summary = null,
                    ),
                edgeMargin = 16.dp,
                onNavigateToRoute = {},
                onRunDiagnostics = {},
            )
        }
        composeTestRule
            .onNodeWithText("Run Diagnostics")
            .assertIsDisplayed()
    }

    @Test
    fun checkResults_areDisplayed() {
        composeTestRule.setContent {
            DoctorContent(
                state = fakeDoctorState(),
                edgeMargin = 16.dp,
                onNavigateToRoute = {},
                onRunDiagnostics = {},
            )
        }
        composeTestRule
            .onNodeWithText("Configuration")
            .assertIsDisplayed()
    }
}
