package com.example.meowmba.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MessageEntity::class, MemoryEntity::class, MemoryFtsEntity::class, MessageFtsEntity::class, AuditEntryEntity::class, RoutineEntity::class],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MeowmbaDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun auditDao(): AuditDao
    abstract fun routineDao(): RoutineDao

    companion object {
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
                        MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
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
