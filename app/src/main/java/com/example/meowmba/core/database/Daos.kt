package com.example.meowmba.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesIn(conversationId: Long, limit: Int): List<MessageEntity>

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM messages")
    suspend fun clearHistory()

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("""
        SELECT messages.* FROM messages
        JOIN messages_fts ON (messages.rowid = messages_fts.docid)
        WHERE messages_fts MATCH :query
        ORDER BY messages.timestamp DESC LIMIT :limit
    """)
    suspend fun searchFullText(query: String, limit: Int): List<MessageEntity>

    /** Fallback when the FTS table is unavailable (older installs). */
    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun searchLike(query: String, limit: Int): List<MessageEntity>
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_policies")
    fun policies(): Flow<List<MemoryPolicyEntity>>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun find(id: Long): MemoryEntity?

    @Query("SELECT * FROM memory_policies WHERE memoryId = :id")
    suspend fun policy(id: Long): MemoryPolicyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePolicy(policy: MemoryPolicyEntity)

    @Query("UPDATE memories SET content = :text, embedding = :embedding WHERE id = :id")
    suspend fun updateContent(id: Long, text: String, embedding: ByteArray)

    @Transaction
    suspend fun correct(id: Long, text: String, embedding: ByteArray, now: Long): Boolean {
        if (find(id) == null) return false
        val old = policy(id) ?: MemoryPolicyEntity(id)
        updateContent(id, text, embedding)
        savePolicy(old.copy(revision = old.revision + 1, correctedAt = now))
        return true
    }

    @Transaction
    suspend fun pin(id: Long, pinned: Boolean) {
        if (find(id) != null) savePolicy((policy(id) ?: MemoryPolicyEntity(id)).copy(pinned = pinned))
    }

    @Query("""
        SELECT m.* FROM memories m JOIN memories_fts ON m.rowid = memories_fts.docid
        LEFT JOIN messages src ON src.id = m.sourceMessageId
        WHERE memories_fts MATCH :query AND
        ((m.sourceMessageId IS NULL AND m.type != 'PROJECT') OR src.conversationId = :conversationId)
        ORDER BY m.importance DESC, m.timestamp DESC, m.id DESC LIMIT :limit
    """)
    suspend fun recallMatches(query: String, conversationId: Long, limit: Int): List<MemoryEntity>

    @Query("""
        SELECT m.* FROM memories m LEFT JOIN messages src ON src.id = m.sourceMessageId
        LEFT JOIN memory_policies p ON p.memoryId = m.id
        WHERE ((m.sourceMessageId IS NULL AND m.type != 'PROJECT') OR src.conversationId = :conversationId)
        ORDER BY COALESCE(p.pinned, 0) DESC, m.timestamp DESC, m.id DESC LIMIT :limit
    """)
    suspend fun recallRecent(conversationId: Long, limit: Int): List<MemoryEntity>

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

    @Query("DELETE FROM memories WHERE timestamp < :cutoff AND id NOT IN (SELECT memoryId FROM memory_policies WHERE pinned = 1)")
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

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters ORDER BY name")
    fun getAll(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters ORDER BY name")
    suspend fun getAllOnce(): List<CharacterEntity>

    @Query("SELECT * FROM characters WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CharacterEntity?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(character: CharacterEntity)

    @Query("DELETE FROM characters WHERE name = :name")
    suspend fun deleteByName(name: String)

    @Query("SELECT COUNT(*) FROM characters")
    suspend fun count(): Int
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY id")
    fun getAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE enabled = 1")
    suspend fun getEnabled(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getById(id: Long): RoutineEntity?

    @Insert
    suspend fun insert(routine: RoutineEntity): Long

    @Update
    suspend fun update(routine: RoutineEntity)

    @Query("UPDATE routines SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE routines SET lastRunAt = :timestamp WHERE id = :id")
    suspend fun markRun(id: Long, timestamp: Long)

    @Query("SELECT COUNT(*) FROM routines")
    suspend fun count(): Int
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastOpenedAt DESC")
    fun getAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("UPDATE projects SET lastOpenedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT name FROM projects")
    suspend fun names(): List<String>
}

@Dao
interface AgenticGoalDao {
    @Query("SELECT * FROM agentic_goals WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AgenticGoalEntity?

    @Query("SELECT * FROM agentic_goals WHERE status IN (:statuses) ORDER BY createdAt ASC")
    suspend fun listByStatuses(statuses: List<String>): List<AgenticGoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: AgenticGoalEntity)

    @Query("DELETE FROM agentic_goals WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface AgenticEventDao {
    @Query("SELECT * FROM agentic_events WHERE goalId = :goalId ORDER BY timestamp ASC")
    suspend fun listForGoal(goalId: String): List<AgenticEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AgenticEventEntity)

    @Query("DELETE FROM agentic_events WHERE goalId = :goalId")
    suspend fun deleteForGoal(goalId: String)
}

@Dao
interface ConnectionProfileDao {
    @Query("SELECT * FROM agentic_connection_profiles ORDER BY displayName ASC")
    suspend fun listAll(): List<ConnectionProfileEntity>

    @Query("SELECT * FROM agentic_connection_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConnectionProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ConnectionProfileEntity)

    @Query("DELETE FROM agentic_connection_profiles WHERE id = :id")
    suspend fun deleteById(id: String): Int
}

