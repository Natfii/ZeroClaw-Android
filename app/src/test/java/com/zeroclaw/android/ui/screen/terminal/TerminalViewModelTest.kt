/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.terminal

import com.zeroclaw.android.model.TerminalEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [TerminalViewModel] companion utilities and [TerminalState].
 *
 * The ViewModel itself requires an [android.app.Application] and native
 * library loading, so these tests exercise the static helper functions
 * and state mapping logic that can be tested without the Android framework.
 */
@DisplayName("TerminalViewModel")
class TerminalViewModelTest {
    @Nested
    @DisplayName("toBlock")
    inner class ToBlock {
        @Test
        @DisplayName("maps input entry to Input block")
        fun `maps input entry to Input block`() {
            val entry =
                TerminalEntry(
                    id = 1,
                    content = "/status",
                    entryType = "input",
                    timestamp = 1000L,
                )
            val block = TerminalViewModel.toBlock(entry)
            assertTrue(block is TerminalBlock.Input)
            assertEquals("/status", (block as TerminalBlock.Input).text)
            assertEquals(1L, block.id)
            assertEquals(1000L, block.timestamp)
        }

        @Test
        @DisplayName("maps response entry to Response block")
        fun `maps response entry to Response block`() {
            val entry =
                TerminalEntry(
                    id = 2,
                    content = "Daemon is running",
                    entryType = "response",
                    timestamp = 2000L,
                )
            val block = TerminalViewModel.toBlock(entry)
            assertTrue(block is TerminalBlock.Response)
            assertEquals("Daemon is running", (block as TerminalBlock.Response).content)
        }

        @Test
        @DisplayName("maps JSON response to Structured block")
        fun `maps JSON response to Structured block`() {
            val json = """{"daemon_running": true, "uptime": 3600}"""
            val entry =
                TerminalEntry(
                    id = 3,
                    content = json,
                    entryType = "response",
                    timestamp = 3000L,
                )
            val block = TerminalViewModel.toBlock(entry)
            assertTrue(block is TerminalBlock.Structured)
            assertEquals(json, (block as TerminalBlock.Structured).json)
        }

        @Test
        @DisplayName("maps array response to Structured block")
        fun `maps array response to Structured block`() {
            val json = """[{"name": "skill-1"}]"""
            val entry =
                TerminalEntry(
                    id = 4,
                    content = json,
                    entryType = "response",
                    timestamp = 4000L,
                )
            val block = TerminalViewModel.toBlock(entry)
            assertTrue(block is TerminalBlock.Structured)
        }

        @Test
        @DisplayName("maps error entry to Error block")
        fun `maps error entry to Error block`() {
            val entry =
                TerminalEntry(
                    id = 5,
                    content = "Connection refused",
                    entryType = "error",
                    timestamp = 5000L,
                )
            val block = TerminalViewModel.toBlock(entry)
            assertTrue(block is TerminalBlock.Error)
            assertEquals("Connection refused", (block as TerminalBlock.Error).message)
        }

        @Test
        @DisplayName("maps system entry to System block")
        fun `maps system entry to System block`() {
            val entry =
                TerminalEntry(
                    id = 6,
                    content = "ZeroClaw Terminal v0.0.29",
                    entryType = "system",
                    timestamp = 6000L,
                )
            val block = TerminalViewModel.toBlock(entry)
            assertTrue(block is TerminalBlock.System)
            assertEquals("ZeroClaw Terminal v0.0.29", (block as TerminalBlock.System).text)
        }

        @Test
        @DisplayName("maps unknown entry type to System block")
        fun `maps unknown entry type to System block`() {
            val entry =
                TerminalEntry(
                    id = 7,
                    content = "Unknown type",
                    entryType = "unknown",
                    timestamp = 7000L,
                )
            val block = TerminalViewModel.toBlock(entry)
            assertTrue(block is TerminalBlock.System)
        }

        @Test
        @DisplayName("maps input entry with image URIs to Input block with image names")
        fun `maps input entry with image URIs to Input block with image names`() {
            val entry =
                TerminalEntry(
                    id = 8,
                    content = "describe this",
                    entryType = "input",
                    timestamp = 8000L,
                    imageUris = listOf("content://media/external/images/photo.jpg"),
                )
            val block = TerminalViewModel.toBlock(entry)
            assertTrue(block is TerminalBlock.Input)
            val inputBlock = block as TerminalBlock.Input
            assertEquals(1, inputBlock.imageNames.size)
            assertEquals("photo.jpg", inputBlock.imageNames.first())
        }
    }

    @Nested
    @DisplayName("stripThinkingTags")
    inner class StripThinkingTags {
        @Test
        @DisplayName("removes think tags from response")
        fun `removes think tags from response`() {
            val input = "<think>Let me reason about this.</think>The answer is 42."
            val result = TerminalViewModel.stripThinkingTags(input)
            assertEquals("The answer is 42.", result)
        }

        @Test
        @DisplayName("removes thinking tags from response")
        fun `removes thinking tags from response`() {
            val input = "<thinking>Internal reasoning here.</thinking>Final answer."
            val result = TerminalViewModel.stripThinkingTags(input)
            assertEquals("Final answer.", result)
        }

        @Test
        @DisplayName("passes through text without thinking tags")
        fun `passes through text without thinking tags`() {
            val input = "Plain response text"
            val result = TerminalViewModel.stripThinkingTags(input)
            assertEquals("Plain response text", result)
        }
    }

    @Nested
    @DisplayName("stripToolCallTags")
    inner class StripToolCallTags {
        @Test
        @DisplayName("removes tool_call tags from response")
        fun `removes tool_call tags from response`() {
            val input = "Result: <tool_call>{\"name\":\"test\"}</tool_call>done"
            val result = TerminalViewModel.stripToolCallTags(input)
            assertEquals("Result: done", result)
        }

        @Test
        @DisplayName("removes unclosed tool_call tags")
        fun `removes unclosed tool_call tags`() {
            val input = "Partial: <tool_call>{\"name\":\"test\"}"
            val result = TerminalViewModel.stripToolCallTags(input)
            assertEquals("Partial:", result)
        }

        @Test
        @DisplayName("passes through text without tool call tags")
        fun `passes through text without tool call tags`() {
            val input = "Normal response"
            val result = TerminalViewModel.stripToolCallTags(input)
            assertEquals("Normal response", result)
        }
    }

    @Nested
    @DisplayName("TerminalState defaults")
    inner class StateDefaults {
        @Test
        @DisplayName("default state has empty blocks and loading false")
        fun `default state has empty blocks and loading false`() {
            val state = TerminalState()
            assertTrue(state.blocks.isEmpty())
            assertEquals(false, state.isLoading)
            assertTrue(state.pendingImages.isEmpty())
            assertEquals(false, state.isProcessingImages)
        }
    }
}
