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
 * @property isActive Whether the tool is usable in the current Android session.
 *   Session-available tools (memory, cron, web tools) are active. Tools requiring
 *   a SecurityPolicy (shell, file I/O, git) are inactive.
 * @property inactiveReason Human-readable reason the tool is inactive, or empty string
 *   when active. Common values: "Available via daemon channels only",
 *   "Disabled in settings".
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val source: String,
    val parametersJson: String,
    val isActive: Boolean,
    val inactiveReason: String,
)
