/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.validation

/**
 * Represents the state of an asynchronous validation operation.
 *
 * Used throughout the app to track live API key and channel token validation
 * in provider setup flows, channel configuration, and settings screens.
 *
 * The [isTerminal] property distinguishes states that represent a final outcome
 * ([Success], [Failure], [Offline]) from transient states ([Idle], [Loading]).
 */
sealed interface ValidationResult {
    /**
     * Whether this result represents a final outcome.
     *
     * Terminal states ([Success], [Failure], [Offline]) indicate that validation
     * has concluded and no further state transitions are expected without a new
     * validation attempt. Non-terminal states ([Idle], [Loading]) indicate that
     * validation has not yet produced a final outcome.
     */
    val isTerminal: Boolean

    /**
     * No validation has been attempted yet.
     *
     * This is the default initial state before the user triggers validation.
     */
    data object Idle : ValidationResult {
        override val isTerminal: Boolean = false
    }

    /**
     * Validation is currently in progress.
     *
     * The UI should display a progress indicator while in this state.
     */
    data object Loading : ValidationResult {
        override val isTerminal: Boolean = false
    }

    /**
     * Validation succeeded.
     *
     * @property details A human-readable description of the successful validation
     *   outcome, such as the number of models returned or endpoint version.
     */
    data class Success(
        val details: String,
    ) : ValidationResult {
        override val isTerminal: Boolean = true
    }

    /**
     * Validation failed.
     *
     * @property message A human-readable error description suitable for display to the user.
     * @property retryable Whether the validation can be retried. Defaults to true.
     *   Set to false for permanent failures such as malformed API keys.
     */
    data class Failure(
        val message: String,
        val retryable: Boolean = true,
    ) : ValidationResult {
        override val isTerminal: Boolean = true
    }

    /**
     * The device is offline or the validation endpoint is unreachable.
     *
     * Distinct from [Failure] to allow the UI to show connectivity-specific
     * guidance (e.g. "Check your internet connection") rather than a generic
     * error message.
     *
     * @property message A human-readable description of the connectivity issue.
     */
    data class Offline(
        val message: String,
    ) : ValidationResult {
        override val isTerminal: Boolean = true
    }
}
