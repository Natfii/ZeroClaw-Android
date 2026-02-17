/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [BatteryOptimization].
 *
 * Verifies OEM instruction URL generation and the mapping between
 * [BatteryOptimization.OemBatteryType] values and dontkillmyapp.com
 * slugs. OEM detection is not tested here because it depends on
 * [android.os.Build.MANUFACTURER] which requires Robolectric.
 */
@DisplayName("BatteryOptimization")
class BatteryOptimizationTest {
    @Test
    @DisplayName("all OEM URLs start with dontkillmyapp.com base")
    fun `all OEM URLs start with dontkillmyapp base`() {
        BatteryOptimization.OemBatteryType.entries.forEach { oemType ->
            val url = BatteryOptimization.getOemInstructionsUrl(oemType)
            assertTrue(url.startsWith("https://dontkillmyapp.com/"))
        }
    }

    @Test
    @DisplayName("Xiaomi URL maps to dontkillmyapp.com/xiaomi")
    fun `Xiaomi URL maps to dontkillmyapp xiaomi`() {
        val url =
            BatteryOptimization.getOemInstructionsUrl(
                BatteryOptimization.OemBatteryType.XIAOMI,
            )
        assertEquals("https://dontkillmyapp.com/xiaomi", url)
    }

    @Test
    @DisplayName("Samsung URL maps to dontkillmyapp.com/samsung")
    fun `Samsung URL maps to dontkillmyapp samsung`() {
        val url =
            BatteryOptimization.getOemInstructionsUrl(
                BatteryOptimization.OemBatteryType.SAMSUNG,
            )
        assertEquals("https://dontkillmyapp.com/samsung", url)
    }

    @Test
    @DisplayName("Huawei URL maps to dontkillmyapp.com/huawei")
    fun `Huawei URL maps to dontkillmyapp huawei`() {
        val url =
            BatteryOptimization.getOemInstructionsUrl(
                BatteryOptimization.OemBatteryType.HUAWEI,
            )
        assertEquals("https://dontkillmyapp.com/huawei", url)
    }

    @Test
    @DisplayName("OnePlus URL maps to dontkillmyapp.com/oneplus")
    fun `OnePlus URL maps to dontkillmyapp oneplus`() {
        val url =
            BatteryOptimization.getOemInstructionsUrl(
                BatteryOptimization.OemBatteryType.ONEPLUS,
            )
        assertEquals("https://dontkillmyapp.com/oneplus", url)
    }
}
