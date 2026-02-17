/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.repository

import com.zeroclaw.android.data.local.dao.LogEntryDao
import com.zeroclaw.android.data.local.entity.LogEntryEntity
import com.zeroclaw.android.data.repository.RoomLogRepository
import com.zeroclaw.android.model.LogSeverity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomLogRepositoryTest {
    private lateinit var dao: LogEntryDao

    @BeforeEach
    fun setUp() {
        dao = mockk(relaxUnitFun = true)
        every { dao.observeRecent(any()) } returns flowOf(emptyList())
    }

    @Test
    fun `append inserts entity via dao`() = runTest {
        coEvery { dao.count() } returns 1
        val repo = RoomLogRepository(dao = dao, ioScope = this, maxEntries = 100)

        repo.append(LogSeverity.INFO, "Test", "Hello")
        advanceUntilIdle()

        val slot = slot<LogEntryEntity>()
        coVerify { dao.insert(capture(slot)) }
        assertEquals("INFO", slot.captured.severity)
        assertEquals("Test", slot.captured.tag)
        assertEquals("Hello", slot.captured.message)
    }

    @Test
    fun `append prunes when count exceeds max`() = runTest {
        val maxEntries = 10
        coEvery { dao.count() } returns maxEntries + 1
        val repo = RoomLogRepository(dao = dao, ioScope = this, maxEntries = maxEntries)

        repo.append(LogSeverity.ERROR, "Test", "Overflow")
        advanceUntilIdle()

        coVerify { dao.pruneOldest(maxEntries) }
    }

    @Test
    fun `append does not prune when count is within limit`() = runTest {
        coEvery { dao.count() } returns 5
        val repo = RoomLogRepository(dao = dao, ioScope = this, maxEntries = 100)

        repo.append(LogSeverity.DEBUG, "Test", "Normal")
        advanceUntilIdle()

        coVerify(exactly = 0) { dao.pruneOldest(any()) }
    }

    @Test
    fun `clear delegates to dao deleteAll`() = runTest {
        val repo = RoomLogRepository(dao = dao, ioScope = this)

        repo.clear()
        advanceUntilIdle()

        coVerify { dao.deleteAll() }
    }
}
