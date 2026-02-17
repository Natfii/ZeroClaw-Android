/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

/**
 * Exponential backoff retry policy for daemon startup failures.
 *
 * Calculates delay intervals using the formula
 * `delay = min(INITIAL_DELAY_MS * 2^attempt, MAX_DELAY_MS)`.
 * After [MAX_ATTEMPTS] consecutive failures, [nextDelay] returns null
 * to signal that retries are exhausted and the caller should transition
 * to a permanent error state.
 *
 * Example delay sequence: 2 s, 4 s, 8 s, 16 s, 30 s (capped).
 */
class RetryPolicy {
    private var currentAttempt = 0

    /**
     * Calculates the delay before the next retry attempt.
     *
     * Increments the internal attempt counter on each call. When
     * [MAX_ATTEMPTS] is reached the method returns null, indicating
     * that the caller should stop retrying.
     *
     * @return Delay in milliseconds before the next attempt, or null
     *   if the maximum number of attempts has been exhausted.
     */
    fun nextDelay(): Long? {
        if (currentAttempt >= MAX_ATTEMPTS) return null
        val delay = (INITIAL_DELAY_MS shl currentAttempt).coerceAtMost(MAX_DELAY_MS)
        currentAttempt++
        return delay
    }

    /**
     * Resets the attempt counter to zero.
     *
     * Call after a successful daemon start or an explicit user-initiated
     * stop so that subsequent failures start a fresh backoff sequence.
     */
    fun reset() {
        currentAttempt = 0
    }

    /** Constants for [RetryPolicy]. */
    companion object {
        /** Base delay for the first retry attempt. */
        const val INITIAL_DELAY_MS = 2_000L

        /** Upper bound on the backoff delay. */
        const val MAX_DELAY_MS = 30_000L

        /** Total retry attempts before giving up. */
        const val MAX_ATTEMPTS = 5
    }
}
