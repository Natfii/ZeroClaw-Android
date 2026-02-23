/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zeroclaw.android.model.Plugin
import com.zeroclaw.android.model.PluginCategory
import com.zeroclaw.android.screen.helpers.fakePluginsState
import com.zeroclaw.android.ui.screen.plugins.PluginsContent
import com.zeroclaw.android.ui.screen.plugins.PluginsState
import com.zeroclaw.android.ui.screen.plugins.SyncUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose screen tests for [PluginsContent].
 */
@RunWith(AndroidJUnit4::class)
class PluginsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun installedTab_showsPluginName() {
        composeTestRule.setContent {
            PluginsContent(
                state = fakePluginsState(),
                edgeMargin = 16.dp,
                snackbarHostState = remember { SnackbarHostState() },
                onNavigateToDetail = {},
                onSelectTab = {},
                onSyncNow = {},
                onSearchChange = {},
                onToggle = {},
                onInstall = {},
                skillsTabContent = {},
            )
        }
        composeTestRule
            .onNodeWithText("HTTP Channel")
            .assertIsDisplayed()
    }

    @Test
    fun tabs_areAllVisible() {
        composeTestRule.setContent {
            PluginsContent(
                state = fakePluginsState(),
                edgeMargin = 16.dp,
                snackbarHostState = remember { SnackbarHostState() },
                onNavigateToDetail = {},
                onSelectTab = {},
                onSyncNow = {},
                onSearchChange = {},
                onToggle = {},
                onInstall = {},
                skillsTabContent = {},
            )
        }
        composeTestRule.onNodeWithText("Installed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Available").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skills").assertIsDisplayed()
    }

    @Test
    fun syncButton_isVisible() {
        composeTestRule.setContent {
            PluginsContent(
                state = fakePluginsState(),
                edgeMargin = 16.dp,
                snackbarHostState = remember { SnackbarHostState() },
                onNavigateToDetail = {},
                onSelectTab = {},
                onSyncNow = {},
                onSearchChange = {},
                onToggle = {},
                onInstall = {},
                skillsTabContent = {},
            )
        }
        composeTestRule
            .onNodeWithContentDescription("Sync plugin registry")
            .assertIsDisplayed()
    }

    @Test
    fun emptyPluginList_showsEmptyState() {
        composeTestRule.setContent {
            PluginsContent(
                state =
                    PluginsState(
                        plugins = emptyList(),
                        selectedTab = 0,
                        searchQuery = "",
                        syncState = SyncUiState.Idle,
                    ),
                edgeMargin = 16.dp,
                snackbarHostState = remember { SnackbarHostState() },
                onNavigateToDetail = {},
                onSelectTab = {},
                onSyncNow = {},
                onSearchChange = {},
                onToggle = {},
                onInstall = {},
                skillsTabContent = {},
            )
        }
        composeTestRule
            .onNodeWithText("No plugins installed yet")
            .assertIsDisplayed()
    }

    @Test
    fun availablePlugin_showsInstallButton() {
        composeTestRule.setContent {
            PluginsContent(
                state =
                    PluginsState(
                        plugins =
                            listOf(
                                Plugin(
                                    id = "p-1",
                                    name = "MQTT Channel",
                                    description = "MQTT support",
                                    version = "1.0.0",
                                    author = "ZeroClaw",
                                    category = PluginCategory.CHANNEL,
                                    isInstalled = false,
                                ),
                            ),
                        selectedTab = 1,
                        searchQuery = "",
                        syncState = SyncUiState.Idle,
                    ),
                edgeMargin = 16.dp,
                snackbarHostState = remember { SnackbarHostState() },
                onNavigateToDetail = {},
                onSelectTab = {},
                onSyncNow = {},
                onSearchChange = {},
                onToggle = {},
                onInstall = {},
                skillsTabContent = {},
            )
        }
        composeTestRule
            .onNodeWithText("Install")
            .assertIsDisplayed()
    }
}
