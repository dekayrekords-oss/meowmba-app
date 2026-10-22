package com.example.meowmba.core.agent.runtime

import com.example.meowmba.core.agent.tools.ToolOutcome
import com.example.meowmba.core.agentic.AgenticOutput

/** Durable evidence, not a replay queue. Approval grants and tool arguments never enter history. */
interface TaskJournal {
    suspend fun read(): List<AgentTask>
    suspend fun write(task: AgentTask)
    suspend fun remove(id: String)
}

object TaskHistory {
    fun forStorage(task: AgentTask): AgentTask = task.copy(
        args = emptyMap(), log = task.log.takeLast(12).map { AgenticOutput.scrub(it, 240) },
        title = AgenticOutput.scrub(task.title, 400),
        result = task.result?.let { AgenticOutput.scrub(it, 24_000) },
        resultTruncated = task.resultTruncated || (task.result?.length ?: 0) > 24_000,
    )

    fun recover(task: AgentTask, now: Long): AgentTask = if (task.isTerminal) task.copy(recovered = true) else task.copy(
        recovered = true, status = TaskStatus.FAILED, completedAt = now,
        outcome = if (task.executionStarted) ToolOutcome.UNCONFIRMED else ToolOutcome.RESULT,
        result = if (task.executionStarted)
            "Meow was interrupted while this action was running. Its outcome is unconfirmed. Check the destination before asking again; it has not been repeated."
        else "Meow was interrupted before this action ran. It has not been repeated. Ask again when you are ready.",
        log = (task.log + "Interrupted; no automatic replay").takeLast(12),
    )

    fun label(task: AgentTask): String = when {
        task.status == TaskStatus.CANCELLED -> if (task.executionStarted) "Stopped · check outcome" else "Stopped"
        task.outcome == ToolOutcome.UNCONFIRMED -> "Check outcome"
        task.status == TaskStatus.QUEUED -> "Queued"
        task.status == TaskStatus.WAITING_APPROVAL -> "Needs your approval"
        task.status == TaskStatus.RUNNING -> "Working"
        task.status == TaskStatus.FAILED -> if (task.recovered && !task.executionStarted) "Not run" else "Could not finish"
        task.outcome == ToolOutcome.HANDOFF -> "Continue in the opened app"
        task.outcome == ToolOutcome.VERIFIED -> "Verified result"
        else -> "Result available"
    }
}
