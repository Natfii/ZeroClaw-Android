/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zeroclaw.android.screen.helpers.fakeDashboardState
import com.zeroclaw.android.screen.helpers.fakeRunningDashboardState
import com.zeroclaw.android.ui.screen.dashboard.DashboardContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose screen tests for [DashboardContent].
 */
@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun stoppedState_showsStartButton() {
        composeTestRule.setContent {
            DashboardContent(
                state = fakeDashboardState(),
                edgeMargin = 16.dp,
                onNavigateToCostDetail = {},
                onNavigateToCronJobs = {},
                onStartDaemon = {},
                onStopDaemon = {},
                onDismissKeyRejection = {},
            )
        }
        composeTestRule
            .onNodeWithText("Start Daemon")
            .assertIsDisplayed()
    }

    @Test
    fun runningState_showsStopButton() {
        composeTestRule.setContent {
            DashboardContent(
                state = fakeRunningDashboardState(),
                edgeMargin = 16.dp,
                onNavigateToCostDetail = {},
                onNavigateToCronJobs = {},
                onStartDaemon = {},
                onStopDaemon = {},
                onDismissKeyRejection = {},
            )
        }
        composeTestRule
            .onNodeWithText("Stop Daemon")
            .assertIsDisplayed()
    }

    @Test
    fun runningState_showsAgentAndPluginCounts() {
        composeTestRule.setContent {
            DashboardContent(
                state = fakeRunningDashboardState(),
                edgeMargin = 16.dp,
                onNavigateToCostDetail = {},
                onNavigateToCronJobs = {},
                onStartDaemon = {},
                onStopDaemon = {},
                onDismissKeyRejection = {},
            )
        }
        composeTestRule
            .onNodeWithText("2")
            .assertIsDisplayed()
    }
}
