// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.model

/**
 * A skill loaded from the workspace or community repository.
 *
 * Maps to the Rust `FfiSkill` record transferred across the FFI boundary.
 *
 * @property name Display name of the skill.
 * @property description Human-readable description.
 * @property version Semantic version string.
 * @property author Optional author name or identifier.
 * @property tags Tags for categorisation (e.g. "automation", "devops").
 * @property toolCount Number of tools provided by this skill.
 * @property toolNames Names of the tools provided by this skill.
 */
data class Skill(
    val name: String,
    val description: String,
    val version: String,
    val author: String?,
    val tags: List<String>,
    val toolCount: Int,
    val toolNames: List<String>,
)
