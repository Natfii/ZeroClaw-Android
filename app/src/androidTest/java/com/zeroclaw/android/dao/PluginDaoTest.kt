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
import com.zeroclaw.android.data.local.dao.PluginDao
import com.zeroclaw.android.data.local.entity.PluginEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PluginDaoTest {
    private lateinit var database: ZeroClawDatabase
    private lateinit var dao: PluginDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, ZeroClawDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.pluginDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsert_and_observeAll_returns_plugins() =
        runBlocking {
            dao.upsert(makePlugin("p1", "Plugin A"))

            val result = dao.observeAll().first()
            assertEquals(1, result.size)
            assertEquals("Plugin A", result[0].name)
        }

    @Test
    fun setInstalled_marks_plugin_as_installed() =
        runBlocking {
            dao.upsert(makePlugin("p1", "Plugin A", isInstalled = false))
            dao.setInstalled("p1")

            val result = dao.getById("p1")
            assertNotNull(result)
            assertEquals(true, result!!.isInstalled)
        }

    @Test
    fun uninstall_clears_installed_and_enabled() =
        runBlocking {
            dao.upsert(makePlugin("p1", "Plugin A", isInstalled = true, isEnabled = true))
            dao.uninstall("p1")

            val result = dao.getById("p1")
            assertNotNull(result)
            assertEquals(false, result!!.isInstalled)
            assertEquals(false, result.isEnabled)
        }

    @Test
    fun toggleEnabled_only_affects_installed_plugins() =
        runBlocking {
            dao.upsert(makePlugin("p1", "Plugin A", isInstalled = false, isEnabled = false))
            dao.toggleEnabled("p1")

            val result = dao.getById("p1")
            assertNotNull(result)
            assertEquals(false, result!!.isEnabled)
        }

    @Test
    fun toggleEnabled_flips_state_for_installed_plugin() =
        runBlocking {
            dao.upsert(makePlugin("p1", "Plugin A", isInstalled = true, isEnabled = false))
            dao.toggleEnabled("p1")

            val result = dao.getById("p1")
            assertNotNull(result)
            assertEquals(true, result!!.isEnabled)
        }

    @Test
    fun updateConfigJson_updates_config() =
        runBlocking {
            dao.upsert(makePlugin("p1", "Plugin A"))
            dao.updateConfigJson("p1", """{"port":"9090"}""")

            val result = dao.getById("p1")
            assertNotNull(result)
            assertEquals("""{"port":"9090"}""", result!!.configJson)
        }

    @Test
    fun count_returns_correct_count() =
        runBlocking {
            assertEquals(0, dao.count())
            dao.upsert(makePlugin("p1", "A"))
            dao.upsert(makePlugin("p2", "B"))
            assertEquals(2, dao.count())
        }

    @Test
    fun insertAllIgnoreConflicts_does_not_overwrite_existing() =
        runBlocking {
            dao.upsert(makePlugin("p1", "Original"))
            dao.insertAllIgnoreConflicts(listOf(makePlugin("p1", "Overwritten")))

            val result = dao.getById("p1")
            assertNotNull(result)
            assertEquals("Original", result!!.name)
        }

    private fun makePlugin(
        id: String,
        name: String,
        isInstalled: Boolean = false,
        isEnabled: Boolean = false,
    ): PluginEntity =
        PluginEntity(
            id = id,
            name = name,
            description = "Test plugin",
            version = "1.0.0",
            author = "Test",
            category = "TOOL",
            isInstalled = isInstalled,
            isEnabled = isEnabled,
            configJson = "{}",
        )
}
