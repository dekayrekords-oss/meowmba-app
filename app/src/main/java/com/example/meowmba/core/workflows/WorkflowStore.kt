package com.example.meowmba.core.workflows

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

@Serializable data class BusinessAgent(
    val id: String = UUID.randomUUID().toString(), val name: String,
    val instructions: String, val tools: Set<String>, val delegates: Set<String> = emptySet(),
    val provider: String = "local", val model: String = "", val cloudConsent: Boolean = false,
    val enabled: Boolean = true, val intervalMinutes: Long = 0, val scheduledGoal: String = "",
    val lastScheduledAt: Long = 0,
    val completionTools: Set<String> = emptySet(),
)
@Serializable data class WorkFrame(val agentId: String, val goal: String, val startedAtStep: Int = 0)
@Serializable data class WorkStep(
    val id: String = UUID.randomUUID().toString(), val agentId: String, val tool: String,
    val args: Map<String, String>, val status: String = "PENDING", val output: String = "",
    val approvalRequired: Boolean = false,
    val approvalExpiresAt: Long = 0,
)
@Serializable data class BusinessRun(
    val id: String = UUID.randomUUID().toString(), val agentId: String, val goal: String,
    val status: String = "QUEUED", val frames: List<WorkFrame> = listOf(WorkFrame(agentId, goal)),
    val steps: List<WorkStep> = emptyList(), val note: String = "Queued", val attempts: Int = 0,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
)
@Serializable data class WorkflowSnapshot(
    val version: Int = 1, val agents: List<BusinessAgent> = emptyList(), val runs: List<BusinessRun> = emptyList(),
    val connections: List<BusinessMcpConnection> = emptyList(),
)
@Serializable data class BusinessMcpTool(val name: String, val description: String, val schema: String, val readOnlyHint: Boolean = false)
@Serializable data class BusinessMcpConnection(
    val id: String = UUID.randomUUID().toString(), val name: String, val endpoint: String,
    val tools: List<BusinessMcpTool>, val enabled: Boolean = true, val verifiedAt: Long = System.currentTimeMillis(),
    val automaticReadTools: Set<String> = emptySet(),
)

/** Single repository per process. Writes are fsynced and atomically renamed;
 * a partial checkpoint cannot replace the last known state. Corrupt data fails
 * closed rather than resetting someone's business jobs. Credentials never live here. */
class WorkflowStore(private val file: File) {
    private val codec = Json { ignoreUnknownKeys = true }
    private val mutable = MutableStateFlow(if (file.exists()) codec.decodeFromString<WorkflowSnapshot>(file.readText()) else WorkflowSnapshot())
    val state = mutable.asStateFlow()
    init { require(mutable.value.version == 1) { "Unsupported workflow data version" } }

    @Synchronized fun change(update: (WorkflowSnapshot) -> WorkflowSnapshot) {
        val next = update(mutable.value)
        if (next == mutable.value) return
        val bytes = codec.encodeToString(next).toByteArray()
        check(bytes.size <= 8_000_000) { "Workflow history is full. Export and remove completed runs." }
        file.parentFile?.mkdirs()
        val pending = File(file.parentFile, file.name + ".pending")
        FileOutputStream(pending).use { out -> out.write(bytes); out.fd.sync() }
        Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        mutable.value = next
    }
    fun agent(id: String) = state.value.agents.find { it.id == id }
    fun run(id: String) = state.value.runs.find { it.id == id }
    fun saveAgent(agent: BusinessAgent) {
        require(agent.name.isNotBlank() && agent.name.length <= 80)
        require(agent.instructions.isNotBlank() && agent.instructions.length <= 8000)
        require(agent.provider in setOf("local", "openrouter"))
        require(agent.intervalMinutes == 0L || agent.intervalMinutes in 15..525_600)
        require(agent.scheduledGoal.length <= 8000 && (agent.intervalMinutes == 0L || agent.scheduledGoal.isNotBlank()))
        require(agent.provider != "openrouter" || agent.cloudConsent) { "Approve cloud use before saving this agent" }
        require(agent.id !in agent.delegates)
        require(agent.tools.size <= 16 && agent.delegates.size <= 8) { "Use at most 16 tools and 8 delegates per agent; split larger teams into specialists" }
        require(agent.tools.containsAll(agent.completionTools)) { "Required completion tools must be granted to this agent" }
        change { it.copy(agents = it.agents.filterNot { old -> old.id == agent.id } + agent) }
    }
    fun enqueue(agentId: String, goal: String): String {
        require(goal.isNotBlank() && goal.length <= 8000)
        check(agent(agentId)?.enabled == true) { "Agent is disabled or missing" }
        val run = BusinessRun(agentId = agentId, goal = goal)
        change { it.copy(runs = it.runs + run) }
        return run.id
    }
    fun updateRun(id: String, update: (BusinessRun) -> BusinessRun) = change { snapshot ->
        snapshot.copy(runs = snapshot.runs.map { if (it.id == id) update(it).copy(updatedAt = System.currentTimeMillis()) else it })
    }
    /** Compare-and-set prevents a late model reply from resurrecting a cancelled job. */
    @Synchronized fun transition(expected: BusinessRun, update: (BusinessRun) -> BusinessRun): Boolean {
        if (run(expected.id) != expected) return false
        updateRun(expected.id, update)
        return true
    }
    fun cancel(id: String) = updateRun(id) { if (it.status in TERMINAL) it else it.copy(status = "CANCELLED", note = "Stopped. An action already in progress may still finish; check its recorded result.") }
    fun removeRun(id: String) = change { snapshot ->
        check(snapshot.runs.find { it.id == id }?.status in TERMINAL) { "Stop the job first" }
        snapshot.copy(runs = snapshot.runs.filterNot { it.id == id })
    }
    fun approve(id: String, stepId: String, approved: Boolean) = updateRun(id) { run ->
        check(run.status == "APPROVAL") { "This approval is no longer pending" }
        check(run.steps.lastOrNull()?.id == stepId) { "This step changed; review it again" }
        val expiry = run.steps.last().approvalExpiresAt
        check(!approved || expiry == 0L || System.currentTimeMillis() < expiry) { "Approval expired; deny this action and start a new job" }
        run.copy(status = if (approved) "QUEUED" else "BLOCKED", note = if (approved) "Approved" else "Action denied",
            steps = run.steps.map { if (it.id == stepId) it.copy(status = if (approved) "APPROVED" else "DENIED") else it })
    }
    fun scheduleDue(now: Long) = change { snapshot ->
        val additions = mutableListOf<BusinessRun>()
        val agents = snapshot.agents.map { agent ->
            val busy = snapshot.runs.any { it.agentId == agent.id && it.status !in TERMINAL }
            if (agent.enabled && agent.intervalMinutes in 15..525_600 && agent.scheduledGoal.isNotBlank() && !busy &&
                now - agent.lastScheduledAt >= agent.intervalMinutes * 60_000) {
                additions += BusinessRun(agentId = agent.id, goal = agent.scheduledGoal)
                agent.copy(lastScheduledAt = now)
            } else agent
        }
        snapshot.copy(agents = agents, runs = snapshot.runs + additions)
    }
    companion object { val TERMINAL = setOf("COMPLETE", "BLOCKED", "CANCELLED") }
}
