package com.example.meowmba.ui.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.neural.NeuralDiagnostics
import com.example.meowmba.core.neural.NeuralMode
import com.example.meowmba.core.neural.NeuralSubstrate
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Surface
import kotlin.math.roundToInt

/**
 * A monitor beside the avatar, with deliberate sensory actions and measured values.
 * Diagnostics come from the controller's existing cadence; this creates no timer.
 * The transparent space outside the sheet keeps the avatar visible and touchable.
 */
@Composable
fun NeuralInfoPanel(
    diagnostics: NeuralDiagnostics?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    desktopPaired: Boolean = false,
    inputNotice: String? = null,
    onUseLocalBrain: () -> Unit = {},
    onPet: () -> Unit = {},
    onFeed: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onPairComputer: () -> Unit = {},
    onEnableNeuralControl: () -> Unit = {},
) {
    val view = neuralInfoPresentation(diagnostics, desktopPaired)
    var details by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize().safeDrawingPadding().testTag("brain-info-overlay")) {
        val landscape = maxWidth > maxHeight
        val sheetWidth = if (landscape) minOf(360.dp, maxWidth * .48f) else maxWidth - 24.dp
        val sheetHeight = if (landscape) maxHeight - 24.dp else maxHeight * .56f
        Surface(
            modifier = Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter)
                .padding(end = if (landscape) 12.dp else 0.dp, bottom = if (landscape) 0.dp else 12.dp)
                .width(sheetWidth).height(sheetHeight).testTag("brain-info-sheet"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Brain activity", style = MaterialTheme.typography.titleLarge)
                        Text(view.network, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close brain activity")
                    }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("brain-info-content"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(view.title, style = MaterialTheme.typography.titleSmall,
                        color = if (view.measurementsAvailable) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface)
                    Text(view.explanation, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (view.showLocalAction) OutlinedButton(onClick = onUseLocalBrain,
                        modifier = Modifier.fillMaxWidth()) { Text("Start local brain") }
                    if (view.showPairAction) OutlinedButton(onClick = onPairComputer,
                        modifier = Modifier.fillMaxWidth()) { Text("Pair computer") }
                    if (view.measurementsAvailable && diagnostics?.mode == NeuralMode.SHADOW) {
                        OutlinedButton(onClick = onEnableNeuralControl, modifier = Modifier.fillMaxWidth()) {
                            Text("Let brain control avatar")
                        }
                    }
                    if (details) {
                        NeuralInfoDetails(diagnostics, view.measurementsAvailable)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            view.metrics.forEach { metric -> NeuralActivityMeter(metric) }
                        }
                        Text(
                            if (!view.measurementsAvailable) "— = unavailable; no activity is being shown."
                            else if (diagnostics?.substrate == NeuralSubstrate.MALECNS_REMOTE)
                                "Measured rates · bars span 0–400 Hz"
                            else "Measured activity · no simulated display effects",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                inputNotice?.takeIf { it.isNotBlank() }?.let {
                    Text(it, Modifier.padding(top = 5.dp).testTag("brain-input-notice"),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPet, enabled = view.interactionEnabled, modifier = Modifier.weight(1f)) {
                        Text("Pet")
                    }
                    OutlinedButton(onClick = onFeed, enabled = view.interactionEnabled, modifier = Modifier.weight(1f)) {
                        Text("Feed")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) { Text("Settings") }
                    OutlinedButton(onClick = { details = !details }, modifier = Modifier.weight(1f)) {
                        Text(if (details) "Less detail" else "Details")
                    }
                }
            }
        }
    }
}

@Composable
private fun NeuralActivityMeter(metric: BrainActivityMetric) {
    Column(Modifier.fillMaxWidth().testTag("brain-metric-${metric.id}").semantics(mergeDescendants = true) {
        contentDescription = "${metric.label}, ${if (metric.value == null) "unavailable" else metric.displayValue}"
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(metric.label, style = MaterialTheme.typography.labelMedium)
            Text(metric.displayValue, style = MaterialTheme.typography.labelMedium)
        }
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            // Zero remains an empty track. Missing/stopped telemetry has no colored fill.
            metric.fraction?.takeIf { it > 0f }?.let { fraction ->
                Box(Modifier.fillMaxWidth(fraction).height(5.dp).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
private fun NeuralInfoDetails(d: NeuralDiagnostics?, measured: Boolean) {
    val core = d?.core
    val remote = d?.substrate == NeuralSubstrate.MALECNS_REMOTE
    val fullGraph = core?.extra?.get("malecns.full_graph") == 1f
    NeuralInfoRow("Core units", core?.units?.toString() ?: "—")
    NeuralInfoRow("Measured steps", d?.steps?.toString() ?: "—")
    NeuralInfoRow("Current action", if (measured) d?.currentAction?.name?.lowercase()?.replace('_', ' ') ?: "—" else "—")
    NeuralInfoRow("Phone step p95", d?.let { "${it.p95StepMicros} µs" } ?: "—")
    NeuralInfoRow("Thermal state", when (d?.thermalTier) {
        null -> "—"
        0 -> "Normal"
        1 -> "Saving power"
        else -> "Cooling"
    })
    if (remote) {
        core?.status?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        fun measuredTime(key: String): String = core?.extra?.get(key)
            ?.takeIf { measured && it.isFinite() && it >= 0f }?.let { "${it.roundToInt()} ms" } ?: "—"
        NeuralInfoRow("Graph", if (fullGraph) "Full annotated graph" else "Retained graph")
        NeuralInfoRow("Connection pairs", if (fullGraph) "25,862,574" else "6,287,749")
        NeuralInfoRow("Anatomical synapse counts", if (fullGraph) "125,024,863" else "90,296,905")
        for ((key, label) in listOf("forward" to "Forward", "backward" to "Backward",
            "halt" to "Stop", "landing" to "Land", "takeoff" to "Take off", "groom2" to "Antenna grooming")) {
            val hz = core?.extra?.get("malecns.${key}_hz")?.takeIf { measured && it.isFinite() }
            NeuralInfoRow(label, hz?.let { "${it.roundToInt()} Hz" } ?: "—")
        }
        NeuralInfoRow("Simulated brain time", measuredTime("malecns.sim_ms"))
        NeuralInfoRow("Meow compute per reply", measuredTime("malecns.compute_ms"))
    }
    Text(
        when (d?.substrate) {
            NeuralSubstrate.MALECNS_REMOTE -> "These are measured population firing rates, not a map of individual synapses. MaleCNS includes annotated brain, optic-lobe and ventral nerve cord neurons. The full graph restores weak connections and self-connections at confidence ≥0.5; unannotated fragments are excluded. Neuron dynamics and avatar movement are modeled; brain time runs independently of animation time. Data: Janelia FlyEM, Cambridge/MRC LMB and Google Research, CC BY 4.0. Simulator: Blendi Remade / fal.ai, MIT."
            NeuralSubstrate.BANC_EXPERIMENTAL -> "These are aggregate values from the BANC anatomical-path adapter: 76 neurons, 515 connection pairs and 3,316 anatomical counts. Connection signs, timing and avatar movement are authored assumptions. This is not a map of individual synapses or a literal biological spike trace."
            else -> "These are measured aggregate values from a small synthetic network, not a map of individual synapses or a biological fly brain. Active units means units above the core's silence threshold."
        },
        modifier = Modifier.testTag("brain-details-explanation"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NeuralInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
    }
}
