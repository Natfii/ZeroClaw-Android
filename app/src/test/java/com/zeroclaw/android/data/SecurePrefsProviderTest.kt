/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data

import android.content.SharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [MapSharedPreferences], the in-memory fallback used
 * when the Android Keystore is completely unusable.
 *
 * [SecurePrefsProvider] itself requires Android Keystore APIs and is
 * tested via instrumented tests. These tests verify the fallback
 * [SharedPreferences] implementation that runs in the [StorageHealth.Degraded] path.
 */
@DisplayName("MapSharedPreferences")
class SecurePrefsProviderTest {
    @Test
    @DisplayName("put and get string round-trips")
    fun `put and get string round-trips`() {
        val prefs = MapSharedPreferences()
        prefs.edit().putString("key", "value").apply()
        assertEquals("value", prefs.getString("key", null))
    }

    @Test
    @DisplayName("put and get boolean round-trips")
    fun `put and get boolean round-trips`() {
        val prefs = MapSharedPreferences()
        prefs.edit().putBoolean("flag", true).apply()
        assertTrue(prefs.getBoolean("flag", false))
    }

    @Test
    @DisplayName("put and get int round-trips")
    fun `put and get int round-trips`() {
        val prefs = MapSharedPreferences()
        prefs.edit().putInt("count", 42).apply()
        assertEquals(42, prefs.getInt("count", 0))
    }

    @Test
    @DisplayName("put and get long round-trips")
    fun `put and get long round-trips`() {
        val prefs = MapSharedPreferences()
        prefs.edit().putLong("time", 123456789L).apply()
        assertEquals(123456789L, prefs.getLong("time", 0L))
    }

    @Test
    @DisplayName("remove deletes key")
    fun `remove deletes key`() {
        val prefs = MapSharedPreferences()
        prefs.edit().putString("key", "value").apply()
        prefs.edit().remove("key").apply()
        assertEquals("default", prefs.getString("key", "default"))
    }

    @Test
    @DisplayName("clear removes all entries")
    fun `clear removes all entries`() {
        val prefs = MapSharedPreferences()
        prefs.edit().putString("a", "1").apply()
        prefs.edit().putString("b", "2").apply()
        prefs.edit().clear().apply()
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    @DisplayName("commit returns true")
    fun `commit returns true`() {
        val prefs = MapSharedPreferences()
        val result = prefs.edit().putString("key", "value").commit()
        assertTrue(result)
        assertEquals("value", prefs.getString("key", null))
    }

    @Test
    @DisplayName("getAll returns snapshot of data")
    fun `getAll returns snapshot of data`() {
        val prefs = MapSharedPreferences()
        prefs.edit().putString("a", "1").apply()
        prefs.edit().putInt("b", 2).apply()
        val all = prefs.all
        assertEquals(2, all.size)
        assertEquals("1", all["a"])
        assertEquals(2, all["b"])
    }

    @Test
    @DisplayName("contains returns true for existing keys")
    fun `contains returns true for existing keys`() {
        val prefs = MapSharedPreferences()
        prefs.edit().putString("exists", "yes").apply()
        assertTrue(prefs.contains("exists"))
        assertTrue(!prefs.contains("missing"))
    }

    @Test
    @DisplayName("listener is notified on change")
    fun `listener is notified on change`() {
        val prefs = MapSharedPreferences()
        val changed = mutableListOf<String?>()
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                changed.add(key)
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        prefs.edit().putString("test", "value").apply()
        assertEquals(listOf("test"), changed)
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        prefs.edit().putString("test2", "value2").apply()
        assertEquals(1, changed.size)
    }
}
