/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings.doctor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.DiagnosticCheck
import com.zeroclaw.android.model.DoctorSummary
import com.zeroclaw.android.service.DoctorValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the ZeroClaw Doctor diagnostics screen.
 *
 * Orchestrates sequential execution of diagnostic check categories
 * and provides incremental UI updates as each category completes.
 *
 * @param application Application context for accessing repositories.
 */
class DoctorViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = application as ZeroClawApplication

    private val validator =
        DoctorValidator(
            context = application,
            agentRepository = app.agentRepository,
            apiKeyRepository = app.apiKeyRepository,
        )

    private val _checks = MutableStateFlow<List<DiagnosticCheck>>(emptyList())

    /** All diagnostic check results, incrementally populated as categories complete. */
    val checks: StateFlow<List<DiagnosticCheck>> = _checks.asStateFlow()

    private val _isRunning = MutableStateFlow(false)

    /** Whether diagnostic checks are currently executing. */
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _summary = MutableStateFlow<DoctorSummary?>(null)

    /** Aggregated check summary, available after all checks complete. */
    val summary: StateFlow<DoctorSummary?> = _summary.asStateFlow()

    /**
     * Runs all diagnostic check categories sequentially.
     *
     * Each category's results are appended to [checks] as they complete,
     * providing incremental UI updates. The [summary] is computed after
     * all categories finish.
     *
     * Safe to call multiple times; resets state on each invocation.
     */
    fun runAllChecks() {
        if (_isRunning.value) return
        viewModelScope.launch {
            _isRunning.value = true
            _checks.value = emptyList()
            _summary.value = null

            val accumulated = mutableListOf<DiagnosticCheck>()

            val configChecks = validator.runConfigChecks()
            accumulated.addAll(configChecks)
            _checks.value = accumulated.toList()

            val apiKeyChecks = validator.runApiKeyChecks()
            accumulated.addAll(apiKeyChecks)
            _checks.value = accumulated.toList()

            val connectivityChecks = validator.runConnectivityChecks()
            accumulated.addAll(connectivityChecks)
            _checks.value = accumulated.toList()

            val daemonChecks = validator.runDaemonHealthChecks()
            accumulated.addAll(daemonChecks)
            _checks.value = accumulated.toList()

            val systemChecks = validator.runSystemChecks()
            accumulated.addAll(systemChecks)
            _checks.value = accumulated.toList()

            _summary.value = DoctorSummary.from(accumulated)
            _isRunning.value = false
        }
    }
}
