/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom

/**
 * Provides the SQLCipher passphrase for the Room database.
 *
 * On first access, a random 32-byte seed is generated, hex-encoded to a
 * 64-character printable string, and stored in [EncryptedSharedPreferences]
 * backed by an AES-256 [MasterKey]. The hex string itself is the passphrase
 * (not the decoded raw bytes). This guarantees the passphrase is safe for
 * SQL string literals (no null bytes) while remaining cryptographically
 * strong via PBKDF2 key derivation inside SQLCipher.
 *
 * Includes corruption recovery: if the encrypted preferences file is
 * corrupted, the file is deleted and recreated. This means the old
 * passphrase is lost and the existing database will be unreadable,
 * but Room's [fallbackToDestructiveMigration] will recreate it cleanly
 * rather than entering a crash loop.
 *
 * On devices with a hardware security module (StrongBox), the master key
 * is hardware-backed.
 */
object DatabasePassphrase {
    private const val TAG = "DatabasePassphrase"
    private const val PREFS_NAME = "zeroclaw_db_passphrase"
    private const val KEY_PASSPHRASE = "db_passphrase"
    private const val PASSPHRASE_BYTES = 32

    /**
     * Returns the database passphrase as a printable hex string.
     *
     * The string is used directly as the SQLCipher passphrase (passed to
     * PBKDF2 inside SQLCipher). Callers that need a [ByteArray] for
     * [net.zetetic.database.sqlcipher.SupportOpenHelperFactory] should
     * convert via [String.toByteArray] with [Charsets.UTF_8].
     *
     * If the encrypted preferences file is corrupted, it is deleted and
     * recreated with a fresh passphrase. The existing database will become
     * unreadable and Room will recreate it via destructive migration.
     *
     * @param context Application context for [EncryptedSharedPreferences].
     * @return 64-character hex passphrase string.
     */
    fun getOrCreate(context: Context): String {
        val masterKey = createMasterKey(context)
        val prefs = openPrefsWithRecovery(context, masterKey)
        return readOrGenerate(prefs)
    }

    private fun createMasterKey(context: Context): MasterKey =
        try {
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setRequestStrongBoxBacked(true)
                .build()
        } catch (
            @Suppress("SwallowedException") e: StrongBoxUnavailableException,
        ) {
            Log.i(TAG, "StrongBox unavailable, using software-backed key")
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }

    private fun openPrefsWithRecovery(
        context: Context,
        masterKey: MasterKey,
    ): SharedPreferences =
        try {
            createEncryptedPrefs(context, masterKey)
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "Passphrase prefs corrupted, recovering: ${e.message}", e)
            recoverPrefs(context, masterKey)
        } catch (e: IOException) {
            Log.w(TAG, "Passphrase prefs IO failure, recovering: ${e.message}", e)
            recoverPrefs(context, masterKey)
        }

    private fun recoverPrefs(
        context: Context,
        masterKey: MasterKey,
    ): SharedPreferences {
        context.deleteSharedPreferences(PREFS_NAME)
        Log.w(TAG, "Deleted corrupted passphrase prefs; database will be recreated")
        return createEncryptedPrefs(context, masterKey)
    }

    private fun createEncryptedPrefs(
        context: Context,
        masterKey: MasterKey,
    ): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private fun readOrGenerate(prefs: SharedPreferences): String {
        val existing = prefs.getString(KEY_PASSPHRASE, null)
        if (existing != null) {
            return existing
        }

        val seed = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(seed)
        val hex = seed.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_PASSPHRASE, hex).commit()
        return hex
    }
}
