/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.util

/**
 * Regex-based redaction of secrets from log messages before sharing.
 *
 * Applied in [com.zeroclaw.android.ui.screen.settings.logs.LogViewerScreen]
 * when the user exports logs via the share intent. Patterns cover common
 * API key formats, bearer tokens, bot tokens, and authorization headers.
 */
object LogSanitizer {
    /** Matches OpenAI-style API keys (`sk-...`). */
    private val API_KEY_PATTERN = Regex("""sk-[A-Za-z0-9_-]{8,}""")

    /** Matches Anthropic API keys (`sk-ant-...`). */
    private val ANTHROPIC_KEY_PATTERN = Regex("""sk-ant-[A-Za-z0-9_-]{8,}""")

    /** Matches Google AI API keys (`AIza...`). */
    private val GOOGLE_KEY_PATTERN = Regex("""AIza[A-Za-z0-9_-]{30,}""")

    /** Matches OAuth bearer tokens (`Bearer ...`). */
    private val BEARER_PATTERN = Regex("""Bearer\s+[A-Za-z0-9\-_.~+/]+=*""")

    /** Matches Slack bot tokens (`xoxb-...`). */
    private val BOT_TOKEN_PATTERN = Regex("""xoxb-[0-9]+-[A-Za-z0-9]+""")

    /** Matches ngrok authentication tokens. */
    private val NGROK_TOKEN_PATTERN = Regex("""ngrok[_-]?[A-Za-z0-9]{20,}""")

    /** Matches Authorization header values. */
    private val AUTH_HEADER_PATTERN = Regex("""Authorization:\s*\S+""")

    /** Matches `x-api-key` header values. */
    private val X_API_KEY_PATTERN = Regex("""x-api-key:\s*\S+""", RegexOption.IGNORE_CASE)

    /** Matches AWS access keys (`AKIA...`). */
    private val AWS_KEY_PATTERN = Regex("""AKIA[A-Z0-9]{16}""")

    /** Matches GitHub personal access tokens and app tokens. */
    private val GITHUB_TOKEN_PATTERN = Regex("""gh[pos]_[A-Za-z0-9_]{36,}""")

    /** Matches Discord bot tokens (base64-encoded). */
    private val DISCORD_TOKEN_PATTERN = Regex("""[A-Za-z0-9_-]{24}\.[A-Za-z0-9_-]{6}\.[A-Za-z0-9_-]{27,}""")

    /** Matches JWT tokens (three dot-separated base64 segments). */
    private val JWT_PATTERN = Regex("""eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")

    /** Matches suspiciously long URLs that may contain credentials. */
    private val LONG_URL_PATTERN = Regex("""https?://[^\s]{50,}""")

    private const val REDACTION_PLACEHOLDER = "[REDACTED]"
    private const val REDACTED_URL = "[REDACTED_URL]"

    /**
     * Applies all redaction patterns to a log message.
     *
     * @param message Raw log message text.
     * @return The message with secrets replaced by redaction placeholders.
     */
    fun sanitizeLogMessage(message: String): String =
        message
            .replace(JWT_PATTERN, REDACTION_PLACEHOLDER)
            .replace(BEARER_PATTERN, "Bearer $REDACTION_PLACEHOLDER")
            .replace(ANTHROPIC_KEY_PATTERN, REDACTION_PLACEHOLDER)
            .replace(API_KEY_PATTERN, REDACTION_PLACEHOLDER)
            .replace(GOOGLE_KEY_PATTERN, REDACTION_PLACEHOLDER)
            .replace(AWS_KEY_PATTERN, REDACTION_PLACEHOLDER)
            .replace(GITHUB_TOKEN_PATTERN, REDACTION_PLACEHOLDER)
            .replace(DISCORD_TOKEN_PATTERN, REDACTION_PLACEHOLDER)
            .replace(BOT_TOKEN_PATTERN, REDACTION_PLACEHOLDER)
            .replace(NGROK_TOKEN_PATTERN, REDACTION_PLACEHOLDER)
            .replace(AUTH_HEADER_PATTERN, "Authorization: $REDACTION_PLACEHOLDER")
            .replace(X_API_KEY_PATTERN, "x-api-key: $REDACTION_PLACEHOLDER")
            .replace(LONG_URL_PATTERN, REDACTED_URL)
}
