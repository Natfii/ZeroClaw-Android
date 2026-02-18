// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.model

/**
 * A scheduled cron job managed by the daemon's cron scheduler.
 *
 * Maps to the Rust `FfiCronJob` record transferred across the FFI boundary.
 * Timestamps are represented as epoch milliseconds for direct use with
 * Kotlin's standard time APIs.
 *
 * @property id Unique identifier for this job.
 * @property expression Cron expression (e.g. `"0 *&#47;5 * * *"`) or one-shot delay marker.
 * @property command Command string that the scheduler will execute.
 * @property nextRunMs Epoch milliseconds of the next scheduled run.
 * @property lastRunMs Epoch milliseconds of the last completed run, or null if never run.
 * @property lastStatus Status string from the last run (e.g. `"ok"`, `"error: ..."`), or null.
 * @property paused Whether this job is currently paused.
 * @property oneShot Whether this is a one-shot job that fires once then self-removes.
 */
data class CronJob(
    val id: String,
    val expression: String,
    val command: String,
    val nextRunMs: Long,
    val lastRunMs: Long?,
    val lastStatus: String?,
    val paused: Boolean,
    val oneShot: Boolean,
)
