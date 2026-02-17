/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import com.zeroclaw.android.model.ServiceState
import com.zeroclaw.ffi.FfiException
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for [DaemonServiceBridge].
 *
 * Uses MockK to mock the static UniFFI-generated functions so that tests
 * run without loading the native library.
 */
@DisplayName("DaemonServiceBridge")
class DaemonServiceBridgeTest {
    private lateinit var bridge: DaemonServiceBridge

    @BeforeEach
    fun setUp() {
        mockkStatic("com.zeroclaw.ffi.Zeroclaw_androidKt")
        bridge = DaemonServiceBridge("/tmp/test")
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("initial service state is STOPPED")
    fun `initial service state is STOPPED`() {
        assertEquals(ServiceState.STOPPED, bridge.serviceState.value)
    }

    @Test
    @DisplayName("initial last status is null")
    fun `initial last status is null`() {
        assertEquals(null, bridge.lastStatus.value)
    }

    @Test
    @DisplayName("start transitions state to RUNNING on success")
    fun `start transitions state to RUNNING on success`() =
        runTest {
            every { com.zeroclaw.ffi.startDaemon(any(), any(), any(), any()) } returns Unit

            bridge.start(configToml = "", host = "127.0.0.1", port = 8080u)

            assertEquals(ServiceState.RUNNING, bridge.serviceState.value)
        }

    @Test
    @DisplayName("stop transitions state to STOPPED on success")
    fun `stop transitions state to STOPPED on success`() =
        runTest {
            every { com.zeroclaw.ffi.startDaemon(any(), any(), any(), any()) } returns Unit
            every { com.zeroclaw.ffi.stopDaemon() } returns Unit

            bridge.start(configToml = "", host = "127.0.0.1", port = 8080u)
            bridge.stop()

            assertEquals(ServiceState.STOPPED, bridge.serviceState.value)
        }

    @Test
    @DisplayName("pollStatus parses JSON and updates lastStatus")
    fun `pollStatus parses JSON and updates lastStatus`() =
        runTest {
            val json =
                """
                {
                    "daemon_running": true,
                    "uptime_seconds": 42,
                    "components": {
                        "gateway": {"status": "ok"}
                    }
                }
                """.trimIndent()
            every { com.zeroclaw.ffi.getStatus() } returns json

            val status = bridge.pollStatus()

            assertEquals(true, status.running)
            assertEquals(42L, status.uptimeSeconds)
            assertEquals("ok", status.components["gateway"]?.status)
            assertEquals(status, bridge.lastStatus.value)
        }

    @Test
    @DisplayName("initial lastError is null")
    fun `initial lastError is null`() {
        assertNull(bridge.lastError.value)
    }

    @Test
    @DisplayName("start sets lastError on failure")
    fun `start sets lastError on failure`() =
        runTest {
            every {
                com.zeroclaw.ffi.startDaemon(any(), any(), any(), any())
            } throws FfiException.ConfigException("bad toml at line 1")

            assertThrows<FfiException> {
                bridge.start(configToml = "", host = "127.0.0.1", port = 8080u)
            }

            assertEquals("bad toml at line 1", bridge.lastError.value)
            assertEquals(ServiceState.ERROR, bridge.serviceState.value)
        }

    @Test
    @DisplayName("start clears lastError on success")
    fun `start clears lastError on success`() =
        runTest {
            every {
                com.zeroclaw.ffi.startDaemon(any(), any(), any(), any())
            } throws FfiException.SpawnException("spawn failure")

            assertThrows<FfiException> {
                bridge.start(configToml = "", host = "127.0.0.1", port = 8080u)
            }
            assertEquals("spawn failure", bridge.lastError.value)

            every {
                com.zeroclaw.ffi.startDaemon(any(), any(), any(), any())
            } returns Unit

            bridge.start(configToml = "", host = "127.0.0.1", port = 8080u)

            assertNull(bridge.lastError.value)
            assertEquals(ServiceState.RUNNING, bridge.serviceState.value)
        }

    @Test
    @DisplayName("stop sets lastError on failure")
    fun `stop sets lastError on failure`() =
        runTest {
            every { com.zeroclaw.ffi.startDaemon(any(), any(), any(), any()) } returns Unit
            every {
                com.zeroclaw.ffi.stopDaemon()
            } throws FfiException.ShutdownException("shutdown timeout")

            bridge.start(configToml = "", host = "127.0.0.1", port = 8080u)

            assertThrows<FfiException> { bridge.stop() }

            assertEquals("shutdown timeout", bridge.lastError.value)
            assertEquals(ServiceState.ERROR, bridge.serviceState.value)
        }

    @Test
    @DisplayName("stop clears lastError on success")
    fun `stop clears lastError on success`() =
        runTest {
            every { com.zeroclaw.ffi.startDaemon(any(), any(), any(), any()) } returns Unit
            every { com.zeroclaw.ffi.stopDaemon() } returns Unit

            bridge.start(configToml = "", host = "127.0.0.1", port = 8080u)
            bridge.stop()

            assertNull(bridge.lastError.value)
            assertEquals(ServiceState.STOPPED, bridge.serviceState.value)
        }
}
