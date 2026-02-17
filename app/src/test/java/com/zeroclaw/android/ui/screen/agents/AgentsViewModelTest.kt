/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.agents

import com.zeroclaw.android.data.repository.AgentRepository
import com.zeroclaw.android.model.Agent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for agent search filtering logic.
 *
 * Tests the filtering logic independently of [AgentsViewModel] to avoid
 * requiring [android.app.Application].
 */
@DisplayName("Agent search filtering")
class AgentsViewModelTest {
    private val sampleAgents =
        listOf(
            Agent(
                id = "1",
                name = "General Assistant",
                provider = "Anthropic",
                modelName = "claude-sonnet-4-5-20250929",
            ),
            Agent(
                id = "2",
                name = "Code Reviewer",
                provider = "OpenAI",
                modelName = "gpt-4o",
            ),
        )

    @Test
    @DisplayName("empty query returns all agents")
    fun `empty query returns all agents`() = runTest {
        val filtered = filterAgents(sampleAgents, "")
        assertEquals(2, filtered.size)
    }

    @Test
    @DisplayName("search by name filters correctly")
    fun `search by name filters correctly`() = runTest {
        val filtered = filterAgents(sampleAgents, "general")
        assertEquals(1, filtered.size)
        assertEquals("General Assistant", filtered.first().name)
    }

    @Test
    @DisplayName("search by provider filters correctly")
    fun `search by provider filters correctly`() = runTest {
        val filtered = filterAgents(sampleAgents, "openai")
        assertEquals(1, filtered.size)
        assertEquals("Code Reviewer", filtered.first().name)
    }

    @Test
    @DisplayName("no match returns empty list")
    fun `no match returns empty list`() = runTest {
        val filtered = filterAgents(sampleAgents, "nonexistent")
        assertTrue(filtered.isEmpty())
    }

    @Test
    @DisplayName("toggle agent flips enabled state")
    fun `toggle agent flips enabled state`() = runTest {
        val repo = TestAgentRepository(sampleAgents.toMutableList())
        val before = repo.agents.first().first().isEnabled
        repo.toggleEnabled("1")
        assertEquals(!before, repo.agents.first().first().isEnabled)
    }
}

private fun filterAgents(agents: List<Agent>, query: String): List<Agent> =
    if (query.isBlank()) {
        agents
    } else {
        agents.filter { agent ->
            agent.name.contains(query, ignoreCase = true) ||
                agent.provider.contains(query, ignoreCase = true)
        }
    }

/**
 * Test double for [AgentRepository].
 */
private class TestAgentRepository(
    initial: MutableList<Agent>,
) : AgentRepository {
    private val _agents = MutableStateFlow(initial.toList())
    override val agents = _agents

    override suspend fun getById(id: String) = _agents.value.find { it.id == id }

    override suspend fun save(agent: Agent) {
        _agents.update { current ->
            val index = current.indexOfFirst { it.id == agent.id }
            if (index >= 0) {
                current.toMutableList().apply { set(index, agent) }
            } else {
                current + agent
            }
        }
    }

    override suspend fun delete(id: String) {
        _agents.update { it.filter { a -> a.id != id } }
    }

    override suspend fun toggleEnabled(id: String) {
        _agents.update { current ->
            current.map { if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it }
        }
    }
}
