/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import com.zeroclaw.ffi.FfiComponentHealth
import com.zeroclaw.ffi.FfiException
import com.zeroclaw.ffi.FfiHealthDetail
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for [HealthBridge].
 *
 * Uses MockK to mock the static UniFFI-generated functions so that tests
 * run without loading the native library.
 */
@DisplayName("HealthBridge")
class HealthBridgeTest {
    private lateinit var bridge: HealthBridge

    /** Sets up mocks and creates a [HealthBridge] with an unconfined dispatcher. */
    @BeforeEach
    fun setUp() {
        mockkStatic("com.zeroclaw.ffi.Zeroclaw_androidKt")
        bridge = HealthBridge(ioDispatcher = UnconfinedTestDispatcher())
    }

    /** Tears down all mocks after each test. */
    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("getHealthDetail converts FFI record to HealthDetail model")
    fun `getHealthDetail converts FFI record to HealthDetail model`() =
        runTest {
            val ffiComponent =
                FfiComponentHealth(
                    name = "gateway",
                    status = "ok",
                    lastError = null,
                    restartCount = 3UL,
                )
            val ffiDetail =
                FfiHealthDetail(
                    daemonRunning = true,
                    pid = 1234U,
                    uptimeSeconds = 42UL,
                    components = listOf(ffiComponent),
                )
            every { com.zeroclaw.ffi.getHealthDetail() } returns ffiDetail

            val result = bridge.getHealthDetail()

            assertEquals(true, result.daemonRunning)
            assertEquals(1234L, result.pid)
            assertEquals(42L, result.uptimeSeconds)
            assertEquals(1, result.components.size)
            with(result.components[0]) {
                assertEquals("gateway", name)
                assertEquals("ok", status)
                assertNull(lastError)
                assertEquals(3L, restartCount)
            }
        }

    @Test
    @DisplayName("getHealthDetail propagates FfiException")
    fun `getHealthDetail propagates FfiException`() =
        runTest {
            every {
                com.zeroclaw.ffi.getHealthDetail()
            } throws FfiException.StateException("daemon not running")

            assertThrows<FfiException> {
                bridge.getHealthDetail()
            }
        }

    @Test
    @DisplayName("getComponentHealth returns null when component not found")
    fun `getComponentHealth returns null when component not found`() =
        runTest {
            every { com.zeroclaw.ffi.getComponentHealth("unknown") } returns null

            val result = bridge.getComponentHealth("unknown")

            assertNull(result)
        }

    @Test
    @DisplayName("getComponentHealth converts FFI record to ComponentHealth model")
    fun `getComponentHealth converts FFI record to ComponentHealth model`() =
        runTest {
            val ffiComponent =
                FfiComponentHealth(
                    name = "scheduler",
                    status = "error",
                    lastError = "timeout after 30s",
                    restartCount = 7UL,
                )
            every { com.zeroclaw.ffi.getComponentHealth("scheduler") } returns ffiComponent

            val result = bridge.getComponentHealth("scheduler")

            assertNotNull(result)
            assertEquals("scheduler", result!!.name)
            assertEquals("error", result.status)
            assertEquals("timeout after 30s", result.lastError)
            assertEquals(7L, result.restartCount)
        }

    @Test
    @DisplayName("getComponentHealth propagates FfiException")
    fun `getComponentHealth propagates FfiException`() =
        runTest {
            every {
                com.zeroclaw.ffi.getComponentHealth(any())
            } throws FfiException.InternalPanic("segfault")

            assertThrows<FfiException> {
                bridge.getComponentHealth("gateway")
            }
        }
}
