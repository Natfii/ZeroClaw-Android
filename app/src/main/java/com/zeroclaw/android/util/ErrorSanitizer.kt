/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.util

import com.zeroclaw.ffi.FfiException

/**
 * Sanitises exception messages before they reach the UI layer.
 *
 * Prevents leaking internal Rust panic backtraces, raw JSON parse errors,
 * or state corruption details to the user. Used by ViewModels and bridge
 * classes that catch [FfiException] or JSON parse exceptions.
 */
object ErrorSanitizer {
    /** Maximum length for a user-visible error message. */
    private const val MAX_UI_MESSAGE_LENGTH = 200

    /**
     * Returns a user-safe error message from an exception.
     *
     * [FfiException.InternalPanic] and [FfiException.StateCorrupted] are
     * mapped to generic messages because their `detail` fields may contain
     * stack traces or memory addresses. JSON parse errors are given a
     * static message. All other messages are truncated to
     * [MAX_UI_MESSAGE_LENGTH] characters.
     *
     * @param e The exception to sanitise.
     * @return A user-safe error string.
     */
    fun sanitizeForUi(e: Exception): String =
        when (e) {
            is FfiException.InternalPanic ->
                "An internal error occurred. Please restart the service."
            is FfiException.StateCorrupted ->
                "Internal state corrupted. Please restart the app."
            is org.json.JSONException ->
                "Received malformed data from the native layer."
            else -> {
                val msg = e.message ?: "Unknown error"
                if (msg.length > MAX_UI_MESSAGE_LENGTH) {
                    msg.take(MAX_UI_MESSAGE_LENGTH) + "..."
                } else {
                    msg
                }
            }
        }

    /**
     * Fixed message for status JSON parse failures.
     *
     * Never includes the raw JSON content or exception detail, which
     * could leak internal health snapshot data. Use directly as the
     * message for [IllegalStateException].
     */
    const val STATUS_PARSE_ERROR = "Native layer returned invalid status JSON"
}
