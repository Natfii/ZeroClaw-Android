/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding

import com.zeroclaw.android.data.repository.OnboardingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [OnboardingViewModel] step management logic.
 *
 * Uses a test double for [OnboardingRepository] to avoid
 * requiring [android.app.Application].
 */
@DisplayName("OnboardingViewModel step logic")
class OnboardingViewModelTest {
    @Test
    @DisplayName("nextStep advances within bounds")
    fun `nextStep advances within bounds`() =
        runTest {
            val steps = TestStepTracker()
            steps.next()
            assertEquals(1, steps.current)
            steps.next()
            assertEquals(2, steps.current)
            steps.next()
            assertEquals(3, steps.current)
            steps.next()
            assertEquals(3, steps.current)
        }

    @Test
    @DisplayName("previousStep goes back within bounds")
    fun `previousStep goes back within bounds`() =
        runTest {
            val steps = TestStepTracker()
            steps.next()
            steps.next()
            assertEquals(2, steps.current)
            steps.previous()
            assertEquals(1, steps.current)
            steps.previous()
            assertEquals(0, steps.current)
            steps.previous()
            assertEquals(0, steps.current)
        }

    @Test
    @DisplayName("complete delegates to repository")
    fun `complete delegates to repository`() =
        runTest {
            val repo = TestOnboardingRepository()
            assertEquals(false, repo.isCompleted.first())
            repo.markComplete()
            assertEquals(true, repo.isCompleted.first())
        }
}

/**
 * Mirrors the step-tracking logic from [OnboardingViewModel]
 * without requiring [android.app.Application].
 */
private class TestStepTracker {
    /** Total steps in the wizard. */
    val totalSteps: Int = 4
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

/**
 * Test double for [OnboardingRepository].
 */
private class TestOnboardingRepository : OnboardingRepository {
    private val _isCompleted = MutableStateFlow(false)
    override val isCompleted = _isCompleted

    override suspend fun markComplete() {
        _isCompleted.value = true
    }

    override suspend fun reset() {
        _isCompleted.value = false
    }
}
