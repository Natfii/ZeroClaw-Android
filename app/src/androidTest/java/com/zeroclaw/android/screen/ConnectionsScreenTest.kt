/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zeroclaw.android.screen.helpers.fakeAgentsState
import com.zeroclaw.android.ui.screen.agents.AgentsContent
import com.zeroclaw.android.ui.screen.agents.AgentsState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose screen tests for [AgentsContent].
 */
@RunWith(AndroidJUnit4::class)
class ConnectionsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun agentsList_showsAgentNames() {
        composeTestRule.setContent {
            AgentsContent(
                state = fakeAgentsState(),
                edgeMargin = 16.dp,
                onNavigateToDetail = {},
                onNavigateToAdd = {},
                onSearchChange = {},
                onToggleAgent = {},
            )
        }
        composeTestRule
            .onNodeWithText("Test Agent")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Backup Agent")
            .assertIsDisplayed()
    }

    @Test
    fun emptyState_showsNoConnectionsMessage() {
        composeTestRule.setContent {
            AgentsContent(
                state = AgentsState(agents = emptyList(), searchQuery = ""),
                edgeMargin = 16.dp,
                onNavigateToDetail = {},
                onNavigateToAdd = {},
                onSearchChange = {},
                onToggleAgent = {},
            )
        }
        composeTestRule
            .onNodeWithText("No connections configured yet")
            .assertIsDisplayed()
    }

    @Test
    fun searchNoResults_showsFilteredMessage() {
        composeTestRule.setContent {
            AgentsContent(
                state = AgentsState(agents = emptyList(), searchQuery = "xyz"),
                edgeMargin = 16.dp,
                onNavigateToDetail = {},
                onNavigateToAdd = {},
                onSearchChange = {},
                onToggleAgent = {},
            )
        }
        composeTestRule
            .onNodeWithText("No connections match your search")
            .assertIsDisplayed()
    }

    @Test
    fun fab_isVisible() {
        composeTestRule.setContent {
            AgentsContent(
                state = fakeAgentsState(),
                edgeMargin = 16.dp,
                onNavigateToDetail = {},
                onNavigateToAdd = {},
                onSearchChange = {},
                onToggleAgent = {},
            )
        }
        composeTestRule
            .onNodeWithContentDescription("Add new connection")
            .assertIsDisplayed()
    }

    @Test
    fun enabledAgent_showsToggleOn() {
        composeTestRule.setContent {
            AgentsContent(
                state = fakeAgentsState(),
                edgeMargin = 16.dp,
                onNavigateToDetail = {},
                onNavigateToAdd = {},
                onSearchChange = {},
                onToggleAgent = {},
            )
        }
        composeTestRule
            .onNodeWithContentDescription("Test Agent enabled")
            .assertIsOn()
    }

    @Test
    fun disabledAgent_showsToggleOff() {
        composeTestRule.setContent {
            AgentsContent(
                state = fakeAgentsState(),
                edgeMargin = 16.dp,
                onNavigateToDetail = {},
                onNavigateToAdd = {},
                onSearchChange = {},
                onToggleAgent = {},
            )
        }
        composeTestRule
            .onNodeWithContentDescription("Backup Agent disabled")
            .assertIsOff()
    }
}
