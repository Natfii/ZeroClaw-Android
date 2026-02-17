/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    }
}
