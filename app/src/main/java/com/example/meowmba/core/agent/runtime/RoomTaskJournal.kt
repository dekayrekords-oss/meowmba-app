package com.example.meowmba.core.agent.runtime

import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "companion_tasks")
data class CompanionTaskRecord(@PrimaryKey val id: String, val createdAt: Long, val terminal: Boolean, val payload: String)

@Dao
abstract class CompanionTaskDao {
    @Query("SELECT * FROM companion_tasks WHERE terminal = 0 OR id IN (SELECT id FROM companion_tasks ORDER BY createdAt DESC LIMIT 200) ORDER BY createdAt DESC")
    abstract suspend fun recent(): List<CompanionTaskRecord>
    @Upsert protected abstract suspend fun upsert(record: CompanionTaskRecord)
    // Linked results stay available through their North Star. Active records must survive recovery.
    @Query("DELETE FROM companion_tasks WHERE terminal = 1 AND id NOT IN (SELECT id FROM companion_tasks ORDER BY createdAt DESC LIMIT 200) AND ('task:' || id) NOT IN (SELECT sourceKey FROM north_star_links WHERE dismissed = 0)")
    protected abstract suspend fun trim()
    @Query("SELECT * FROM companion_tasks WHERE id = :id") abstract suspend fun find(id: String): CompanionTaskRecord?
    @Query("DELETE FROM north_star_links WHERE sourceKey = ('task:' || :id)") protected abstract suspend fun removeLinks(id: String)
    @Query("DELETE FROM companion_tasks WHERE id = :id") protected abstract suspend fun removeRecord(id: String)
    @Transaction open suspend fun remove(id: String) { removeLinks(id); removeRecord(id) }
    @Transaction open suspend fun save(record: CompanionTaskRecord) { upsert(record); trim() }
}

class RoomTaskJournal(private val dao: CompanionTaskDao) : TaskJournal {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun decode(record: CompanionTaskRecord): AgentTask = json.decodeFromString<AgentTask>(record.payload).also {
        require(it.id == record.id && it.createdAt == record.createdAt && it.isTerminal == record.terminal) { "Invalid task record" }
    }
    override suspend fun read(): List<AgentTask> = withContext(Dispatchers.IO) { dao.recent().map(::decode) }
    override suspend fun remove(id: String) = dao.remove(id)
    override suspend fun write(task: AgentTask) = withContext(Dispatchers.IO) {
        val safe = TaskHistory.forStorage(task)
        dao.save(CompanionTaskRecord(safe.id, safe.createdAt, safe.isTerminal, json.encodeToString(safe)))
    }
    suspend fun find(id: String): AgentTask? = withContext(Dispatchers.IO) { dao.find(id)?.let { TaskHistory.recover(decode(it), System.currentTimeMillis()) } }
}

const val TASK_HISTORY_MIGRATION = "CREATE TABLE IF NOT EXISTS `companion_tasks` (`id` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `terminal` INTEGER NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`id`))"
