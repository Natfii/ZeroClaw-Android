/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
 * On devices with a hardware security module (StrongBox), the master key
 * is hardware-backed.
 */
object DatabasePassphrase {
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
     * @param context Application context for [EncryptedSharedPreferences].
     * @return 64-character hex passphrase string.
     */
    fun getOrCreate(context: Context): String {
        val masterKey =
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

        val prefs =
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        val existing = prefs.getString(KEY_PASSPHRASE, null)
        if (existing != null) {
            return existing
        }

        val seed = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(seed)
        val hex = seed.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_PASSPHRASE, hex).apply()
        return hex
    }
}
