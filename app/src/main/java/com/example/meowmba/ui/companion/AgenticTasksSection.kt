package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.example.meowmba.ui.theme.Button
import androidx.compose.material3.MaterialTheme
import com.example.meowmba.ui.theme.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
@OptIn(ExperimentalLayoutApi::class)
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
    modelOnly: Boolean = false,
) {
    var title by rememberSaveable { mutableStateOf("") }
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
        if (!modelOnly) {
        Text("Tasks", style = MaterialTheme.typography.titleSmall)
        Text(
            "Describe a task, create it, then run its next step. Review any approval request before continuing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("What should Meow do?") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        FlowRow(
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
                    status = "Task submitted"
                    refreshNonce++
                    onRefresh()
                },
            ) { Text("Create task") }
            OutlinedButton(
                onClick = {
                    onTickOpenGoals()
                    status = "Requested the next step for open tasks"
                    refreshNonce++
                    onRefresh()
                },
            ) { Text("Run next step") }
            OutlinedButton(onClick = { refreshNonce++; onRefresh() }) { Text("Refresh") }
        }

        if (goals.isEmpty()) {
            Text("No tasks yet.", style = MaterialTheme.typography.bodySmall)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                goals.forEach { g ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(g.title, style = MaterialTheme.typography.titleSmall)
                        val stateLabel = when(g.status) {
                            GoalStatus.OPEN -> "Ready"
                            GoalStatus.RUNNING -> "Working"
                            GoalStatus.BLOCKED -> "Needs attention"
                            GoalStatus.WAITING_APPROVAL -> "Waiting for your approval"
                            GoalStatus.DONE -> "Completed"
                            GoalStatus.CANCELLED -> "Cancelled"
                        }
                        Text("$stateLabel · ${g.budgets.stepsUsed} of ${g.budgets.maxSteps} steps",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                onTickGoal(g.id)
                                status = "Next step requested"
                                onRefresh()
                            }) { Text("Run next step") }
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

        }
        if (modelOnly) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Task planning model", style = MaterialTheme.typography.titleSmall)
            Text(
                "Use an OpenAI-compatible provider for task planning. Without a configured model, only the built-in planner is available.",
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
                        "Configuration incomplete — using the built-in planner"
                    }
                    onRefresh()
                },
            ) { Text("Save model") }
            Text(
                if (modelConfig.configured) "Active: HTTP (${modelConfig.modelName})"
                else "Using the built-in planner",
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
