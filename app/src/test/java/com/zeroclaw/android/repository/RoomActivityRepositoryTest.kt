/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.repository

import com.zeroclaw.android.data.local.dao.ActivityEventDao
import com.zeroclaw.android.data.local.entity.ActivityEventEntity
import com.zeroclaw.android.data.repository.RoomActivityRepository
import com.zeroclaw.android.model.ActivityType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomActivityRepositoryTest {
    private lateinit var dao: ActivityEventDao

    @BeforeEach
    fun setUp() {
        dao = mockk(relaxUnitFun = true)
        every { dao.observeRecent(any()) } returns flowOf(emptyList())
    }

    @Test
    fun `record inserts entity via dao`() =
        runTest {
            coEvery { dao.count() } returns 1
            val repo = RoomActivityRepository(dao = dao, ioScope = this, maxEvents = 100)

            repo.record(ActivityType.DAEMON_STARTED, "Daemon started")
            advanceUntilIdle()

            val slot = slot<ActivityEventEntity>()
            coVerify { dao.insert(capture(slot)) }
            assertEquals("DAEMON_STARTED", slot.captured.type)
            assertEquals("Daemon started", slot.captured.message)
        }

    @Test
    fun `record prunes when count exceeds max`() =
        runTest {
            val maxEvents = 10
            coEvery { dao.count() } returns maxEvents + 1
            val repo = RoomActivityRepository(dao = dao, ioScope = this, maxEvents = maxEvents)

            repo.record(ActivityType.FFI_CALL, "Test")
            advanceUntilIdle()

            coVerify { dao.pruneOldest(maxEvents) }
        }

    @Test
    fun `record does not prune when count is within limit`() =
        runTest {
            coEvery { dao.count() } returns 5
            val repo = RoomActivityRepository(dao = dao, ioScope = this, maxEvents = 100)

            repo.record(ActivityType.NETWORK_CHANGE, "WiFi connected")
            advanceUntilIdle()

            coVerify(exactly = 0) { dao.pruneOldest(any()) }
        }
}
