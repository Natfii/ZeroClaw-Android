/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * Type of AI server detected during a local network scan.
 *
 * @property label Human-readable name for display in scan results.
 */
enum class LocalServerType(val label: String) {
    /** Ollama server detected via `/api/tags` endpoint. */
    OLLAMA("Ollama"),

    /** Server responding to `/v1/models` (LM Studio, vLLM, LocalAI, etc.). */
    OPENAI_COMPATIBLE("OpenAI-Compatible"),
}

/**
 * An AI server discovered on the local network during a scan.
 *
 * @property host IP address of the discovered server (e.g. "192.168.1.50").
 * @property port Port number the server is listening on.
 * @property serverType Detected server protocol type.
 * @property models List of model names loaded on the server, empty if unknown.
 */
data class DiscoveredServer(
    val host: String,
    val port: Int,
    val serverType: LocalServerType,
    val models: List<String> = emptyList(),
) {
    /**
     * Base URL suitable for use as a provider endpoint.
     *
     * Ollama uses the root URL, while OpenAI-compatible servers use the `/v1` path.
     */
    val baseUrl: String
        get() = when (serverType) {
            LocalServerType.OLLAMA -> "http://$host:$port"
            LocalServerType.OPENAI_COMPATIBLE -> "http://$host:$port/v1"
        }

    /** Short display string combining server type, host, and port. */
    val displayLabel: String
        get() = "${serverType.label} at $host:$port"
}

/**
 * State of an ongoing or completed network scan.
 */
sealed interface ScanState {
    /** Scan has not started. */
    data object Idle : ScanState

    /** Scan is in progress with the given fractional [progress] (0.0 to 1.0). */
    data class Scanning(val progress: Float) : ScanState

    /** Scan completed with [servers] found on the network. */
    data class Completed(val servers: List<DiscoveredServer>) : ScanState

    /** Scan failed with a human-readable [message]. */
    data class Error(val message: String) : ScanState
}
