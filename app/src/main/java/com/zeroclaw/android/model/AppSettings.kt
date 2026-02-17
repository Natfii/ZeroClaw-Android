/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * Persistent application settings.
 *
 * @property host Gateway bind address.
 * @property port Gateway bind port.
 * @property autoStartOnBoot Whether to start the daemon automatically after reboot.
 * @property logLevel Minimum severity for log output.
 * @property defaultProvider Default provider ID for new agents (e.g. "openai").
 * @property defaultModel Default model name for new agents (e.g. "gpt-4o").
 */
data class AppSettings(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val autoStartOnBoot: Boolean = false,
    val logLevel: LogLevel = LogLevel.INFO,
    val defaultProvider: String = "",
    val defaultModel: String = "",
) {
    /** Constants for [AppSettings]. */
    companion object {
        /** Default gateway bind address. */
        const val DEFAULT_HOST = "127.0.0.1"

        /** Default gateway bind port. */
        const val DEFAULT_PORT = 8080
    }
}

/**
 * Log severity levels for daemon output filtering.
 */
enum class LogLevel {
    /** Verbose debug output. */
    DEBUG,

    /** Standard informational messages. */
    INFO,

    /** Warning conditions. */
    WARN,

    /** Error conditions. */
    ERROR,
}
