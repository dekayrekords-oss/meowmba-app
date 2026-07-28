package com.example.meowmba.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<MessageEntity>

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM messages")
    suspend fun clearHistory()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int): List<MemoryEntity>

    @Insert
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Query("""
        SELECT * FROM memories
        JOIN memories_fts ON (memories.rowid = memories_fts.docid)
        WHERE memories_fts MATCH :query
    """)
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories")
    suspend fun clearAll()

    @Query("DELETE FROM memories WHERE timestamp < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long): Int

    /** Stamp recalled memories so the user can audit why/when they surfaced. */
    @Query("UPDATE memories SET lastAccessedAt = :timestamp WHERE id IN (:ids)")
    suspend fun touchRecalled(ids: List<Long>, timestamp: Long)
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(entry: AuditEntryEntity)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT 200")
    fun getRecent(): Flow<List<AuditEntryEntity>>

    @Query("DELETE FROM audit_log")
    suspend fun clear()
}
