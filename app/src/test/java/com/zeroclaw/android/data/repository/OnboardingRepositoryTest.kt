/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for the [OnboardingRepository] contract.
 *
 * Uses an in-memory implementation to verify completion state
 * without requiring Android DataStore.
 */
@DisplayName("OnboardingRepository")
class OnboardingRepositoryTest {
    @Test
    @DisplayName("initial state is not completed")
    fun `initial state is not completed`() = runTest {
        val repo = InMemoryOnboardingRepository()
        assertEquals(false, repo.isCompleted.first())
    }

    @Test
    @DisplayName("markComplete sets completed to true")
    fun `markComplete sets completed to true`() = runTest {
        val repo = InMemoryOnboardingRepository()
        repo.markComplete()
        assertEquals(true, repo.isCompleted.first())
    }

    @Test
    @DisplayName("reset sets completed back to false")
    fun `reset sets completed back to false`() = runTest {
        val repo = InMemoryOnboardingRepository()
        repo.markComplete()
        assertEquals(true, repo.isCompleted.first())
        repo.reset()
        assertEquals(false, repo.isCompleted.first())
    }

    @Test
    @DisplayName("multiple markComplete calls are idempotent")
    fun `multiple markComplete calls are idempotent`() = runTest {
        val repo = InMemoryOnboardingRepository()
        repo.markComplete()
        repo.markComplete()
        assertEquals(true, repo.isCompleted.first())
    }
}

/**
 * In-memory [OnboardingRepository] for testing.
 *
 * Stores state in a [MutableStateFlow] without requiring
 * Android context or DataStore infrastructure.
 */
private class InMemoryOnboardingRepository : OnboardingRepository {
    private val _isCompleted = MutableStateFlow(false)
    override val isCompleted = _isCompleted

    override suspend fun markComplete() {
        _isCompleted.value = true
    }

    override suspend fun reset() {
        _isCompleted.value = false
    }
}
