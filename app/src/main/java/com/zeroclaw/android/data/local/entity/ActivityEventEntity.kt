/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an event in the dashboard activity feed.
 *
 * The timestamp column is indexed for efficient time-ordered queries and pruning.
 *
 * @property id Auto-generated primary key.
 * @property timestamp Epoch milliseconds when the event occurred.
 * @property type Activity type name matching [com.zeroclaw.android.model.ActivityType].
 * @property message Human-readable event description.
 */
@Entity(
    tableName = "activity_events",
    indices = [Index(value = ["timestamp"])],
)
data class ActivityEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val message: String,
)
