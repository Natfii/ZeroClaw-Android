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
        cleanUpInterruptedMigration(dbFile)
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
     * Cleans up artifacts from a previously interrupted migration.
     *
     * If a `.bak` file exists but the primary database does not, the
     * migration was interrupted after the rename but before the
     * encrypted file was moved into place. The backup is restored so
     * the next call to [migrateIfNeeded] can retry the migration.
     */
    private fun cleanUpInterruptedMigration(dbFile: File) {
        val backupFile = File(dbFile.absolutePath + ".bak")
        if (backupFile.exists() && !dbFile.exists()) {
            Log.w(TAG, "Found orphaned backup from interrupted migration, restoring")
            backupFile.renameTo(dbFile)
        } else if (backupFile.exists() && dbFile.exists()) {
            Log.i(TAG, "Removing stale backup from completed migration")
            backupFile.delete()
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
     *
     * The replacement is performed atomically: the original file is
     * renamed to a `.bak` suffix before the encrypted file is moved
     * into place. If the rename fails, the backup is restored. The
     * backup is only deleted after the encrypted file is successfully
     * in position, so a process death at any point leaves at least
     * one valid copy of the data on disk.
     */
    private fun encrypt(
        dbFile: File,
        passphrase: String,
    ) {
        val tempFile = File(dbFile.parentFile, "zeroclaw_encrypting.db")
        val backupFile = File(dbFile.absolutePath + ".bak")
        tempFile.delete()
        backupFile.delete()

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

        if (!dbFile.renameTo(backupFile)) {
            tempFile.delete()
            error("Failed to back up plaintext database before encryption swap")
        }
        deleteWithCompanions(File(dbFile.absolutePath))

        if (!tempFile.renameTo(dbFile)) {
            backupFile.renameTo(dbFile)
            tempFile.delete()
            error("Failed to rename encrypted database into place")
        }

        backupFile.delete()
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
