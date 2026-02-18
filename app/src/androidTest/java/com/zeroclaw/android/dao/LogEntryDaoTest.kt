/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zeroclaw.android.data.local.ZeroClawDatabase
import com.zeroclaw.android.data.local.dao.LogEntryDao
import com.zeroclaw.android.data.local.entity.LogEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogEntryDaoTest {
    private lateinit var database: ZeroClawDatabase
    private lateinit var dao: LogEntryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, ZeroClawDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.logEntryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insert_and_observeRecent_returns_entries_newest_first() =
        runBlocking {
            dao.insert(makeEntry(timestamp = 1000L, message = "First"))
            dao.insert(makeEntry(timestamp = 2000L, message = "Second"))

            val result = dao.observeRecent(10).first()
            assertEquals(2, result.size)
            assertEquals("Second", result[0].message)
            assertEquals("First", result[1].message)
        }

    @Test
    fun observeRecent_respects_limit() =
        runBlocking {
            repeat(5) { i ->
                dao.insert(makeEntry(timestamp = i.toLong() * 1000, message = "Entry $i"))
            }

            val result = dao.observeRecent(3).first()
            assertEquals(3, result.size)
        }

    @Test
    fun deleteAll_clears_all_entries() =
        runBlocking {
            dao.insert(makeEntry(timestamp = 1000L))
            dao.insert(makeEntry(timestamp = 2000L))
            dao.deleteAll()

            val result = dao.observeRecent(10).first()
            assertTrue(result.isEmpty())
        }

    @Test
    fun pruneOldest_retains_most_recent() =
        runBlocking {
            dao.insert(makeEntry(timestamp = 1000L, message = "Old"))
            dao.insert(makeEntry(timestamp = 2000L, message = "Middle"))
            dao.insert(makeEntry(timestamp = 3000L, message = "New"))
            dao.pruneOldest(2)

            val count = dao.count()
            assertEquals(2, count)

            val result = dao.observeRecent(10).first()
            assertEquals("New", result[0].message)
            assertEquals("Middle", result[1].message)
        }

    @Test
    fun count_returns_correct_value() =
        runBlocking {
            assertEquals(0, dao.count())
            dao.insert(makeEntry(timestamp = 1000L))
            assertEquals(1, dao.count())
        }

    private fun makeEntry(
        timestamp: Long = System.currentTimeMillis(),
        message: String = "Test log",
    ): LogEntryEntity =
        LogEntryEntity(
            timestamp = timestamp,
            severity = "INFO",
            tag = "Test",
            message = message,
        )
}
