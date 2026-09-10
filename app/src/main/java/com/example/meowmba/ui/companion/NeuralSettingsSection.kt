package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.neural.NeuralDiagnostics
import com.example.meowmba.core.neural.NeuralMode
import com.example.meowmba.core.neural.NeuralSubstrate

/**
 * Small, deliberately explicit settings block for the neural controller.
 *
 * It is unmounted until the integration step wires its callbacks to
 * `NeuralModeStore` and `NeuralBehaviorController`; keeping the section
 * callback-driven makes it safe to preview and easy to use from the existing
 * settings state without adding a second source of truth.
 */
@Composable
fun NeuralSettingsSection(
    mode: NeuralMode,
    substrate: NeuralSubstrate,
    traceConsent: Boolean,
    diagnostics: NeuralDiagnostics?,
    onModeChanged: (NeuralMode) -> Unit,
    onSubstrateChanged: (NeuralSubstrate) -> Unit,
    onTraceConsentChanged: (Boolean) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
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