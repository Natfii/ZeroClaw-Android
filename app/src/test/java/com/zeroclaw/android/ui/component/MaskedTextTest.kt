/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [maskText] function.
 */
@DisplayName("MaskedText")
class MaskedTextTest {
    @Test
    @DisplayName("masks all but last 4 characters")
    fun `masks all but last 4 characters`() {
        assertEquals("\u2022\u2022\u2022\u2022\u2022st12", maskText("sk-test12"))
    }

    @Test
    @DisplayName("short text is fully masked with minimum mask length")
    fun `short text is fully masked with minimum mask length`() {
        assertEquals("\u2022\u2022\u2022\u2022", maskText("abcd"))
    }

    @Test
    @DisplayName("empty text returns minimum mask characters")
    fun `empty text returns minimum mask characters`() {
        assertEquals("\u2022\u2022\u2022\u2022", maskText(""))
    }

    @Test
    @DisplayName("text shorter than 4 chars is fully masked")
    fun `text shorter than 4 chars is fully masked`() {
        assertEquals("\u2022\u2022\u2022\u2022", maskText("abc"))
    }

    @Test
    @DisplayName("exactly 4 chars is fully masked")
    fun `exactly 4 chars is fully masked`() {
        assertEquals("\u2022\u2022\u2022\u2022", maskText("1234"))
    }
}
