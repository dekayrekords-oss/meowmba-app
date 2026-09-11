package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.agentic.AgenticGoal
import com.example.meowmba.core.agentic.AgenticModelConfig
import com.example.meowmba.core.agentic.GoalStatus

/**
 * Main UX entry for agentic goals: create a goal, run tick, show open goals,
 * approve/reject waiting SSH, and configure the optional HTTP model adapter.
 */
@Composable
fun AgenticTasksSection(
    goals: List<AgenticGoal>,
    modelConfig: AgenticModelConfig.Snapshot,
    sshCapabilitySummary: String,
    onCreateGoal: (title: String) -> Unit,
    onTickGoal: (goalId: String) -> Unit,
    onTickOpenGoals: () -> Unit,
    onApproveGoal: (goalId: String) -> Unit,
    onRejectGoal: (goalId: String) -> Unit,
    onRefresh: () -> Unit,
    onSaveModelConfig: (baseUrl: String, apiKeyRef: String, apiKeySecret: String, modelName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by remember { mutableStateOf("") }
    var baseUrl by remember(modelConfig.baseUrl) { mutableStateOf(modelConfig.baseUrl) }
    var apiKeyRef by remember(modelConfig.apiKeyRef) { mutableStateOf(modelConfig.apiKeyRef) }
    var apiKeySecret by remember { mutableStateOf("") }
    var modelName by remember(modelConfig.modelName) { mutableStateOf(modelConfig.modelName) }
    var status by remember { mutableStateOf<String?>(null) }
    var refreshNonce by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Tasks", style = MaterialTheme.typography.titleSmall)
        Text(
            "Title → Create → Tick (one planner step) → Approve if SSH needs it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            sshCapabilitySummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("New goal title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val t = title.trim()
                    if (t.isEmpty()) {
                        status = "Enter a goal title"
                        return@Button
                    }
                    onCreateGoal(t)
                    title = ""
                    status = "Created goal"
                    refreshNonce++
                    onRefresh()
                },
            ) { Text("Create") }
            OutlinedButton(
                onClick = {
                    onTickOpenGoals()
                    status = "Ticked open goals"
                    refreshNonce++
                    onRefresh()
                },
            ) { Text("Tick") }
            OutlinedButton(onClick = { refreshNonce++; onRefresh() }) { Text("Refresh") }
        }

        if (goals.isEmpty()) {
            Text("No open goals.", style = MaterialTheme.typography.bodySmall)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                goals.forEach { g ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "${g.title.take(60)}  [${g.status}]  " +
                                "steps=${g.budgets.stepsUsed}/${g.budgets.maxSteps}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                onTickGoal(g.id)
                                status = "Ticked ${g.id.take(8)}"
                                onRefresh()
                            }) { Text("Tick") }
                            if (g.status == GoalStatus.WAITING_APPROVAL) {
                                Button(onClick = {
                                    onApproveGoal(g.id)
                                    onRefresh()
                                }) { Text("Approve") }
                                OutlinedButton(onClick = {
                                    onRejectGoal(g.id)
                                    onRefresh()
                                }) { Text("Reject") }
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Model", style = MaterialTheme.typography.titleSmall)
            Text(
                "Optional OpenAI-compatible URL + key ref. Empty = stub.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKeyRef,
                onValueChange = { apiKeyRef = it },
                label = { Text("API key credential ref") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKeySecret,
                onValueChange = { apiKeySecret = it },
                label = { Text("API key (write-only)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("Model name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onSaveModelConfig(baseUrl.trim(), apiKeyRef.trim(), apiKeySecret, modelName.trim())
                    apiKeySecret = ""
                    status = if (baseUrl.isNotBlank() && apiKeyRef.isNotBlank()) {
                        "Saved HTTP model config (ref=$apiKeyRef)"
                    } else {
                        "Cleared / incomplete — using stub"
                    }
                    onRefresh()
                },
            ) { Text("Save model") }
            Text(
                if (modelConfig.configured) "Active: HTTP (${modelConfig.modelName})"
                else "Active: stub",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        @Suppress("UNUSED_VARIABLE")
        val _nonce = refreshNonce
    }
}
