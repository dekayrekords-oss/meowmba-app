package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.FilterChip
import com.example.meowmba.ui.theme.Surface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.neural.*
import kotlin.math.roundToInt

/** Settings only: keeps the existing persisted callbacks and adds no polling. */
@Composable
internal fun NeuralSetupControls(
    mode: NeuralMode, substrate: NeuralSubstrate, traceConsent: Boolean,
    diagnostics: NeuralDiagnostics?, showCore: Boolean,
    onMode: (NeuralMode) -> Unit, onSubstrate: (NeuralSubstrate) -> Unit,
    onTrace: (Boolean) -> Unit, onReset: () -> Unit,
    reflex: Boolean, onReflex: (Boolean) -> Unit,
    gain: Float, onGain: (Float) -> Unit, fps: Int, onFps: (Int) -> Unit,
) {
    if (showCore) {
        SetupHelpCard("1 · Who controls the avatar?", "Shadow is the default. Select Neural when you want the network to influence behavior.") {
            Column(Modifier.selectableGroup()) {
                NeuralOption("Classic", "Uses the authored behavior system. The neural network does not run; this saves its extra processing.", mode == NeuralMode.LEGACY) { onMode(NeuralMode.LEGACY) }
                NeuralOption("Shadow · compare", "Runs both systems, but Classic still controls the avatar. Useful for observing the network before giving it control.", mode == NeuralMode.SHADOW) { onMode(NeuralMode.SHADOW) }
                NeuralOption("Neural · control", "Lets the network influence attention, drives and allowed actions. Authored animation and safety rules still apply, with Classic as a fallback.", mode == NeuralMode.NEURAL) { onMode(NeuralMode.NEURAL) }
            }
        }
        SetupHelpCard("2 · Choose a network", "Local networks work offline. MaleCNS runs the larger fly model on Meow through your paired computer.") {
            if (mode == NeuralMode.LEGACY) HelpText("Your choice is saved, but the network stays idle in Classic mode.")
            Column(Modifier.selectableGroup()) {
                NeuralOption("Recurrent · rate", "Uses continuous activity that carries information between steps. The default and faster option in our phone comparison.", substrate == NeuralSubstrate.RATE) { onSubstrate(NeuralSubstrate.RATE) }
                NeuralOption("Spiking · experimental", "Uses simulated firing pulses and membrane state. Useful for comparison; it took more processing in our phone test and is not proven more capable.", substrate == NeuralSubstrate.SPIKING) { onSubstrate(NeuralSubstrate.SPIKING) }
                NeuralOption("BANC fly circuit · experimental", "Routes touch through named leg/thorax nociceptor groups and DNg27 to wing-power neurons from a 76-neuron BANC extract. Connection signs, timing and flight/muscle behavior are bounded authored assumptions; this is not FlyWire or a complete biological fly brain.", substrate == NeuralSubstrate.BANC_EXPERIMENTAL) { onSubstrate(NeuralSubstrate.BANC_EXPERIMENTAL) }
                NeuralOption("MaleCNS · Meow brain", "Runs 176,422 fly neurons and 6.29 million retained connections on Meow. Keep your paired computer online with its MaleCNS helper enabled. Food, touch and looming cues stimulate named circuits; motor activity influences reactions and wings. Requires a connection; Classic takes over when replies are unavailable. Neural dynamics and avatar movements are modeled, not a validated biological fly.", substrate == NeuralSubstrate.MALECNS_REMOTE) { onSubstrate(NeuralSubstrate.MALECNS_REMOTE) }
            }
            if (substrate == NeuralSubstrate.MALECNS_REMOTE) {
                HelpText("Sends six numeric sensory values through the paired Desktop Bridge. This feature does not send camera images, audio or chat. It pauses when hidden or too hot. Use Brain activity to check the live connection.")
                diagnostics?.core?.status?.takeIf { it.isNotBlank() }?.let { HelpText(it) }
            }
            HelpText("Switching networks resets its transient state and turns off trace recording.")
        }
    }
    SetupHelpCard("Sensory reactions", "Lumen reflex adds quick visual reactions to available touch, motion, speech and vision signals.") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enable Lumen reflex", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Switch(checked = reflex, onCheckedChange = onReflex)
        }
        HelpText("Camera and microphone keep their own controls. Enabling reflex does not turn them on.")
        Text("Sensitivity · ${(gain.coerceIn(.25f, 1.5f) * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall)
        Slider(value = gain.coerceIn(.25f, 1.5f), onValueChange = onGain,
            valueRange = .25f..1.5f, enabled = reflex)
        HelpText("25% is subtle, 100% is the default, and 150% gives stronger reactions. This changes response strength, not learning speed. Enable reflex to adjust it.")
    }
    SetupHelpCard("Animation & battery", "Adjust visual smoothness independently of the neural network.") {
        Text("Animation target · ${fps.coerceIn(15, 60)} FPS", style = MaterialTheme.typography.titleSmall)
        Slider(value = fps.coerceIn(15, 60).toFloat(), onValueChange = { onFps(it.roundToInt()) }, valueRange = 15f..60f, steps = 44)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 60).forEach { target ->
                FilterChip(selected = fps == target, onClick = { onFps(target) }, label = { Text("$target FPS") })
            }
        }
        HelpText("15 uses less animation work; 30 is the default; 60 requests smoother motion with more work. Idle motion runs more slowly. Android reduces the cadence when warm, keeps a sparse motion cadence at moderate heat, and pauses at severe heat.")
    }
    if (showCore) {
        var details by rememberSaveable { mutableStateOf(false) }
        SetupHelpCard("Activity & troubleshooting", "Use the Brain tile for a live overview. Recording is optional and off by default.") {
            TextButton(onClick = { details = !details }) { Text(if (details) "Hide diagnostics" else "Show diagnostics") }
            if (details) {
                diagnostics?.let { d ->
                    Text("${d.steps} steps · ${d.currentAction.name.lowercase().replace('_', ' ')}", style = MaterialTheme.typography.titleSmall)
                    HelpText("Step time p95: ${d.p95StepMicros} µs. This means 95% of measured steps completed within this time.")
                    HelpText("Fallback: ${d.fallback.name.lowercase().replace('_', ' ')}. A fallback uses Classic when neural output cannot be used.")
                } ?: HelpText("Waiting for the controller's first status update.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Record replay trace", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = traceConsent, onCheckedChange = onTrace, enabled = substrate != NeuralSubstrate.MALECNS_REMOTE)
                }
                if (substrate == NeuralSubstrate.MALECNS_REMOTE) HelpText("Local replay recording is unavailable for this remote model. Its neural clock and state live on Meow; the Brain view shows measured population rates.")
                HelpText("Saves numeric sensory features and controller state locally for replay testing. It does not record raw audio or images. Starting a trace resets neural state so the replay has a known starting point.")
                OutlinedButton(onClick = onReset) { Text("Reset neural state") }
                HelpText("Restarts the network's transient state and stops tracing. It keeps your avatar selection and care preferences.")
            }
        }
    }
}

@Composable
internal fun SetupHelpCard(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            HelpText(description)
            content()
        }
    }
}

@Composable
private fun HelpText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NeuralOption(title: String, explanation: String, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect).padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            HelpText(explanation)
        }
    }
}
