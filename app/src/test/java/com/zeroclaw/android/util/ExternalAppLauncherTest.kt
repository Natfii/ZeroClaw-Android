/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ExternalAppLauncher].
 *
 * Verifies deep-link URI constants and provider console lookup logic.
 * The [ExternalAppLauncher.launch] function requires an Android [android.content.Context]
 * and is therefore not covered here.
 */
@DisplayName("ExternalAppLauncher")
class ExternalAppLauncherTest {
    @Nested
    @DisplayName("Telegram targets")
    inner class TelegramTargets {
        @Test
        @DisplayName("TELEGRAM_BOTFATHER uri is tg scheme for BotFather")
        fun `TELEGRAM_BOTFATHER uri is tg scheme for BotFather`() {
            assertEquals(
                "tg://resolve?domain=BotFather",
                ExternalAppLauncher.TELEGRAM_BOTFATHER.uri,
            )
        }

        @Test
        @DisplayName("TELEGRAM_BOTFATHER fallbackUri is https t.me link")
        fun `TELEGRAM_BOTFATHER fallbackUri is https t me link`() {
            assertEquals(
                "https://t.me/BotFather",
                ExternalAppLauncher.TELEGRAM_BOTFATHER.fallbackUri,
            )
        }

        @Test
        @DisplayName("TELEGRAM_USERINFOBOT uri is tg scheme for userinfobot")
        fun `TELEGRAM_USERINFOBOT uri is tg scheme for userinfobot`() {
            assertEquals(
                "tg://resolve?domain=userinfobot",
                ExternalAppLauncher.TELEGRAM_USERINFOBOT.uri,
            )
        }
    }

    @Nested
    @DisplayName("Discord target")
    inner class DiscordTarget {
        @Test
        @DisplayName("DISCORD_DEV_PORTAL uri is developer applications page")
        fun `DISCORD_DEV_PORTAL uri is developer applications page`() {
            assertEquals(
                "https://discord.com/developers/applications",
                ExternalAppLauncher.DISCORD_DEV_PORTAL.uri,
            )
        }

        @Test
        @DisplayName("DISCORD_DEV_PORTAL fallbackUri is null")
        fun `DISCORD_DEV_PORTAL fallbackUri is null`() {
            assertNull(ExternalAppLauncher.DISCORD_DEV_PORTAL.fallbackUri)
        }
    }

    @Nested
    @DisplayName("Slack target")
    inner class SlackTarget {
        @Test
        @DisplayName("SLACK_APP_CONSOLE uri is Slack apps page")
        fun `SLACK_APP_CONSOLE uri is Slack apps page`() {
            assertEquals(
                "https://api.slack.com/apps",
                ExternalAppLauncher.SLACK_APP_CONSOLE.uri,
            )
        }
    }

    @Nested
    @DisplayName("providerConsoleTarget")
    inner class ProviderConsoleTarget {
        @Test
        @DisplayName("openai returns platform API keys page")
        fun `openai returns platform API keys page`() {
            val target = ExternalAppLauncher.providerConsoleTarget("openai")
            assertEquals("https://platform.openai.com/api-keys", target?.uri)
            assertEquals("Get OpenAI API Key", target?.label)
        }

        @Test
        @DisplayName("anthropic returns console keys page")
        fun `anthropic returns console keys page`() {
            val target = ExternalAppLauncher.providerConsoleTarget("anthropic")
            assertEquals("https://console.anthropic.com/settings/keys", target?.uri)
            assertEquals("Get Anthropic API Key", target?.label)
        }

        @Test
        @DisplayName("openrouter returns keys page")
        fun `openrouter returns keys page`() {
            val target = ExternalAppLauncher.providerConsoleTarget("openrouter")
            assertEquals("https://openrouter.ai/keys", target?.uri)
            assertEquals("Get OpenRouter API Key", target?.label)
        }

        @Test
        @DisplayName("google_gemini returns AI Studio API key page")
        fun `google_gemini returns AI Studio API key page`() {
            val target = ExternalAppLauncher.providerConsoleTarget("google_gemini")
            assertEquals("https://aistudio.google.com/apikey", target?.uri)
            assertEquals("Get Google AI API Key", target?.label)
        }

        @Test
        @DisplayName("groq returns console keys page")
        fun `groq returns console keys page`() {
            val target = ExternalAppLauncher.providerConsoleTarget("groq")
            assertEquals("https://console.groq.com/keys", target?.uri)
            assertEquals("Get Groq API Key", target?.label)
        }

        @Test
        @DisplayName("unknown_provider returns null")
        fun `unknown_provider returns null`() {
            assertNull(ExternalAppLauncher.providerConsoleTarget("unknown_provider"))
        }
    }
}
