/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.plugins

import com.zeroclaw.android.data.repository.PluginRepository
import com.zeroclaw.android.model.Plugin
import com.zeroclaw.android.model.PluginCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for plugin filtering and tab logic.
 *
 * Tests the filtering logic independently of [PluginsViewModel]
 * to avoid requiring [android.app.Application].
 */
@DisplayName("Plugin tab and search filtering")
class PluginsViewModelTest {
    private val samplePlugins =
        listOf(
            Plugin(
                id = "1",
                name = "HTTP Channel",
                description = "REST channel",
                version = "1.0.0",
                author = "ZeroClaw",
                category = PluginCategory.CHANNEL,
                isInstalled = true,
                isEnabled = true,
            ),
            Plugin(
                id = "2",
                name = "Web Search",
                description = "Search tool",
                version = "0.5.0",
                author = "Community",
                category = PluginCategory.TOOL,
                isInstalled = false,
            ),
        )

    @Test
    @DisplayName("installed tab shows only installed plugins")
    fun `installed tab shows only installed plugins`() =
        runTest {
            val filtered = filterPlugins(samplePlugins, TAB_INSTALLED, "")
            assertEquals(1, filtered.size)
            assertTrue(filtered.all { it.isInstalled })
        }

    @Test
    @DisplayName("available tab shows only uninstalled plugins")
    fun `available tab shows only uninstalled plugins`() =
        runTest {
            val filtered = filterPlugins(samplePlugins, TAB_AVAILABLE, "")
            assertEquals(1, filtered.size)
            assertTrue(filtered.none { it.isInstalled })
        }

    @Test
    @DisplayName("search by name filters correctly")
    fun `search by name filters correctly`() =
        runTest {
            val filtered = filterPlugins(samplePlugins, TAB_INSTALLED, "HTTP")
            assertEquals(1, filtered.size)
            assertEquals("HTTP Channel", filtered.first().name)
        }

    @Test
    @DisplayName("search by description filters correctly")
    fun `search by description filters correctly`() =
        runTest {
            val filtered = filterPlugins(samplePlugins, TAB_AVAILABLE, "search")
            assertEquals(1, filtered.size)
            assertEquals("Web Search", filtered.first().name)
        }

    @Test
    @DisplayName("no match returns empty list")
    fun `no match returns empty list`() =
        runTest {
            val filtered = filterPlugins(samplePlugins, TAB_INSTALLED, "nonexistent")
            assertTrue(filtered.isEmpty())
        }

    @Test
    @DisplayName("install moves plugin to installed tab")
    fun `install moves plugin to installed tab`() =
        runTest {
            val repo = TestPluginRepository(samplePlugins.toMutableList())
            repo.install("2")
            val installed = repo.plugins.first().filter { it.isInstalled }
            assertEquals(2, installed.size)
        }
}

private fun filterPlugins(
    plugins: List<Plugin>,
    tab: Int,
    query: String,
): List<Plugin> {
    val tabFiltered =
        when (tab) {
            TAB_INSTALLED -> plugins.filter { it.isInstalled }
            else -> plugins.filter { !it.isInstalled }
        }
    return if (query.isBlank()) {
        tabFiltered
    } else {
        tabFiltered.filter { plugin ->
            plugin.name.contains(query, ignoreCase = true) ||
                plugin.description.contains(query, ignoreCase = true)
        }
    }
}

/**
 * Test double for [PluginRepository].
 */
private class TestPluginRepository(
    initial: MutableList<Plugin>,
) : PluginRepository {
    private val _plugins = MutableStateFlow(initial.toList())
    override val plugins = _plugins

    override suspend fun getById(id: String) = _plugins.value.find { it.id == id }

    override suspend fun install(id: String) {
        _plugins.update { current ->
            current.map { if (it.id == id) it.copy(isInstalled = true) else it }
        }
    }

    override suspend fun uninstall(id: String) {
        _plugins.update { current ->
            current.map {
                if (it.id == id) it.copy(isInstalled = false, isEnabled = false) else it
            }
        }
    }

    override suspend fun toggleEnabled(id: String) {
        _plugins.update { current ->
            current.map {
                if (it.id == id && it.isInstalled) it.copy(isEnabled = !it.isEnabled) else it
            }
        }
    }

    override suspend fun updateConfig(
        pluginId: String,
        key: String,
        value: String,
    ) {
        _plugins.update { current ->
            current.map {
                if (it.id ==
                    pluginId
                ) {
                    it.copy(configFields = it.configFields + (key to value))
                } else {
                    it
                }
            }
        }
    }
}
