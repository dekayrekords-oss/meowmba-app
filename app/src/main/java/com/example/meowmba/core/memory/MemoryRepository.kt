package com.example.meowmba.core.memory

import com.example.meowmba.core.database.ImportanceLevel
import com.example.meowmba.core.database.MemoryDao
import com.example.meowmba.core.database.MemoryEntity
import com.example.meowmba.core.database.MemoryType
import com.example.meowmba.core.database.MessageDao
import com.example.meowmba.core.database.MessageEntity
import com.example.meowmba.core.database.MessageRole
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first memory store. Room is the source of truth; every write is
 * embedded on the way in so recall ranking never blocks on an embedder.
 * Optional remote sync lives behind [MetaMemoryConnector], never in here.
 */
class MemoryRepository(
    private val messageDao: MessageDao,
    private val memoryDao: MemoryDao,
    private val embedder: EmbeddingInterface = LocalHashEmbedder()
) {
    val allMessages: Flow<List<MessageEntity>> = messageDao.getAllMessages()
    val allMemories: Flow<List<MemoryEntity>> = memoryDao.getAllMemories()

    suspend fun addMessage(
        role: MessageRole,
        content: String,
        conversationId: Long = 0L,
        speakerName: String? = null,
    ): Long {
        return messageDao.insertMessage(
            MessageEntity(
                role = role,
                content = content,
                conversationId = conversationId,
                speakerName = speakerName,
            )
        )
    }

    suspend fun getRecentMessages(limit: Int, conversationId: Long = 0L): List<MessageEntity> {
        return messageDao.getRecentMessagesIn(conversationId, limit).sortedBy { it.timestamp }
    }

    suspend fun addMemory(
        content: String,
        type: MemoryType = MemoryType.SEMANTIC,
        importance: ImportanceLevel = ImportanceLevel.MEDIUM,
        tags: List<String> = emptyList(),
        sourceMessageId: Long? = null
    ): Long {
        val embedding = VectorCodec.encode(embedder.getEmbedding(content))
        return memoryDao.insertMemory(
            MemoryEntity(
                content = content,
                type = type,
                importance = importance,
                tags = tags,
                embedding = embedding,
                sourceMessageId = sourceMessageId
            )
        )
    }

    /** Candidate pool for embedding recall (bounded for speed). */
    suspend fun getRecentMemories(limit: Int = 200): List<MemoryEntity> {
        return memoryDao.getRecentMemories(limit)
    }

    /** Full-text search for the user-driven Memory screen search bar. */
    /**
     * Search everything ever discussed. FTS first; if the FTS table is
     * missing on this install (pre-migration database), fall back to LIKE.
     */
    suspend fun searchMessages(query: String, limit: Int = 8): List<MessageEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return runCatching { messageDao.searchFullText(trimmed, limit) }
            .getOrElse { runCatching { messageDao.searchLike(trimmed, limit) }.getOrDefault(emptyList()) }
    }

    suspend fun searchMemories(query: String): List<MemoryEntity> {
        val sanitized = query.trim().replace("\"", " ")
        if (sanitized.isBlank()) return emptyList()
        return memoryDao.searchMemories("\"$sanitized\"")
    }

    /** Auto-checkpoint of a finished conversation turn (episodic memory). */
    suspend fun saveEpisodicCheckpoint(userText: String, assistantText: String, sourceMessageId: Long?) {
        val content = "User asked: ${userText.take(160)}\nYou answered: ${assistantText.take(320)}"
        addMemory(
            content = content,
            type = MemoryType.EPISODIC,
            importance = ImportanceLevel.LOW,
            tags = listOf("conversation"),
            sourceMessageId = sourceMessageId
        )
    }

    /** Stamp recalled memories so the audit trail shows when they surfaced. */
    suspend fun touchRecalled(ids: List<Long>) {
        if (ids.isNotEmpty()) {
            memoryDao.touchRecalled(ids, System.currentTimeMillis())
        }
    }

    suspend fun deleteMemoryById(id: Long) {
        memoryDao.deleteById(id)
    }

    suspend fun deleteMessageById(id: Long) {
        messageDao.deleteById(id)
    }

    suspend fun clearAllMemories() {
        memoryDao.clearAll()
    }

    /** Retention control: delete memories older than [cutoffDays] days. */
    suspend fun purgeOlderThan(cutoffDays: Int): Int {
        val cutoff = System.currentTimeMillis() - cutoffDays * 24L * 60 * 60 * 1000
        return memoryDao.purgeOlderThan(cutoff)
    }

    suspend fun clearHistory() {
        messageDao.clearHistory()
    }
}
