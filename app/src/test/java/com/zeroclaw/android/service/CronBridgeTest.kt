// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.service

import com.zeroclaw.ffi.FfiCronJob
import com.zeroclaw.ffi.FfiException
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for [CronBridge].
 *
 * Uses MockK to mock the static UniFFI-generated functions so that tests
 * run without loading the native library.
 */
@DisplayName("CronBridge")
class CronBridgeTest {
    private lateinit var bridge: CronBridge

    /** Sets up mocks and creates a [CronBridge] with an unconfined dispatcher. */
    @BeforeEach
    fun setUp() {
        mockkStatic("com.zeroclaw.ffi.Zeroclaw_androidKt")
        bridge = CronBridge(ioDispatcher = UnconfinedTestDispatcher())
    }

    /** Tears down all mocks after each test. */
    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("listJobs converts FFI records to CronJob models")
    fun `listJobs converts FFI records to CronJob models`() =
        runTest {
            val ffiJobs =
                listOf(
                    makeFfiCronJob(id = "job-1", expression = "0 * * * *"),
                    makeFfiCronJob(id = "job-2", paused = true, oneShot = true),
                )
            every { com.zeroclaw.ffi.listCronJobs() } returns ffiJobs

            val result = bridge.listJobs()

            assertEquals(2, result.size)
            assertEquals("job-1", result[0].id)
            assertEquals("0 * * * *", result[0].expression)
            assertTrue(result[1].paused)
            assertTrue(result[1].oneShot)
        }

    @Test
    @DisplayName("listJobs returns empty list when no jobs exist")
    fun `listJobs returns empty list when no jobs exist`() =
        runTest {
            every { com.zeroclaw.ffi.listCronJobs() } returns emptyList()

            val result = bridge.listJobs()

            assertTrue(result.isEmpty())
        }

    @Test
    @DisplayName("listJobs propagates FfiException")
    fun `listJobs propagates FfiException`() =
        runTest {
            every {
                com.zeroclaw.ffi.listCronJobs()
            } throws FfiException.StateException("daemon not running")

            assertThrows<FfiException> {
                bridge.listJobs()
            }
        }

    @Test
    @DisplayName("getJob returns CronJob when found")
    fun `getJob returns CronJob when found`() =
        runTest {
            val ffiJob = makeFfiCronJob(id = "job-42", command = "echo hello")
            every { com.zeroclaw.ffi.getCronJob("job-42") } returns ffiJob

            val result = bridge.getJob("job-42")

            assertEquals("job-42", result?.id)
            assertEquals("echo hello", result?.command)
        }

    @Test
    @DisplayName("getJob returns null when not found")
    fun `getJob returns null when not found`() =
        runTest {
            every { com.zeroclaw.ffi.getCronJob("nonexistent") } returns null

            val result = bridge.getJob("nonexistent")

            assertNull(result)
        }

    @Test
    @DisplayName("addJob passes expression and command and returns created job")
    fun `addJob passes expression and command and returns created job`() =
        runTest {
            val ffiJob = makeFfiCronJob(id = "new-1", expression = "*/10 * * * *")
            every { com.zeroclaw.ffi.addCronJob("*/10 * * * *", "run check") } returns ffiJob

            val result = bridge.addJob("*/10 * * * *", "run check")

            assertEquals("new-1", result.id)
            assertEquals("*/10 * * * *", result.expression)
        }

    @Test
    @DisplayName("addOneShot passes delay and command and returns created job")
    fun `addOneShot passes delay and command and returns created job`() =
        runTest {
            val ffiJob = makeFfiCronJob(id = "once-1", oneShot = true)
            every { com.zeroclaw.ffi.addOneShotJob("5m", "echo once") } returns ffiJob

            val result = bridge.addOneShot("5m", "echo once")

            assertEquals("once-1", result.id)
            assertTrue(result.oneShot)
        }

    @Test
    @DisplayName("removeJob delegates to FFI")
    fun `removeJob delegates to FFI`() =
        runTest {
            every { com.zeroclaw.ffi.removeCronJob("job-1") } returns Unit

            bridge.removeJob("job-1")

            verify(exactly = 1) { com.zeroclaw.ffi.removeCronJob("job-1") }
        }

    @Test
    @DisplayName("pauseJob delegates to FFI")
    fun `pauseJob delegates to FFI`() =
        runTest {
            every { com.zeroclaw.ffi.pauseCronJob("job-1") } returns Unit

            bridge.pauseJob("job-1")

            verify(exactly = 1) { com.zeroclaw.ffi.pauseCronJob("job-1") }
        }

    @Test
    @DisplayName("resumeJob delegates to FFI")
    fun `resumeJob delegates to FFI`() =
        runTest {
            every { com.zeroclaw.ffi.resumeCronJob("job-1") } returns Unit

            bridge.resumeJob("job-1")

            verify(exactly = 1) { com.zeroclaw.ffi.resumeCronJob("job-1") }
        }

    @Test
    @DisplayName("removeJob propagates FfiException")
    fun `removeJob propagates FfiException`() =
        runTest {
            every {
                com.zeroclaw.ffi.removeCronJob(any())
            } throws FfiException.SpawnException("remove_job failed")

            assertThrows<FfiException> {
                bridge.removeJob("bad-id")
            }
        }

    /** Helper to construct an [FfiCronJob] with sensible defaults. */
    companion object {
        private const val DEFAULT_NEXT_RUN_MS = 1_700_000_000_000L
        private const val DEFAULT_LAST_RUN_MS = 1_699_999_990_000L

        @Suppress("LongParameterList")
        private fun makeFfiCronJob(
            id: String = "test-id",
            expression: String = "0 * * * *",
            command: String = "echo test",
            nextRunMs: Long = DEFAULT_NEXT_RUN_MS,
            lastRunMs: Long? = DEFAULT_LAST_RUN_MS,
            lastStatus: String? = "ok",
            paused: Boolean = false,
            oneShot: Boolean = false,
        ): FfiCronJob =
            FfiCronJob(
                id = id,
                expression = expression,
                command = command,
                nextRunMs = nextRunMs,
                lastRunMs = lastRunMs,
                lastStatus = lastStatus,
                paused = paused,
                oneShot = oneShot,
            )
    }
}
