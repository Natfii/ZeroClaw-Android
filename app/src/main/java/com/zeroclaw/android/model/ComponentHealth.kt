/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * Rich health snapshot for a single daemon component, mirroring the Rust `FfiComponentHealth` type.
 *
 * Unlike [ComponentStatus], this class carries additional diagnostics produced by the new health
 * detail endpoint: the last error message and cumulative restart count.
 *
 * @property name Human-readable component name (e.g. "gateway", "scheduler").
 * @property status Status string: "ok", "error", or "starting".
 * @property lastError Last error message recorded for this component, or `null` if none.
 * @property restartCount Cumulative number of times this component has been restarted. The
 *   underlying Rust type is `u64`; values are mapped to [Long] for JVM compatibility.
 */
data class ComponentHealth(
    val name: String,
    val status: String,
    val lastError: String?,
    val restartCount: Long,
)
