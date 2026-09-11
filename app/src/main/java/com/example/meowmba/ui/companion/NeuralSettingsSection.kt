package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.neural.NeuralDiagnostics
import com.example.meowmba.core.neural.NeuralMode
import com.example.meowmba.core.neural.NeuralSubstrate
import com.example.meowmba.core.agentic.AgenticGoal
import com.example.meowmba.core.agentic.AgenticModelConfig
import com.example.meowmba.core.agentic.ConnectionProfile

/**
 * Neural controller + Lumen nervous-system settings.
 *
 * Lumen reflex / agentic runtime toggles are wired to [com.example.meowmba.core.config.FeatureFlags]
 * (defaults OFF). Neural mode/substrate callbacks remain optional until that layer is mounted.
 */
@Composable
fun NeuralSettingsSection(
    mode: NeuralMode = NeuralMode.SHADOW,
    substrate: NeuralSubstrate = NeuralSubstrate.RATE,
    traceConsent: Boolean = false,
    diagnostics: NeuralDiagnostics? = null,
    onModeChanged: (NeuralMode) -> Unit = {},
    onSubstrateChanged: (NeuralSubstrate) -> Unit = {},
    onTraceConsentChanged: (Boolean) -> Unit = {},
    onReset: () -> Unit = {},
    lumenReflexV2: Boolean = false,
    onLumenReflexV2Change: (Boolean) -> Unit = {},
    agenticRuntimeV1: Boolean = false,
    onAgenticRuntimeV1Change: (Boolean) -> Unit = {},
    reflexGain: Float = 1f,
    onReflexGainChange: (Float) -> Unit = {},
    showNeuralCoreControls: Boolean = false,
    connectionProfiles: List<ConnectionProfile> = emptyList(),
    onSaveConnectionProfile: (ConnectionProfile) -> Unit = {},
    onDeleteConnectionProfile: (String) -> Unit = {},
    onSaveAgenticCredential: (String, String) -> Unit = { _, _ -> },
    agenticGoals: List<AgenticGoal> = emptyList(),
    agenticModelConfig: AgenticModelConfig.Snapshot = AgenticModelConfig.Snapshot(
        baseUrl = "",
        apiKeyRef = "",
        modelName = AgenticModelConfig.DEFAULT_MODEL,
        configured = false,
    ),
    sshCapabilitySummary: String = "not probed",
    onCreateAgenticGoal: (String) -> Unit = {},
    onTickAgenticGoal: (String) -> Unit = {},
    onTickOpenAgenticGoals: () -> Unit = {},
    onApproveAgenticGoal: (String) -> Unit = {},
    onRejectAgenticGoal: (String) -> Unit = {},
    onRefreshAgenticTasks: () -> Unit = {},
    onSaveAgenticModelConfig: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Lumen nervous system", style = MaterialTheme.typography.titleMedium)
        Text(
            "Optional reflex layer that modulates Lumen from vision, speech, touch and motion. " +
                "Defaults off; enabling wires end-to-end without FlyWire data.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = lumenReflexV2, onCheckedChange = onLumenReflexV2Change)
            Text("Enable Lumen reflex v2")
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = agenticRuntimeV1, onCheckedChange = onAgenticRuntimeV1Change)
            Text("Enable agentic runtime v1 (tasks + SSH + model)")
        }
        if (lumenReflexV2) {
            Text(
                "Reflex gain ${(reflexGain * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Slider(
                value = reflexGain.coerceIn(0.25f, 1.5f),
                onValueChange = { onReflexGainChange(it.coerceIn(0.25f, 1.5f)) },
                valueRange = 0.25f..1.5f,
            )
            Text(
                "Gain scales how strongly sensory drives push curiosity / social / caution. " +
                    "Action competition hysteresis is unchanged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }


        if (agenticRuntimeV1) {
            Spacer(Modifier.height(16.dp))
            AgenticTasksSection(
                goals = agenticGoals,
                modelConfig = agenticModelConfig,
                sshCapabilitySummary = sshCapabilitySummary,
                onCreateGoal = onCreateAgenticGoal,
                onTickGoal = onTickAgenticGoal,
                onTickOpenGoals = onTickOpenAgenticGoals,
                onApproveGoal = onApproveAgenticGoal,
                onRejectGoal = onRejectAgenticGoal,
                onRefresh = onRefreshAgenticTasks,
                onSaveModelConfig = onSaveAgenticModelConfig,
            )
            Spacer(Modifier.height(16.dp))
            AgenticProfilesSection(
                profiles = connectionProfiles,
                onSaveProfile = onSaveConnectionProfile,
                onDeleteProfile = onDeleteConnectionProfile,
                onSaveCredential = onSaveAgenticCredential,
            )
        }

        if (showNeuralCoreControls) {
            Spacer(Modifier.height(16.dp))
            Text("Neural controller", style = MaterialTheme.typography.titleMedium)
            Text(
                "Shadow mode compares the neural layer without changing avatar behaviour.",
                style = MaterialTheme.typography.bodySmall,
            )
            NeuralChoiceRow(
                label = "Mode",
                choices = NeuralMode.entries.toList(),
                selected = mode,
                onSelected = onModeChanged,
            )
            NeuralChoiceRow(
                label = "Substrate",
                choices = NeuralSubstrate.entries.toList(),
                selected = substrate,
                onSelected = onSubstrateChanged,
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = traceConsent, onCheckedChange = onTraceConsentChanged)
                Text("Allow feature-only neural traces")
            }
            diagnostics?.let {
                Text(
                    "Core: ${it.substrate.name.lowercase()}  " +
                        "steps ${it.steps}  p95 ${it.p95StepMicros}µs  " +
                        "fallback ${it.fallback.name.lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Shadow divergence ${"%.3f".format(it.divergenceMean)} · " +
                        "action ${it.currentAction.name.lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = onReset, modifier = Modifier.padding(top = 4.dp)) {
                Text("Reset neural state")
            }
        }
    }
}

@Composable
private fun <T : Enum<T>> NeuralChoiceRow(
    label: String,
    choices: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(label, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium)
        choices.forEach { choice ->
            Button(
                onClick = { onSelected(choice) },
                enabled = choice != selected,
                modifier = Modifier.padding(end = 4.dp),
            ) {
                Text(choice.name.lowercase().replace('_', ' '))
            }
        }
    }
}
