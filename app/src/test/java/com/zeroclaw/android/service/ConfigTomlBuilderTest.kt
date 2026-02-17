/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ConfigTomlBuilder].
 *
 * Verifies TOML generation for cloud providers, self-hosted endpoints,
 * Ollama variants, Anthropic-compatible endpoints, and edge cases.
 */
@DisplayName("ConfigTomlBuilder")
class ConfigTomlBuilderTest {

    @Nested
    @DisplayName("build()")
    inner class Build {

        @Test
        @DisplayName("cloud provider produces TOML with provider, key, and temperature")
        fun `cloud provider produces correct TOML`() {
            val toml = ConfigTomlBuilder.build(
                provider = "openai",
                model = "gpt-4o",
                apiKey = "sk-test-key-123",
                baseUrl = "",
            )

            assertTrue(toml.contains("default_temperature = 0.7"))
            assertTrue(toml.contains("""default_provider = "openai""""))
            assertTrue(toml.contains("""default_model = "gpt-4o""""))
            assertTrue(toml.contains("""api_key = "sk-test-key-123""""))
        }

        @Test
        @DisplayName("self-hosted LM Studio uses custom:URL provider")
        fun `lmstudio with URL maps to custom provider`() {
            val toml = ConfigTomlBuilder.build(
                provider = "lmstudio",
                model = "local-model",
                apiKey = "",
                baseUrl = "http://localhost:1234/v1",
            )

            assertTrue(toml.contains("""default_provider = "custom:http://localhost:1234/v1""""))
            assertFalse(toml.contains("api_key"))
        }

        @Test
        @DisplayName("vLLM with URL and key produces custom provider with api_key")
        fun `vllm with URL and key maps to custom provider`() {
            val toml = ConfigTomlBuilder.build(
                provider = "vllm",
                model = "meta-llama/Llama-3",
                apiKey = "token-abc",
                baseUrl = "http://192.168.1.50:8000/v1",
            )

            assertTrue(toml.contains("""default_provider = "custom:http://192.168.1.50:8000/v1""""))
            assertTrue(toml.contains("""api_key = "token-abc""""))
        }

        @Test
        @DisplayName("Ollama with default URL uses plain ollama provider")
        fun `ollama default uses plain provider`() {
            val toml = ConfigTomlBuilder.build(
                provider = "ollama",
                model = "llama3",
                apiKey = "",
                baseUrl = "",
            )

            assertTrue(toml.contains("""default_provider = "ollama""""))
            assertFalse(toml.contains("custom:"))
        }

        @Test
        @DisplayName("Ollama with default localhost URL uses plain ollama provider")
        fun `ollama with default localhost URL uses plain provider`() {
            val toml = ConfigTomlBuilder.build(
                provider = "ollama",
                model = "llama3",
                apiKey = "",
                baseUrl = "http://localhost:11434",
            )

            assertTrue(toml.contains("""default_provider = "ollama""""))
            assertFalse(toml.contains("custom:"))
        }

        @Test
        @DisplayName("Ollama with custom URL uses custom:URL provider")
        fun `ollama with custom URL maps to custom provider`() {
            val toml = ConfigTomlBuilder.build(
                provider = "ollama",
                model = "mistral",
                apiKey = "",
                baseUrl = "http://192.168.1.100:11434/v1",
            )

            assertTrue(toml.contains("""default_provider = "custom:http://192.168.1.100:11434/v1""""))
        }

        @Test
        @DisplayName("custom-anthropic uses anthropic-custom:URL provider")
        fun `custom anthropic maps to anthropic-custom provider`() {
            val toml = ConfigTomlBuilder.build(
                provider = "custom-anthropic",
                model = "claude-sonnet-4-5-20250929",
                apiKey = "sk-ant-test",
                baseUrl = "http://my-proxy.internal:8443",
            )

            assertTrue(toml.contains("""default_provider = "anthropic-custom:http://my-proxy.internal:8443""""))
            assertTrue(toml.contains("""api_key = "sk-ant-test""""))
        }

        @Test
        @DisplayName("empty provider and model omits those fields")
        fun `empty provider and model omits fields`() {
            val toml = ConfigTomlBuilder.build(
                provider = "",
                model = "",
                apiKey = "",
                baseUrl = "",
            )

            assertTrue(toml.contains("default_temperature = 0.7"))
            assertFalse(toml.contains("default_provider"))
            assertFalse(toml.contains("default_model"))
            assertFalse(toml.contains("api_key"))
        }

        @Test
        @DisplayName("special characters in API key are escaped")
        fun `special characters in api key are escaped`() {
            val toml = ConfigTomlBuilder.build(
                provider = "openai",
                model = "gpt-4o",
                apiKey = "sk-key\"with\\special\nnewline",
                baseUrl = "",
            )

            assertTrue(toml.contains("""api_key = "sk-key\"with\\special\nnewline""""))
            assertFalse(toml.contains("\n\""))
        }

        @Test
        @DisplayName("temperature is always present")
        fun `temperature is always present`() {
            val toml = ConfigTomlBuilder.build(
                provider = "",
                model = "",
                apiKey = "",
                baseUrl = "",
            )

            assertTrue(toml.startsWith("default_temperature = 0.7"))
        }
    }

    @Nested
    @DisplayName("resolveProvider()")
    inner class ResolveProvider {

        @Test
        @DisplayName("blank provider returns blank")
        fun `blank provider returns blank`() {
            assertEquals("", ConfigTomlBuilder.resolveProvider("", ""))
            assertEquals("", ConfigTomlBuilder.resolveProvider("  ", ""))
        }

        @Test
        @DisplayName("cloud provider passes through unchanged")
        fun `cloud provider passes through`() {
            assertEquals("openai", ConfigTomlBuilder.resolveProvider("openai", ""))
            assertEquals("anthropic", ConfigTomlBuilder.resolveProvider("anthropic", ""))
            assertEquals("groq", ConfigTomlBuilder.resolveProvider("groq", ""))
        }

        @Test
        @DisplayName("localai with URL resolves to custom")
        fun `localai with URL resolves to custom`() {
            assertEquals(
                "custom:http://localhost:8080/v1",
                ConfigTomlBuilder.resolveProvider("localai", "http://localhost:8080/v1"),
            )
        }

        @Test
        @DisplayName("custom-openai with URL resolves to custom")
        fun `custom-openai with URL resolves to custom`() {
            assertEquals(
                "custom:http://my-server:9090/v1",
                ConfigTomlBuilder.resolveProvider("custom-openai", "http://my-server:9090/v1"),
            )
        }

        @Test
        @DisplayName("custom-openai without URL passes through")
        fun `custom-openai without URL passes through`() {
            assertEquals("custom-openai", ConfigTomlBuilder.resolveProvider("custom-openai", ""))
        }
    }
}
