/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zeroclaw.android.model.ServiceState
import com.zeroclaw.android.screen.helpers.fakeTerminalState
import com.zeroclaw.android.ui.screen.terminal.TerminalBlock
import com.zeroclaw.android.ui.screen.terminal.TerminalContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose screen tests for [TerminalContent].
 *
 * Uses the stateless [TerminalContent] composable with fake state to verify
 * rendering of terminal blocks, input bar controls, loading indicators,
 * and error display.
 */
@RunWith(AndroidJUnit4::class)
class TerminalScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun terminalContent_rendersSystemBlock() {
        val state = fakeTerminalState()
        composeTestRule.setContent {
            TerminalContent(
                state = state,
                serviceState = ServiceState.RUNNING,
                onSubmit = {},
                onAttachImages = {},
                onRemoveImage = {},
                edgeMargin = 16.dp,
            )
        }
        composeTestRule
            .onNodeWithText("ZeroClaw Terminal v0.0.29 \u2014 Type /help for commands")
            .assertIsDisplayed()
    }

    @Test
    fun inputBar_hasSendButton() {
        composeTestRule.setContent {
            TerminalContent(
                state = fakeTerminalState(),
                serviceState = ServiceState.RUNNING,
                onSubmit = {},
                onAttachImages = {},
                onRemoveImage = {},
                edgeMargin = 16.dp,
            )
        }
        composeTestRule
            .onNodeWithContentDescription("Send")
            .assertIsDisplayed()
    }

    @Test
    fun inputBar_hasAttachButton() {
        composeTestRule.setContent {
            TerminalContent(
                state = fakeTerminalState(),
                serviceState = ServiceState.RUNNING,
                onSubmit = {},
                onAttachImages = {},
                onRemoveImage = {},
                edgeMargin = 16.dp,
            )
        }
        composeTestRule
            .onNodeWithContentDescription("Attach images")
            .assertIsDisplayed()
    }

    @Test
    fun spinner_showsWhenLoading() {
        val state = fakeTerminalState().copy(isLoading = true)
        composeTestRule.setContent {
            TerminalContent(
                state = state,
                serviceState = ServiceState.RUNNING,
                onSubmit = {},
                onAttachImages = {},
                onRemoveImage = {},
                edgeMargin = 16.dp,
            )
        }
        composeTestRule
            .onNodeWithText("Thinking\u2026")
            .assertIsDisplayed()
    }

    @Test
    fun errorBlock_showsErrorPrefix() {
        val state =
            fakeTerminalState().copy(
                blocks =
                    listOf(
                        TerminalBlock.Error(
                            id = 2,
                            timestamp = System.currentTimeMillis(),
                            message = "Connection refused",
                        ),
                    ),
            )
        composeTestRule.setContent {
            TerminalContent(
                state = state,
                serviceState = ServiceState.RUNNING,
                onSubmit = {},
                onAttachImages = {},
                onRemoveImage = {},
                edgeMargin = 16.dp,
            )
        }
        composeTestRule
            .onNodeWithText("Error: Connection refused")
            .assertIsDisplayed()
    }

    @Test
    fun terminalHeader_showsStatusDot() {
        composeTestRule.setContent {
            TerminalContent(
                state = fakeTerminalState(),
                serviceState = ServiceState.RUNNING,
                onSubmit = {},
                onAttachImages = {},
                onRemoveImage = {},
                edgeMargin = 16.dp,
            )
        }
        composeTestRule
            .onNodeWithContentDescription("ZeroClaw Terminal, status: running")
            .assertIsDisplayed()
    }
}
