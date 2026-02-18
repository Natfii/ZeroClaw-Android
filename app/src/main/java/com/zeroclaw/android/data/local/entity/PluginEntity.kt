/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a plugin that extends daemon functionality.
 *
 * Configuration fields are stored as a JSON-encoded TEXT column
 * to support arbitrary key-value pairs without schema changes.
 *
 * @property id Unique identifier for the plugin.
 * @property name Human-readable display name.
 * @property description Short description of the plugin's purpose.
 * @property version Current version string.
 * @property author Plugin author or organization.
 * @property category Functional category name matching [com.zeroclaw.android.model.PluginCategory].
 * @property isInstalled Whether the plugin is installed locally.
 * @property isEnabled Whether the plugin is active.
 * @property configJson JSON-serialized map of configuration keys to values.
 * @property remoteVersion Latest version available in the remote registry.
 */
@Entity(tableName = "plugins")
data class PluginEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    val category: String,
    @ColumnInfo(name = "is_installed")
    val isInstalled: Boolean,
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean,
    @ColumnInfo(name = "config_json")
    val configJson: String,
    @ColumnInfo(name = "remote_version")
    val remoteVersion: String? = null,
)
