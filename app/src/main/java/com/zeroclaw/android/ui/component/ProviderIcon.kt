/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.data.ProviderRegistry

/** Minimum touch target size for accessibility. */
private const val ICON_SIZE_DP = 40

/** Brand color for Anthropic provider. */
private const val COLOR_ANTHROPIC = 0xFFD97706

/** Brand color for OpenAI provider. */
private const val COLOR_OPENAI = 0xFF10A37F

/** Brand color for Google Gemini provider. */
private const val COLOR_GOOGLE = 0xFF4285F4

/** Brand color for Mistral provider. */
private const val COLOR_MISTRAL = 0xFFFF7000

/** Brand color for Meta provider. */
private const val COLOR_META = 0xFF0668E1

/** Brand color for OpenRouter provider. */
private const val COLOR_OPENROUTER = 0xFF6366F1

/** Brand color for Groq provider. */
private const val COLOR_GROQ = 0xFFF55036

/** Brand color for xAI / Grok provider. */
private const val COLOR_XAI = 0xFF1DA1F2

/** Brand color for DeepSeek provider. */
private const val COLOR_DEEPSEEK = 0xFF0A84FF

/** Brand color for Together AI provider. */
private const val COLOR_TOGETHER = 0xFF6C5CE7

/** Brand color for Fireworks AI provider. */
private const val COLOR_FIREWORKS = 0xFFFF6B35

/** Brand color for Perplexity provider. */
private const val COLOR_PERPLEXITY = 0xFF20B2AA

/** Brand color for Cohere provider. */
private const val COLOR_COHERE = 0xFF39594D

/** Brand color for Ollama provider. */
private const val COLOR_OLLAMA = 0xFF000000

/** Brand color for Cloudflare AI provider. */
private const val COLOR_CLOUDFLARE = 0xFFF48120

/** Brand color for Amazon Bedrock provider. */
private const val COLOR_BEDROCK = 0xFFFF9900

/**
 * Circular icon showing the first letter and brand color of an AI provider.
 *
 * Uses [ProviderRegistry] to resolve aliases so that e.g. "google" and
 * "google-gemini" both produce the same icon.
 *
 * @param provider Provider ID or name (e.g. "anthropic", "OpenAI").
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun ProviderIcon(
    provider: String,
    modifier: Modifier = Modifier,
) {
    val resolved = ProviderRegistry.findById(provider)
    val displayName = resolved?.displayName ?: provider
    val resolvedId = resolved?.id ?: provider.lowercase()
    val (bgColor, fgColor) = providerColors(resolvedId)
    val initial = displayName.firstOrNull()?.uppercase() ?: "?"

    Box(
        modifier =
            modifier
                .size(ICON_SIZE_DP.dp)
                .clip(CircleShape)
                .background(bgColor)
                .semantics { contentDescription = "$displayName provider" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = fgColor,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * Returns background and foreground colors for the given provider ID.
 *
 * @param providerId Resolved canonical provider ID.
 * @return Pair of (background, foreground) colors.
 */
@Composable
private fun providerColors(providerId: String): Pair<Color, Color> =
    when (providerId) {
        "anthropic" -> Pair(Color(COLOR_ANTHROPIC), Color.White)
        "openai" -> Pair(Color(COLOR_OPENAI), Color.White)
        "google-gemini" -> Pair(Color(COLOR_GOOGLE), Color.White)
        "mistral" -> Pair(Color(COLOR_MISTRAL), Color.White)
        "meta" -> Pair(Color(COLOR_META), Color.White)
        "openrouter" -> Pair(Color(COLOR_OPENROUTER), Color.White)
        "groq" -> Pair(Color(COLOR_GROQ), Color.White)
        "xai" -> Pair(Color(COLOR_XAI), Color.White)
        "deepseek" -> Pair(Color(COLOR_DEEPSEEK), Color.White)
        "together" -> Pair(Color(COLOR_TOGETHER), Color.White)
        "fireworks" -> Pair(Color(COLOR_FIREWORKS), Color.White)
        "perplexity" -> Pair(Color(COLOR_PERPLEXITY), Color.White)
        "cohere" -> Pair(Color(COLOR_COHERE), Color.White)
        "ollama" -> Pair(Color(COLOR_OLLAMA), Color.White)
        "cloudflare" -> Pair(Color(COLOR_CLOUDFLARE), Color.White)
        "bedrock" -> Pair(Color(COLOR_BEDROCK), Color.White)
        else ->
            Pair(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer,
            )
    }
