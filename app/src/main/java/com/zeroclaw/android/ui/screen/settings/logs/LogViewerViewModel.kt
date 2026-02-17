/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings.logs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.LogEntry
import com.zeroclaw.android.model.LogSeverity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the log viewer screen.
 *
 * Provides filtered log entries based on selected severity levels
 * and supports pausing/resuming the live log stream.
 *
 * @param application Application context for accessing the log repository.
 */
class LogViewerViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = (application as ZeroClawApplication).logRepository

    private val _selectedSeverities =
        MutableStateFlow(LogSeverity.entries.toSet())

    /** Currently selected severity filter levels. */
    val selectedSeverities: StateFlow<Set<LogSeverity>> =
        _selectedSeverities.asStateFlow()

    private val _isPaused = MutableStateFlow(false)

    /** Whether the live log stream is paused. */
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _snapshot = MutableStateFlow<List<LogEntry>>(emptyList())
    private val _liveEntries = MutableStateFlow<List<LogEntry>>(emptyList())

    init {
        viewModelScope.launch {
            repository.entries.collect { entries ->
                _liveEntries.value = entries
                if (!_isPaused.value) {
                    _snapshot.value = entries
                }
            }
        }
    }

    /** Filtered log entries based on severity selection and pause state. */
    val filteredEntries: StateFlow<List<LogEntry>> =
        combine(_snapshot, _selectedSeverities) { entries, severities ->
            entries.filter { it.severity in severities }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    /**
     * Toggles a severity level in the filter.
     *
     * @param severity The severity to toggle.
     */
    fun toggleSeverity(severity: LogSeverity) {
        _selectedSeverities.value =
            _selectedSeverities.value.let { current ->
                if (severity in current) current - severity else current + severity
            }
    }

    /** Pauses the live log stream, freezing the current entries. */
    fun pause() {
        _isPaused.value = true
    }

    /** Resumes the live log stream. */
    fun resume() {
        _isPaused.value = false
        _snapshot.value = _liveEntries.value
    }

    /** Clears all log entries. */
    fun clearLogs() {
        repository.clear()
    }

    /** Constants for [LogViewerViewModel]. */
    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
