/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings.doctor

import com.zeroclaw.android.model.DoctorSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DoctorSummary] computation.
 *
 * The [DoctorViewModel] itself requires an [android.app.Application]
 * context, so we test the summary logic independently.
 */
@DisplayName("DoctorSummary")
class DoctorViewModelTest {
    @Test
    @DisplayName("computes summary from empty list")
    fun `empty list gives zero counts`() {
        val summary = DoctorSummary.from(emptyList())
        assertEquals(0, summary.passCount)
        assertEquals(0, summary.warnCount)
        assertEquals(0, summary.failCount)
    }

    @Test
    @DisplayName("computes summary from mixed checks")
    fun `mixed checks counted correctly`() {
        val checks =
            listOf(
                testCheck("1", com.zeroclaw.android.model.CheckStatus.PASS),
                testCheck("2", com.zeroclaw.android.model.CheckStatus.PASS),
                testCheck("3", com.zeroclaw.android.model.CheckStatus.WARN),
                testCheck("4", com.zeroclaw.android.model.CheckStatus.FAIL),
                testCheck("5", com.zeroclaw.android.model.CheckStatus.RUNNING),
            )
        val summary = DoctorSummary.from(checks)
        assertEquals(2, summary.passCount)
        assertEquals(1, summary.warnCount)
        assertEquals(1, summary.failCount)
    }

    private fun testCheck(
        id: String,
        status: com.zeroclaw.android.model.CheckStatus,
    ): com.zeroclaw.android.model.DiagnosticCheck =
        com.zeroclaw.android.model.DiagnosticCheck(
            id = id,
            category = com.zeroclaw.android.model.DiagnosticCategory.CONFIG,
            title = "Test check $id",
            status = status,
        )
}
