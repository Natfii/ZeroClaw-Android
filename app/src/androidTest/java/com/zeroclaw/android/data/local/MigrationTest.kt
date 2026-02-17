/*
 * Copyright 2026 ZeroClaw Contributors
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
 * transforms the schema. Currently verifies the initial schema (v1).
 * Add migration tests here as the schema evolves.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    /** Room migration test helper for schema validation. */
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ZeroClawDatabase::class.java,
    )

    /**
     * Verifies schema version 1 creates all four expected tables.
     */
    @Test
    fun createDatabase_v1_hasFourTables() {
        val db = helper.createDatabase(TEST_DB, 1)
        val cursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'",
        )
        val tables = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            tables.add(cursor.getString(0))
        }
        cursor.close()
        db.close()

        assert(tables.contains("agents")) { "Missing agents table" }
        assert(tables.contains("plugins")) { "Missing plugins table" }
        assert(tables.contains("log_entries")) { "Missing log_entries table" }
        assert(tables.contains("activity_events")) { "Missing activity_events table" }
    }

    /** Constants for [MigrationTest]. */
    companion object {
        private const val TEST_DB = "migration-test"
    }
}
