package com.example.meowmba.core.agent.runtime

import com.example.meowmba.core.agent.tools.ConfirmationPolicy
import com.example.meowmba.core.agent.tools.RiskLevel
import com.example.meowmba.core.agent.tools.ToolRegistry
import com.example.meowmba.core.agent.tools.ToolResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A tool call that needs the user's explicit decision before it may run. */
data class ApprovalRequest(
    val id: String = UUID.randomUUID().toString().take(8),
    val taskId: String,
    val toolId: String,
    val title: String,
    val riskLevel: RiskLevel,
    val detail: String
)

/** Where audit events go. Implementations must not throw. */
interface AuditSink {
    suspend fun log(action: String, agentId: String, taskId: String?, detail: String)
}

sealed class SubmitResult {
    data class Started(val taskId: String, val queued: Boolean) : SubmitResult()
    data class Rejected(val reason: String) : SubmitResult()
}

/**
 * The companion's agent orchestrator (Phase 5).
 *
 * Owns the full task lifecycle: permission checks, per-agent concurrency
 * limits, approval gates (with auto-deny timeout), execution timeouts,
 * cancellation, queue draining, and audit emission. Agents never run tools
 * they don't own; users always have the final say on risky actions.
 *
 * Pure Kotlin + coroutines: fully testable with a TestScope.
 */
class AgentOrchestrator(
    private val scope: CoroutineScope,
    private val toolRegistry: ToolRegistry,
    private val auditSink: AuditSink,
    // 60s was long enough to auto-deny anything the user didn't answer while
    // the phone was in their pocket, and the task then reported "Denied by
    // user" for a decision they never made. Five minutes, and the timeout now
    // says what actually happened.
    private val approvalTimeoutMs: Long = 300_000
) {
    private val _tasks = MutableStateFlow<List<AgentTask>>(emptyList())
    val tasks: StateFlow<List<AgentTask>> = _tasks.asStateFlow()

    private val _pendingApproval = MutableStateFlow<ApprovalRequest?>(null)
    val pendingApproval: StateFlow<ApprovalRequest?> = _pendingApproval.asStateFlow()

    /** Fired when a task reaches a terminal state (for voice feedback). */
    var onTaskCompleted: ((AgentTask) -> Unit)? = null

    private val approvals = ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<Boolean>>()
    private val jobs = ConcurrentHashMap<String, Job>()

    /** Approval requests surface one at a time; the rest wait in order. */
    private val approvalQueue = java.util.concurrent.ConcurrentLinkedQueue<ApprovalRequest>()

    // ── Submission ──────────────────────────────────────────────────────────

    fun submit(agentId: String, toolId: String, args: Map<String, String>, title: String): SubmitResult {
        val agent = AgentRegistry.byId(agentId)
            ?: return SubmitResult.Rejected("Unknown agent '$agentId'")
        if (!agent.available) {
            return SubmitResult.Rejected("${agent.name} isn't available: ${agent.statusNote ?: "no adapter"}")
        }
        if (toolId !in agent.allowedTools) {
            return SubmitResult.Rejected("${agent.name} isn't allowed to use $toolId")
        }
        val tool = toolRegistry.byId(toolId)
            ?: return SubmitResult.Rejected("Tool '$toolId' is not installed")

        // A desktop goal reserves one slot for its approved child action.
        // A second parent waiting on approval would otherwise occupy that slot.
        if (toolId == "desktop.run_goal" && _tasks.value.any { it.toolId == toolId && !it.isTerminal }) {
            return SubmitResult.Rejected("Another desktop goal is active. Stop or finish it first.")
        }

        val task = AgentTask(agentId = agentId, toolId = toolId, title = title, args = args)
        _tasks.value = listOf(task) + _tasks.value

        val running = runningCount(agentId)
        return if (running < agent.maxConcurrentTasks) {
            launchTask(task)
            audit("task.submit", agentId, task.id, title)
            SubmitResult.Started(task.id, queued = false)
        } else {
            audit("task.queued", agentId, task.id, "$title (limit ${agent.maxConcurrentTasks})")
            SubmitResult.Started(task.id, queued = true)
        }
    }

    // ── Approval ────────────────────────────────────────────────────────────

    /** User decision on the currently displayed approval request. */
    fun resolveApproval(approved: Boolean) {
        val request = _pendingApproval.value ?: return
        approvalQueue.remove(request)
        approvals.remove(request.taskId)?.complete(approved)
        showNextApproval()
    }

    // ── Cancellation ────────────────────────────────────────────────────────

    fun cancel(taskId: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        if (task.isTerminal) return
        approvals.remove(taskId)?.complete(false)
        approvalQueue.removeAll { it.taskId == taskId }
        if (_pendingApproval.value?.taskId == taskId) showNextApproval()
        jobs.remove(taskId)?.cancel()
        updateTask(taskId) { it.copy(status = TaskStatus.CANCELLED, completedAt = System.currentTimeMillis()) }
        audit("task.cancelled", task.agentId, taskId, task.title)
        drainQueue(task.agentId)
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private fun launchTask(task: AgentTask) {
        // Reserve the slot before dispatch: multiple submits can happen before
        // the dispatcher runs the first coroutine (and otherwise exceed the limit).
        updateTask(task.id) { it.copy(status = TaskStatus.RUNNING, startedAt = System.currentTimeMillis()) }
        jobs[task.id] = scope.launch {
            try {
                runTask(task)
            } finally {
                jobs.remove(task.id)
            }
        }
    }

    private suspend fun runTask(task: AgentTask) {
        val tool = toolRegistry.byId(task.toolId) ?: run {
            failTask(task, "Tool '${task.toolId}' disappeared")
            return
        }

        updateTask(task.id) { it.copy(status = TaskStatus.RUNNING, startedAt = System.currentTimeMillis()) }
        audit("task.start", task.agentId, task.id, task.title)

        // Approval gate: risky tools wait for the user's explicit decision
        if (tool.definition.confirmationPolicy == ConfirmationPolicy.ALWAYS) {
            updateTask(task.id) { it.copy(status = TaskStatus.WAITING_APPROVAL) }
            val argsSummary = task.args.entries.joinToString("\n") {
                val limit = if (task.toolId == "desktop.control" && it.key in setOf("value", "text")) 8000 else if (it.key == "goal") 3000 else 160
                "${it.key}=${it.value.take(limit)}${if (it.value.length > limit) " … (truncated; review before approving)" else ""}"
            }
            val request = ApprovalRequest(
                taskId = task.id,
                toolId = task.toolId,
                title = task.title,
                riskLevel = tool.definition.riskLevel,
                detail = "${tool.definition.description}\n$argsSummary"
            )
            val decision = kotlinx.coroutines.CompletableDeferred<Boolean>()
            approvals[task.id] = decision
            approvalQueue.add(request)
            if (_pendingApproval.value == null) showNextApproval()
            audit("task.approval_requested", task.agentId, task.id, task.title)

            var timedOut = false
            val approved = withTimeoutOrNull(approvalTimeoutMs) { decision.await() } ?: run {
                timedOut = true
                audit("task.approval_timeout", task.agentId, task.id, "auto-denied")
                false
            }
            approvals.remove(task.id)
            // If this request is still the displayed one (timeout path), advance the queue
            if (approvalQueue.remove(request) && _pendingApproval.value?.id == request.id) {
                showNextApproval()
            }

            if (!approved) {
                failTask(
                    task,
                    if (timedOut) {
                        "No answer on the approval card after ${approvalTimeoutMs / 1000}s, " +
                            "so it wasn't run. Ask again to retry."
                    } else {
                        "Denied by user"
                    }
                )
                return
            }
            audit("task.approved", task.agentId, task.id, task.title)
            updateTask(task.id) { it.copy(status = TaskStatus.RUNNING) }
        }

        // Execute with the tool's own timeout.
        //
        // A tool that THROWS rather than returning ToolResult.Failure used to
        // take the whole process with it: this runs in a coroutine scope with
        // no exception handler, so a single unchecked exception anywhere in any
        // tool — a malformed intent, a SecurityException from a revoked
        // permission, a parser meeting input it did not expect — became a crash
        // instead of a failed task. Tools are the least trusted code in the
        // app (several parse model-authored arguments), so the boundary belongs
        // here rather than in each of the two dozen implementations.
        //
        // CancellationException is deliberately not caught: swallowing it would
        // break the timeout above and leave tasks un-cancellable.
        val result = try {
            withTimeoutOrNull(tool.definition.timeoutMs) {
                tool.execute(task.args)
            } ?: ToolResult.Failure("Tool timed out after ${tool.definition.timeoutMs}ms")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            runCatching {
                android.util.Log.w("AgentOrchestrator", "tool ${tool.definition.id} threw", e)
            }
            ToolResult.Failure(
                "${tool.definition.displayName} failed: ${e.message ?: e::class.java.simpleName}"
            )
        }

        when (result) {
            is ToolResult.Success -> completeTask(task, result.output)
            is ToolResult.Failure -> failTask(task, result.error)
        }
    }

    private fun completeTask(task: AgentTask, output: String) {
        val finalTask = updateTask(task.id) {
            it.copy(status = TaskStatus.COMPLETED, result = output, completedAt = System.currentTimeMillis())
        }
        audit("task.complete", task.agentId, task.id, output.take(120))
        finalTask?.let { onTaskCompleted?.invoke(it) }
        drainQueue(task.agentId)
    }

    private fun failTask(task: AgentTask, error: String) {
        val finalTask = updateTask(task.id) {
            it.copy(status = TaskStatus.FAILED, result = error, completedAt = System.currentTimeMillis())
        }
        audit("task.fail", task.agentId, task.id, error.take(120))
        finalTask?.let { onTaskCompleted?.invoke(it) }
        drainQueue(task.agentId)
    }

    /** Start the oldest queued task when a concurrency slot frees up. */
    private fun drainQueue(agentId: String) {
        val agent = AgentRegistry.byId(agentId) ?: return
        if (runningCount(agentId) >= agent.maxConcurrentTasks) return
        val next = _tasks.value
            .filter { it.agentId == agentId && it.status == TaskStatus.QUEUED }
            .minByOrNull { it.createdAt }   // oldest queued genuinely goes first (was maxBy: LIFO, starved old tasks)
            ?: return
        launchTask(next)
    }

    private fun runningCount(agentId: String): Int =
        _tasks.value.count {
            it.agentId == agentId &&
                (it.status == TaskStatus.RUNNING || it.status == TaskStatus.WAITING_APPROVAL)
        }

    private fun showNextApproval() {
        _pendingApproval.value = approvalQueue.peek()
    }

    private fun updateTask(id: String, transform: (AgentTask) -> AgentTask): AgentTask? {
        var updated: AgentTask? = null
        _tasks.value = _tasks.value.map { task ->
            if (task.id == id) {
                transform(task).also { updated = it }
            } else {
                task
            }
        }
        return updated
    }

    /** Fire-and-forget audit emission; never blocks or throws into callers. */
    private fun audit(action: String, agentId: String, taskId: String?, detail: String) {
        scope.launch {
            runCatching { auditSink.log(action, agentId, taskId, detail) }
        }
    }
}
