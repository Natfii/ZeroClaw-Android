/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.data.remote.OkHttpPluginRegistryClient
import kotlinx.coroutines.flow.first

/**
 * Periodic [CoroutineWorker] that synchronises the local plugin database
 * with the remote plugin registry.
 *
 * Reads the registry URL from [AppSettings], fetches remote metadata via
 * [OkHttpPluginRegistryClient], and merges it into the local database.
 * Updates the `lastPluginSyncTimestamp` setting on success.
 *
 * Returns [Result.retry] on transient network failures and [Result.failure]
 * on permanent errors (e.g. malformed JSON).
 *
 * @param context Application context.
 * @param params Worker parameters including constraints and input data.
 */
class PluginSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        val app =
            applicationContext as? ZeroClawApplication
                ?: return Result.failure()

        val settings = app.settingsRepository.settings.first()
        val registryUrl = settings.pluginRegistryUrl

        return try {
            val client = OkHttpPluginRegistryClient()
            val remotePlugins = client.fetchPlugins(registryUrl)
            app.pluginRepository.mergeRemotePlugins(remotePlugins)
            app.settingsRepository.setLastPluginSyncTimestamp(System.currentTimeMillis())
            Log.i(TAG, "Plugin sync complete: ${remotePlugins.size} plugins")
            Result.success()
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Plugin sync transient failure, will retry", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Plugin sync permanent failure", e)
            Result.failure()
        }
    }

    /** Constants for [PluginSyncWorker]. */
    companion object {
        private const val TAG = "PluginSync"

        /** Unique work name for the periodic sync job. */
        const val WORK_NAME = "plugin_registry_sync"
    }
}
