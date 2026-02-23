/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zeroclaw.android.screen.helpers.fakeConsoleState
import com.zeroclaw.android.ui.screen.console.ConsoleContent
import com.zeroclaw.android.ui.screen.console.ConsoleState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose screen tests for [ConsoleContent].
 */
@RunWith(AndroidJUnit4::class)
class ConsoleScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun messages_areDisplayed() {
        composeTestRule.setContent {
            ConsoleContent(
                state = fakeConsoleState(),
                edgeMargin = 16.dp,
                onSendMessage = {},
                onClearHistory = {},
                onRemoveImage = {},
                onRetryMessage = {},
                onAttachImages = {},
            )
        }
        composeTestRule
            .onNodeWithText("Hello")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Hello! How can I help?")
            .assertIsDisplayed()
    }

    @Test
    fun loadingState_showsThinkingIndicator() {
        composeTestRule.setContent {
            ConsoleContent(
                state = fakeConsoleState().copy(isLoading = true),
                edgeMargin = 16.dp,
                onSendMessage = {},
                onClearHistory = {},
                onRemoveImage = {},
                onRetryMessage = {},
                onAttachImages = {},
            )
        }
        composeTestRule
            .onNodeWithText("Thinking...")
            .assertIsDisplayed()
    }

    @Test
    fun emptyState_clearButtonDisabled() {
        composeTestRule.setContent {
            ConsoleContent(
                state =
                    ConsoleState(
                        messages = emptyList(),
                        isLoading = false,
                        pendingImages = emptyList(),
                        isProcessingImages = false,
                    ),
                edgeMargin = 16.dp,
                onSendMessage = {},
                onClearHistory = {},
                onRemoveImage = {},
                onRetryMessage = {},
                onAttachImages = {},
            )
        }
        composeTestRule
            .onNodeWithContentDescription("Clear chat history")
            .assertIsNotEnabled()
    }

    @Test
    fun withMessages_clearButtonEnabled() {
        composeTestRule.setContent {
            ConsoleContent(
                state = fakeConsoleState(),
                edgeMargin = 16.dp,
                onSendMessage = {},
                onClearHistory = {},
                onRemoveImage = {},
                onRetryMessage = {},
                onAttachImages = {},
            )
        }
        composeTestRule
            .onNodeWithContentDescription("Clear chat history")
            .assertIsEnabled()
    }

    @Test
    fun sendButton_isPresent() {
        composeTestRule.setContent {
            ConsoleContent(
                state = fakeConsoleState(),
                edgeMargin = 16.dp,
                onSendMessage = {},
                onClearHistory = {},
                onRemoveImage = {},
                onRetryMessage = {},
                onAttachImages = {},
            )
        }
        composeTestRule
            .onNodeWithContentDescription("Send message")
            .assertIsDisplayed()
    }

    @Test
    fun attachButton_isPresent() {
        composeTestRule.setContent {
            ConsoleContent(
                state = fakeConsoleState(),
                edgeMargin = 16.dp,
                onSendMessage = {},
                onClearHistory = {},
                onRemoveImage = {},
                onRetryMessage = {},
                onAttachImages = {},
            )
        }
        composeTestRule
            .onNodeWithContentDescription("Attach images")
            .assertIsDisplayed()
    }
}
