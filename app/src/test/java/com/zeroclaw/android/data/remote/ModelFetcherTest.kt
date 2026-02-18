/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.remote

import com.zeroclaw.android.model.ModelListFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ModelFetcher] JSON parsing logic.
 *
 * These tests verify parsing of mock API responses for each [ModelListFormat]
 * variant without making actual network calls.
 */
@DisplayName("ModelFetcher")
class ModelFetcherTest {
    @Test
    @DisplayName("parseModels handles OpenAI-compatible format")
    fun `parseModels handles OpenAI-compatible format`() {
        val json =
            """
            {
                "data": [
                    {"id": "gpt-4o", "object": "model"},
                    {"id": "gpt-4o-mini", "object": "model"},
                    {"id": "o1", "object": "model"}
                ]
            }
            """.trimIndent()

        val models = ModelFetcher.parseModels(json, ModelListFormat.OPENAI_COMPATIBLE)
        assertEquals(listOf("gpt-4o", "gpt-4o-mini", "o1"), models)
    }

    @Test
    @DisplayName("parseModels handles Anthropic format")
    fun `parseModels handles Anthropic format`() {
        val json =
            """
            {
                "data": [
                    {"id": "claude-sonnet-4-5-20250929", "type": "model"},
                    {"id": "claude-haiku-4-5-20251001", "type": "model"}
                ]
            }
            """.trimIndent()

        val models = ModelFetcher.parseModels(json, ModelListFormat.ANTHROPIC)
        assertEquals(listOf("claude-sonnet-4-5-20250929", "claude-haiku-4-5-20251001"), models)
    }

    @Test
    @DisplayName("parseModels handles Google Gemini format and strips prefix")
    fun `parseModels handles Google Gemini format and strips prefix`() {
        val json =
            """
            {
                "models": [
                    {"name": "models/gemini-2.0-flash", "displayName": "Gemini 2.0 Flash"},
                    {"name": "models/gemini-1.5-pro", "displayName": "Gemini 1.5 Pro"}
                ]
            }
            """.trimIndent()

        val models = ModelFetcher.parseModels(json, ModelListFormat.GOOGLE_GEMINI)
        assertEquals(listOf("gemini-2.0-flash", "gemini-1.5-pro"), models)
    }

    @Test
    @DisplayName("parseModels handles Ollama format")
    fun `parseModels handles Ollama format`() {
        val json =
            """
            {
                "models": [
                    {"name": "llama3.3:latest", "modified_at": "2024-01-01"},
                    {"name": "mistral:latest", "modified_at": "2024-01-01"}
                ]
            }
            """.trimIndent()

        val models = ModelFetcher.parseModels(json, ModelListFormat.OLLAMA)
        assertEquals(listOf("llama3.3:latest", "mistral:latest"), models)
    }

    @Test
    @DisplayName("parseModels handles OpenRouter format")
    fun `parseModels handles OpenRouter format`() {
        val json =
            """
            {
                "data": [
                    {"id": "openai/gpt-4o", "name": "GPT-4o"},
                    {"id": "anthropic/claude-sonnet-4-5", "name": "Claude 3.5 Sonnet"}
                ]
            }
            """.trimIndent()

        val models = ModelFetcher.parseModels(json, ModelListFormat.OPENROUTER)
        assertEquals(listOf("openai/gpt-4o", "anthropic/claude-sonnet-4-5"), models)
    }

    @Test
    @DisplayName("parseModels handles Together AI bare array format")
    fun `parseModels handles Together AI bare array format`() {
        val json =
            """
            [
                {"id": "meta-llama/Llama-3.3-70B-Instruct-Turbo", "type": "chat"},
                {"id": "Qwen/Qwen2.5-72B-Instruct-Turbo", "type": "chat"}
            ]
            """.trimIndent()

        val models = ModelFetcher.parseModels(json, ModelListFormat.TOGETHER)
        assertEquals(
            listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo", "Qwen/Qwen2.5-72B-Instruct-Turbo"),
            models,
        )
    }

    @Test
    @DisplayName("parseModels handles Cohere format")
    fun `parseModels handles Cohere format`() {
        val json =
            """
            {
                "models": [
                    {"name": "command-r-plus", "endpoints": ["chat"]},
                    {"name": "command-r", "endpoints": ["chat"]},
                    {"name": "command-a-03-2025", "endpoints": ["chat"]}
                ]
            }
            """.trimIndent()

        val models = ModelFetcher.parseModels(json, ModelListFormat.COHERE)
        assertEquals(listOf("command-r-plus", "command-r", "command-a-03-2025"), models)
    }

    @Test
    @DisplayName("parseModels returns empty list for NONE format")
    fun `parseModels returns empty list for NONE format`() {
        val models = ModelFetcher.parseModels("{}", ModelListFormat.NONE)
        assertTrue(models.isEmpty())
    }

    @Test
    @DisplayName("parseModels skips entries with empty id field")
    fun `parseModels skips entries with empty id field`() {
        val json =
            """
            {
                "data": [
                    {"id": "gpt-4o"},
                    {"id": ""},
                    {"id": "o1"}
                ]
            }
            """.trimIndent()

        val models = ModelFetcher.parseModels(json, ModelListFormat.OPENAI_COMPATIBLE)
        assertEquals(listOf("gpt-4o", "o1"), models)
    }

    @Test
    @DisplayName("parseModels handles empty data array")
    fun `parseModels handles empty data array`() {
        val json = """{"data": []}"""
        val models = ModelFetcher.parseModels(json, ModelListFormat.OPENAI_COMPATIBLE)
        assertTrue(models.isEmpty())
    }

    @Test
    @DisplayName("parseModels handles empty models array for Gemini")
    fun `parseModels handles empty models array for Gemini`() {
        val json = """{"models": []}"""
        val models = ModelFetcher.parseModels(json, ModelListFormat.GOOGLE_GEMINI)
        assertTrue(models.isEmpty())
    }

    @Test
    @DisplayName("parseModels handles empty bare array for Together")
    fun `parseModels handles empty bare array for Together`() {
        val json = """[]"""
        val models = ModelFetcher.parseModels(json, ModelListFormat.TOGETHER)
        assertTrue(models.isEmpty())
    }
}
