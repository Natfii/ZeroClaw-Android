/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.model.ActivityEvent
import com.zeroclaw.android.model.ActivityType
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for dashboard activity feed events.
 */
interface ActivityRepository {
    /** Observable stream of recent activity events, newest first. */
    val events: Flow<List<ActivityEvent>>

    /**
     * Records a new activity event.
     *
     * Thread-safe: may be called from any thread.
     *
     * @param type Event category.
     * @param message Human-readable event description.
     */
    fun record(
        type: ActivityType,
        message: String,
    )
}
