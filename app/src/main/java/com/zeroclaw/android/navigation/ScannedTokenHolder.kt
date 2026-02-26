/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.navigation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Transient in-memory holder for QR-scanned tokens.
 *
 * Replaces [SavedStateHandle]-based token transport which would persist
 * secrets to disk via Bundle serialization. Tokens held here do not
 * survive process death, which is the desired security behavior.
 */
class ScannedTokenHolder : ViewModel() {
    private val _token = MutableStateFlow("")

    /** The most recently scanned token, or empty if consumed. */
    val token: StateFlow<String> = _token.asStateFlow()

    /**
     * Stores a newly scanned token for consumption by the calling screen.
     *
     * @param value The scanned token string.
     */
    fun set(value: String) {
        _token.value = value
    }

    /** Clears the token after the calling screen has consumed it. */
    fun consume() {
        _token.value = ""
    }
}
