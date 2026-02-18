/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zeroclaw.android.data.local.dao.ActivityEventDao
import com.zeroclaw.android.data.local.dao.AgentDao
import com.zeroclaw.android.data.local.dao.ConnectedChannelDao
import com.zeroclaw.android.data.local.dao.LogEntryDao
import com.zeroclaw.android.data.local.dao.PluginDao
import com.zeroclaw.android.data.local.entity.ActivityEventEntity
import com.zeroclaw.android.data.local.entity.AgentEntity
import com.zeroclaw.android.data.local.entity.ConnectedChannelEntity
import com.zeroclaw.android.data.local.entity.LogEntryEntity
import com.zeroclaw.android.data.local.entity.PluginEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Room database for persistent storage of agents, plugins, log entries,
 * and activity events.
 *
 * Use [build] to create an instance with seed data callback.
 *
 * Migration strategy: explicit [Migration] objects in [MIGRATIONS] are
 * preferred for schema changes. [fallbackToDestructiveMigration] is
 * configured as a safety net during development to prevent crashes
 * when a migration is not yet written. Before production release,
 * all schema changes must have corresponding migrations.
 */
@Database(
    entities = [
        AgentEntity::class,
        PluginEntity::class,
        LogEntryEntity::class,
        ActivityEventEntity::class,
        ConnectedChannelEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class ZeroClawDatabase : RoomDatabase() {
    /** Data access object for agent operations. */
    abstract fun agentDao(): AgentDao

    /** Data access object for plugin operations. */
    abstract fun pluginDao(): PluginDao

    /** Data access object for log entry operations. */
    abstract fun logEntryDao(): LogEntryDao

    /** Data access object for activity event operations. */
    abstract fun activityEventDao(): ActivityEventDao

    /** Data access object for connected channel operations. */
    abstract fun connectedChannelDao(): ConnectedChannelDao

    /** Factory and constants for [ZeroClawDatabase]. */
    companion object {
        /** Database file name. */
        private const val DATABASE_NAME = "zeroclaw.db"

        /** Migration from schema version 1 to 2: adds the connected_channels table. */
        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `connected_channels` (
                            `id` TEXT NOT NULL,
                            `type` TEXT NOT NULL,
                            `is_enabled` INTEGER NOT NULL,
                            `config_json` TEXT NOT NULL,
                            `created_at` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent(),
                    )
                }
            }

        /** Migration from schema version 2 to 3: adds temperature and max_depth to agents. */
        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE agents ADD COLUMN temperature REAL")
                    db.execSQL(
                        "ALTER TABLE agents ADD COLUMN max_depth INTEGER NOT NULL DEFAULT 3",
                    )
                }
            }

        /**
         * Ordered array of schema migrations.
         *
         * Add new [Migration] instances here as the schema evolves.
         * Each migration covers a single version increment (e.g. 1->2).
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        /**
         * Builds a [ZeroClawDatabase] instance with seed data inserted on first creation.
         *
         * Applies all registered [MIGRATIONS] first, then falls back to destructive
         * migration as a development safety net for unhandled version jumps.
         *
         * @param context Application context for database file location.
         * @param scope Coroutine scope for seed data insertion.
         * @return Configured [ZeroClawDatabase] instance.
         */
        fun build(
            context: Context,
            scope: CoroutineScope,
        ): ZeroClawDatabase {
            var instance: ZeroClawDatabase? = null
            val db =
                Room
                    .databaseBuilder(
                        context.applicationContext,
                        ZeroClawDatabase::class.java,
                        DATABASE_NAME,
                    ).apply { MIGRATIONS.forEach { addMigrations(it) } }
                    .fallbackToDestructiveMigration()
                    .addCallback(
                        object : Callback() {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                super.onCreate(db)
                                scope.launch {
                                    instance?.let { database ->
                                        database.pluginDao().insertAllIgnoreConflicts(
                                            SeedData.seedPlugins(),
                                        )
                                    }
                                }
                            }
                        },
                    ).build()
            instance = db
            return db
        }
    }
}
