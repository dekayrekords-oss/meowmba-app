package com.example.meowmba.core.agent.runtime

import java.util.UUID

/**
 * Agent architecture model (Phase 5).
 *
 * The companion is the orchestrator: user goals are routed to a specialized
 * agent, which executes tasks through approved tools only. Every agent has a
 * unique ID, purpose, tool permissions, resource limits, and an availability
 * flag — integrations that don't exist yet are registered but clearly marked
 * UNSUPPORTED, never faked.
 */

/** Lifecycle of one agent task. */
enum class TaskStatus {
    QUEUED,
    RUNNING,
    WAITING_APPROVAL,
    COMPLETED,
    FAILED,
    CANCELLED
}

/** A specialized worker agent definition. */
data class AgentDefinition(
    val id: String,
    val name: String,
    val purpose: String,
    /** Model this agent prefers (falls back to the active provider). */
    val modelAssignment: String = "active",
    /** Tool IDs this agent is allowed to execute. */
    val allowedTools: Set<String> = emptySet(),
    /** Resource limit: max simultaneously running tasks. */
    val maxConcurrentTasks: Int = 2,
    /** False when the integration adapter doesn't exist yet (clearly marked). */
    val available: Boolean = true,
    val statusNote: String? = null
)

/** One unit of work owned by an agent. */
data class AgentTask(
    val id: String = UUID.randomUUID().toString().take(8),
    val agentId: String,
    val toolId: String,
    val title: String,
    val args: Map<String, String> = emptyMap(),
    val status: TaskStatus = TaskStatus.QUEUED,
    val parentTaskId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val result: String? = null,
    val log: List<String> = emptyList()
) {
    val isTerminal: Boolean
        get() = status == TaskStatus.COMPLETED || status == TaskStatus.FAILED ||
            status == TaskStatus.CANCELLED
}

/** The registry of specialized agents. Integrations are marked honestly. */
object AgentRegistry {
    const val MAIN = "main"

    val agents: List<AgentDefinition> = listOf(
        AgentDefinition(
            id = MAIN, name = "Meow",
            purpose = "Main conversational agent. Routes goals to specialists and answers directly.",
            allowedTools = setOf("task.list")
        ),
        AgentDefinition(
            id = "file", name = "Shelve",
            purpose = "Organizes the workspace: read, write, and delete files (sandboxed).",
            allowedTools = setOf("file.read", "file.write", "file.delete", "file.list")
        ),
        AgentDefinition(
            id = "memory", name = "Keeper",
            purpose = "Manages long-term memory: save and search.",
            allowedTools = setOf("memory.save", "memory.search")
        ),
        AgentDefinition(
            id = "project", name = "Builder",
            purpose = "Creates and organizes project folders and manifests.",
            allowedTools = setOf("project.create", "file.write", "file.read", "file.list")
        ),
        AgentDefinition(
            id = "coding", name = "Forge",
            purpose = "Helps write, edit, and debug code inside projects.",
            allowedTools = setOf("file.read", "file.write", "file.list", "project.create")
        ),
        AgentDefinition(
            id = "research", name = "Scout",
            purpose = "Research agent. Web tools are not installed yet — searches memory only for now.",
            allowedTools = setOf("memory.search"),
            statusNote = "Limited: web search adapter not available"
        ),
        // ── Registered but unsupported integrations (marked, never faked) ──
        AgentDefinition(
            id = "email", name = "Post",
            purpose = "Drafts and summarizes email.",
            available = false,
            statusNote = "Email adapter not available yet"
        ),
        AgentDefinition(
            id = "calendar", name = "Planner",
            purpose = "Reviews calendar and prepares meetings.",
            available = false,
            statusNote = "Calendar adapter not available yet"
        ),
        AgentDefinition(
            id = "computer", name = "Bridge",
            purpose = "Controls paired computers through the desktop bridge.",
            available = false,
            statusNote = "Desktop bridge ships in Phase 6"
        ),
        AgentDefinition(
            id = "media", name = "Canvas",
            purpose = "Generates images and video through configured services.",
            available = false,
            statusNote = "Media generation adapter not available yet"
        )
    )

    fun byId(id: String): AgentDefinition? = agents.find { it.id == id }

    /** Which available agent owns a tool, or null when no agent may run it. */
    fun ownerOf(toolId: String): AgentDefinition? =
        agents.firstOrNull { it.available && toolId in it.allowedTools }
}
