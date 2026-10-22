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
@kotlinx.serialization.Serializable
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
@kotlinx.serialization.Serializable
data class AgentTask(
    val id: String = UUID.randomUUID().toString(),
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
    val log: List<String> = emptyList(),
    /** Captured at submission; changing projects never reassigns in-flight work. */
    val projectId: Long? = null,
    val conversationId: Long? = null,
    val outcome: com.example.meowmba.core.agent.tools.ToolOutcome = com.example.meowmba.core.agent.tools.ToolOutcome.RESULT,
    /** Written before dispatch. An interrupted call may have changed an external system. */
    val executionStarted: Boolean = false,
    /** Restored history is displayed quietly, never replayed or spoken as a new result. */
    val recovered: Boolean = false,
    val resultTruncated: Boolean = false,
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
            // avatar.animate is self-expression (the companion performing its own
            // invented clips), so it belongs to Meow rather than a specialist.
            // vault.note.* are Meow's own spoken notes ("create a note called …",
            // "list my notes") — the router sends them here, so the permission
            // set has to include them or every vault command dies in the
            // orchestrator's check with "Meow isn't allowed to use vault.note.*".
            allowedTools = setOf(
                "task.list",
                "brain.models", "brain.select_model",
                "business.agents", "business.start", "business.jobs", "business.open",
                "avatar.animate",
                "avatar.invent",
                "avatar.form",
                "avatar.describe_animation",
                "vault.note.list",
                "vault.note.read",
                "vault.note.search",
                "vault.note.write",
                "vault.note.delete",
                "ssh.run",
                "ssh.add_host",
            )
        ),
        AgentDefinition(
            id = "file", name = "Shelve",
            purpose = "Organizes the workspace: read, write, and delete files (sandboxed).",
            allowedTools = setOf("file.read", "file.write", "file.delete", "file.list", "file.browser", "file.search", "file.cleanup", "file.move", "file.trash", "file.restore")
        ),
        AgentDefinition(
            id = "memory", name = "Keeper",
            purpose = "Manages long-term memory: save, search, and continuous capture via MetaMemory.",
            allowedTools = setOf("memory.save", "memory.search", "memory.start_remembering", "memory.stop_remembering")
        ),
        AgentDefinition(
            id = "project", name = "Builder",
            purpose = "Creates and organizes project folders and manifests.",
            allowedTools = setOf("project.create", "project.overview", "project.plan", "file.write", "file.read", "file.list", "file.search", "file.move")
        ),
        AgentDefinition(
            id = "coding", name = "Forge",
            purpose = "Helps write, edit, and debug code inside projects.",
            allowedTools = setOf("file.read", "file.write", "file.list", "project.create", "file.search")
        ),
        AgentDefinition(
            id = "research", name = "Scout",
            purpose = "Searches the web and memory for information.",
            allowedTools = setOf(
                "memory.search", "web.search", "web.fetch",
                "browser.navigate", "browser.read", "browser.links", "browser.html",
                "browser.click", "browser.type", "browser.scroll",
            )
        ),
        AgentDefinition(
            id = "browser", name = "Weaver",
            purpose = "Drives a real browser — an offscreen WebView exposed as MCP tools. " +
                "Can navigate, read pages, follow links, click, type, and scroll.",
            allowedTools = setOf(
                "browser.navigate", "browser.read", "browser.links", "browser.html",
                "browser.click", "browser.type", "browser.scroll",
                "web.search", "web.fetch",
            )
        ),
        AgentDefinition(
            id = "email", name = "Post",
            purpose = "Saves drafts or opens a prefilled email composer. You tap Send in your email app.",
            allowedTools = setOf("email.send", "email.draft")
        ),
        AgentDefinition(
            id = "calendar", name = "Planner",
            purpose = "Manages the calendar: lists upcoming events and creates new ones.",
            allowedTools = setOf("calendar.list_events", "calendar.create_event", "calendar.cancel_event")
        ),
        AgentDefinition(
            id = "computer", name = "Computer",
            purpose = "OpenCode is the default task workspace in Computer. Paired desktop controls remain available through the desktop bridge.",
            allowedTools = setOf("desktop.write_file", "desktop.run_build", "desktop.run_script", "desktop.control", "desktop.run_goal", "desktop.blender_scene", "logic.inspect", "logic.edit", "logic.receipt", "logic.verify_audio", "logic.screen", "logic.act", "logic.cancel", "logic.run_goal")
        ),
        AgentDefinition(
            id = "phone", name = "Paws",
            purpose = "Controls this phone: opens apps, links, and settings pages.",
            allowedTools = setOf(
                "phone.launch_app", "phone.list_apps", "phone.open_url", "phone.open_settings",
                "phone.play_music", "phone.set_alarm", "phone.set_timer", "phone.flashlight",
                "phone.dnd", "phone.battery", "phone.set_volume", "phone.take_photo",
                "phone.compose_sms", "phone.dial_call", "phone.media_control", "phone.set_brightness",
                "phone.cleanup",
                "phone.bluetooth_devices", "phone.bluetooth_setup",
                "nav.find_places", "nav.navigate", "nav.save_place", "nav.list_saved_places",
                "shopping.store_deals",
                "car.status", "car.drive_mode", "car.trip", "car.eta", "car.help",
                "car.vehicle", "car.obd", "car.obd_connect", "car.cameras",
            )
        ),
        AgentDefinition(
            id = "phone_control", name = "Claw",
            purpose = "Operates accessible app screens through taps, typing and navigation. Requires Accessibility and a working model; some app screens cannot be controlled.",
            allowedTools = setOf(
                "ui.automate", "phone.launch_app", "phone.list_apps", "phone.run_goal"
            ),
            maxConcurrentTasks = 1
        ),
        AgentDefinition(
            id = "media", name = "Canvas",
            purpose = "Generates images, videos and audio using configured backends. Image/video failures are reported without placeholder results.",
            allowedTools = setOf("media.generate_image", "media.generate_video", "media.generate_audio")
        ),
        AgentDefinition(
            id = "robot", name = "LOOI",
            purpose = "Controls only LOOI: drive, turn, neck, headlight. Never controls Cozmo.",
            allowedTools = setOf("robot.move")
        ),
        AgentDefinition(
            id = "cozmo", name = "Cozmo",
            purpose = "Controls only Cozmo: short moves, head, lift, expressions, lights and status. Never controls LOOI.",
            allowedTools = setOf("cozmo.control")
        ),
        AgentDefinition(
            id = "studio", name = "Ink",
            purpose = "Creates comics, YouTube scripts, ads, and posts. Remembers recurring " +
                "characters so comics and cartoon versions share the exact same designs, and " +
                "every finished piece opens in the browser panel for review.",
            allowedTools = setOf(
                "studio.save_character", "studio.get_character",
                "studio.create_comic", "studio.write_script"
            )
        ),
        AgentDefinition(
            id = "car", name = "Cruise",
            purpose = "In-car assistant: drive mode, trip/ETA, vehicle Bluetooth awareness, " +
                "OBD-II diagnostics, and car cameras. Media/phone use phone.* tools " +
                "(MediaSession / dialer) — not full Apple CarPlay ownership.",
            allowedTools = setOf(
                "car.status", "car.drive_mode", "car.trip", "car.eta", "car.help",
                "car.vehicle", "car.obd", "car.obd_connect", "car.cameras",
                "phone.media_control", "phone.set_volume", "phone.dial_call",
                "phone.play_music",
            )
        ),
        AgentDefinition(
            id = "glasses", name = "Shades",
            purpose = "Operates the Meta Ray-Ban glasses: photos through their camera.",
            allowedTools = setOf("glasses.take_photo")
        )
    )

    fun byId(id: String): AgentDefinition? = agents.find { it.id == id }

    /** Which available agent owns a tool, or null when no agent may run it. */
    fun ownerOf(toolId: String): AgentDefinition? =
        agents.firstOrNull { it.available && toolId in it.allowedTools }
}
