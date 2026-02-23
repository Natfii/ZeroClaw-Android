/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import com.zeroclaw.android.model.Agent
import com.zeroclaw.android.model.ChannelType
import com.zeroclaw.android.model.ConnectedChannel
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
                    provider = "ollama",
                    model = "mistral",
                    apiKey = "",
                    baseUrl = "http://192.168.1.100:11434/v1",
                )

            assertTrue(
                toml.contains("""default_provider = "custom:http://192.168.1.100:11434/v1""""),
            )
        }

        @Test
        @DisplayName("custom-anthropic uses anthropic-custom:URL provider")
        fun `custom anthropic maps to anthropic-custom provider`() {
            val toml =
                ConfigTomlBuilder.build(
                    provider = "custom-anthropic",
                    model = "claude-sonnet-4-5-20250929",
                    apiKey = "sk-ant-test",
                    baseUrl = "http://my-proxy.internal:8443",
                )

            assertTrue(
                toml.contains(
                    """default_provider = "anthropic-custom:http://my-proxy.internal:8443"""",
                ),
            )
            assertTrue(toml.contains("""api_key = "sk-ant-test""""))
        }

        @Test
        @DisplayName("empty provider and model omits those fields")
        fun `empty provider and model omits fields`() {
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
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
            val toml =
                ConfigTomlBuilder.build(
                    GlobalTomlConfig(
                        provider = "",
                        model = "",
                        apiKey = "",
                        baseUrl = "",
                    ),
                )
            assertFalse(toml.contains("[reliability]"))
        }

        @Test
        @DisplayName("transcription enabled emits transcription section")
        fun `transcription enabled emits transcription section`() {
            val toml =
                ConfigTomlBuilder.build(
                    GlobalTomlConfig(
                        provider = "",
                        model = "",
                        apiKey = "",
                        baseUrl = "",
                        transcriptionEnabled = true,
                        transcriptionLanguage = "en",
                    ),
                )
            assertTrue(toml.contains("[transcription]"))
            assertTrue(toml.contains("enabled = true"))
            assertTrue(toml.contains("api_url"))
            assertTrue(toml.contains("""model = "whisper-large-v3-turbo""""))
            assertTrue(toml.contains("""language = "en""""))
            assertTrue(toml.contains("max_duration_secs = 120"))
        }

        @Test
        @DisplayName("transcription disabled omits transcription section")
        fun `transcription disabled omits transcription section`() {
            val toml =
                ConfigTomlBuilder.build(
                    GlobalTomlConfig(
                        provider = "",
                        model = "",
                        apiKey = "",
                        baseUrl = "",
                        transcriptionEnabled = false,
                    ),
                )
            assertFalse(toml.contains("[transcription]"))
        }

        @Test
        @DisplayName("non-default multimodal emits multimodal section")
        fun `non-default multimodal emits multimodal section`() {
            val toml =
                ConfigTomlBuilder.build(
                    GlobalTomlConfig(
                        provider = "",
                        model = "",
                        apiKey = "",
                        baseUrl = "",
                        multimodalMaxImages = 8,
                        multimodalAllowRemoteFetch = true,
                    ),
                )
            assertTrue(toml.contains("[multimodal]"))
            assertTrue(toml.contains("max_images = 8"))
            assertTrue(toml.contains("allow_remote_fetch = true"))
        }

        @Test
        @DisplayName("default multimodal omits multimodal section")
        fun `default multimodal omits multimodal section`() {
            val toml =
                ConfigTomlBuilder.build(
                    GlobalTomlConfig(
                        provider = "",
                        model = "",
                        apiKey = "",
                        baseUrl = "",
                    ),
                )
            assertFalse(toml.contains("[multimodal]"))
        }

        @Test
        @DisplayName("proxy enabled emits proxy section")
        fun `proxy enabled emits proxy section`() {
            val toml =
                ConfigTomlBuilder.build(
                    GlobalTomlConfig(
                        provider = "",
                        model = "",
                        apiKey = "",
                        baseUrl = "",
                        proxyEnabled = true,
                        proxyHttpProxy = "http://proxy:8080",
                        proxyNoProxy = listOf("localhost", "127.0.0.1"),
                    ),
                )
            assertTrue(toml.contains("[proxy]"))
            assertTrue(toml.contains("enabled = true"))
            assertTrue(toml.contains("""http_proxy = "http://proxy:8080""""))
            assertTrue(toml.contains(""""localhost""""))
            assertTrue(toml.contains(""""127.0.0.1""""))
        }

        @Test
        @DisplayName("proxy disabled omits proxy section")
        fun `proxy disabled omits proxy section`() {
            val toml =
                ConfigTomlBuilder.build(
                    GlobalTomlConfig(
                        provider = "",
                        model = "",
                        apiKey = "",
                        baseUrl = "",
                        proxyEnabled = false,
                    ),
                )
            assertFalse(toml.contains("[proxy]"))
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
            val entries =
                listOf(
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
            val entries =
                listOf(
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
            val entries =
                listOf(
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
            val entries =
                listOf(
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
            val entries =
                listOf(
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
            val entries =
                listOf(
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
            val entries =
                listOf(
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
            val entries =
                listOf(
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
            val entries =
                listOf(
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
            val entries =
                listOf(
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

    @Nested
    @DisplayName("buildChannelsToml()")
    inner class BuildChannelsToml {
        @Test
        @DisplayName("empty list returns empty string")
        fun `empty list returns empty string`() {
            assertEquals("", ConfigTomlBuilder.buildChannelsToml(emptyList()))
        }

        @Test
        @DisplayName("Mattermost channel emits correct TOML section")
        fun `mattermost channel emits correct TOML`() {
            val channel = ConnectedChannel(id = "1", type = ChannelType.MATTERMOST)
            val values =
                mapOf(
                    "url" to "https://mm.example.com",
                    "bot_token" to "xoxb-test",
                    "channel_id" to "abc123",
                    "thread_replies" to "true",
                )
            val toml = ConfigTomlBuilder.buildChannelsToml(listOf(channel to values))

            assertTrue(toml.contains("[channels_config.mattermost]"))
            assertTrue(toml.contains("""url = "https://mm.example.com""""))
            assertTrue(toml.contains("""bot_token = "xoxb-test""""))
            assertTrue(toml.contains("""channel_id = "abc123""""))
            assertTrue(toml.contains("thread_replies = true"))
        }

        @Test
        @DisplayName("Signal channel emits correct TOML section")
        fun `signal channel emits correct TOML`() {
            val channel = ConnectedChannel(id = "2", type = ChannelType.SIGNAL)
            val values =
                mapOf(
                    "http_url" to "http://localhost:8080",
                    "account" to "+1234567890",
                    "ignore_attachments" to "true",
                )
            val toml = ConfigTomlBuilder.buildChannelsToml(listOf(channel to values))

            assertTrue(toml.contains("[channels_config.signal]"))
            assertTrue(toml.contains("""http_url = "http://localhost:8080""""))
            assertTrue(toml.contains("""account = "+1234567890""""))
            assertTrue(toml.contains("ignore_attachments = true"))
        }

        @Test
        @DisplayName("Nostr channel emits relays as list")
        fun `nostr channel emits relays as list`() {
            val channel = ConnectedChannel(id = "3", type = ChannelType.NOSTR)
            val values =
                mapOf(
                    "private_key" to "nsec1test",
                    "relays" to "wss://relay.damus.io, wss://nos.lol",
                )
            val toml = ConfigTomlBuilder.buildChannelsToml(listOf(channel to values))

            assertTrue(toml.contains("[channels_config.nostr]"))
            assertTrue(toml.contains("""private_key = "nsec1test""""))
            assertTrue(toml.contains(""""wss://relay.damus.io""""))
            assertTrue(toml.contains(""""wss://nos.lol""""))
        }

        @Test
        @DisplayName("ClawdTalk channel emits correct TOML section")
        fun `clawdtalk channel emits correct TOML`() {
            val channel = ConnectedChannel(id = "4", type = ChannelType.CLAWDTALK)
            val values =
                mapOf(
                    "api_key" to "ct-key-123",
                    "connection_id" to "conn-abc",
                    "from_number" to "+15551234567",
                )
            val toml = ConfigTomlBuilder.buildChannelsToml(listOf(channel to values))

            assertTrue(toml.contains("[channels_config.clawdtalk]"))
            assertTrue(toml.contains("""api_key = "ct-key-123""""))
            assertTrue(toml.contains("""connection_id = "conn-abc""""))
            assertTrue(toml.contains("""from_number = "+15551234567""""))
        }

        @Test
        @DisplayName("DingTalk channel emits correct TOML section")
        fun `dingtalk channel emits correct TOML`() {
            val channel = ConnectedChannel(id = "5", type = ChannelType.DINGTALK)
            val values =
                mapOf(
                    "client_id" to "dingabc",
                    "client_secret" to "secret123",
                    "allowed_users" to "user1, user2",
                )
            val toml = ConfigTomlBuilder.buildChannelsToml(listOf(channel to values))

            assertTrue(toml.contains("[channels_config.dingtalk]"))
            assertTrue(toml.contains("""client_id = "dingabc""""))
            assertTrue(toml.contains("""client_secret = "secret123""""))
            assertTrue(toml.contains(""""user1""""))
            assertTrue(toml.contains(""""user2""""))
        }

        @Test
        @DisplayName("Feishu channel emits correct TOML section")
        fun `feishu channel emits correct TOML`() {
            val channel = ConnectedChannel(id = "6", type = ChannelType.FEISHU)
            val values =
                mapOf(
                    "app_id" to "cli_test",
                    "app_secret" to "secret",
                    "receive_mode" to "websocket",
                    "port" to "8443",
                )
            val toml = ConfigTomlBuilder.buildChannelsToml(listOf(channel to values))

            assertTrue(toml.contains("[channels_config.feishu]"))
            assertTrue(toml.contains("""app_id = "cli_test""""))
            assertTrue(toml.contains("""receive_mode = "websocket""""))
            assertTrue(toml.contains("port = 8443"))
        }

        @Test
        @DisplayName("all new channel types have unique toml keys")
        fun `all channel types have unique toml keys`() {
            val keys = ChannelType.entries.map { it.tomlKey }
            assertEquals(keys.size, keys.toSet().size)
        }

        @Test
        @DisplayName("all new channel types have non-empty display names")
        fun `all channel types have non-empty display names`() {
            for (type in ChannelType.entries) {
                assertTrue(type.displayName.isNotBlank(), "${type.name} has blank displayName")
            }
        }

        @Test
        @DisplayName("WhatsApp includes web mode fields")
        fun `whatsapp includes web mode fields`() {
            val fields = ChannelType.WHATSAPP.fields.map { it.key }
            assertTrue("session_path" in fields)
            assertTrue("pair_phone" in fields)
            assertTrue("pair_code" in fields)
        }
    }
}
