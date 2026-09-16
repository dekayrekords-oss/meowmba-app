package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.Surface

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.agent.runtime.AgentDefinition
import com.example.meowmba.core.agent.runtime.AgentRegistry
import com.example.meowmba.core.agent.runtime.AgentTask
import com.example.meowmba.core.agent.runtime.TaskStatus
import com.example.meowmba.core.database.AuditEntryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Agents & Tools screen (opened from the Agents or Tools tile).
 *
 * Sections: agent roster (with honest availability), active tasks (with
 * cancel), the installed tool list with risk levels, and the audit trail.
 */
@Composable
fun AgentsScreen(
    state: CompanionState,
    onCancelTask: (String) -> Unit,
    onSelectAgent: (String?) -> Unit = {},
    onClose: () -> Unit
) {
    // One agent selected: show what that agent has actually done instead of the roster.
    state.selectedAgentId?.let { id ->
        AgentRegistry.byId(id)?.let { agent ->
            AgentDetail(
                agent = agent,
                state = state,
                onCancelTask = onCancelTask,
                onBack = { onSelectAgent(null) },
            )
            return
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Agents & Tools",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Agent roster ────────────────────────────────────────────────
            BusinessAgentsLauncher()
            SectionTitle("Agents")
            Body("Tap an agent for its tools and task history. Tools ready means installed, not that accounts, permissions or hardware have been verified.")
            Spacer(modifier = Modifier.height(8.dp))
            AgentRegistry.agents.forEach { agent ->
                val worked = state.activeTasks.count { it.agentId == agent.id }
                AgentCard(
                    agent = agent,
                    taskCount = worked,
                    registeredTools = state.registeredAgentTools,
                    onClick = { onSelectAgent(agent.id) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Active tasks ────────────────────────────────────────────────
            SectionTitle("Tasks")
            val visibleTasks = state.activeTasks.take(10)
            if (visibleTasks.isEmpty()) {
                Body("No tasks yet. Try: \"create file called notes.txt\" or \"create a project called Website\".")
            } else {
                visibleTasks.forEach { task ->
                    TaskCard(task, onCancel = { onCancelTask(task.id) })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Audit trail ─────────────────────────────────────────────────
            SectionTitle("Audit trail")
            if (state.auditEntries.isEmpty()) {
                Body("Nothing audited yet.")
            } else {
                state.auditEntries.take(20).forEach { entry ->
                    AuditRow(entry)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun Body(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
}

@Composable
private fun AgentCard(
    agent: AgentDefinition,
    taskCount: Int = 0,
    registeredTools: Set<String> = emptySet(),
    onClick: (() -> Unit)? = null,
) {
    val installed = agent.allowedTools.count { it in registeredTools }
    val (label, color) = when {
        !agent.available -> "UNAVAILABLE" to MaterialTheme.colorScheme.error
        installed == 0 -> "NO TOOLS" to MaterialTheme.colorScheme.error
        installed < agent.allowedTools.size -> "PARTIAL" to Color(0xFFD29922)
        else -> "TOOLS READY" to MaterialTheme.colorScheme.primary
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = if (onClick != null) {
            Modifier.fillMaxWidth().clickable(onClick = onClick)
        } else {
            Modifier.fillMaxWidth()
        },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    agent.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (taskCount > 0) {
                    Text(
                        "$taskCount",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        label,
                        color = color,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(agent.purpose, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text("$installed/${agent.allowedTools.size} tools installed · check task results for success",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            agent.statusNote?.let {
                Text(it, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun TaskCard(task: AgentTask, onCancel: () -> Unit) {
    val (label, color) = when (task.status) {
        TaskStatus.QUEUED -> "QUEUED" to MaterialTheme.colorScheme.onSurfaceVariant
        TaskStatus.RUNNING -> "RUNNING" to MaterialTheme.colorScheme.primary
        TaskStatus.WAITING_APPROVAL -> "NEEDS YOU" to Color(0xFFD29922)
        TaskStatus.COMPLETED -> "DONE" to MaterialTheme.colorScheme.primary
        TaskStatus.FAILED -> "FAILED" to MaterialTheme.colorScheme.error
        TaskStatus.CANCELLED -> "CANCELLED" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        task.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                        Text(
                            label,
                            color = color,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                task.result?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 2)
                }
            }
            if (!task.isTerminal) {
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AuditRow(entry: AuditEntryEntity) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            timeFormat.format(Date(entry.timestamp)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "${entry.action} · ${entry.agentId} · ${entry.detail}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            fontSize = 11.sp
        )
    }
}

/**
 * One agent's record: what it is allowed to do, what it is doing, and what it has done.
 *
 * The task list lives in memory and the audit trail lives in Room, so both are shown —
 * tasks answer "what is happening", the audit answers "what happened yesterday".
 */
@Composable
private fun AgentDetail(
    agent: AgentDefinition,
    state: CompanionState,
    onCancelTask: (String) -> Unit,
    onBack: () -> Unit,
) {
    val tasks = state.activeTasks.filter { it.agentId == agent.id }
    val audit = state.auditEntries.filter { it.agentId == agent.id }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text("‹ Agents", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
            }
            Text(
                agent.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(agent.purpose, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("Tool installation")
            if (agent.allowedTools.isEmpty()) {
                Body("No tools assigned.")
            } else {
                Text(
                    agent.allowedTools.sorted().joinToString("\n") {
                        "$it — ${if (it in state.registeredAgentTools) "installed" else "missing"}"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("Worked on")
            if (tasks.isEmpty()) {
                Body("Nothing this session.")
            } else {
                tasks.forEach { task ->
                    TaskCard(task, onCancel = { onCancelTask(task.id) })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("History")
            if (audit.isEmpty()) {
                Body("No audited activity yet.")
            } else {
                audit.take(40).forEach { entry -> AuditRow(entry) }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back to agents")
            }
        }
    }
}
