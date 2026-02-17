/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.data.local.dao.ActivityEventDao
import com.zeroclaw.android.data.local.entity.ActivityEventEntity
import com.zeroclaw.android.data.local.entity.toModel
import com.zeroclaw.android.model.ActivityEvent
import com.zeroclaw.android.model.ActivityType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Room-backed [ActivityRepository] implementation.
 *
 * The [record] method is non-suspend to match the [ActivityRepository]
 * interface contract. It launches a coroutine on the provided [ioScope]
 * for fire-and-forget database writes.
 *
 * Automatically prunes events when the count exceeds [maxEvents].
 *
 * @param dao The data access object for activity event operations.
 * @param ioScope Coroutine scope for background database writes.
 * @param maxEvents Maximum number of events to retain before pruning.
 */
class RoomActivityRepository(
    private val dao: ActivityEventDao,
    private val ioScope: CoroutineScope,
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
) : ActivityRepository {
    override val events: Flow<List<ActivityEvent>> =
        dao.observeRecent(maxEvents).map { entities -> entities.map { it.toModel() } }

    override fun record(
        type: ActivityType,
        message: String,
    ) {
        ioScope.launch {
            dao.insert(
                ActivityEventEntity(
                    timestamp = System.currentTimeMillis(),
                    type = type.name,
                    message = message,
                ),
            )
            pruneIfNeeded()
        }
    }

    /**
     * Prunes old events when the total count exceeds [maxEvents].
     */
    private suspend fun pruneIfNeeded() {
        val count = dao.count()
        if (count > maxEvents) {
            dao.pruneOldest(maxEvents)
        }
    }

    /** Constants for [RoomActivityRepository]. */
    companion object {
        /** Default maximum number of activity events retained. */
        const val DEFAULT_MAX_EVENTS = 500
    }
}
