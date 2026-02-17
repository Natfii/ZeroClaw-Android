/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import android.content.Context
import android.content.SharedPreferences
import com.zeroclaw.android.data.SecurePrefsProvider

/**
 * Factory for creating [SharedPreferences] instances.
 *
 * Abstracted to allow unit tests to substitute a plain in-memory
 * implementation instead of requiring the Android Keystore.
 */
fun interface SharedPreferencesFactory {
    /**
     * Creates or retrieves a [SharedPreferences] instance.
     *
     * @param context Application or service context.
     * @param name Preferences file name.
     * @return A [SharedPreferences] instance.
     */
    fun create(
        context: Context,
        name: String,
    ): SharedPreferences
}

/**
 * Manages persistent state for daemon lifecycle recovery.
 *
 * Non-sensitive state (the "was running" flag, host, and port) is stored
 * in plain [SharedPreferences][android.content.SharedPreferences]. Sensitive
 * data (the TOML configuration, which may contain API keys) is stored in
 * encrypted preferences via [SecurePrefsProvider].
 *
 * Uses the same plain preferences file ([BootReceiver.PREFS_NAME]) as the
 * boot receiver to keep all service-related non-sensitive preferences in
 * one place.
 *
 * @param context Application or service context.
 * @param prefsFactory Factory for creating [SharedPreferences] instances.
 *   Defaults to [EncryptedPrefsFactory] for production use.
 */
class DaemonPersistence(
    context: Context,
    prefsFactory: SharedPreferencesFactory = EncryptedPrefsFactory,
) {
    private val plainPrefs =
        context.getSharedPreferences(
            BootReceiver.PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    private val securePrefs = prefsFactory.create(context, SECURE_PREFS_NAME)

    /**
     * Records that the daemon was successfully started with the given config.
     *
     * Persists the configuration so that [restoreConfiguration] can return
     * it after a process restart. The TOML configuration is stored in
     * encrypted preferences; host and port go to plain preferences.
     *
     * @param configToml TOML configuration string passed to the FFI layer.
     * @param host Gateway bind address (e.g. "127.0.0.1").
     * @param port Gateway bind port.
     */
    fun recordRunning(
        configToml: String,
        host: String,
        port: UShort,
    ) {
        plainPrefs
            .edit()
            .putBoolean(KEY_WAS_RUNNING, true)
            .putString(KEY_HOST, host)
            .putInt(KEY_PORT, port.toInt())
            .apply()
        securePrefs
            .edit()
            .putString(KEY_CONFIG_TOML, configToml)
            .apply()
    }

    /**
     * Records that the daemon was explicitly stopped by the user.
     *
     * Clears the "was running" flag so that [restoreConfiguration] returns
     * null on the next process start, preventing unwanted auto-restarts.
     */
    fun recordStopped() {
        plainPrefs
            .edit()
            .putBoolean(KEY_WAS_RUNNING, false)
            .remove(KEY_HOST)
            .remove(KEY_PORT)
            .apply()
        securePrefs
            .edit()
            .remove(KEY_CONFIG_TOML)
            .apply()
    }

    /**
     * Returns the saved daemon configuration if the daemon was running
     * when the process last died.
     *
     * @return The saved [DaemonConfiguration], or null if the daemon was
     *   explicitly stopped or no configuration has been saved.
     */
    fun restoreConfiguration(): DaemonConfiguration? {
        if (!plainPrefs.getBoolean(KEY_WAS_RUNNING, false)) return null
        val configToml = securePrefs.getString(KEY_CONFIG_TOML, null) ?: return null
        val host = plainPrefs.getString(KEY_HOST, null) ?: return null
        val port = plainPrefs.getInt(KEY_PORT, -1)
        if (port < 0) return null
        return DaemonConfiguration(
            configToml = configToml,
            host = host,
            port = port.toUShort(),
        )
    }

    /**
     * Saved daemon startup parameters for recovery after process death.
     *
     * @property configToml TOML configuration string.
     * @property host Gateway bind address.
     * @property port Gateway bind port.
     */
    data class DaemonConfiguration(
        val configToml: String,
        val host: String,
        val port: UShort,
    )

    /** Constants for [DaemonPersistence]. */
    companion object {
        private const val SECURE_PREFS_NAME = "zeroclaw_secure_prefs"
        private const val KEY_WAS_RUNNING = "was_running"
        private const val KEY_CONFIG_TOML = "config_toml"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"

        /**
         * Default [SharedPreferencesFactory] that creates encrypted preferences
         * via [SecurePrefsProvider] with StrongBox support and corruption recovery.
         */
        val EncryptedPrefsFactory =
            SharedPreferencesFactory { context, name ->
                SecurePrefsProvider.create(context, name).first
            }
    }
}
