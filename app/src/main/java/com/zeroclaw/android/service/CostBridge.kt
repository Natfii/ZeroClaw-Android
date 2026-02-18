/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import com.zeroclaw.android.model.BudgetStatus
import com.zeroclaw.android.model.CostSummary
import com.zeroclaw.ffi.FfiBudgetStatus as FfiBudget
import com.zeroclaw.ffi.FfiException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridge between the Android UI layer and the Rust cost-tracking FFI.
 *
 * Wraps [com.zeroclaw.ffi.getCostSummary], [com.zeroclaw.ffi.getDailyCost],
 * [com.zeroclaw.ffi.getMonthlyCost], and [com.zeroclaw.ffi.checkBudget] in
 * coroutine-safe suspend functions dispatched to [Dispatchers.IO].
 *
 * @param ioDispatcher Dispatcher for blocking FFI calls. Defaults to [Dispatchers.IO].
 */
class CostBridge(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Fetches the aggregated cost summary for the current daemon session.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @return Parsed [CostSummary] snapshot.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun getCostSummary(): CostSummary =
        withContext(ioDispatcher) {
            val ffi = com.zeroclaw.ffi.getCostSummary()
            CostSummary(
                sessionCostUsd = ffi.sessionCostUsd,
                dailyCostUsd = ffi.dailyCostUsd,
                monthlyCostUsd = ffi.monthlyCostUsd,
                totalTokens = ffi.totalTokens.toLong(),
                requestCount = ffi.requestCount.toInt(),
                modelBreakdownJson = ffi.modelBreakdownJson,
            )
        }

    /**
     * Fetches the total cost for a specific calendar day.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @param year Calendar year (e.g. 2026).
     * @param month Month of year (1-12).
     * @param day Day of month (1-31).
     * @return Cost in USD for the specified day.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun getDailyCost(
        year: Int,
        month: Int,
        day: Int,
    ): Double =
        withContext(ioDispatcher) {
            com.zeroclaw.ffi.getDailyCost(year, month.toUInt(), day.toUInt())
        }

    /**
     * Fetches the total cost for a specific calendar month.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @param year Calendar year (e.g. 2026).
     * @param month Month of year (1-12).
     * @return Cost in USD for the specified month.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun getMonthlyCost(
        year: Int,
        month: Int,
    ): Double =
        withContext(ioDispatcher) {
            com.zeroclaw.ffi.getMonthlyCost(year, month.toUInt())
        }

    /**
     * Checks the current spending against configured budget limits.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @param estimatedCostUsd Estimated cost of the next request, in USD.
     * @return [BudgetStatus] indicating whether spending is within, approaching,
     *   or exceeding the configured limit.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun checkBudget(estimatedCostUsd: Double): BudgetStatus =
        withContext(ioDispatcher) {
            when (val ffi = com.zeroclaw.ffi.checkBudget(estimatedCostUsd)) {
                is FfiBudget.Allowed -> BudgetStatus.Allowed
                is FfiBudget.Warning -> BudgetStatus.Warning(ffi.currentUsd, ffi.limitUsd, ffi.period)
                is FfiBudget.Exceeded -> BudgetStatus.Exceeded(ffi.currentUsd, ffi.limitUsd, ffi.period)
            }
        }
}
