/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Handles one-time migration from an unencrypted Room database to a
 * SQLCipher-encrypted database.
 *
 * Upgrading users (v0.0.11 and earlier) have a plaintext `zeroclaw.db`.
 * SQLCipher cannot open a plaintext file with a passphrase, so this
 * migrator detects the unencrypted file, exports its contents into a
 * new encrypted copy via `sqlcipher_export`, and replaces the original.
 *
 * Detection uses the SQLite file header: plaintext databases start with
 * the 16-byte magic string `"SQLite format 3\u0000"`. Encrypted files
 * have random bytes in the header.
 */
object DatabaseEncryptionMigrator {
    private const val TAG = "DbEncryptionMigrator"
    private const val DATABASE_NAME = "zeroclaw.db"
    private const val SQLITE_HEADER_SIZE = 16
    private const val SQLITE_MAGIC = "SQLite format 3"

    /**
     * Migrates the database from plaintext to encrypted if needed.
     *
     * Must be called **before** Room opens the database. If the
     * database file does not exist (fresh install) or is already
     * encrypted, this method returns immediately.
     *
     * On migration failure the plaintext database is deleted so that
     * Room can recreate it with seed data, avoiding a crash loop.
     *
     * @param context Application context for locating the database file.
     * @param passphrase Printable hex passphrase string (same value
     *   passed to [net.zetetic.database.sqlcipher.SupportOpenHelperFactory]).
     */
    fun migrateIfNeeded(
        context: Context,
        passphrase: String,
    ) {
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        if (!dbFile.exists()) return
        if (!isUnencrypted(dbFile)) return

        Log.i(TAG, "Detected unencrypted database — migrating to SQLCipher")
        try {
            encrypt(dbFile, passphrase)
            Log.i(TAG, "Database encryption migration completed successfully")
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Log.e(TAG, "Migration failed — deleting database for fresh start", e)
            deleteWithCompanions(dbFile)
        }
    }

    /**
     * Returns `true` if [dbFile] is a plaintext SQLite database.
     *
     * Reads the first 16 bytes and checks for the ASCII magic string
     * `"SQLite format 3\0"`. Encrypted files have random header bytes.
     */
    private fun isUnencrypted(dbFile: File): Boolean =
        try {
            val header = ByteArray(SQLITE_HEADER_SIZE)
            dbFile.inputStream().use { it.read(header) }
            String(header, 0, SQLITE_MAGIC.length, Charsets.US_ASCII) == SQLITE_MAGIC
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Log.w(TAG, "Could not read database header", e)
            false
        }

    /**
     * Encrypts [dbFile] in place using SQLCipher's `sqlcipher_export`.
     *
     * Opens the plaintext database with an empty password, attaches
     * a new encrypted database with the given [passphrase], exports all
     * schema and data, then replaces the original file.
     */
    private fun encrypt(
        dbFile: File,
        passphrase: String,
    ) {
        val tempFile = File(dbFile.parentFile, "zeroclaw_encrypting.db")
        tempFile.delete()

        val db =
            net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                "",
                null,
                net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE,
                null,
            )
        try {
            val escaped = passphrase.replace("'", "''")
            db.execSQL(
                "ATTACH DATABASE '${tempFile.absolutePath}' AS encrypted KEY '$escaped'",
            )
            db.execSQL("SELECT sqlcipher_export('encrypted')")
            db.execSQL("DETACH DATABASE encrypted")
        } finally {
            db.close()
        }

        deleteWithCompanions(dbFile)
        if (!tempFile.renameTo(dbFile)) {
            tempFile.delete()
            error("Failed to rename encrypted database into place")
        }
    }

    /**
     * Deletes [dbFile] and its WAL/journal/SHM companion files.
     */
    private fun deleteWithCompanions(dbFile: File) {
        dbFile.delete()
        File(dbFile.absolutePath + "-journal").delete()
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
    }
}
