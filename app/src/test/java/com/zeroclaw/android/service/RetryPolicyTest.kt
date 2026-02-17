/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [RetryPolicy].
 *
 * Verifies exponential backoff timing, maximum attempt enforcement,
 * and reset behaviour.
 */
@DisplayName("RetryPolicy")
class RetryPolicyTest {
    @Test
    @DisplayName("delays follow exponential backoff sequence")
    fun `delays follow exponential backoff sequence`() {
        val policy = RetryPolicy()
        assertEquals(2_000L, policy.nextDelay())
        assertEquals(4_000L, policy.nextDelay())
        assertEquals(8_000L, policy.nextDelay())
        assertEquals(16_000L, policy.nextDelay())
        assertEquals(30_000L, policy.nextDelay())
    }

    @Test
    @DisplayName("returns null after max attempts exhausted")
    fun `returns null after max attempts exhausted`() {
        val policy = RetryPolicy()
        repeat(RetryPolicy.MAX_ATTEMPTS) { policy.nextDelay() }
        assertNull(policy.nextDelay())
    }

    @Test
    @DisplayName("reset restarts the backoff sequence")
    fun `reset restarts the backoff sequence`() {
        val policy = RetryPolicy()
        policy.nextDelay()
        policy.nextDelay()
        policy.reset()
        assertEquals(2_000L, policy.nextDelay())
    }

    @Test
    @DisplayName("delay is capped at MAX_DELAY_MS")
    fun `delay is capped at MAX_DELAY_MS`() {
        val policy = RetryPolicy()
        var lastDelay = 0L
        repeat(RetryPolicy.MAX_ATTEMPTS) {
            val delay = policy.nextDelay()
            if (delay != null) lastDelay = delay
        }
        assertEquals(RetryPolicy.MAX_DELAY_MS, lastDelay)
    }
}
