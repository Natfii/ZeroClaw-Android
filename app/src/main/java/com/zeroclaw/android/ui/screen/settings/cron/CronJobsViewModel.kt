// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.ui.screen.settings.cron

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.CronJob
import com.zeroclaw.android.service.CronBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the cron jobs screen.
 *
 * @param T The type of content data.
 */
sealed interface CronJobsUiState<out T> {
    /** Data is being loaded from the bridge. */
    data object Loading : CronJobsUiState<Nothing>

    /**
     * Loading or mutation failed.
     *
     * @property detail Human-readable error message.
     */
    data class Error(
        val detail: String,
    ) : CronJobsUiState<Nothing>

    /**
     * Data loaded successfully.
     *
     * @param T Content data type.
     * @property data The loaded content.
     */
    data class Content<T>(
        val data: T,
    ) : CronJobsUiState<T>
}

/**
 * ViewModel for the scheduled cron jobs management screen.
 *
 * Loads cron jobs from [CronBridge] and exposes CRUD operations as
 * suspend-safe methods that refresh the job list upon completion.
 *
 * @param application Application context for accessing [ZeroClawApplication.cronBridge].
 */
class CronJobsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val cronBridge: CronBridge =
        (application as ZeroClawApplication).cronBridge

    private val _uiState =
        MutableStateFlow<CronJobsUiState<List<CronJob>>>(CronJobsUiState.Loading)

    /** Observable UI state for the cron jobs list. */
    val uiState: StateFlow<CronJobsUiState<List<CronJob>>> = _uiState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)

    /**
     * One-shot snackbar message shown after a successful mutation.
     *
     * Collect with `collectAsStateWithLifecycle` and call [clearSnackbar]
     * after displaying.
     */
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        loadJobs()
    }

    /** Reloads the cron jobs list from the native layer. */
    fun loadJobs() {
        _uiState.value = CronJobsUiState.Loading
        viewModelScope.launch {
            loadJobsInternal()
        }
    }

    /**
     * Adds a new recurring cron job.
     *
     * @param expression Valid cron expression.
     * @param command Command for the scheduler to execute.
     */
    fun addJob(
        expression: String,
        command: String,
    ) {
        viewModelScope.launch {
            runMutation("Job added") {
                cronBridge.addJob(expression, command)
            }
        }
    }

    /**
     * Adds a one-shot job that fires once after the given delay.
     *
     * @param delay Human-readable delay (e.g. "5m", "2h").
     * @param command Command for the scheduler to execute.
     */
    fun addOneShot(
        delay: String,
        command: String,
    ) {
        viewModelScope.launch {
            runMutation("One-shot job added") {
                cronBridge.addOneShot(delay, command)
            }
        }
    }

    /**
     * Pauses the job with the given identifier.
     *
     * @param id Unique job identifier.
     */
    fun pauseJob(id: String) {
        viewModelScope.launch {
            runMutation("Job paused") {
                cronBridge.pauseJob(id)
            }
        }
    }

    /**
     * Resumes a previously paused job.
     *
     * @param id Unique job identifier.
     */
    fun resumeJob(id: String) {
        viewModelScope.launch {
            runMutation("Job resumed") {
                cronBridge.resumeJob(id)
            }
        }
    }

    /**
     * Removes the job with the given identifier.
     *
     * @param id Unique job identifier.
     */
    fun removeJob(id: String) {
        viewModelScope.launch {
            runMutation("Job removed") {
                cronBridge.removeJob(id)
            }
        }
    }

    /** Clears the current snackbar message. */
    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadJobsInternal() {
        try {
            val jobs = cronBridge.listJobs()
            _uiState.value = CronJobsUiState.Content(jobs)
        } catch (e: Exception) {
            _uiState.value =
                CronJobsUiState.Error(
                    e.message ?: "Failed to load cron jobs",
                )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runMutation(
        successMessage: String,
        block: suspend () -> Any?,
    ) {
        try {
            block()
            _snackbarMessage.value = successMessage
            loadJobsInternal()
        } catch (e: Exception) {
            _snackbarMessage.value = "Error: ${e.message ?: "unknown"}"
        }
    }
}
