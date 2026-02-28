/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.terminal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CommandRegistry].
 *
 * Validates command lookup, prefix matching, and input parsing for
 * slash commands, local actions, and plain chat messages.
 */
@DisplayName("CommandRegistry")
class CommandRegistryTest {
    @Nested
    @DisplayName("find")
    inner class Find {
        @Test
        @DisplayName("returns correct command for known name")
        fun `find returns correct command for known name`() {
            val command = CommandRegistry.find("status")
            assertNotNull(command)
            assertEquals("status", command!!.name)
        }

        @Test
        @DisplayName("returns null for unknown name")
        fun `find returns null for unknown name`() {
            val command = CommandRegistry.find("nonexistent")
            assertNull(command)
        }
    }

    @Nested
    @DisplayName("matches")
    inner class Matches {
        @Test
        @DisplayName("filters by prefix")
        fun `matches filters by prefix`() {
            val results = CommandRegistry.matches("co")
            val names = results.map { it.name }
            assertTrue(names.contains("cost"))
            assertTrue(names.contains("cost daily"))
            assertTrue(names.contains("cost monthly"))
        }

        @Test
        @DisplayName("returns all commands for empty prefix")
        fun `matches returns all commands for empty prefix`() {
            val results = CommandRegistry.matches("")
            assertEquals(CommandRegistry.commands.size, results.size)
        }
    }

    @Nested
    @DisplayName("parseAndTranslate")
    inner class ParseAndTranslate {
        @Test
        @DisplayName("routes slash commands to RhaiExpression")
        fun `parseAndTranslate routes slash commands to RhaiExpression`() {
            val result = CommandRegistry.parseAndTranslate("/status")
            assertTrue(result is CommandResult.RhaiExpression)
            assertEquals("status()", (result as CommandResult.RhaiExpression).expression)
        }

        @Test
        @DisplayName("routes plain text to ChatMessage")
        fun `parseAndTranslate routes plain text to ChatMessage`() {
            val result = CommandRegistry.parseAndTranslate("hello")
            assertTrue(result is CommandResult.ChatMessage)
            assertEquals("hello", (result as CommandResult.ChatMessage).text)
        }

        @Test
        @DisplayName("routes help to LocalAction")
        fun `parseAndTranslate routes help to LocalAction`() {
            val result = CommandRegistry.parseAndTranslate("/help")
            assertTrue(result is CommandResult.LocalAction)
            assertEquals("help", (result as CommandResult.LocalAction).action)
        }

        @Test
        @DisplayName("routes clear to LocalAction")
        fun `parseAndTranslate routes clear to LocalAction`() {
            val result = CommandRegistry.parseAndTranslate("/clear")
            assertTrue(result is CommandResult.LocalAction)
            assertEquals("clear", (result as CommandResult.LocalAction).action)
        }

        @Test
        @DisplayName("cost daily with args generates correct expression")
        fun `cost daily with args generates correct expression`() {
            val result = CommandRegistry.parseAndTranslate("/cost daily 2026 2 27")
            assertTrue(result is CommandResult.RhaiExpression)
            assertEquals(
                "cost_daily(2026, 2, 27)",
                (result as CommandResult.RhaiExpression).expression,
            )
        }

        @Test
        @DisplayName("memory recall escapes quotes in query")
        fun `memory recall escapes quotes in query`() {
            val result = CommandRegistry.parseAndTranslate("/memory recall he said \"hello\"")
            assertTrue(result is CommandResult.RhaiExpression)
            val expression = (result as CommandResult.RhaiExpression).expression
            assertTrue(expression.contains("\\\"hello\\\""))
        }

        @Test
        @DisplayName("cron add with expression and command")
        fun `cron add with expression and command`() {
            val result = CommandRegistry.parseAndTranslate("/cron add 0/5 echo test")
            assertTrue(result is CommandResult.RhaiExpression)
            val expression = (result as CommandResult.RhaiExpression).expression
            assertEquals("cron_add(\"0/5\", \"echo test\")", expression)
        }

        @Test
        @DisplayName("empty input returns empty ChatMessage")
        fun `empty input returns empty ChatMessage`() {
            val result = CommandRegistry.parseAndTranslate("")
            assertTrue(result is CommandResult.ChatMessage)
            assertEquals("", (result as CommandResult.ChatMessage).text)
        }

        @Test
        @DisplayName("unknown slash command falls through to ChatMessage")
        fun `unknown slash command falls through to ChatMessage`() {
            val result = CommandRegistry.parseAndTranslate("/nonexistent")
            assertTrue(result is CommandResult.ChatMessage)
        }

        @Test
        @DisplayName("version command generates correct expression")
        fun `version command generates correct expression`() {
            val result = CommandRegistry.parseAndTranslate("/version")
            assertTrue(result is CommandResult.RhaiExpression)
            assertEquals("version()", (result as CommandResult.RhaiExpression).expression)
        }
    }
}
