/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * A single log entry from the daemon or service layer.
 *
 * @property id Monotonically increasing identifier.
 * @property timestamp Epoch milliseconds when the entry was created.
 * @property severity Log severity level.
 * @property tag Source tag (e.g. "FFI", "Service", "Daemon").
 * @property message Human-readable log message.
 */
data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val severity: LogSeverity,
    val tag: String,
    val message: String,
)

/**
 * Severity levels for log entries.
 */
enum class LogSeverity {
    /** Verbose debug output. */
    DEBUG,

    /** Standard informational messages. */
    INFO,

    /** Warning conditions. */
    WARN,

    /** Error conditions. */
    ERROR,
}
