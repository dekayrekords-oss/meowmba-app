package com.example.meowmba.ui.companion

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
    onClose: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D1117)) {
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
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Agent roster ────────────────────────────────────────────────
            SectionTitle("Agents")
            AgentRegistry.agents.forEach { agent ->
                AgentCard(agent)
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
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun Body(text: String) {
    Text(text, color = Color.Gray, fontSize = 13.sp)
}

@Composable
private fun AgentCard(agent: AgentDefinition) {
    val (label, color) = when {
        !agent.available -> "UNAVAILABLE" to Color(0xFFF85149)
        agent.statusNote != null -> "LIMITED" to Color(0xFFD29922)
        else -> "ACTIVE" to Color(0xFF3FB950)
    }
    Surface(color = Color(0xFF161B22), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    agent.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
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
            Text(agent.purpose, color = Color.Gray, fontSize = 12.sp)
            agent.statusNote?.let {
                Text(it, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun TaskCard(task: AgentTask, onCancel: () -> Unit) {
    val (label, color) = when (task.status) {
        TaskStatus.QUEUED -> "QUEUED" to Color.Gray
        TaskStatus.RUNNING -> "RUNNING" to Color(0xFF58A6FF)
        TaskStatus.WAITING_APPROVAL -> "NEEDS YOU" to Color(0xFFD29922)
        TaskStatus.COMPLETED -> "DONE" to Color(0xFF3FB950)
        TaskStatus.FAILED -> "FAILED" to Color(0xFFF85149)
        TaskStatus.CANCELLED -> "CANCELLED" to Color.Gray
    }
    Surface(color = Color(0xFF161B22), shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        task.title,
                        color = Color.White,
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
                    Text(it, color = Color.Gray, fontSize = 11.sp, maxLines = 2)
                }
            }
            if (!task.isTerminal) {
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = Color(0xFFF85149), fontSize = 12.sp)
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
            color = Color.Gray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "${entry.action} · ${entry.agentId} · ${entry.detail}",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp
        )
    }
}
