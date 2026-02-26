/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.validation

import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.data.remote.ModelFetcher
import com.zeroclaw.android.model.ModelListFormat
import com.zeroclaw.android.util.LogSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Validates provider API keys by probing the provider's model listing endpoint.
 *
 * Wraps [ModelFetcher.fetchModels] to return a [ValidationResult] instead of
 * `Result<List<String>>`, classifying the outcome into user-friendly states
 * suitable for display in the onboarding wizard and settings screens.
 *
 * Use [validate] for live network validation and [classifyProbeResult] for
 * unit-testable classification without network access.
 */
object ProviderValidator {
    /** HTTP status code pattern for authentication failures. */
    private const val AUTH_PATTERN_401 = "HTTP 401"

    /** HTTP status code pattern for authorization failures. */
    private const val AUTH_PATTERN_403 = "HTTP 403"

    /**
     * Validates a provider's API key by probing its model listing endpoint.
     *
     * Switches to [Dispatchers.IO] for the network call. Returns [ValidationResult.Idle]
     * if the provider ID is blank, [ValidationResult.Loading] is never returned (the caller
     * should set that state before invoking this suspend function).
     *
     * @param providerId Canonical provider identifier (e.g. "openai", "anthropic").
     * @param apiKey API key to authenticate with, may be blank for unauthenticated providers.
     * @param baseUrl User-configured base URL override, may be blank to use defaults.
     * @return A terminal [ValidationResult] classifying the probe outcome.
     */
    @Suppress("InjectDispatcher")
    suspend fun validate(
        providerId: String,
        apiKey: String,
        baseUrl: String,
    ): ValidationResult =
        withContext(Dispatchers.IO) {
            if (providerId.isBlank()) return@withContext ValidationResult.Idle

            val provider =
                ProviderRegistry.findById(providerId)
                    ?: return@withContext ValidationResult.Failure(
                        message = "Unknown provider",
                        retryable = false,
                    )

            if (provider.modelListFormat == ModelListFormat.NONE) {
                return@withContext ValidationResult.Success(
                    details = "Provider configured (no endpoint to validate)",
                )
            }

            if (apiKey.isBlank() && baseUrl.isBlank()) {
                return@withContext ValidationResult.Idle
            }

            val probeResult =
                ModelFetcher.fetchModels(
                    provider = provider,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                )
            classifyProbeResult(providerId, probeResult)
        }

    /**
     * Classifies a probe result into a [ValidationResult].
     *
     * This function is exposed as `internal` for unit testing without requiring
     * network access. It applies the same classification logic used by [validate]
     * to an already-obtained [Result] from [ModelFetcher.fetchModels].
     *
     * @param providerId Canonical provider identifier. Blank returns [ValidationResult.Idle].
     * @param probeResult The result of a model listing probe to classify.
     * @return A terminal [ValidationResult] based on the probe outcome.
     */
    internal fun classifyProbeResult(
        providerId: String,
        probeResult: Result<List<String>>,
    ): ValidationResult {
        if (providerId.isBlank()) return ValidationResult.Idle

        val provider =
            ProviderRegistry.findById(providerId)
                ?: return ValidationResult.Failure(
                    message = "Unknown provider",
                    retryable = false,
                )

        if (provider.modelListFormat == ModelListFormat.NONE) {
            return ValidationResult.Success(
                details = "Provider configured (no endpoint to validate)",
            )
        }

        return probeResult.fold(
            onSuccess = { models -> classifySuccess(models) },
            onFailure = { error -> classifyError(error) },
        )
    }

    /**
     * Classifies a successful probe result based on the returned model count.
     *
     * @param models List of model IDs returned by the provider.
     * @return [ValidationResult.Success] with a human-readable model count message.
     */
    private fun classifySuccess(models: List<String>): ValidationResult.Success {
        val count = models.size
        val noun = if (count == 1) "model" else "models"
        return ValidationResult.Success(
            details = "Connected \u2014 $count $noun available",
        )
    }

    /**
     * Classifies a probe failure based on the error message content.
     *
     * HTTP 401 and 403 responses indicate definitive authentication failures
     * and are classified as non-retryable [ValidationResult.Failure]. All other
     * errors (network timeouts, DNS failures, server errors) are classified as
     * [ValidationResult.Offline] since they may be transient.
     *
     * @param error The exception thrown during the probe.
     * @return Either [ValidationResult.Failure] for auth errors or
     *   [ValidationResult.Offline] for transient errors.
     */
    private fun classifyError(error: Throwable): ValidationResult {
        val message = error.message.orEmpty()

        if (AUTH_PATTERN_401 in message || AUTH_PATTERN_403 in message) {
            return ValidationResult.Failure(
                message = "Invalid API key \u2014 check your credentials",
                retryable = false,
            )
        }

        val safeMessage = LogSanitizer.sanitizeLogMessage(message)
        return ValidationResult.Offline(
            message = "Could not reach provider \u2014 $safeMessage",
        )
    }
}
