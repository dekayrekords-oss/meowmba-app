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
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
