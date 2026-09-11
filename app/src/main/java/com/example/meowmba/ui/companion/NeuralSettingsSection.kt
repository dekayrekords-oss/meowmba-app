package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.agentic.AgenticGoal
import com.example.meowmba.core.agentic.AgenticModelConfig
import com.example.meowmba.core.agentic.ConnectionProfile
import com.example.meowmba.core.neural.NeuralDiagnostics
import com.example.meowmba.core.neural.NeuralMode
import com.example.meowmba.core.neural.NeuralSubstrate

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
    avatarFpsTarget: Int = 30,
    onAvatarFpsTargetChange: (Int) -> Unit = {},
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Lumen / Neural", style = MaterialTheme.typography.titleMedium)
        Text(
            "Optional reflex and agentic layers for the avatar. Defaults stay off until you enable them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("How to use", style = MaterialTheme.typography.titleSmall)
            Text(
                "• Avatar FPS — 15–60 (default 30). Lower values reduce heat.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "• Lumen reflex + gain — live avatar attention from vision, speech, touch, motion.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "• Agentic — tasks, SSH profiles, and optional model adapter.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "• Tasks — title → Create → Tick (one planner step) → Approve if SSH needs it.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "• Model — optional OpenAI-compatible URL + key ref; empty uses the stub.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "• Terminal is separate: Chat → Terminal → pick an SSH profile for a real machine.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Avatar FPS", style = MaterialTheme.typography.titleSmall)
            Text(
                "Target ${avatarFpsTarget.coerceIn(15, 60)} FPS when cool (default 30). " +
                    "Light thermal quietly halves FPS; sustained warmer states pause avatar work " +
                    "with a mild banner — never an overheating alarm for light load.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = avatarFpsTarget.coerceIn(15, 60).toFloat(),
                onValueChange = { onAvatarFpsTargetChange(it.toInt().coerceIn(15, 60)) },
                valueRange = 15f..60f,
                steps = 44,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Lumen reflex", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = lumenReflexV2, onCheckedChange = onLumenReflexV2Change)
                Text("Enable Lumen reflex")
            }
            if (lumenReflexV2) {
                Text(
                    "Gain ${(reflexGain * 100).toInt()}% — how strongly senses push curiosity / social / caution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = reflexGain.coerceIn(0.25f, 1.5f),
                    onValueChange = { onReflexGainChange(it.coerceIn(0.25f, 1.5f)) },
                    valueRange = 0.25f..1.5f,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Agentic runtime", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = agenticRuntimeV1, onCheckedChange = onAgenticRuntimeV1Change)
                Text("Enable agentic (tasks / SSH / model)")
            }
        }

        if (agenticRuntimeV1) {
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
            AgenticProfilesSection(
                profiles = connectionProfiles,
                onSaveProfile = onSaveConnectionProfile,
                onDeleteProfile = onDeleteConnectionProfile,
                onSaveCredential = onSaveAgenticCredential,
            )
        }

        if (showNeuralCoreControls) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Neural controller", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Shadow mode compares the neural layer without changing avatar behaviour.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                Button(onClick = onReset) {
                    Text("Reset neural state")
                }
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
    Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
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
