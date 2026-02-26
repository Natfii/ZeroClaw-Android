/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Describes a deep-link destination with an optional fallback URL.
 *
 * When the primary [uri] cannot be resolved (e.g. the native app is not installed),
 * the [fallbackUri] is tried as a second attempt, typically opening a web browser.
 *
 * @property uri Primary URI to open (may use a custom scheme such as `tg://`).
 * @property fallbackUri Optional HTTPS fallback when the primary URI has no handler.
 * @property label Human-readable description of the target, suitable for button text.
 */
data class DeepLinkTarget(
    val uri: String,
    val fallbackUri: String? = null,
    val label: String,
)

/**
 * Centralises deep-link URIs and [Intent] construction for onboarding and settings flows.
 *
 * Provides pre-defined [DeepLinkTarget] constants for messaging-platform setup pages
 * and a lookup function for AI provider API-key consoles. The [launch] function
 * handles fallback when the primary URI scheme has no registered handler.
 */
object ExternalAppLauncher {
    /**
     * Opens the Telegram BotFather conversation for creating new bots.
     *
     * Falls back to the `t.me` web link when Telegram is not installed.
     */
    val TELEGRAM_BOTFATHER =
        DeepLinkTarget(
            uri = "tg://resolve?domain=BotFather",
            fallbackUri = "https://t.me/BotFather",
            label = "Open @BotFather",
        )

    /**
     * Opens the Telegram userinfobot conversation for retrieving the user's numeric ID.
     *
     * Falls back to the `t.me` web link when Telegram is not installed.
     */
    val TELEGRAM_USERINFOBOT =
        DeepLinkTarget(
            uri = "tg://resolve?domain=userinfobot",
            fallbackUri = "https://t.me/userinfobot",
            label = "Get My User ID",
        )

    /**
     * Opens the Discord Developer Portal applications page.
     *
     * No fallback is needed because this is already an HTTPS URL.
     */
    val DISCORD_DEV_PORTAL =
        DeepLinkTarget(
            uri = "https://discord.com/developers/applications",
            label = "Open Developer Portal",
        )

    /**
     * Opens the Slack App Console for creating and managing Slack apps.
     *
     * No fallback is needed because this is already an HTTPS URL.
     */
    val SLACK_APP_CONSOLE =
        DeepLinkTarget(
            uri = "https://api.slack.com/apps",
            label = "Open Slack App Console",
        )

    /**
     * Provider-ID-to-console-URL lookup for AI provider API-key pages.
     *
     * Each entry maps a ZeroClaw provider identifier to the web console
     * where the user can create or retrieve an API key.
     */
    private val PROVIDER_CONSOLES: Map<String, DeepLinkTarget> =
        mapOf(
            "openai" to
                DeepLinkTarget(
                    uri = "https://platform.openai.com/api-keys",
                    label = "Get OpenAI API Key",
                ),
            "anthropic" to
                DeepLinkTarget(
                    uri = "https://console.anthropic.com/settings/keys",
                    label = "Get Anthropic API Key",
                ),
            "openrouter" to
                DeepLinkTarget(
                    uri = "https://openrouter.ai/keys",
                    label = "Get OpenRouter API Key",
                ),
            "google_gemini" to
                DeepLinkTarget(
                    uri = "https://aistudio.google.com/apikey",
                    label = "Get Google AI API Key",
                ),
            "groq" to
                DeepLinkTarget(
                    uri = "https://console.groq.com/keys",
                    label = "Get Groq API Key",
                ),
            "together" to
                DeepLinkTarget(
                    uri = "https://api.together.xyz/settings/api-keys",
                    label = "Get Together API Key",
                ),
            "mistral" to
                DeepLinkTarget(
                    uri = "https://console.mistral.ai/api-keys",
                    label = "Get Mistral API Key",
                ),
        )

    /**
     * Returns the API-key console [DeepLinkTarget] for the given provider, or null
     * if the provider is not recognised.
     *
     * @param providerId ZeroClaw provider identifier (e.g. `"openai"`, `"anthropic"`).
     * @return The console target, or null for unknown providers.
     */
    fun providerConsoleTarget(providerId: String): DeepLinkTarget? = PROVIDER_CONSOLES[providerId]

    /**
     * Launches an [Intent.ACTION_VIEW] for the given [target].
     *
     * If the primary [DeepLinkTarget.uri] has no registered handler on the device,
     * the [DeepLinkTarget.fallbackUri] is attempted as a second try. If neither
     * URI can be resolved, the [ActivityNotFoundException] from the fallback
     * attempt propagates to the caller.
     *
     * @param context Android context used to start the activity.
     * @param target Deep-link destination to open.
     */
    fun launch(
        context: Context,
        target: DeepLinkTarget,
    ) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target.uri))
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val fallback = target.fallbackUri
            if (fallback != null) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallback)))
            } else {
                throw e
            }
        }
    }
}
