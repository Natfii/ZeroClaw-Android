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

    /** Matches OAuth bearer tokens (`Bearer ...`). */
    private val BEARER_PATTERN = Regex("""Bearer\s+[A-Za-z0-9\-_.~+/]+=*""")

    /** Matches Slack bot tokens (`xoxb-...`). */
    private val BOT_TOKEN_PATTERN = Regex("""xoxb-[0-9]+-[A-Za-z0-9]+""")

    /** Matches Authorization header values. */
    private val AUTH_HEADER_PATTERN = Regex("""Authorization:\s*\S+""")

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
            .replace(BEARER_PATTERN, "Bearer $REDACTION_PLACEHOLDER")
            .replace(API_KEY_PATTERN, REDACTION_PLACEHOLDER)
            .replace(BOT_TOKEN_PATTERN, REDACTION_PLACEHOLDER)
            .replace(AUTH_HEADER_PATTERN, "Authorization: $REDACTION_PLACEHOLDER")
            .replace(LONG_URL_PATTERN, REDACTED_URL)
}
