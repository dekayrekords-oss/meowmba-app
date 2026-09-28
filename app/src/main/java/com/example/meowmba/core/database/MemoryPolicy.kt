package com.example.meowmba.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Separate table keeps legacy memory and encrypted-backup rows compatible. */
@Entity(tableName = "memory_policies", foreignKeys = [ForeignKey(entity = MemoryEntity::class,
    parentColumns = ["id"], childColumns = ["memoryId"], onDelete = ForeignKey.CASCADE)])
data class MemoryPolicyEntity(
    @PrimaryKey val memoryId: Long,
    val pinned: Boolean = false,
    val revision: Int = 0,
    val correctedAt: Long = 0,
)

const val MEMORY_POLICY_MIGRATION = "CREATE TABLE IF NOT EXISTS `memory_policies` (`memoryId` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `correctedAt` INTEGER NOT NULL, PRIMARY KEY(`memoryId`), FOREIGN KEY(`memoryId`) REFERENCES `memories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
