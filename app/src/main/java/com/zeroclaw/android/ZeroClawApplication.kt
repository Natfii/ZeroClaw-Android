/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android

import android.app.Application
import android.util.Log
import com.zeroclaw.android.data.StorageHealth
import com.zeroclaw.android.data.local.ZeroClawDatabase
import com.zeroclaw.android.data.repository.ActivityRepository
import com.zeroclaw.android.data.repository.AgentRepository
import com.zeroclaw.android.data.repository.ApiKeyRepository
import com.zeroclaw.android.data.repository.DataStoreOnboardingRepository
import com.zeroclaw.android.data.repository.DataStoreSettingsRepository
import com.zeroclaw.android.data.repository.EncryptedApiKeyRepository
import com.zeroclaw.android.data.repository.InMemoryApiKeyRepository
import com.zeroclaw.android.data.repository.LogRepository
import com.zeroclaw.android.data.repository.OnboardingRepository
import com.zeroclaw.android.data.repository.PluginRepository
import com.zeroclaw.android.data.repository.RoomActivityRepository
import com.zeroclaw.android.data.repository.RoomAgentRepository
import com.zeroclaw.android.data.repository.RoomLogRepository
import com.zeroclaw.android.data.repository.RoomPluginRepository
import com.zeroclaw.android.data.repository.SettingsRepository
import com.zeroclaw.android.service.DaemonServiceBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application subclass that initialises the native ZeroClaw library and
 * shared service components.
 *
 * The native library is loaded once during process creation so that every
 * component can call FFI functions without additional setup. Shared
 * singletons are created here and available for the lifetime of the process.
 *
 * Persistent data is stored in a Room database ([ZeroClawDatabase]) that
 * survives process restarts. Settings and API keys remain in DataStore
 * and EncryptedSharedPreferences respectively.
 */
class ZeroClawApplication : Application() {
    /**
     * Shared bridge between the Android service layer and the Rust FFI.
     *
     * Initialised in [onCreate] and available for the lifetime of the process.
     * Access from [ZeroClawDaemonService][com.zeroclaw.android.service.ZeroClawDaemonService]
     * and [DaemonViewModel][com.zeroclaw.android.viewmodel.DaemonViewModel].
     */
    lateinit var daemonBridge: DaemonServiceBridge
        private set

    /** Room database instance for agents, plugins, logs, and activity events. */
    lateinit var database: ZeroClawDatabase
        private set

    /** Application settings repository backed by Jetpack DataStore. */
    lateinit var settingsRepository: SettingsRepository
        private set

    /** API key repository backed by EncryptedSharedPreferences. */
    lateinit var apiKeyRepository: ApiKeyRepository
        private set

    /** Log repository backed by Room with automatic pruning. */
    lateinit var logRepository: LogRepository
        private set

    /** Activity feed repository backed by Room with automatic pruning. */
    lateinit var activityRepository: ActivityRepository
        private set

    /** Onboarding state repository backed by Jetpack DataStore. */
    lateinit var onboardingRepository: OnboardingRepository
        private set

    /** Agent repository backed by Room. */
    lateinit var agentRepository: AgentRepository
        private set

    /** Plugin repository backed by Room. */
    lateinit var pluginRepository: PluginRepository
        private set

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("zeroclaw")

        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        daemonBridge = DaemonServiceBridge(filesDir.absolutePath)
        database = ZeroClawDatabase.build(this, ioScope)
        settingsRepository = DataStoreSettingsRepository(this)
        apiKeyRepository = createApiKeyRepository(ioScope)
        logRepository = RoomLogRepository(database.logEntryDao(), ioScope)
        activityRepository = RoomActivityRepository(database.activityEventDao(), ioScope)
        onboardingRepository = DataStoreOnboardingRepository(this)
        agentRepository = RoomAgentRepository(database.agentDao())
        pluginRepository = RoomPluginRepository(database.pluginDao())
    }

    /**
     * Creates the API key repository with a safety net around keystore access.
     *
     * If [EncryptedApiKeyRepository] construction itself throws (e.g. due to
     * a completely broken keystore), falls back to an [InMemoryApiKeyRepository]
     * so the app can still launch. The initial key load is deferred to
     * [ioScope] to avoid blocking Application.onCreate on slow keystore
     * operations.
     *
     * @param ioScope Background scope for deferred key loading.
     * @return An [ApiKeyRepository] instance.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun createApiKeyRepository(ioScope: CoroutineScope): ApiKeyRepository =
        try {
            val repo = EncryptedApiKeyRepository(context = this, ioScope = ioScope)
            when (repo.storageHealth) {
                is StorageHealth.Healthy ->
                    Log.i(TAG, "API key storage: healthy")
                is StorageHealth.Recovered ->
                    Log.w(TAG, "API key storage: recovered from corruption (keys lost)")
                is StorageHealth.Degraded ->
                    Log.w(TAG, "API key storage: degraded (in-memory only)")
            }
            repo
        } catch (e: Exception) {
            Log.e(TAG, "API key storage init failed, using in-memory fallback", e)
            InMemoryApiKeyRepository()
        }

    /** Constants for [ZeroClawApplication]. */
    companion object {
        private const val TAG = "ZeroClawApp"
    }
}
