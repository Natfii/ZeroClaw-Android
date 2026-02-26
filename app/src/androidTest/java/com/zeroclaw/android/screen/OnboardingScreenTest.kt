/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zeroclaw.android.screen.helpers.fakeOnboardingState
import com.zeroclaw.android.ui.screen.onboarding.OnboardingContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose screen tests for [OnboardingContent].
 */
@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun firstStep_showsStepIndicator() {
        composeTestRule.setContent {
            OnboardingContent(
                state = fakeOnboardingState(),
                snackbarHostState = remember { SnackbarHostState() },
                onNextStep = {},
                onPreviousStep = {},
                stepContent = { Text("Step $it") },
            )
        }
        composeTestRule
            .onNodeWithText("Step 1 of 9")
            .assertIsDisplayed()
    }

    @Test
    fun firstStep_showsNextButton() {
        composeTestRule.setContent {
            OnboardingContent(
                state = fakeOnboardingState(),
                snackbarHostState = remember { SnackbarHostState() },
                onNextStep = {},
                onPreviousStep = {},
                stepContent = { Text("Step $it") },
            )
        }
        composeTestRule
            .onNodeWithText("Next")
            .assertIsDisplayed()
    }

    @Test
    fun firstStep_hidesBackButton() {
        composeTestRule.setContent {
            OnboardingContent(
                state = fakeOnboardingState(),
                snackbarHostState = remember { SnackbarHostState() },
                onNextStep = {},
                onPreviousStep = {},
                stepContent = { Text("Step $it") },
            )
        }
        composeTestRule
            .onNodeWithText("Back")
            .assertDoesNotExist()
    }

    @Test
    fun middleStep_showsBackAndNext() {
        composeTestRule.setContent {
            OnboardingContent(
                state = fakeOnboardingState().copy(currentStep = 2),
                snackbarHostState = remember { SnackbarHostState() },
                onNextStep = {},
                onPreviousStep = {},
                stepContent = { Text("Step $it") },
            )
        }
        composeTestRule.onNodeWithText("Back").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next").assertIsDisplayed()
        composeTestRule.onNodeWithText("Step 3 of 9").assertIsDisplayed()
    }

    @Test
    fun stepContent_isRendered() {
        composeTestRule.setContent {
            OnboardingContent(
                state = fakeOnboardingState(),
                snackbarHostState = remember { SnackbarHostState() },
                onNextStep = {},
                onPreviousStep = {},
                stepContent = { Text("Custom step content") },
            )
        }
        composeTestRule
            .onNodeWithText("Custom step content")
            .assertIsDisplayed()
    }
}
