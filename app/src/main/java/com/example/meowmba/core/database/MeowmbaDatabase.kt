package com.example.meowmba.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [MessageEntity::class, MemoryEntity::class, MemoryFtsEntity::class, AuditEntryEntity::class],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MeowmbaDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun auditDao(): AuditDao

    companion object {
        @Volatile
        private var INSTANCE: MeowmbaDatabase? = null

        fun getDatabase(context: Context): MeowmbaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeowmbaDatabase::class.java,
                    "meowmba_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
