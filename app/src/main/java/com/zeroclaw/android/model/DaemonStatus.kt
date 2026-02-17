/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * Snapshot of daemon health at a single point in time.
 *
 * Parsed from the JSON returned by [com.zeroclaw.ffi.getStatus].
 *
 * @property running Whether the daemon process is currently active.
 * @property uptimeSeconds Wall-clock seconds since the daemon was started.
 * @property components Map of component name to its [ComponentStatus].
 */
data class DaemonStatus(
    val running: Boolean,
    val uptimeSeconds: Long,
    val components: Map<String, ComponentStatus>,
)

/**
 * Health status of a single daemon component.
 *
 * @property name Human-readable component name (e.g. "gateway", "scheduler").
 * @property status Status string: "ok", "error", or "starting".
 */
data class ComponentStatus(
    val name: String,
    val status: String,
)

/**
 * Lifecycle states of the foreground service.
 */
enum class ServiceState {
    /** Service is not running. */
    STOPPED,

    /** Service is starting the daemon. */
    STARTING,

    /** Service is running and the daemon is healthy. */
    RUNNING,

    /** Service is stopping the daemon. */
    STOPPING,

    /** Service encountered an error. */
    ERROR,
}
