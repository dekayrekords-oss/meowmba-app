package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Agentic / Tasks", style = MaterialTheme.typography.titleMedium)
        Text(
            "Create a goal and run the planner tick loop. Side-effecting SSH waits for approval. " +
                "HTTP model is optional (SecureStorage refs); falls back to stub when unset.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))

        Text(
            "SSH path: $sshCapabilitySummary",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("New goal title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
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
            ) { Text("Create goal") }
            Spacer(Modifier.padding(horizontal = 6.dp))
            OutlinedButton(
                onClick = {
                    onTickOpenGoals()
                    status = "Ticked open goals"
                    refreshNonce++
                    onRefresh()
                },
            ) { Text("Tick open") }
            Spacer(Modifier.padding(horizontal = 6.dp))
            OutlinedButton(onClick = { refreshNonce++; onRefresh() }) { Text("Refresh") }
        }

        Spacer(Modifier.height(10.dp))
        if (goals.isEmpty()) {
            Text("No open goals.", style = MaterialTheme.typography.bodySmall)
        } else {
            goals.forEach { g ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        "${g.title.take(60)}  [${g.status}]  steps=${g.budgets.stepsUsed}/${g.budgets.maxSteps}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row {
                        OutlinedButton(onClick = {
                            onTickGoal(g.id)
                            status = "Ticked ${g.id.take(8)}"
                            onRefresh()
                        }) { Text("Tick") }
                        if (g.status == GoalStatus.WAITING_APPROVAL) {
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Button(onClick = {
                                onApproveGoal(g.id)
                                onRefresh()
                            }) { Text("Approve") }
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            OutlinedButton(onClick = {
                                onRejectGoal(g.id)
                                onRefresh()
                            }) { Text("Reject") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Model adapter (OpenAI-compatible)", style = MaterialTheme.typography.titleSmall)
        Text(
            "Base URL + credential ref. API key is stored under agentic_cred_<ref>. " +
                "Unset → StubModelAdapter.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Base URL (e.g. https://api.openai.com)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        OutlinedTextField(
            value = apiKeyRef,
            onValueChange = { apiKeyRef = it },
            label = { Text("API key credential ref") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        OutlinedTextField(
            value = apiKeySecret,
            onValueChange = { apiKeySecret = it },
            label = { Text("API key (write-only)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        OutlinedTextField(
            value = modelName,
            onValueChange = { modelName = it },
            label = { Text("Model name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
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
        ) { Text("Save model config") }
        Text(
            if (modelConfig.configured) "Active: HTTP (${modelConfig.modelName})"
            else "Active: StubModelAdapter",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
        // refreshNonce kept to force recomposition after actions when parent reloads lists
        @Suppress("UNUSED_VARIABLE")
        val _nonce = refreshNonce
    }
}
