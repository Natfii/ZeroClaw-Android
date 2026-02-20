/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.util

import androidx.lifecycle.LifecycleOwner
import com.zeroclaw.android.model.AppSettings
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SessionLockManager].
 *
 * Verifies cold-start lock state, unlock/lock transitions,
 * and timeout-based re-locking on lifecycle callbacks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("SessionLockManager")
class SessionLockManagerTest {
    private val owner: LifecycleOwner = mockk(relaxed = true)
    private var managerScope: CoroutineScope? = null

    @AfterEach
    fun tearDown() {
        managerScope?.cancel()
    }

    private fun createManager(
        settings: AppSettings =
            AppSettings(
                lockEnabled = true,
                lockTimeoutMinutes = 1,
                pinHash = "fakehash",
            ),
    ): Pair<SessionLockManager, MutableStateFlow<AppSettings>> {
        val flow = MutableStateFlow(settings)
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        managerScope = scope
        val manager = SessionLockManager(flow, scope)
        return manager to flow
    }

    @Test
    @DisplayName("starts locked on cold launch")
    fun `starts locked on cold launch`() =
        runTest {
            val (manager, _) = createManager()
            assertTrue(manager.isLocked.value)
        }

    @Test
    @DisplayName("unlock sets isLocked to false")
    fun `unlock sets isLocked to false`() =
        runTest {
            val (manager, _) = createManager()
            manager.unlock()
            assertFalse(manager.isLocked.value)
        }

    @Test
    @DisplayName("lock sets isLocked to true")
    fun `lock sets isLocked to true`() =
        runTest {
            val (manager, _) = createManager()
            manager.unlock()
            assertFalse(manager.isLocked.value)
            manager.lock()
            assertTrue(manager.isLocked.value)
        }

    @Test
    @DisplayName("onStart does not lock when returning quickly")
    fun `onStart does not lock when returning quickly`() =
        runTest {
            val (manager, _) = createManager()
            manager.unlock()
            manager.onStop(owner)
            manager.onStart(owner)
            assertFalse(manager.isLocked.value)
        }

    @Test
    @DisplayName("onStart does not lock when lock is disabled")
    fun `onStart does not lock when lock is disabled`() =
        runTest {
            val (manager, _) =
                createManager(
                    AppSettings(lockEnabled = false, lockTimeoutMinutes = 0, pinHash = "hash"),
                )
            manager.unlock()
            manager.onStop(owner)
            manager.onStart(owner)
            assertFalse(manager.isLocked.value)
        }

    @Test
    @DisplayName("onStart does not lock when no PIN is set")
    fun `onStart does not lock when no PIN is set`() =
        runTest {
            val (manager, _) =
                createManager(
                    AppSettings(lockEnabled = true, lockTimeoutMinutes = 0, pinHash = ""),
                )
            manager.unlock()
            manager.onStop(owner)
            manager.onStart(owner)
            assertFalse(manager.isLocked.value)
        }

    @Test
    @DisplayName("onStart without prior onStop does not lock")
    fun `onStart without prior onStop does not lock`() =
        runTest {
            val (manager, _) = createManager()
            manager.unlock()
            manager.onStart(owner)
            assertFalse(manager.isLocked.value)
        }

    @Test
    @DisplayName("settings flow updates are reflected")
    fun `settings flow updates are reflected`() =
        runTest {
            val (manager, flow) =
                createManager(
                    AppSettings(lockEnabled = false, pinHash = ""),
                )
            manager.unlock()
            flow.value = AppSettings(lockEnabled = true, lockTimeoutMinutes = 0, pinHash = "hash")
            manager.onStop(owner)
            Thread.sleep(1)
            manager.onStart(owner)
            assertTrue(manager.isLocked.value)
        }
}
