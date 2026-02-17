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
 * Room entity representing a configured chat channel.
 *
 * Secret field values (bot tokens, passwords) are stored separately
 * in EncryptedSharedPreferences. This entity holds only non-secret
 * configuration and metadata.
 *
 * @property id Unique identifier for the channel (UUID string).
 * @property type Channel type name matching [com.zeroclaw.android.model.ChannelType.name].
 * @property isEnabled Whether the channel is active for daemon communication.
 * @property configJson JSON-serialized map of non-secret configuration values.
 * @property createdAt Epoch milliseconds when the channel was configured.
 */
@Entity(tableName = "connected_channels")
data class ConnectedChannelEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean,
    @ColumnInfo(name = "config_json")
    val configJson: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
