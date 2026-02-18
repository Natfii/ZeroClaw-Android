/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import com.zeroclaw.android.model.Agent
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
        @DisplayName("self-hosted LM Studio uses custom:URL provider with placeholder key")
        fun `lmstudio with URL maps to custom provider`() {
            val toml = ConfigTomlBuilder.build(
                provider = "lmstudio",
                model = "local-model",
                apiKey = "",
                baseUrl = "http://localhost:1234/v1",
            )

            assertTrue(toml.contains("""default_provider = "custom:http://localhost:1234/v1""""))
            assertTrue(toml.contains("""api_key = "not-needed""""))
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
    @DisplayName("build(GlobalTomlConfig)")
    inner class BuildGlobalConfig {

        @Test
        @DisplayName("custom temperature is emitted")
        fun `custom temperature is emitted`() {
            val toml = ConfigTomlBuilder.build(
                GlobalTomlConfig(
                    provider = "openai",
                    model = "gpt-4o",
                    apiKey = "sk-test",
                    baseUrl = "",
                    temperature = 1.2f,
                ),
            )
            assertTrue(toml.contains("default_temperature = 1.2"))
            assertFalse(toml.contains("default_temperature = 0.7"))
        }

        @Test
        @DisplayName("compact context enabled emits agent section")
        fun `compact context enabled emits agent section`() {
            val toml = ConfigTomlBuilder.build(
                GlobalTomlConfig(
                    provider = "",
                    model = "",
                    apiKey = "",
                    baseUrl = "",
                    compactContext = true,
                ),
            )
            assertTrue(toml.contains("[agent]"))
            assertTrue(toml.contains("compact_context = true"))
        }

        @Test
        @DisplayName("compact context disabled omits agent section")
        fun `compact context disabled omits agent section`() {
            val toml = ConfigTomlBuilder.build(
                GlobalTomlConfig(
                    provider = "",
                    model = "",
                    apiKey = "",
                    baseUrl = "",
                    compactContext = false,
                ),
            )
            assertFalse(toml.contains("[agent]"))
        }

        @Test
        @DisplayName("cost enabled emits cost section")
        fun `cost enabled emits cost section`() {
            val toml = ConfigTomlBuilder.build(
                GlobalTomlConfig(
                    provider = "",
                    model = "",
                    apiKey = "",
                    baseUrl = "",
                    costEnabled = true,
                    dailyLimitUsd = 5f,
                    monthlyLimitUsd = 50f,
                    costWarnAtPercent = 75,
                ),
            )
            assertTrue(toml.contains("[cost]"))
            assertTrue(toml.contains("enabled = true"))
            assertTrue(toml.contains("daily_limit_usd = 5.0"))
            assertTrue(toml.contains("monthly_limit_usd = 50.0"))
            assertTrue(toml.contains("warn_at_percent = 75"))
        }

        @Test
        @DisplayName("cost disabled omits cost section")
        fun `cost disabled omits cost section`() {
            val toml = ConfigTomlBuilder.build(
                GlobalTomlConfig(
                    provider = "",
                    model = "",
                    apiKey = "",
                    baseUrl = "",
                    costEnabled = false,
                ),
            )
            assertFalse(toml.contains("[cost]"))
        }

        @Test
        @DisplayName("identity JSON emits identity section")
        fun `identity JSON emits identity section`() {
            val json = """{"name":"TestBot"}"""
            val toml = ConfigTomlBuilder.build(
                GlobalTomlConfig(
                    provider = "",
                    model = "",
                    apiKey = "",
                    baseUrl = "",
                    identityJson = json,
                ),
            )
            assertTrue(toml.contains("[identity]"))
            assertTrue(toml.contains("""format = "aieos""""))
            assertTrue(toml.contains("aieos_inline"))
        }

        @Test
        @DisplayName("blank identity JSON omits identity section")
        fun `blank identity JSON omits identity section`() {
            val toml = ConfigTomlBuilder.build(
                GlobalTomlConfig(
                    provider = "",
                    model = "",
                    apiKey = "",
                    baseUrl = "",
                    identityJson = "",
                ),
            )
            assertFalse(toml.contains("[identity]"))
        }

        @Test
        @DisplayName("memory backend is always emitted")
        fun `memory backend is always emitted`() {
            val toml = ConfigTomlBuilder.build(
                GlobalTomlConfig(
                    provider = "",
                    model = "",
                    apiKey = "",
                    baseUrl = "",
                    memoryBackend = "lucid",
                ),
            )
            assertTrue(toml.contains("[memory]"))
            assertTrue(toml.contains("""backend = "lucid""""))
        }

        @Test
        @DisplayName("memory auto_save defaults to true")
        fun `memory auto_save defaults to true`() {
            val toml = ConfigTomlBuilder.build(
                GlobalTomlConfig(
                    provider = "",
                    model = "",
                    apiKey = "",
                    baseUrl = "",
                ),
            )
            assertTrue(toml.contains("auto_save = true"))
        }

        @Test
        @DisplayName("memory auto_save false is emitted")
        fun `memory auto_save false is emitted`() {
            val toml = ConfigTomlBuilder.build(
                GlobalTomlConfig(
                    provider = "",
                    model = "",
                    apiKey = "",
                    baseUrl = "",
                    memoryAutoSave = false,
                ),
            )
            assertTrue(toml.contains("auto_save = false"))
        }

        @Test
        @DisplayName("non-default retries emit reliability section")
        fun `non-default retries emit reliability section`() {
            val toml = ConfigTomlBuilder.build(
                GlobalTomlConfig(
                    provider = "",
                    model = "",
                    apiKey = "",
                    baseUrl = "",
                    providerRetries = 5,
                ),
            )
            assertTrue(toml.contains("[reliability]"))
            assertTrue(toml.contains("provider_retries = 5"))
        }

        @Test
        @DisplayName("fallback providers emit reliability section")
        fun `fallback providers emit reliability section`() {
            val toml = ConfigTomlBuilder.build(
                GlobalTomlConfig(
                    provider = "",
                    model = "",
                    apiKey = "",
                    baseUrl = "",
                    fallbackProviders = listOf("groq", "anthropic"),
                ),
            )
            assertTrue(toml.contains("[reliability]"))
            assertTrue(toml.contains("""fallback_providers = ["groq", "anthropic"]"""))
        }

        @Test
        @DisplayName("default retries and no fallbacks omit reliability section")
        fun `default values omit reliability section`() {
            val toml = ConfigTomlBuilder.build(
                GlobalTomlConfig(
                    provider = "",
                    model = "",
                    apiKey = "",
                    baseUrl = "",
                ),
            )
            assertFalse(toml.contains("[reliability]"))
        }
    }

    @Nested
    @DisplayName("buildAgentsToml()")
    inner class BuildAgentsToml {

        @Test
        @DisplayName("empty list returns empty string")
        fun `empty list returns empty string`() {
            assertEquals("", ConfigTomlBuilder.buildAgentsToml(emptyList()))
        }

        @Test
        @DisplayName("single agent emits correct TOML section")
        fun `single agent emits correct TOML section`() {
            val entries = listOf(
                AgentTomlEntry(
                    name = "researcher",
                    provider = "custom:http://192.168.1.197:1234/v1",
                    model = "google/gemma-3-12b",
                    apiKey = "",
                    systemPrompt = "You are a research assistant.",
                ),
            )
            val toml = ConfigTomlBuilder.buildAgentsToml(entries)

            assertTrue(toml.contains("[agents.researcher]"))
            assertTrue(toml.contains("""provider = "custom:http://192.168.1.197:1234/v1""""))
            assertTrue(toml.contains("""model = "google/gemma-3-12b""""))
            assertTrue(toml.contains("""system_prompt = "You are a research assistant.""""))
            assertTrue(toml.contains("""api_key = "not-needed""""))
        }

        @Test
        @DisplayName("agent with API key emits api_key field")
        fun `agent with api key emits api_key field`() {
            val entries = listOf(
                AgentTomlEntry(
                    name = "coder",
                    provider = "openai",
                    model = "gpt-4o",
                    apiKey = "sk-test-key",
                    systemPrompt = "",
                ),
            )
            val toml = ConfigTomlBuilder.buildAgentsToml(entries)

            assertTrue(toml.contains("[agents.coder]"))
            assertTrue(toml.contains("""api_key = "sk-test-key""""))
            assertFalse(toml.contains("system_prompt"))
        }

        @Test
        @DisplayName("multiple agents produce separate sections")
        fun `multiple agents produce separate sections`() {
            val entries = listOf(
                AgentTomlEntry(
                    name = "agent_a",
                    provider = "openai",
                    model = "gpt-4o",
                ),
                AgentTomlEntry(
                    name = "agent_b",
                    provider = "anthropic",
                    model = "claude-sonnet-4-5-20250929",
                    apiKey = "sk-ant-key",
                    systemPrompt = "Be concise.",
                ),
            )
            val toml = ConfigTomlBuilder.buildAgentsToml(entries)

            assertTrue(toml.contains("[agents.agent_a]"))
            assertTrue(toml.contains("[agents.agent_b]"))
            assertTrue(toml.contains("""model = "gpt-4o""""))
            assertTrue(toml.contains("""model = "claude-sonnet-4-5-20250929""""))
        }

        @Test
        @DisplayName("agent name with spaces is quoted in table header")
        fun `agent name with spaces is quoted in table header`() {
            val entries = listOf(
                AgentTomlEntry(
                    name = "My Agent",
                    provider = "custom:http://192.168.1.50:1234/v1",
                    model = "google/gemma-3-12b",
                ),
            )
            val toml = ConfigTomlBuilder.buildAgentsToml(entries)

            assertTrue(toml.contains("""[agents."My Agent"]"""))
            assertFalse(toml.contains("[agents.My Agent]"))
        }

        @Test
        @DisplayName("agent name without special characters is bare key")
        fun `agent name without special characters is bare key`() {
            val entries = listOf(
                AgentTomlEntry(
                    name = "my-agent_1",
                    provider = "openai",
                    model = "gpt-4o",
                ),
            )
            val toml = ConfigTomlBuilder.buildAgentsToml(entries)

            assertTrue(toml.contains("[agents.my-agent_1]"))
        }

        @Test
        @DisplayName("special characters in system prompt are escaped")
        fun `special characters in system prompt are escaped`() {
            val entries = listOf(
                AgentTomlEntry(
                    name = "escaper",
                    provider = "openai",
                    model = "gpt-4o",
                    systemPrompt = "Line1\nLine2\twith\"quotes",
                ),
            )
            val toml = ConfigTomlBuilder.buildAgentsToml(entries)

            assertTrue(toml.contains("""\n"""))
            assertTrue(toml.contains("""\t"""))
            assertTrue(toml.contains("""\""""))
        }

        @Test
        @DisplayName("agent with temperature emits temperature field")
        fun `agent with temperature emits temperature field`() {
            val entries = listOf(
                AgentTomlEntry(
                    name = "warm",
                    provider = "openai",
                    model = "gpt-4o",
                    temperature = 1.5f,
                ),
            )
            val toml = ConfigTomlBuilder.buildAgentsToml(entries)
            assertTrue(toml.contains("temperature = 1.5"))
        }

        @Test
        @DisplayName("agent without temperature omits temperature field")
        fun `agent without temperature omits temperature field`() {
            val entries = listOf(
                AgentTomlEntry(
                    name = "default",
                    provider = "openai",
                    model = "gpt-4o",
                    temperature = null,
                ),
            )
            val toml = ConfigTomlBuilder.buildAgentsToml(entries)
            assertFalse(toml.contains("temperature"))
        }

        @Test
        @DisplayName("agent with non-default maxDepth emits max_depth field")
        fun `agent with non-default maxDepth emits max_depth field`() {
            val entries = listOf(
                AgentTomlEntry(
                    name = "deep",
                    provider = "openai",
                    model = "gpt-4o",
                    maxDepth = 7,
                ),
            )
            val toml = ConfigTomlBuilder.buildAgentsToml(entries)
            assertTrue(toml.contains("max_depth = 7"))
        }

        @Test
        @DisplayName("agent with default maxDepth omits max_depth field")
        fun `agent with default maxDepth omits max_depth field`() {
            val entries = listOf(
                AgentTomlEntry(
                    name = "shallow",
                    provider = "openai",
                    model = "gpt-4o",
                    maxDepth = Agent.DEFAULT_MAX_DEPTH,
                ),
            )
            val toml = ConfigTomlBuilder.buildAgentsToml(entries)
            assertFalse(toml.contains("max_depth"))
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
