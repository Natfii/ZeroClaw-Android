/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.data.local.dao.LogEntryDao
import com.zeroclaw.android.data.local.entity.LogEntryEntity
import com.zeroclaw.android.data.local.entity.toModel
import com.zeroclaw.android.model.LogEntry
import com.zeroclaw.android.model.LogSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Room-backed [LogRepository] implementation.
 *
 * The [append] and [clear] methods are non-suspend to match the
 * [LogRepository] interface contract. They launch coroutines on the
 * provided [ioScope] for fire-and-forget database writes.
 *
 * Automatically prunes entries when the count exceeds [maxEntries].
 *
 * @param dao The data access object for log entry operations.
 * @param ioScope Coroutine scope for background database writes.
 * @param maxEntries Maximum number of entries to retain before pruning.
 */
class RoomLogRepository(
    private val dao: LogEntryDao,
    private val ioScope: CoroutineScope,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : LogRepository {
    private var insertCounter = 0

    override val entries: Flow<List<LogEntry>> =
        dao.observeRecent(maxEntries).map { entities -> entities.map { it.toModel() } }

    override fun append(
        severity: LogSeverity,
        tag: String,
        message: String,
    ) {
        ioScope.launch {
            dao.insert(
                LogEntryEntity(
                    timestamp = System.currentTimeMillis(),
                    severity = severity.name,
                    tag = tag,
                    message = message,
                ),
            )
            if (++insertCounter % PRUNE_CHECK_INTERVAL == 0) {
                dao.pruneOldest(maxEntries)
            }
        }
    }

    override fun clear() {
        ioScope.launch {
            dao.deleteAll()
        }
    }

    /** Constants for [RoomLogRepository]. */
    companion object {
        /** Default maximum number of log entries retained. */
        const val DEFAULT_MAX_ENTRIES = 5000

        private const val PRUNE_CHECK_INTERVAL = 50
    }
}
