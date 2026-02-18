/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import com.zeroclaw.android.model.BudgetStatus
import com.zeroclaw.ffi.FfiBudgetStatus
import com.zeroclaw.ffi.FfiCostSummary
import com.zeroclaw.ffi.FfiException
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for [CostBridge].
 *
 * Uses MockK to mock the static UniFFI-generated functions so that tests
 * run without loading the native library.
 */
@DisplayName("CostBridge")
class CostBridgeTest {
    private lateinit var bridge: CostBridge

    /** Sets up mocks and creates a [CostBridge] with an unconfined dispatcher. */
    @BeforeEach
    fun setUp() {
        mockkStatic("com.zeroclaw.ffi.Zeroclaw_androidKt")
        bridge = CostBridge(ioDispatcher = UnconfinedTestDispatcher())
    }

    /** Tears down all mocks after each test. */
    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("getCostSummary converts FFI record to CostSummary model")
    fun `getCostSummary converts FFI record to CostSummary model`() =
        runTest {
            val ffiSummary =
                FfiCostSummary(
                    sessionCostUsd = 1.25,
                    dailyCostUsd = 3.50,
                    monthlyCostUsd = 42.0,
                    totalTokens = 5000UL,
                    requestCount = 10U,
                    modelBreakdownJson = """{"gpt-4": 1.0}""",
                )
            every { com.zeroclaw.ffi.getCostSummary() } returns ffiSummary

            val result = bridge.getCostSummary()

            assertEquals(1.25, result.sessionCostUsd)
            assertEquals(3.50, result.dailyCostUsd)
            assertEquals(42.0, result.monthlyCostUsd)
            assertEquals(5000L, result.totalTokens)
            assertEquals(10, result.requestCount)
            assertEquals("""{"gpt-4": 1.0}""", result.modelBreakdownJson)
        }

    @Test
    @DisplayName("getCostSummary propagates FfiException")
    fun `getCostSummary propagates FfiException`() =
        runTest {
            every {
                com.zeroclaw.ffi.getCostSummary()
            } throws FfiException.StateException("daemon not running")

            assertThrows<FfiException> {
                bridge.getCostSummary()
            }
        }

    @Test
    @DisplayName("getDailyCost passes through FFI result")
    fun `getDailyCost passes through FFI result`() =
        runTest {
            every { com.zeroclaw.ffi.getDailyCost(2026, 2U, 18U) } returns 4.75

            val result = bridge.getDailyCost(2026, 2, 18)

            assertEquals(4.75, result)
        }

    @Test
    @DisplayName("getMonthlyCost passes through FFI result")
    fun `getMonthlyCost passes through FFI result`() =
        runTest {
            every { com.zeroclaw.ffi.getMonthlyCost(2026, 2U) } returns 95.50

            val result = bridge.getMonthlyCost(2026, 2)

            assertEquals(95.50, result)
        }

    @Test
    @DisplayName("checkBudget returns Allowed when within limits")
    fun `checkBudget returns Allowed when within limits`() =
        runTest {
            every { com.zeroclaw.ffi.checkBudget(0.01) } returns FfiBudgetStatus.Allowed

            val result = bridge.checkBudget(0.01)

            assertEquals(BudgetStatus.Allowed, result)
        }

    @Test
    @DisplayName("checkBudget returns Warning with correct fields")
    fun `checkBudget returns Warning with correct fields`() =
        runTest {
            every {
                com.zeroclaw.ffi.checkBudget(0.50)
            } returns
                FfiBudgetStatus.Warning(
                    currentUsd = 8.0,
                    limitUsd = 10.0,
                    period = "daily",
                )

            val result = bridge.checkBudget(0.50)

            val warning = result as BudgetStatus.Warning
            assertEquals(8.0, warning.currentUsd)
            assertEquals(10.0, warning.limitUsd)
            assertEquals("daily", warning.period)
        }

    @Test
    @DisplayName("checkBudget returns Exceeded with correct fields")
    fun `checkBudget returns Exceeded with correct fields`() =
        runTest {
            every {
                com.zeroclaw.ffi.checkBudget(1.0)
            } returns
                FfiBudgetStatus.Exceeded(
                    currentUsd = 11.0,
                    limitUsd = 10.0,
                    period = "daily",
                )

            val result = bridge.checkBudget(1.0)

            val exceeded = result as BudgetStatus.Exceeded
            assertEquals(11.0, exceeded.currentUsd)
            assertEquals(10.0, exceeded.limitUsd)
            assertEquals("daily", exceeded.period)
        }

    @Test
    @DisplayName("checkBudget propagates FfiException")
    fun `checkBudget propagates FfiException`() =
        runTest {
            every {
                com.zeroclaw.ffi.checkBudget(any())
            } throws FfiException.StateException("daemon not running")

            assertThrows<FfiException> {
                bridge.checkBudget(0.01)
            }
        }
}
