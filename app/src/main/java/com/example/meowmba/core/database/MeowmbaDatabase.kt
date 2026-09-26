package com.example.meowmba.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MessageEntity::class, MemoryEntity::class, MemoryFtsEntity::class, MessageFtsEntity::class, AuditEntryEntity::class, RoutineEntity::class, ProjectEntity::class, CharacterEntity::class, AgenticGoalEntity::class, AgenticEventEntity::class, ConnectionProfileEntity::class, com.example.meowmba.core.flow.db.FlowIntentionEntity::class, com.example.meowmba.core.flow.db.FlowOutcomeEntity::class, com.example.meowmba.core.context.live.LifeEventEntity::class, com.example.meowmba.core.context.live.LocationSampleEntity::class, com.example.meowmba.core.northstar.NorthStar::class, com.example.meowmba.core.northstar.NorthStarLink::class, com.example.meowmba.core.agent.runtime.CompanionTaskRecord::class],
    version = 14,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MeowmbaDatabase : RoomDatabase() {
    abstract fun companionTaskDao(): com.example.meowmba.core.agent.runtime.CompanionTaskDao
    abstract fun northStarDao(): com.example.meowmba.core.northstar.NorthStarDao
    abstract fun flowDao(): com.example.meowmba.core.flow.db.FlowDao
    abstract fun lifeJournalDao(): com.example.meowmba.core.context.live.LifeJournalDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun auditDao(): AuditDao
    abstract fun routineDao(): RoutineDao
    abstract fun projectDao(): ProjectDao
    abstract fun characterDao(): CharacterDao
    abstract fun agenticGoalDao(): AgenticGoalDao
    abstract fun agenticEventDao(): AgenticEventDao
    abstract fun connectionProfileDao(): ConnectionProfileDao

    companion object {
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL(com.example.meowmba.core.agent.runtime.TASK_HISTORY_MIGRATION) }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                com.example.meowmba.core.northstar.MIGRATION_12_13_STATEMENTS.forEach(db::execSQL)
            }
        }
        /** The context journal and location samples (docs/MEOW_CONTEXT_PLAN.md). */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                com.example.meowmba.core.context.live.MIGRATION_11_12_STATEMENTS.forEach(db::execSQL)
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                com.example.meowmba.core.flow.db.MIGRATION_10_11_STATEMENTS.forEach(db::execSQL)
            }
        }

        /**
         * Adds the recall columns to `memories`, preserving every saved memory.
         *
         * Written as create-copy-drop-rename rather than four ALTER TABLEs
         * because two of the new columns are NOT NULL: adding those in place
         * requires a SQL DEFAULT, and a default present in the table but absent
         * from the entity fails Room's schema validation on the next launch.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `memories_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                        "`type` TEXT NOT NULL, `importance` INTEGER NOT NULL, " +
                        "`tags` TEXT NOT NULL, `embedding` BLOB, " +
                        "`sourceMessageId` INTEGER, `lastAccessedAt` INTEGER NOT NULL)"
                )
                // type defaults to the general kind and lastAccessedAt to the
                // creation time, which is the truthful answer for a row that
                // predates the column.
                db.execSQL(
                    "INSERT INTO `memories_new` " +
                        "(`id`, `content`, `timestamp`, `type`, `importance`, `tags`, " +
                        "`embedding`, `sourceMessageId`, `lastAccessedAt`) " +
                        "SELECT `id`, `content`, `timestamp`, 'FACT', `importance`, `tags`, " +
                        "NULL, NULL, `timestamp` FROM `memories`"
                )
                db.execSQL("DROP TABLE `memories`")
                db.execSQL("ALTER TABLE `memories_new` RENAME TO `memories`")
            }
        }

        /** Adds the agent audit log. Purely additive — nothing to carry over. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audit_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, `agentId` TEXT NOT NULL, " +
                        "`taskId` TEXT, `action` TEXT NOT NULL, `detail` TEXT NOT NULL)"
                )
            }
        }

        /** Adds full-text search over chat history without losing any data. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `messages_fts` USING FTS4(`content` TEXT NOT NULL, content=`messages`)")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_messages_fts_BEFORE_UPDATE BEFORE UPDATE ON `messages` BEGIN DELETE FROM `messages_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_messages_fts_BEFORE_DELETE BEFORE DELETE ON `messages` BEGIN DELETE FROM `messages_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_messages_fts_AFTER_UPDATE AFTER UPDATE ON `messages` BEGIN INSERT INTO `messages_fts`(docid, `content`) VALUES (NEW.`rowid`, NEW.`content`); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_messages_fts_AFTER_INSERT AFTER INSERT ON `messages` BEGIN INSERT INTO `messages_fts`(docid, `content`) VALUES (NEW.`rowid`, NEW.`content`); END")
                db.execSQL("INSERT INTO `messages_fts`(`messages_fts`) VALUES('rebuild')")
            }
        }

        /** Phase E: routines table, additive — no existing data touched. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `routines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `timeOfDay` TEXT, `intervalMinutes` INTEGER, `action` TEXT NOT NULL, `message` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `lastRunAt` INTEGER NOT NULL)")
            }
        }

        /**
         * Projects/conversations (the deployed app's v7) — ported verbatim from
         * the AndroidStudioProjects/Meowmba checkout, which is what phones run.
         * This checkout was behind the installed build and crashed with
         * "migration from 8 to 7 required" until these were added.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `conversationId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `routines` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'system'")
                db.execSQL("ALTER TABLE `routines` ADD COLUMN `launchTarget` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `projects` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `lastOpenedAt` INTEGER NOT NULL, " +
                        "`conversationId` INTEGER NOT NULL)"
                )
            }
        }

        /** Speaker attribution on user messages (deployed v8). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `speakerName` TEXT")
            }
        }


        /** Agentic goals / events / connection profiles. Additive. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `agentic_goals` (" +
                        "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                        "`maxSteps` INTEGER NOT NULL, `maxToolCalls` INTEGER NOT NULL, " +
                        "`maxSshExecs` INTEGER NOT NULL, `stepsUsed` INTEGER NOT NULL, " +
                        "`toolCallsUsed` INTEGER NOT NULL, `sshExecsUsed` INTEGER NOT NULL, " +
                        "`approvalGranted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `agentic_events` (" +
                        "`id` TEXT NOT NULL, `goalId` TEXT NOT NULL, `taskId` TEXT, " +
                        "`type` TEXT NOT NULL, `detail` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_agentic_events_goalId` ON `agentic_events` (`goalId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `agentic_connection_profiles` (" +
                        "`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                        "`host` TEXT NOT NULL, `port` INTEGER NOT NULL, `username` TEXT NOT NULL, " +
                        "`credentialRef` TEXT NOT NULL, `hostKeyFingerprint` TEXT, " +
                        "`workspaceRoot` TEXT, `pinHostKeyOnFirstConnect` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        /** Studio character bibles. Additive — nothing to carry over. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `characters` (" +
                        "`name` TEXT NOT NULL PRIMARY KEY, " +
                        "`sheet` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
            }
        }

        @Volatile
        private var INSTANCE: MeowmbaDatabase? = null

        fun getDatabase(context: Context): MeowmbaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeowmbaDatabase::class.java,
                    "meowmba_database"
                )
                    .addMigrations(
                        MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                    )
                    // No fallbackToDestructiveMigration. It was silently
                    // dropping every table for anyone upgrading from v2 or v3,
                    // because only the 4→5 and 5→6 migrations existed: chat
                    // history, memories, the vault index and the audit log all
                    // disappeared on update with nothing said. AGENTS.md states
                    // the rule this violated — "bump + migrate, never
                    // fallbackToDestructiveMigration". Every version from 2 up
                    // now has a real migration, so there is nothing left for a
                    // fallback to do except hide the next missing one.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
