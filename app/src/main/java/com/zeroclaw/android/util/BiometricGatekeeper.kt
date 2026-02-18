/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Result of a biometric authentication attempt.
 *
 * Sealed interface allowing callers to handle all possible outcomes
 * of [BiometricGatekeeper.authenticate].
 */
sealed interface AuthResult {
    /** Authentication succeeded. */
    data object Success : AuthResult

    /** User cancelled the biometric prompt. */
    data object Cancelled : AuthResult

    /** Biometric hardware is not available or not enrolled on this device. */
    data object NotAvailable : AuthResult

    /**
     * Authentication failed with an error.
     *
     * @property errorCode Error code from [BiometricPrompt].
     * @property message Human-readable error description.
     */
    data class Failed(
        val errorCode: Int,
        val message: String,
    ) : AuthResult
}

/**
 * Singleton utility wrapping [BiometricPrompt] and [BiometricManager].
 *
 * Provides a simple API for checking biometric availability and
 * launching authentication prompts. Replaces inline biometric prompt
 * creation throughout the app with a single, testable entry point.
 */
object BiometricGatekeeper {
    /**
     * Checks whether the device supports biometric authentication.
     *
     * Returns true when at least [BiometricManager.BIOMETRIC_WEAK]
     * level hardware is present and the user has enrolled biometrics.
     *
     * @param context Application or activity context.
     * @return True if biometric authentication can be performed.
     */
    fun isAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Launches a biometric authentication prompt.
     *
     * If the device does not support biometrics, [onResult] is
     * called immediately with [AuthResult.NotAvailable]. Otherwise
     * a [BiometricPrompt] is shown and the result is delivered
     * asynchronously on the main thread.
     *
     * Safe to call from main thread. The prompt is displayed
     * immediately and the callback fires on the main executor.
     *
     * @param activity The hosting [FragmentActivity] required by [BiometricPrompt].
     * @param title Title text shown on the biometric dialog.
     * @param subtitle Subtitle text shown below the title.
     * @param negativeButtonText Text for the cancel/negative button.
     * @param onResult Callback receiving the [AuthResult].
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onResult: (AuthResult) -> Unit,
    ) {
        if (!isAvailable(activity)) {
            onResult(AuthResult.NotAvailable)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    onResult(AuthResult.Success)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onResult(AuthResult.Cancelled)
                    } else {
                        onResult(AuthResult.Failed(errorCode, errString.toString()))
                    }
                }

                override fun onAuthenticationFailed() {
                    // Partial failure (bad fingerprint read); prompt stays open.
                }
            }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(negativeButtonText)
                .build()
        prompt.authenticate(promptInfo)
    }
}
