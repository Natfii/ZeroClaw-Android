/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import com.zeroclaw.android.data.repository.ActivityRepository
import com.zeroclaw.android.model.ActivityType
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [EventBridge].
 *
 * Uses MockK to mock the static UniFFI-generated functions and the
 * [ActivityRepository] dependency so that tests run without loading
 * the native library or a Room database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("EventBridge")
class EventBridgeTest {
    private lateinit var bridge: EventBridge
    private lateinit var activityRepository: ActivityRepository

    /** Sets up mocks and creates an [EventBridge] with a test coroutine scope. */
    @BeforeEach
    fun setUp() {
        mockkStatic("com.zeroclaw.ffi.Zeroclaw_androidKt")
        activityRepository = io.mockk.mockk(relaxUnitFun = true)
    }

    /** Tears down all mocks after each test. */
    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("onEvent parses valid JSON and emits DaemonEvent on SharedFlow")
    fun `onEvent parses valid JSON and emits DaemonEvent on SharedFlow`() =
        runTest {
            bridge = EventBridge(activityRepository, this)
            val json = """{"id":1,"timestamp_ms":1700000000000,"kind":"llm_request","data":{"provider":"openai","model":"gpt-4"}}"""

            var received: com.zeroclaw.android.model.DaemonEvent? = null
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    received = bridge.events.first()
                }

            bridge.onEvent(json)
            advanceUntilIdle()

            assertEquals(1L, received?.id)
            assertEquals(1700000000000L, received?.timestampMs)
            assertEquals("llm_request", received?.kind)
            assertEquals("openai", received?.data?.get("provider"))
            assertEquals("gpt-4", received?.data?.get("model"))
            job.cancel()
        }

    @Test
    @DisplayName("onEvent records activity for llm_request kind")
    fun `onEvent records activity for llm_request kind`() =
        runTest {
            bridge = EventBridge(activityRepository, this)
            val json = """{"id":2,"timestamp_ms":1700000000000,"kind":"llm_request","data":{"provider":"anthropic","model":"claude-3"}}"""

            bridge.onEvent(json)
            advanceUntilIdle()

            verify {
                activityRepository.record(
                    ActivityType.FFI_CALL,
                    "LLM Request: anthropic / claude-3",
                )
            }
        }

    @Test
    @DisplayName("onEvent records error activity for error kind")
    fun `onEvent records error activity for error kind`() =
        runTest {
            bridge = EventBridge(activityRepository, this)
            val json = """{"id":3,"timestamp_ms":1700000000000,"kind":"error","data":{"component":"gateway","message":"connection refused"}}"""

            bridge.onEvent(json)
            advanceUntilIdle()

            verify {
                activityRepository.record(
                    ActivityType.DAEMON_ERROR,
                    match { it.contains("connection refused") },
                )
            }
        }

    @Test
    @DisplayName("onEvent silently drops malformed JSON")
    fun `onEvent silently drops malformed JSON`() =
        runTest {
            bridge = EventBridge(activityRepository, this)

            bridge.onEvent("not valid json")
            advanceUntilIdle()

            verify(exactly = 0) {
                activityRepository.record(any(), any())
            }
        }

    @Test
    @DisplayName("onEvent handles missing data object gracefully")
    fun `onEvent handles missing data object gracefully`() =
        runTest {
            bridge = EventBridge(activityRepository, this)
            val json = """{"id":4,"timestamp_ms":1700000000000,"kind":"heartbeat_tick"}"""

            var received: com.zeroclaw.android.model.DaemonEvent? = null
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    received = bridge.events.first()
                }

            bridge.onEvent(json)
            advanceUntilIdle()

            assertEquals("heartbeat_tick", received?.kind)
            assertEquals(emptyMap<String, String>(), received?.data)

            verify {
                activityRepository.record(ActivityType.FFI_CALL, "Heartbeat")
            }
            job.cancel()
        }

    @Test
    @DisplayName("onEvent maps tool_call kind to correct activity message")
    fun `onEvent maps tool_call kind to correct activity message`() =
        runTest {
            bridge = EventBridge(activityRepository, this)
            val json = """{"id":5,"timestamp_ms":1700000000000,"kind":"tool_call","data":{"tool":"web_search","duration_ms":"150"}}"""

            bridge.onEvent(json)
            advanceUntilIdle()

            verify {
                activityRepository.record(
                    ActivityType.FFI_CALL,
                    "Tool: web_search (150ms)",
                )
            }
        }

    @Test
    @DisplayName("register calls FFI registerEventListener")
    fun `register calls FFI registerEventListener`() =
        runTest {
            bridge = EventBridge(activityRepository, this)
            every { com.zeroclaw.ffi.registerEventListener(any()) } returns Unit

            bridge.register()

            verify { com.zeroclaw.ffi.registerEventListener(bridge) }
        }

    @Test
    @DisplayName("unregister calls FFI unregisterEventListener")
    fun `unregister calls FFI unregisterEventListener`() =
        runTest {
            bridge = EventBridge(activityRepository, this)
            every { com.zeroclaw.ffi.unregisterEventListener() } returns Unit

            bridge.unregister()

            verify { com.zeroclaw.ffi.unregisterEventListener() }
        }
}
