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
import com.zeroclaw.android.data.local.dao.ChatMessageDao
import com.zeroclaw.android.data.local.dao.ConnectedChannelDao
import com.zeroclaw.android.data.local.dao.LogEntryDao
import com.zeroclaw.android.data.local.dao.PluginDao
import com.zeroclaw.android.data.local.entity.ActivityEventEntity
import com.zeroclaw.android.data.local.entity.AgentEntity
import com.zeroclaw.android.data.local.entity.ChatMessageEntity
import com.zeroclaw.android.data.local.entity.ConnectedChannelEntity
import com.zeroclaw.android.data.local.entity.LogEntryEntity
import com.zeroclaw.android.data.local.entity.PluginEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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
        ChatMessageEntity::class,
    ],
    version = 7,
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

    /** Data access object for daemon console chat message operations. */
    abstract fun chatMessageDao(): ChatMessageDao

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

        /** Migration from schema version 3 to 4: adds the chat_messages table. */
        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `chat_messages` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `content` TEXT NOT NULL,
                            `is_from_user` INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_chat_messages_timestamp` ON `chat_messages` (`timestamp`)",
                    )
                }
            }

        /** Migration from schema version 4 to 5: adds remote_version column to plugins. */
        private val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE plugins ADD COLUMN remote_version TEXT")
                }
            }

        /** Migration from schema version 5 to 6: adds images_json column to chat_messages. */
        private val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE chat_messages ADD COLUMN images_json TEXT DEFAULT NULL",
                    )
                }
            }

        /** Migration from schema version 6 to 7: adds unique index on connected_channels.type. */
        private val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_connected_channels_type` ON `connected_channels` (`type`)",
                    )
                }
            }

        /**
         * Ordered array of schema migrations.
         *
         * Add new [Migration] instances here as the schema evolves.
         * Each migration covers a single version increment (e.g. 1->2).
         */
        val MIGRATIONS: Array<Migration> =
            arrayOf(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
            )

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
            val passphrase = DatabasePassphrase.getOrCreate(context)
            DatabaseEncryptionMigrator.migrateIfNeeded(context, passphrase)
            val factory = SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8))
            val db =
                Room
                    .databaseBuilder(
                        context.applicationContext,
                        ZeroClawDatabase::class.java,
                        DATABASE_NAME,
                    ).openHelperFactory(factory)
                    .apply { MIGRATIONS.forEach { addMigrations(it) } }
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
