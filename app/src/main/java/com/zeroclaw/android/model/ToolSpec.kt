// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.model

/**
 * Specification of a tool available to the daemon.
 *
 * Maps to the Rust `FfiToolSpec` record transferred across the FFI boundary.
 *
 * @property name Unique tool name (e.g. "shell", "file_read").
 * @property description Human-readable description of the tool.
 * @property source Origin of the tool: "built-in" or the skill name.
 * @property parametersJson JSON schema for the tool parameters, or "{}" if unavailable.
 *   **Security note:** this field may contain user-supplied JSON from skill definitions.
 *   Display it read-only and never evaluate it as executable code.
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val source: String,
    val parametersJson: String,
)
