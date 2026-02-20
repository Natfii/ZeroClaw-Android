/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.zeroclaw.android.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages app-wide session lock state based on background timeout.
 *
 * Observes the process lifecycle via [DefaultLifecycleObserver] and
 * locks the app when the user returns after the configured timeout.
 * The app starts locked on cold launch.
 *
 * Register with `ProcessLifecycleOwner.get().lifecycle.addObserver(...)`.
 *
 * @param settingsFlow Flow of [AppSettings] providing lock configuration.
 * @param scope Coroutine scope for collecting the settings flow.
 */
class SessionLockManager(
    settingsFlow: Flow<AppSettings>,
    scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private val _isLocked = MutableStateFlow(true)

    /** Whether the app is currently locked and the lock gate should be displayed. */
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var backgroundTimestamp: Long = 0L
    private var lockEnabled: Boolean = false
    private var pinHashSet: Boolean = false
    private var timeoutMillis: Long = AppSettings.DEFAULT_LOCK_TIMEOUT * MILLIS_PER_MINUTE

    init {
        scope.launch {
            settingsFlow.collect { settings ->
                lockEnabled = settings.lockEnabled
                pinHashSet = settings.pinHash.isNotEmpty()
                timeoutMillis = settings.lockTimeoutMinutes.toLong() * MILLIS_PER_MINUTE
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundTimestamp = System.currentTimeMillis()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (backgroundTimestamp == 0L) return
        val elapsed = System.currentTimeMillis() - backgroundTimestamp
        if (elapsed >= timeoutMillis && lockEnabled && pinHashSet) {
            _isLocked.value = true
        }
    }

    /** Unlocks the app after successful authentication. */
    fun unlock() {
        _isLocked.value = false
    }

    /** Locks the app immediately. */
    fun lock() {
        _isLocked.value = true
    }

    /** Constants for [SessionLockManager]. */
    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
