// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.service

import com.zeroclaw.android.model.CronJob
import com.zeroclaw.ffi.FfiCronJob
import com.zeroclaw.ffi.FfiException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridge between the Android UI layer and the Rust cron job FFI.
 *
 * Wraps the seven cron-related UniFFI-generated functions in
 * coroutine-safe suspend functions dispatched to [Dispatchers.IO].
 *
 * @param ioDispatcher Dispatcher for blocking FFI calls. Defaults to [Dispatchers.IO].
 */
class CronBridge(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Lists all cron jobs registered with the running daemon.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @return List of all [CronJob] instances.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun listJobs(): List<CronJob> =
        withContext(ioDispatcher) {
            com.zeroclaw.ffi
                .listCronJobs()
                .map { it.toModel() }
        }

    /**
     * Retrieves a single cron job by its identifier.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @param id Unique identifier of the job to retrieve.
     * @return The [CronJob] if found, or null if no job with the given [id] exists.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun getJob(id: String): CronJob? =
        withContext(ioDispatcher) {
            com.zeroclaw.ffi
                .getCronJob(id)
                ?.toModel()
        }

    /**
     * Adds a new recurring cron job with the given expression and command.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @param expression Valid cron expression (e.g. `"0 *&#47;5 * * *"`).
     * @param command Command string for the scheduler to execute.
     * @return The newly created [CronJob].
     * @throws FfiException if the expression is invalid or the native layer fails.
     */
    @Throws(FfiException::class)
    suspend fun addJob(
        expression: String,
        command: String,
    ): CronJob =
        withContext(ioDispatcher) {
            com.zeroclaw.ffi
                .addCronJob(expression, command)
                .toModel()
        }

    /**
     * Adds a one-shot job that fires once after the given delay.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @param delay Human-readable delay string (e.g. `"5m"`, `"2h"`).
     * @param command Command string for the scheduler to execute.
     * @return The newly created [CronJob].
     * @throws FfiException if the delay is invalid or the native layer fails.
     */
    @Throws(FfiException::class)
    suspend fun addOneShot(
        delay: String,
        command: String,
    ): CronJob =
        withContext(ioDispatcher) {
            com.zeroclaw.ffi
                .addOneShotJob(delay, command)
                .toModel()
        }

    /**
     * Removes a cron job by its identifier.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @param id Unique identifier of the job to remove.
     * @throws FfiException if the job does not exist or the native layer fails.
     */
    @Throws(FfiException::class)
    suspend fun removeJob(id: String) {
        withContext(ioDispatcher) {
            com.zeroclaw.ffi.removeCronJob(id)
        }
    }

    /**
     * Pauses a cron job so it will not fire until resumed.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @param id Unique identifier of the job to pause.
     * @throws FfiException if the job does not exist or the native layer fails.
     */
    @Throws(FfiException::class)
    suspend fun pauseJob(id: String) {
        withContext(ioDispatcher) {
            com.zeroclaw.ffi.pauseCronJob(id)
        }
    }

    /**
     * Resumes a previously paused cron job.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @param id Unique identifier of the job to resume.
     * @throws FfiException if the job does not exist or the native layer fails.
     */
    @Throws(FfiException::class)
    suspend fun resumeJob(id: String) {
        withContext(ioDispatcher) {
            com.zeroclaw.ffi.resumeCronJob(id)
        }
    }
}

/**
 * Converts an FFI cron job record to the domain model.
 *
 * @receiver FFI-generated [FfiCronJob] record from the native layer.
 * @return Domain [CronJob] model with identical field values.
 */
private fun FfiCronJob.toModel(): CronJob =
    CronJob(
        id = id,
        expression = expression,
        command = command,
        nextRunMs = nextRunMs,
        lastRunMs = lastRunMs,
        lastStatus = lastStatus,
        paused = paused,
        oneShot = oneShot,
    )
