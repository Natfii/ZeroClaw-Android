/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single log entry from the daemon or service layer.
 *
 * The timestamp column is indexed for efficient time-ordered queries and pruning.
 *
 * @property id Auto-generated primary key.
 * @property timestamp Epoch milliseconds when the entry was created.
 * @property severity Log severity level name matching [com.zeroclaw.android.model.LogSeverity].
 * @property tag Source tag (e.g. "FFI", "Service", "Daemon").
 * @property message Human-readable log message.
 */
@Entity(
    tableName = "log_entries",
    indices = [Index(value = ["timestamp"])],
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val severity: String,
    val tag: String,
    val message: String,
)
