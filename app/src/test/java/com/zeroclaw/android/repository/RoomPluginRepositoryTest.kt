/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.repository

import app.cash.turbine.test
import com.zeroclaw.android.data.local.dao.PluginDao
import com.zeroclaw.android.data.local.entity.PluginEntity
import com.zeroclaw.android.data.repository.RoomPluginRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RoomPluginRepositoryTest {
    private lateinit var dao: PluginDao
    private lateinit var repo: RoomPluginRepository

    @BeforeEach
    fun setUp() {
        dao = mockk(relaxUnitFun = true)
        every { dao.observeAll() } returns flowOf(emptyList())
        repo = RoomPluginRepository(dao)
    }

    @Test
    fun `plugins flow maps entities to domain models`() =
        runTest {
            val entity = makeEntity("p1", "Plugin A")
            every { dao.observeAll() } returns flowOf(listOf(entity))
            repo = RoomPluginRepository(dao)

            repo.plugins.test {
                val plugins = awaitItem()
                assertEquals(1, plugins.size)
                assertEquals("Plugin A", plugins[0].name)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `getById returns mapped model`() =
        runTest {
            coEvery { dao.getById("p1") } returns makeEntity("p1", "Plugin A")

            val result = repo.getById("p1")
            assertEquals("Plugin A", result?.name)
        }

    @Test
    fun `getById returns null when not found`() =
        runTest {
            coEvery { dao.getById("missing") } returns null

            assertNull(repo.getById("missing"))
        }

    @Test
    fun `install delegates to dao setInstalled`() =
        runTest {
            repo.install("p1")

            coVerify { dao.setInstalled("p1") }
        }

    @Test
    fun `uninstall delegates to dao uninstall`() =
        runTest {
            repo.uninstall("p1")

            coVerify { dao.uninstall("p1") }
        }

    @Test
    fun `toggleEnabled delegates to dao toggleEnabled`() =
        runTest {
            repo.toggleEnabled("p1")

            coVerify { dao.toggleEnabled("p1") }
        }

    @Test
    fun `updateConfig merges key into existing config`() =
        runTest {
            val existing =
                makeEntity("p1", "Plugin A").copy(
                    configJson = """{"port":"8080"}""",
                )
            coEvery { dao.getById("p1") } returns existing

            repo.updateConfig("p1", "host", "0.0.0.0")

            coVerify {
                dao.updateConfigJson(
                    "p1",
                    match { json ->
                        json.contains("port") &&
                            json.contains("8080") &&
                            json.contains("host") &&
                            json.contains("0.0.0.0")
                    },
                )
            }
        }

    @Test
    fun `updateConfig does nothing when plugin not found`() =
        runTest {
            coEvery { dao.getById("missing") } returns null

            repo.updateConfig("missing", "key", "value")

            coVerify(exactly = 0) { dao.updateConfigJson(any(), any()) }
        }

    private fun makeEntity(
        id: String,
        name: String,
    ): PluginEntity =
        PluginEntity(
            id = id,
            name = name,
            description = "Test",
            version = "1.0.0",
            author = "Test",
            category = "TOOL",
            isInstalled = false,
            isEnabled = false,
            configJson = "{}",
        )
}
