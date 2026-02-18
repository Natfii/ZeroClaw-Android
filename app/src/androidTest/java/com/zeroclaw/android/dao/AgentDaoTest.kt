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
import com.zeroclaw.android.data.local.dao.AgentDao
import com.zeroclaw.android.data.local.entity.AgentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentDaoTest {
    private lateinit var database: ZeroClawDatabase
    private lateinit var dao: AgentDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, ZeroClawDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.agentDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsert_and_observeAll_returns_inserted_agents() =
        runBlocking {
            val agent = makeAgent("a1", "Alpha Agent")
            dao.upsert(agent)

            val result = dao.observeAll().first()
            assertEquals(1, result.size)
            assertEquals("Alpha Agent", result[0].name)
        }

    @Test
    fun upsert_updates_existing_agent() =
        runBlocking {
            val agent = makeAgent("a1", "Original")
            dao.upsert(agent)
            dao.upsert(agent.copy(name = "Updated"))

            val result = dao.observeAll().first()
            assertEquals(1, result.size)
            assertEquals("Updated", result[0].name)
        }

    @Test
    fun getById_returns_matching_agent() =
        runBlocking {
            dao.upsert(makeAgent("a1", "Alpha"))
            val result = dao.getById("a1")
            assertNotNull(result)
            assertEquals("Alpha", result!!.name)
        }

    @Test
    fun getById_returns_null_for_missing_id() =
        runBlocking {
            assertNull(dao.getById("nonexistent"))
        }

    @Test
    fun deleteById_removes_agent() =
        runBlocking {
            dao.upsert(makeAgent("a1", "Alpha"))
            dao.deleteById("a1")

            val result = dao.observeAll().first()
            assertTrue(result.isEmpty())
        }

    @Test
    fun toggleEnabled_flips_state() =
        runBlocking {
            dao.upsert(makeAgent("a1", "Alpha", isEnabled = true))
            dao.toggleEnabled("a1")

            val result = dao.getById("a1")
            assertNotNull(result)
            assertEquals(false, result!!.isEnabled)
        }

    @Test
    fun observeAll_orders_by_name() =
        runBlocking {
            dao.upsert(makeAgent("a2", "Zeta"))
            dao.upsert(makeAgent("a1", "Alpha"))

            val result = dao.observeAll().first()
            assertEquals("Alpha", result[0].name)
            assertEquals("Zeta", result[1].name)
        }

    private fun makeAgent(
        id: String,
        name: String,
        isEnabled: Boolean = true,
    ): AgentEntity =
        AgentEntity(
            id = id,
            name = name,
            provider = "TestProvider",
            modelName = "test-model",
            isEnabled = isEnabled,
            systemPrompt = "",
            channelsJson = "[]",
        )
}
