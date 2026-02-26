/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding

import com.zeroclaw.android.data.validation.ValidationResult
import com.zeroclaw.android.model.ChannelType
import com.zeroclaw.android.ui.screen.onboarding.state.ActivationStepState
import com.zeroclaw.android.ui.screen.onboarding.state.ChannelSelectionState
import com.zeroclaw.android.ui.screen.onboarding.state.ChannelSubFlowState
import com.zeroclaw.android.ui.screen.onboarding.state.IdentityStepState
import com.zeroclaw.android.ui.screen.onboarding.state.MemoryStepState
import com.zeroclaw.android.ui.screen.onboarding.state.ProviderStepState
import com.zeroclaw.android.ui.screen.onboarding.state.SecurityStepState
import com.zeroclaw.android.ui.screen.onboarding.state.TunnelStepState
import com.zeroclaw.android.ui.screen.onboarding.state.WelcomeStepState
import java.util.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for the onboarding coordinator step states and navigation logic.
 *
 * Tests the 9-step navigation bounds, data class defaults and copy semantics,
 * and channel sub-flow state management. The coordinator ViewModel itself
 * requires an Android context and will be integration-tested when wired to the UI.
 */
@DisplayName("OnboardingCoordinator")
class OnboardingCoordinatorTest {
    /** Total steps in the 9-step wizard. */
    private val totalSteps = 9

    @Nested
    @DisplayName("Step navigation")
    inner class StepNavigation {
        @Test
        @DisplayName("initial step is 0 and total is 9")
        fun `initial step is 0 and total is 9`() {
            val tracker = NineStepTracker()
            assertEquals(0, tracker.current)
            assertEquals(totalSteps, tracker.totalSteps)
        }

        @Test
        @DisplayName("nextStep advances through all 9 steps")
        fun `nextStep advances through all 9 steps`() {
            val tracker = NineStepTracker()
            repeat(totalSteps - 1) { tracker.next() }
            assertEquals(totalSteps - 1, tracker.current)
        }

        @Test
        @DisplayName("nextStep does not exceed max step")
        fun `nextStep does not exceed max step`() {
            val tracker = NineStepTracker()
            repeat(totalSteps + 5) { tracker.next() }
            assertEquals(totalSteps - 1, tracker.current)
        }

        @Test
        @DisplayName("previousStep goes back within bounds")
        fun `previousStep goes back within bounds`() {
            val tracker = NineStepTracker()
            repeat(5) { tracker.next() }
            assertEquals(5, tracker.current)
            repeat(3) { tracker.previous() }
            assertEquals(2, tracker.current)
        }

        @Test
        @DisplayName("previousStep does not go below 0")
        fun `previousStep does not go below 0`() {
            val tracker = NineStepTracker()
            tracker.previous()
            assertEquals(0, tracker.current)
            tracker.next()
            tracker.previous()
            tracker.previous()
            assertEquals(0, tracker.current)
        }
    }

    @Nested
    @DisplayName("Step state defaults")
    inner class StepStateDefaults {
        @Test
        @DisplayName("WelcomeStepState defaults to unacknowledged")
        fun `WelcomeStepState defaults`() {
            val state = WelcomeStepState()
            assertFalse(state.acknowledged)
        }

        @Test
        @DisplayName("ProviderStepState defaults are empty")
        fun `ProviderStepState defaults`() {
            val state = ProviderStepState()
            assertEquals("", state.providerId)
            assertEquals("", state.apiKey)
            assertEquals("", state.baseUrl)
            assertEquals("", state.model)
            assertEquals(ValidationResult.Idle, state.validationResult)
            assertTrue(state.availableModels.isEmpty())
            assertFalse(state.isLoadingModels)
        }

        @Test
        @DisplayName("ChannelSelectionState defaults to empty")
        fun `ChannelSelectionState defaults`() {
            val state = ChannelSelectionState()
            assertTrue(state.selectedTypes.isEmpty())
            assertNull(state.activeSubFlowType)
        }

        @Test
        @DisplayName("ChannelSubFlowState defaults to empty")
        fun `ChannelSubFlowState defaults`() {
            val state = ChannelSubFlowState()
            assertTrue(state.fieldValues.isEmpty())
            assertEquals(ValidationResult.Idle, state.validationResult)
            assertEquals(0, state.currentSubStep)
            assertFalse(state.completed)
        }

        @Test
        @DisplayName("TunnelStepState defaults to none")
        fun `TunnelStepState defaults`() {
            val state = TunnelStepState()
            assertEquals("none", state.tunnelType)
            assertEquals("", state.tunnelToken)
            assertEquals("", state.customEndpoint)
        }

        @Test
        @DisplayName("SecurityStepState defaults to supervised")
        fun `SecurityStepState defaults`() {
            val state = SecurityStepState()
            assertEquals("supervised", state.autonomyLevel)
        }

        @Test
        @DisplayName("MemoryStepState defaults to sqlite with auto-save")
        fun `MemoryStepState defaults`() {
            val state = MemoryStepState()
            assertEquals("sqlite", state.backend)
            assertTrue(state.autoSave)
            assertEquals("", state.embeddingProvider)
            assertEquals(EXPECTED_DEFAULT_RETENTION_DAYS, state.retentionDays)
        }

        @Test
        @DisplayName("IdentityStepState defaults to sensible values")
        fun `IdentityStepState defaults`() {
            val state = IdentityStepState()
            assertEquals("My Agent", state.agentName)
            assertEquals("", state.userName)
            assertEquals(TimeZone.getDefault().id, state.timezone)
            assertEquals("", state.communicationStyle)
            assertEquals("openclaw", state.identityFormat)
        }

        @Test
        @DisplayName("ActivationStepState defaults to not completing")
        fun `ActivationStepState defaults`() {
            val state = ActivationStepState()
            assertFalse(state.isCompleting)
            assertNull(state.completeError)
        }
    }

    @Nested
    @DisplayName("Step state copy operations")
    inner class StepStateCopy {
        @Test
        @DisplayName("ProviderStepState copy preserves unmodified fields")
        fun `ProviderStepState copy preserves fields`() {
            val original =
                ProviderStepState(
                    providerId = "openai",
                    apiKey = "sk-test",
                    model = "gpt-4o",
                )
            val updated = original.copy(model = "gpt-4o-mini")
            assertEquals("openai", updated.providerId)
            assertEquals("sk-test", updated.apiKey)
            assertEquals("gpt-4o-mini", updated.model)
        }

        @Test
        @DisplayName("ChannelSelectionState toggles types correctly")
        fun `ChannelSelectionState toggle`() {
            val empty = ChannelSelectionState()
            val withTelegram =
                empty.copy(selectedTypes = empty.selectedTypes + ChannelType.TELEGRAM)
            assertTrue(ChannelType.TELEGRAM in withTelegram.selectedTypes)

            val withBoth =
                withTelegram.copy(
                    selectedTypes = withTelegram.selectedTypes + ChannelType.DISCORD,
                )
            assertEquals(2, withBoth.selectedTypes.size)

            val withoutTelegram =
                withBoth.copy(
                    selectedTypes = withBoth.selectedTypes - ChannelType.TELEGRAM,
                )
            assertEquals(1, withoutTelegram.selectedTypes.size)
            assertTrue(ChannelType.DISCORD in withoutTelegram.selectedTypes)
        }

        @Test
        @DisplayName("ChannelSubFlowState field updates are additive")
        fun `ChannelSubFlowState field updates`() {
            var state = ChannelSubFlowState()
            state = state.copy(fieldValues = state.fieldValues + ("bot_token" to "abc"))
            state = state.copy(fieldValues = state.fieldValues + ("guild_id" to "123"))
            assertEquals(2, state.fieldValues.size)
            assertEquals("abc", state.fieldValues["bot_token"])
            assertEquals("123", state.fieldValues["guild_id"])
        }

        @Test
        @DisplayName("ChannelSubFlowState sub-step advances and retreats")
        fun `ChannelSubFlowState sub-step navigation`() {
            var state = ChannelSubFlowState()
            state = state.copy(currentSubStep = state.currentSubStep + 1)
            assertEquals(1, state.currentSubStep)
            state = state.copy(currentSubStep = state.currentSubStep + 1)
            assertEquals(2, state.currentSubStep)
            state = state.copy(currentSubStep = state.currentSubStep - 1)
            assertEquals(1, state.currentSubStep)
        }

        @Test
        @DisplayName("ActivationStepState tracks error and completion")
        fun `ActivationStepState tracks error`() {
            var state = ActivationStepState()
            state = state.copy(isCompleting = true)
            assertTrue(state.isCompleting)
            state = state.copy(completeError = "Something went wrong")
            assertEquals("Something went wrong", state.completeError)
            state = state.copy(completeError = null)
            assertNull(state.completeError)
        }
    }

    @Nested
    @DisplayName("Auth failure detection")
    inner class AuthFailureDetection {
        @Test
        @DisplayName("401 error is definitive auth failure")
        fun `401 is auth failure`() {
            val result = Result.failure<List<String>>(Exception("HTTP 401 from api.openai.com"))
            assertTrue(isDefinitiveAuthFailure(result))
        }

        @Test
        @DisplayName("403 error is definitive auth failure")
        fun `403 is auth failure`() {
            val result = Result.failure<List<String>>(Exception("HTTP 403"))
            assertTrue(isDefinitiveAuthFailure(result))
        }

        @Test
        @DisplayName("500 error is not auth failure")
        fun `500 is not auth failure`() {
            val result = Result.failure<List<String>>(Exception("HTTP 500"))
            assertFalse(isDefinitiveAuthFailure(result))
        }

        @Test
        @DisplayName("network error is not auth failure")
        fun `network error is not auth failure`() {
            val result = Result.failure<List<String>>(Exception("Connection timed out"))
            assertFalse(isDefinitiveAuthFailure(result))
        }

        @Test
        @DisplayName("success is not auth failure")
        fun `success is not auth failure`() {
            val result = Result.success(listOf("gpt-4o"))
            assertFalse(isDefinitiveAuthFailure(result))
        }
    }

    /** Expected default retention days value. */
    companion object {
        private const val EXPECTED_DEFAULT_RETENTION_DAYS = 30
    }
}

/**
 * Mirrors the step-tracking logic from [OnboardingCoordinator]
 * without requiring [android.app.Application].
 */
private class NineStepTracker {
    /** Total steps in the wizard. */
    val totalSteps: Int = 9
    var current: Int = 0
        private set

    /** Advances to the next step if not at the end. */
    fun next() {
        if (current < totalSteps - 1) current++
    }

    /** Returns to the previous step if not at the start. */
    fun previous() {
        if (current > 0) current--
    }
}
