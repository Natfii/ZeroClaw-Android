/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DaemonPersistence].
 *
 * Uses a mock [SharedPreferences] via a plain [SharedPreferencesFactory]
 * to verify that daemon configuration is correctly persisted and restored
 * without requiring the Android Keystore.
 */
@DisplayName("DaemonPersistence")
class DaemonPersistenceTest {
    private lateinit var plainPrefs: SharedPreferences
    private lateinit var securePrefs: SharedPreferences
    private lateinit var plainEditor: SharedPreferences.Editor
    private lateinit var secureEditor: SharedPreferences.Editor
    private lateinit var context: Context
    private val plainStore = mutableMapOf<String, Any?>()
    private val secureStore = mutableMapOf<String, Any?>()

    @BeforeEach
    fun setUp() {
        plainStore.clear()
        secureStore.clear()
        plainEditor = buildMockEditor(plainStore)
        secureEditor = buildMockEditor(secureStore)
        plainPrefs = buildMockPrefs(plainEditor, plainStore)
        securePrefs = buildMockPrefs(secureEditor, secureStore)
        context =
            mockk {
                every {
                    getSharedPreferences(any(), any())
                } returns plainPrefs
            }
    }

    private fun buildMockEditor(store: MutableMap<String, Any?>): SharedPreferences.Editor =
        mockk(relaxed = true) {
            every { putBoolean(any(), any()) } answers {
                store[firstArg()] = secondArg<Boolean>()
                this@mockk
            }
            every { putString(any(), any()) } answers {
                store[firstArg()] = secondArg<String?>()
                this@mockk
            }
            every { putInt(any(), any()) } answers {
                store[firstArg()] = secondArg<Int>()
                this@mockk
            }
            every { remove(any()) } answers {
                store.remove(firstArg())
                this@mockk
            }
        }

    private fun buildMockPrefs(
        editor: SharedPreferences.Editor,
        store: MutableMap<String, Any?>,
    ): SharedPreferences =
        mockk {
            every { edit() } returns editor
            every { getBoolean(any(), any()) } answers {
                store[firstArg()] as? Boolean ?: secondArg()
            }
            every { getString(any(), any()) } answers {
                store[firstArg()] as? String ?: secondArg()
            }
            every { getInt(any(), any()) } answers {
                store[firstArg()] as? Int ?: secondArg()
            }
        }

    /**
     * Creates a [DaemonPersistence] instance with a test factory that
     * returns [securePrefs] instead of real [EncryptedSharedPreferences].
     */
    private fun createPersistence(): DaemonPersistence {
        val testFactory = SharedPreferencesFactory { _, _ -> securePrefs }
        return DaemonPersistence(context, testFactory)
    }

    @Test
    @DisplayName("recordRunning saves and restoreConfiguration returns config")
    fun `recordRunning saves and restoreConfiguration returns config`() {
        val persistence = createPersistence()
        persistence.recordRunning("config_data", "127.0.0.1", 8080u)

        val restored = persistence.restoreConfiguration()
        assertEquals("config_data", restored?.configToml)
        assertEquals("127.0.0.1", restored?.host)
        assertEquals(8080.toUShort(), restored?.port)
    }

    @Test
    @DisplayName("recordStopped clears so restoreConfiguration returns null")
    fun `recordStopped clears so restoreConfiguration returns null`() {
        val persistence = createPersistence()
        persistence.recordRunning("config_data", "127.0.0.1", 8080u)
        persistence.recordStopped()

        assertNull(persistence.restoreConfiguration())
    }

    @Test
    @DisplayName("restoreConfiguration returns null when nothing saved")
    fun `restoreConfiguration returns null when nothing saved`() {
        val persistence = createPersistence()
        assertNull(persistence.restoreConfiguration())
    }

    @Test
    @DisplayName("restoreConfiguration returns null when was_running is false")
    fun `restoreConfiguration returns null when was_running is false`() {
        val persistence = createPersistence()
        plainStore["was_running"] = false
        secureStore["config_toml"] = "some_config"
        plainStore["host"] = "127.0.0.1"
        plainStore["port"] = 8080

        assertNull(persistence.restoreConfiguration())
    }

    @Test
    @DisplayName("config_toml is stored in secure prefs not plain prefs")
    fun `config_toml is stored in secure prefs not plain prefs`() {
        val persistence = createPersistence()
        persistence.recordRunning("secret_config", "localhost", 9090u)

        assertEquals("secret_config", secureStore["config_toml"])
        assertNull(plainStore["config_toml"])
    }

    @Test
    @DisplayName("host and port are stored in plain prefs not secure prefs")
    fun `host and port are stored in plain prefs not secure prefs`() {
        val persistence = createPersistence()
        persistence.recordRunning("config", "192.168.1.1", 3000u)

        assertEquals("192.168.1.1", plainStore["host"])
        assertEquals(3000, plainStore["port"])
        assertNull(secureStore["host"])
        assertNull(secureStore["port"])
    }
}
