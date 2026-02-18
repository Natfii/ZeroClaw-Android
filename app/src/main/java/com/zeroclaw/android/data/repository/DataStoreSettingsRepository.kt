/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.model.LogLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Extension property providing the singleton [DataStore] for app settings. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings",
)

/**
 * [SettingsRepository] implementation backed by Jetpack DataStore Preferences.
 *
 * Each setting is stored as a separate preference key. The [settings] flow
 * emits a new [AppSettings] whenever any key changes.
 *
 * @param context Application context for DataStore initialization.
 */
class DataStoreSettingsRepository(
    private val context: Context,
) : SettingsRepository {
    override val settings: Flow<AppSettings> =
        context.settingsDataStore.data.map { prefs ->
            AppSettings(
                host = prefs[KEY_HOST] ?: AppSettings.DEFAULT_HOST,
                port = prefs[KEY_PORT] ?: AppSettings.DEFAULT_PORT,
                autoStartOnBoot = prefs[KEY_AUTO_START] ?: false,
                logLevel =
                    prefs[KEY_LOG_LEVEL]?.let { name ->
                        runCatching { LogLevel.valueOf(name) }.getOrNull()
                    } ?: LogLevel.INFO,
                defaultProvider = prefs[KEY_DEFAULT_PROVIDER] ?: "",
                defaultModel = prefs[KEY_DEFAULT_MODEL] ?: "",
                defaultTemperature = prefs[KEY_DEFAULT_TEMPERATURE]
                    ?: AppSettings.DEFAULT_TEMPERATURE,
                compactContext = prefs[KEY_COMPACT_CONTEXT] ?: false,
                costEnabled = prefs[KEY_COST_ENABLED] ?: false,
                dailyLimitUsd = prefs[KEY_DAILY_LIMIT_USD]
                    ?: AppSettings.DEFAULT_DAILY_LIMIT_USD,
                monthlyLimitUsd = prefs[KEY_MONTHLY_LIMIT_USD]
                    ?: AppSettings.DEFAULT_MONTHLY_LIMIT_USD,
                costWarnAtPercent = prefs[KEY_COST_WARN_PERCENT]
                    ?: AppSettings.DEFAULT_COST_WARN_PERCENT,
                providerRetries = prefs[KEY_PROVIDER_RETRIES]
                    ?: AppSettings.DEFAULT_PROVIDER_RETRIES,
                fallbackProviders = prefs[KEY_FALLBACK_PROVIDERS] ?: "",
                memoryBackend = prefs[KEY_MEMORY_BACKEND]
                    ?: AppSettings.DEFAULT_MEMORY_BACKEND,
                memoryAutoSave = prefs[KEY_MEMORY_AUTO_SAVE] ?: true,
                identityJson = prefs[KEY_IDENTITY_JSON] ?: "",
            )
        }

    override suspend fun setHost(host: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_HOST] = host
        }
    }

    override suspend fun setPort(port: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_PORT] = port
        }
    }

    override suspend fun setAutoStartOnBoot(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_AUTO_START] = enabled
        }
    }

    override suspend fun setLogLevel(level: LogLevel) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_LOG_LEVEL] = level.name
        }
    }

    override suspend fun setDefaultProvider(provider: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DEFAULT_PROVIDER] = provider
        }
    }

    override suspend fun setDefaultModel(model: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DEFAULT_MODEL] = model
        }
    }

    override suspend fun setDefaultTemperature(temperature: Float) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DEFAULT_TEMPERATURE] = temperature
        }
    }

    override suspend fun setCompactContext(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_COMPACT_CONTEXT] = enabled
        }
    }

    override suspend fun setCostEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_COST_ENABLED] = enabled
        }
    }

    override suspend fun setDailyLimitUsd(limit: Float) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DAILY_LIMIT_USD] = limit
        }
    }

    override suspend fun setMonthlyLimitUsd(limit: Float) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_MONTHLY_LIMIT_USD] = limit
        }
    }

    override suspend fun setCostWarnAtPercent(percent: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_COST_WARN_PERCENT] = percent
        }
    }

    override suspend fun setProviderRetries(retries: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_PROVIDER_RETRIES] = retries
        }
    }

    override suspend fun setFallbackProviders(providers: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_FALLBACK_PROVIDERS] = providers
        }
    }

    override suspend fun setMemoryBackend(backend: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_MEMORY_BACKEND] = backend
        }
    }

    override suspend fun setMemoryAutoSave(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_MEMORY_AUTO_SAVE] = enabled
        }
    }

    override suspend fun setIdentityJson(json: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_IDENTITY_JSON] = json
        }
    }

    /** DataStore preference keys for [DataStoreSettingsRepository]. */
    companion object {
        /** Preference key for the gateway host address. */
        val KEY_HOST = stringPreferencesKey("host")

        /** Preference key for the gateway port. */
        val KEY_PORT = intPreferencesKey("port")

        /** Preference key for auto-start on boot. */
        val KEY_AUTO_START = booleanPreferencesKey("auto_start_on_boot")

        /** Preference key for the minimum log level. */
        val KEY_LOG_LEVEL = stringPreferencesKey("log_level")

        /** Preference key for the default provider ID. */
        val KEY_DEFAULT_PROVIDER = stringPreferencesKey("default_provider")

        /** Preference key for the default model name. */
        val KEY_DEFAULT_MODEL = stringPreferencesKey("default_model")

        /** Preference key for the default inference temperature. */
        val KEY_DEFAULT_TEMPERATURE = floatPreferencesKey("default_temperature")

        /** Preference key for compact context mode. */
        val KEY_COMPACT_CONTEXT = booleanPreferencesKey("compact_context")

        /** Preference key for cost limit enforcement. */
        val KEY_COST_ENABLED = booleanPreferencesKey("cost_enabled")

        /** Preference key for daily cost limit. */
        val KEY_DAILY_LIMIT_USD = floatPreferencesKey("daily_limit_usd")

        /** Preference key for monthly cost limit. */
        val KEY_MONTHLY_LIMIT_USD = floatPreferencesKey("monthly_limit_usd")

        /** Preference key for cost warning threshold. */
        val KEY_COST_WARN_PERCENT = intPreferencesKey("cost_warn_at_percent")

        /** Preference key for provider retries. */
        val KEY_PROVIDER_RETRIES = intPreferencesKey("provider_retries")

        /** Preference key for fallback providers. */
        val KEY_FALLBACK_PROVIDERS = stringPreferencesKey("fallback_providers")

        /** Preference key for memory backend. */
        val KEY_MEMORY_BACKEND = stringPreferencesKey("memory_backend")

        /** Preference key for memory auto-save. */
        val KEY_MEMORY_AUTO_SAVE = booleanPreferencesKey("memory_auto_save")

        /** Preference key for AIEOS identity JSON. */
        val KEY_IDENTITY_JSON = stringPreferencesKey("identity_json")
    }
}
