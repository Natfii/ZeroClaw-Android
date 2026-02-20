/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for Room database migrations.
 *
 * Uses [MigrationTestHelper] to verify that each migration correctly
 * transforms the schema. Each test creates a database at version N,
 * runs the migration to N+1, and validates the resulting schema by
 * querying the expected tables, columns, or indexes.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    /** Room migration test helper for schema validation. */
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            ZeroClawDatabase::class.java,
        )

    /**
     * Verifies schema version 1 creates all four expected tables.
     */
    @Test
    fun createDatabase_v1_hasFourTables() {
        val db = helper.createDatabase(TEST_DB, 1)
        val tables = queryTableNames(db)
        db.close()

        assert(tables.contains("agents")) { "Missing agents table" }
        assert(tables.contains("plugins")) { "Missing plugins table" }
        assert(tables.contains("log_entries")) { "Missing log_entries table" }
        assert(tables.contains("activity_events")) { "Missing activity_events table" }
    }

    /**
     * Verifies migration 1 to 2 adds the connected_channels table.
     */
    @Test
    fun migrate_1_to_2_addsConnectedChannelsTable() {
        helper.createDatabase(TEST_DB, 1).close()
        val db =
            helper.runMigrationsAndValidate(
                TEST_DB,
                2,
                true,
                ZeroClawDatabase.MIGRATIONS[0],
            )
        val tables = queryTableNames(db)
        db.close()

        assert(tables.contains("connected_channels")) {
            "Migration 1→2 did not create connected_channels table"
        }
    }

    /**
     * Verifies migration 2 to 3 adds temperature and max_depth columns to agents.
     */
    @Test
    fun migrate_2_to_3_addsAgentColumns() {
        helper.createDatabase(TEST_DB, 1).close()
        helper
            .runMigrationsAndValidate(
                TEST_DB,
                2,
                true,
                ZeroClawDatabase.MIGRATIONS[0],
            ).close()
        val db =
            helper.runMigrationsAndValidate(
                TEST_DB,
                3,
                true,
                ZeroClawDatabase.MIGRATIONS[1],
            )
        val columns = queryColumnNames(db, "agents")
        db.close()

        assert(columns.contains("temperature")) {
            "Migration 2→3 did not add temperature column"
        }
        assert(columns.contains("max_depth")) {
            "Migration 2→3 did not add max_depth column"
        }
    }

    /**
     * Verifies migration 3 to 4 adds the chat_messages table with a timestamp index.
     */
    @Test
    fun migrate_3_to_4_addsChatMessagesTable() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 2, true, ZeroClawDatabase.MIGRATIONS[0]).close()
        helper.runMigrationsAndValidate(TEST_DB, 3, true, ZeroClawDatabase.MIGRATIONS[1]).close()
        val db =
            helper.runMigrationsAndValidate(
                TEST_DB,
                4,
                true,
                ZeroClawDatabase.MIGRATIONS[2],
            )
        val tables = queryTableNames(db)
        val indexes = queryIndexNames(db)
        db.close()

        assert(tables.contains("chat_messages")) {
            "Migration 3→4 did not create chat_messages table"
        }
        assert(indexes.contains("index_chat_messages_timestamp")) {
            "Migration 3→4 did not create timestamp index"
        }
    }

    /**
     * Verifies migration 4 to 5 adds remote_version column to plugins.
     */
    @Test
    fun migrate_4_to_5_addsRemoteVersion() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 2, true, ZeroClawDatabase.MIGRATIONS[0]).close()
        helper.runMigrationsAndValidate(TEST_DB, 3, true, ZeroClawDatabase.MIGRATIONS[1]).close()
        helper.runMigrationsAndValidate(TEST_DB, 4, true, ZeroClawDatabase.MIGRATIONS[2]).close()
        val db =
            helper.runMigrationsAndValidate(
                TEST_DB,
                5,
                true,
                ZeroClawDatabase.MIGRATIONS[3],
            )
        val columns = queryColumnNames(db, "plugins")
        db.close()

        assert(columns.contains("remote_version")) {
            "Migration 4→5 did not add remote_version column"
        }
    }

    /**
     * Verifies migration 5 to 6 adds images_json column to chat_messages.
     */
    @Test
    fun migrate_5_to_6_addsImagesJson() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 2, true, ZeroClawDatabase.MIGRATIONS[0]).close()
        helper.runMigrationsAndValidate(TEST_DB, 3, true, ZeroClawDatabase.MIGRATIONS[1]).close()
        helper.runMigrationsAndValidate(TEST_DB, 4, true, ZeroClawDatabase.MIGRATIONS[2]).close()
        helper.runMigrationsAndValidate(TEST_DB, 5, true, ZeroClawDatabase.MIGRATIONS[3]).close()
        val db =
            helper.runMigrationsAndValidate(
                TEST_DB,
                6,
                true,
                ZeroClawDatabase.MIGRATIONS[4],
            )
        val columns = queryColumnNames(db, "chat_messages")
        db.close()

        assert(columns.contains("images_json")) {
            "Migration 5→6 did not add images_json column"
        }
    }

    /**
     * Verifies migration 6 to 7 adds unique index on connected_channels type column.
     */
    @Test
    fun migrate_6_to_7_addsUniqueIndex() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 2, true, ZeroClawDatabase.MIGRATIONS[0]).close()
        helper.runMigrationsAndValidate(TEST_DB, 3, true, ZeroClawDatabase.MIGRATIONS[1]).close()
        helper.runMigrationsAndValidate(TEST_DB, 4, true, ZeroClawDatabase.MIGRATIONS[2]).close()
        helper.runMigrationsAndValidate(TEST_DB, 5, true, ZeroClawDatabase.MIGRATIONS[3]).close()
        helper.runMigrationsAndValidate(TEST_DB, 6, true, ZeroClawDatabase.MIGRATIONS[4]).close()
        val db =
            helper.runMigrationsAndValidate(
                TEST_DB,
                7,
                true,
                ZeroClawDatabase.MIGRATIONS[5],
            )
        val indexes = queryIndexNames(db)
        db.close()

        assert(indexes.contains("index_connected_channels_type")) {
            "Migration 6→7 did not create unique index on connected_channels.type"
        }
    }

    /**
     * Verifies a full migration from version 1 through version 7 succeeds.
     */
    @Test
    fun migrate_1_to_7_fullChain() {
        helper.createDatabase(TEST_DB, 1).close()
        val db =
            helper.runMigrationsAndValidate(
                TEST_DB,
                7,
                true,
                *ZeroClawDatabase.MIGRATIONS,
            )
        val tables = queryTableNames(db)
        db.close()

        assert(tables.contains("agents")) { "Missing agents table after full migration" }
        assert(tables.contains("plugins")) { "Missing plugins table after full migration" }
        assert(tables.contains("log_entries")) { "Missing log_entries table after full migration" }
        assert(tables.contains("activity_events")) { "Missing activity_events table" }
        assert(tables.contains("connected_channels")) { "Missing connected_channels table" }
        assert(tables.contains("chat_messages")) { "Missing chat_messages table" }
    }

    private fun queryTableNames(db: androidx.sqlite.db.SupportSQLiteDatabase): Set<String> {
        val cursor =
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' " +
                    "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'",
            )
        val tables = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            tables.add(cursor.getString(0))
        }
        cursor.close()
        return tables
    }

    private fun queryColumnNames(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
    ): Set<String> {
        val cursor = db.query("PRAGMA table_info($table)")
        val columns = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()
        return columns
    }

    private fun queryIndexNames(db: androidx.sqlite.db.SupportSQLiteDatabase): Set<String> {
        val cursor =
            db.query(
                "SELECT name FROM sqlite_master WHERE type='index' " +
                    "AND name NOT LIKE 'sqlite_%'",
            )
        val indexes = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            indexes.add(cursor.getString(0))
        }
        cursor.close()
        return indexes
    }

    /** Constants for [MigrationTest]. */
    companion object {
        private const val TEST_DB = "migration-test"
    }
}
