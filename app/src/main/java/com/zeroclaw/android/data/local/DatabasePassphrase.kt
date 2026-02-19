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
 * On first access, a random 32-byte passphrase is generated and stored
 * in [EncryptedSharedPreferences] backed by an AES-256 [MasterKey].
 * Subsequent calls return the same passphrase. On devices with a hardware
 * security module (StrongBox), the master key is hardware-backed.
 *
 * The passphrase is stored as a hex-encoded string to survive
 * SharedPreferences serialisation without encoding issues.
 */
object DatabasePassphrase {
    private const val PREFS_NAME = "zeroclaw_db_passphrase"
    private const val KEY_PASSPHRASE = "db_passphrase"
    private const val PASSPHRASE_BYTES = 32

    /**
     * Returns the database passphrase, generating one if it does not exist.
     *
     * @param context Application context for [EncryptedSharedPreferences].
     * @return Passphrase bytes suitable for [net.zetetic.database.sqlcipher.SupportOpenHelperFactory].
     */
    fun getOrCreate(context: Context): ByteArray {
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
            return hexToBytes(existing)
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(passphrase)
        prefs.edit().putString(KEY_PASSPHRASE, bytesToHex(passphrase)).apply()
        return passphrase
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    @Suppress("MagicNumber")
    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
}
