package com.example.meowmba.core.database

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

enum class MessageRole {
    USER, ASSISTANT, SYSTEM
}

enum class ImportanceLevel(val value: Int) {
    LOW(1), MEDIUM(3), HIGH(5)
}

/** The distinct memory types from the Memba memory architecture. */
enum class MemoryType {
    /** Auto-saved checkpoints of finished conversation turns. */
    EPISODIC,
    /** Facts the user explicitly asked the companion to remember. */
    SEMANTIC,
    /** Project-specific context and decisions. */
    PROJECT,
    /** User preferences (settings-adjacent facts). */
    PREFERENCE
}

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** 0 = inbox. Non-zero = a project conversation. */
    val conversationId: Long = 0L,
    /** Who said this user line: enrolled name, or Left/Right. */
    val speakerName: String? = null,
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: MemoryType = MemoryType.SEMANTIC,
    val importance: ImportanceLevel = ImportanceLevel.LOW,
    val tags: List<String> = emptyList(),
    /** Vector embedding of [content], encoded via VectorCodec. */
    val embedding: ByteArray? = null,
    /** Source record link: the conversation message this memory came from. */
    val sourceMessageId: Long? = null,
    /** Updated whenever the memory is recalled, for retention and audit. */
    val lastAccessedAt: Long = timestamp
) {
    // ByteArray field requires structural equality overrides
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryEntity) return false
        return id == other.id && content == other.content && timestamp == other.timestamp &&
            type == other.type && importance == other.importance && tags == other.tags &&
            (embedding?.contentEquals(other.embedding ?: return false) ?: (other.embedding == null)) &&
            sourceMessageId == other.sourceMessageId && lastAccessedAt == other.lastAccessedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + importance.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + (sourceMessageId?.hashCode() ?: 0)
        result = 31 * result + lastAccessedAt.hashCode()
        return result
    }
}

@Fts4(contentEntity = MemoryEntity::class)
@Entity(tableName = "memories_fts")
data class MemoryFtsEntity(
    val content: String
)

/** Full-text index over the chat history so recall can search everything ever said. */
@Fts4(contentEntity = MessageEntity::class)
@Entity(tableName = "messages_fts")
data class MessageFtsEntity(
    val content: String
)

/**
 * Immutable audit trail for the agent system: every task transition,
 * approval decision, and tool outcome. Never stores secrets.
 */
/**
 * Phase E: a scheduled routine — time-of-day or interval trigger, fired by
 * AlarmManager and spoken/acted by the companion.
 */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "HH:mm" for daily time-of-day routines; null when [intervalMinutes] is used. */
    val timeOfDay: String? = null,
    /** Repeat interval in minutes; null for time-of-day routines. */
    val intervalMinutes: Int? = null,
    /** "speak" | "morning_brief" | "memory_followup" | "play_music" | "launch_app". */
    val action: String = "speak",
    val message: String = "",
    val enabled: Boolean = true,
    val lastRunAt: Long = 0L,
    /** "system" seeded templates vs "user" tools made by voice. */
    val source: String = "system",
    /** App or music query for play_music / launch_app. */
    val launchTarget: String = "",
)

/**
 * A saved work session (website / photo / code). Chat for the project
 * lives in [MessageEntity] rows with the same [conversationId].
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = createdAt,
    val conversationId: Long,
)

@Entity(tableName = "audit_log")
data class AuditEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val agentId: String,
    val taskId: String?,
    val action: String,
    val detail: String
)
