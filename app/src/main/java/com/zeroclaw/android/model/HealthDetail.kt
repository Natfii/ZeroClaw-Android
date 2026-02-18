/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * Detailed health snapshot returned by the extended health endpoint, mirroring the Rust
 * `FfiHealthDetail` type.
 *
 * This is a richer alternative to [DaemonStatus] that includes per-process metadata and
 * structured [ComponentHealth] entries rather than a plain status map.
 *
 * @property daemonRunning Whether the daemon process is currently active.
 * @property pid Operating-system process ID of the running daemon. The underlying Rust type is
 *   `u32`; the value is widened to [Long] to avoid sign ambiguity on the JVM.
 * @property uptimeSeconds Wall-clock seconds elapsed since the daemon was started. The underlying
 *   Rust type is `u64`; values are mapped to [Long] for JVM compatibility.
 * @property components Ordered list of [ComponentHealth] entries, one per registered component.
 */
data class HealthDetail(
    val daemonRunning: Boolean,
    val pid: Long,
    val uptimeSeconds: Long,
    val components: List<ComponentHealth>,
)
