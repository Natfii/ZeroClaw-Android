/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.zeroclaw.android.data.SecurePrefsProvider
import com.zeroclaw.android.data.StorageHealth
import com.zeroclaw.android.data.local.ZeroClawDatabase
import com.zeroclaw.android.data.repository.ActivityRepository
import com.zeroclaw.android.data.repository.AgentRepository
import com.zeroclaw.android.data.repository.ApiKeyRepository
import com.zeroclaw.android.data.repository.ChannelConfigRepository
import com.zeroclaw.android.data.repository.ChatMessageRepository
import com.zeroclaw.android.data.repository.DataStoreOnboardingRepository
import com.zeroclaw.android.data.repository.DataStoreSettingsRepository
import com.zeroclaw.android.data.repository.EncryptedApiKeyRepository
import com.zeroclaw.android.data.repository.InMemoryApiKeyRepository
import com.zeroclaw.android.data.repository.LogRepository
import com.zeroclaw.android.data.repository.OnboardingRepository
import com.zeroclaw.android.data.repository.PluginRepository
import com.zeroclaw.android.data.repository.RoomActivityRepository
import com.zeroclaw.android.data.repository.RoomAgentRepository
import com.zeroclaw.android.data.repository.RoomChannelConfigRepository
import com.zeroclaw.android.data.repository.RoomChatMessageRepository
import com.zeroclaw.android.data.repository.RoomLogRepository
import com.zeroclaw.android.data.repository.RoomPluginRepository
import com.zeroclaw.android.data.repository.SettingsRepository
import com.zeroclaw.android.service.CostBridge
import com.zeroclaw.android.service.CronBridge
import com.zeroclaw.android.service.DaemonServiceBridge
import com.zeroclaw.android.service.EventBridge
import com.zeroclaw.android.service.HealthBridge
import com.zeroclaw.android.service.MemoryBridge
import com.zeroclaw.android.service.PluginSyncWorker
import com.zeroclaw.android.service.SkillsBridge
import com.zeroclaw.android.service.ToolsBridge
import com.zeroclaw.android.service.VisionBridge
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
class ZeroClawApplication :
    Application(),
    SingletonImageLoader.Factory {
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

    /** Channel configuration repository backed by Room + EncryptedSharedPreferences. */
    lateinit var channelConfigRepository: ChannelConfigRepository
        private set

    /** Daemon console chat message repository backed by Room. */
    lateinit var chatMessageRepository: ChatMessageRepository
        private set

    /** Bridge for structured health detail FFI calls. */
    lateinit var healthBridge: HealthBridge
        private set

    /** Bridge for cost-tracking FFI calls. */
    lateinit var costBridge: CostBridge
        private set

    /** Bridge for daemon event callbacks from the native layer. */
    lateinit var eventBridge: EventBridge
        private set

    /** Bridge for cron job CRUD FFI calls. */
    lateinit var cronBridge: CronBridge
        private set

    /** Bridge for skills browsing and management FFI calls. */
    lateinit var skillsBridge: SkillsBridge
        private set

    /** Bridge for tools inventory browsing FFI calls. */
    lateinit var toolsBridge: ToolsBridge
        private set

    /** Bridge for memory browsing and management FFI calls. */
    lateinit var memoryBridge: MemoryBridge
        private set

    /** Bridge for direct-to-provider multimodal vision API calls. */
    val visionBridge: VisionBridge by lazy { VisionBridge() }

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("zeroclaw")

        @Suppress("InjectDispatcher")
        val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        val ioScope = CoroutineScope(SupervisorJob() + ioDispatcher)

        daemonBridge = DaemonServiceBridge(filesDir.absolutePath)
        database = ZeroClawDatabase.build(this, ioScope)
        settingsRepository = DataStoreSettingsRepository(this)
        apiKeyRepository = createApiKeyRepository(ioScope)
        logRepository = RoomLogRepository(database.logEntryDao(), ioScope)
        activityRepository = RoomActivityRepository(database.activityEventDao(), ioScope)
        onboardingRepository = DataStoreOnboardingRepository(this)
        agentRepository = RoomAgentRepository(database.agentDao())
        pluginRepository = RoomPluginRepository(database.pluginDao())
        channelConfigRepository = createChannelConfigRepository()
        chatMessageRepository = RoomChatMessageRepository(database.chatMessageDao(), ioScope)
        healthBridge = HealthBridge()
        costBridge = CostBridge()
        cronBridge = CronBridge()
        skillsBridge = SkillsBridge()
        toolsBridge = ToolsBridge()
        memoryBridge = MemoryBridge()
        eventBridge = EventBridge(activityRepository, ioScope)
        daemonBridge.eventBridge = eventBridge

        schedulePluginSyncIfEnabled(ioScope)
    }

    /**
     * Observes the plugin sync setting and schedules/cancels the
     * periodic sync worker accordingly.
     *
     * @param scope Background scope for observing settings.
     */
    private fun schedulePluginSyncIfEnabled(scope: CoroutineScope) {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                val workManager = WorkManager.getInstance(this@ZeroClawApplication)
                if (settings.pluginSyncEnabled) {
                    val constraints =
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    val request =
                        PeriodicWorkRequestBuilder<PluginSyncWorker>(
                            settings.pluginSyncIntervalHours.toLong(),
                            TimeUnit.HOURS,
                        ).setConstraints(constraints)
                            .build()
                    workManager.enqueueUniquePeriodicWork(
                        PluginSyncWorker.WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request,
                    )
                } else {
                    workManager.cancelUniqueWork(PluginSyncWorker.WORK_NAME)
                }
            }
        }
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

    /**
     * Creates the channel configuration repository with encrypted secret storage.
     *
     * Uses a separate EncryptedSharedPreferences file (`zeroclaw_channel_secrets`)
     * from the API key storage to isolate channel secrets.
     *
     * @return A [ChannelConfigRepository] instance.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun createChannelConfigRepository(): ChannelConfigRepository {
        val (prefs, health) = SecurePrefsProvider.create(this, CHANNEL_SECRETS_PREFS)
        when (health) {
            is StorageHealth.Healthy ->
                Log.i(TAG, "Channel secret storage: healthy")
            is StorageHealth.Recovered ->
                Log.w(TAG, "Channel secret storage: recovered from corruption")
            is StorageHealth.Degraded ->
                Log.w(TAG, "Channel secret storage: degraded (in-memory only)")
        }
        return RoomChannelConfigRepository(database.connectedChannelDao(), prefs)
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader
            .Builder(context)
            .crossfade(true)
            .memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(context, MEMORY_CACHE_PERCENT)
                    .build()
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                    .build()
            }.build()

    /** Constants for [ZeroClawApplication]. */
    companion object {
        private const val TAG = "ZeroClawApp"
        private const val CHANNEL_SECRETS_PREFS = "zeroclaw_channel_secrets"
        private const val MEMORY_CACHE_PERCENT = 0.15
        private const val DISK_CACHE_MAX_BYTES = 64L * 1024 * 1024
    }
}
