/*
 * Copyright 2026 ZeroClaw Contributors
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
import com.zeroclaw.android.data.local.dao.LogEntryDao
import com.zeroclaw.android.data.local.dao.PluginDao
import com.zeroclaw.android.data.local.entity.ActivityEventEntity
import com.zeroclaw.android.data.local.entity.AgentEntity
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
    ],
    version = 1,
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

    /** Factory and constants for [ZeroClawDatabase]. */
    companion object {
        /** Database file name. */
        private const val DATABASE_NAME = "zeroclaw.db"

        /**
         * Ordered array of schema migrations.
         *
         * Add new [Migration] instances here as the schema evolves.
         * Each migration covers a single version increment (e.g. 1->2).
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

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
        fun build(context: Context, scope: CoroutineScope): ZeroClawDatabase {
            var instance: ZeroClawDatabase? = null
            val db = Room.databaseBuilder(
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
