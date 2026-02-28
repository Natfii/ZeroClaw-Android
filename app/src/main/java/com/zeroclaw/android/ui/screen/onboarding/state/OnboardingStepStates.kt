/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding.state

import com.zeroclaw.android.data.validation.ValidationResult
import com.zeroclaw.android.model.ChannelType
import java.util.TimeZone

/**
 * State for the welcome step.
 *
 * @property acknowledged Whether the user has acknowledged the welcome screen.
 */
data class WelcomeStepState(
    val acknowledged: Boolean = false,
)

/**
 * State for the provider configuration step.
 *
 * Tracks the selected provider, API key, base URL, model selection,
 * and the result of live credential validation.
 *
 * @property providerId Canonical provider ID (e.g. "openai", "anthropic").
 * @property apiKey API key entered by the user.
 * @property baseUrl Base URL for custom or local providers.
 * @property model Selected model name.
 * @property validationResult Result of the most recent provider validation attempt.
 * @property availableModels Models fetched from the provider's API.
 * @property isLoadingModels Whether a model fetch is currently in progress.
 * @property isOAuthInProgress Whether an OAuth login flow is currently running.
 * @property oauthEmail Display email or label for the authenticated OAuth session.
 * @property oauthRefreshToken OAuth refresh token for obtaining new access tokens.
 * @property oauthExpiresAt Epoch milliseconds when the current OAuth access token expires.
 */
data class ProviderStepState(
    val providerId: String = "",
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val validationResult: ValidationResult = ValidationResult.Idle,
    val availableModels: List<String> = emptyList(),
    val isLoadingModels: Boolean = false,
    val isOAuthInProgress: Boolean = false,
    val oauthEmail: String = "",
    val oauthRefreshToken: String = "",
    val oauthExpiresAt: Long = 0L,
)

/**
 * State for the channel selection step.
 *
 * Tracks which channel types the user has selected and which sub-flow
 * is currently active.
 *
 * @property selectedTypes Set of channel types the user has chosen to configure.
 * @property activeSubFlowType The channel type whose sub-flow is currently displayed,
 *   or null when no sub-flow is active.
 */
data class ChannelSelectionState(
    val selectedTypes: Set<ChannelType> = emptySet(),
    val activeSubFlowType: ChannelType? = null,
)

/**
 * State for a single channel's sub-flow configuration.
 *
 * Each selected channel type maintains its own independent sub-flow state
 * so the user can configure multiple channels without losing progress.
 *
 * @property fieldValues Configuration field values keyed by TOML field name.
 * @property validationResult Result of the most recent channel token validation attempt.
 * @property currentSubStep Zero-based index of the current sub-step within the channel flow.
 * @property completed Whether the user has finished configuring this channel.
 */
data class ChannelSubFlowState(
    val fieldValues: Map<String, String> = emptyMap(),
    val validationResult: ValidationResult = ValidationResult.Idle,
    val currentSubStep: Int = 0,
    val completed: Boolean = false,
)

/**
 * State for the tunnel configuration step.
 *
 * @property tunnelType Tunnel provider: "none", "cloudflare", "tailscale", "ngrok", or "custom".
 * @property tunnelToken Authentication token for the selected tunnel provider.
 * @property customEndpoint User-provided endpoint URL for custom tunnels.
 */
data class TunnelStepState(
    val tunnelType: String = "none",
    val tunnelToken: String = "",
    val customEndpoint: String = "",
)

/**
 * State for the security/autonomy configuration step.
 *
 * @property autonomyLevel Autonomy level: "supervised", "constrained", or "unconstrained".
 */
data class SecurityStepState(
    val autonomyLevel: String = "supervised",
)

/**
 * State for the memory configuration step.
 *
 * @property backend Memory backend: "sqlite", "none", "markdown", or "lucid".
 * @property autoSave Whether the memory backend auto-saves conversation context.
 * @property embeddingProvider Embedding provider: "none", "openai", or "custom:URL".
 * @property retentionDays Number of days before memory entries are archived.
 */
data class MemoryStepState(
    val backend: String = "sqlite",
    val autoSave: Boolean = true,
    val embeddingProvider: String = "",
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
)

/** Default memory retention period in days. */
private const val DEFAULT_RETENTION_DAYS = 30

/**
 * State for the identity configuration step.
 *
 * @property agentName Name for the AI agent.
 * @property userName Name of the human user interacting with the agent.
 * @property timezone IANA timezone ID for the agent's locale awareness.
 * @property communicationStyle Preferred communication style or tone.
 * @property identityFormat Identity specification format: "openclaw" or "aieos".
 */
data class IdentityStepState(
    val agentName: String = "My Agent",
    val userName: String = "",
    val timezone: String = TimeZone.getDefault().id,
    val communicationStyle: String = "",
    val identityFormat: String = "aieos",
)

/**
 * State for the activation (final) step.
 *
 * @property isCompleting Whether the completion flow is currently running.
 * @property completeError One-shot error message from the completion flow, or null.
 */
data class ActivationStepState(
    val isCompleting: Boolean = false,
    val completeError: String? = null,
)
