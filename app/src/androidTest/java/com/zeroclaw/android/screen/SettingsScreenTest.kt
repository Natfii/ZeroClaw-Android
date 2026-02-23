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
import com.zeroclaw.android.screen.helpers.fakeAppSettings
import com.zeroclaw.android.ui.screen.settings.SettingsContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose screen tests for [SettingsContent].
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sectionHeaders_areDisplayed() {
        composeTestRule.setContent {
            SettingsContent(
                settings = fakeAppSettings(),
                restartRequired = false,
                edgeMargin = 16.dp,
                onNavigate = {},
                onRerunWizard = {},
                onRestartDaemon = {},
                onThemeSelected = {},
            )
        }
        composeTestRule.onNodeWithText("Daemon").assertIsDisplayed()
        composeTestRule.onNodeWithText("Security").assertIsDisplayed()
        composeTestRule.onNodeWithText("Network").assertIsDisplayed()
        composeTestRule.onNodeWithText("App").assertIsDisplayed()
    }

    @Test
    fun settingsItems_areDisplayed() {
        composeTestRule.setContent {
            SettingsContent(
                settings = fakeAppSettings(),
                restartRequired = false,
                edgeMargin = 16.dp,
                onNavigate = {},
                onRerunWizard = {},
                onRestartDaemon = {},
                onThemeSelected = {},
            )
        }
        composeTestRule.onNodeWithText("Service Configuration").assertIsDisplayed()
        composeTestRule.onNodeWithText("API Keys").assertIsDisplayed()
        composeTestRule.onNodeWithText("Theme").assertIsDisplayed()
        composeTestRule.onNodeWithText("About").assertIsDisplayed()
    }

    @Test
    fun restartBanner_shownWhenRequired() {
        composeTestRule.setContent {
            SettingsContent(
                settings = fakeAppSettings(),
                restartRequired = true,
                edgeMargin = 16.dp,
                onNavigate = {},
                onRerunWizard = {},
                onRestartDaemon = {},
                onThemeSelected = {},
            )
        }
        composeTestRule
            .onNodeWithText("Restart daemon to apply changes")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Restart")
            .assertIsDisplayed()
    }

    @Test
    fun restartBanner_hiddenByDefault() {
        composeTestRule.setContent {
            SettingsContent(
                settings = fakeAppSettings(),
                restartRequired = false,
                edgeMargin = 16.dp,
                onNavigate = {},
                onRerunWizard = {},
                onRestartDaemon = {},
                onThemeSelected = {},
            )
        }
        composeTestRule
            .onNodeWithText("Restart daemon to apply changes")
            .assertDoesNotExist()
    }

    @Test
    fun themeSubtitle_showsSystemDefault() {
        composeTestRule.setContent {
            SettingsContent(
                settings = fakeAppSettings(),
                restartRequired = false,
                edgeMargin = 16.dp,
                onNavigate = {},
                onRerunWizard = {},
                onRestartDaemon = {},
                onThemeSelected = {},
            )
        }
        composeTestRule
            .onNodeWithText("System default")
            .assertIsDisplayed()
    }
}
