/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * An event in the dashboard activity feed.
 *
 * @property id Unique identifier.
 * @property timestamp Epoch milliseconds when the event occurred.
 * @property type Category of the event.
 * @property message Human-readable event description.
 */
data class ActivityEvent(
    val id: Long,
    val timestamp: Long,
    val type: ActivityType,
    val message: String,
)

/**
 * Categories for activity feed events.
 */
enum class ActivityType {
    /** Daemon started successfully. */
    DAEMON_STARTED,

    /** Daemon stopped. */
    DAEMON_STOPPED,

    /** Daemon encountered an error. */
    DAEMON_ERROR,

    /** An FFI call was made. */
    FFI_CALL,

    /** Network connectivity changed. */
    NETWORK_CHANGE,

    /** Configuration was updated. */
    CONFIG_CHANGE,
}
