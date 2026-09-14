package com.example.meowmba.ui.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.neural.NeuralDiagnostics
import kotlin.math.roundToInt

/**
 * Read-only neural telemetry for curious users. The controller exposes
 * aggregate activity, not individual biological spikes, so the bars are
 * explicitly labelled as a visualisation of the running core. No new timer is
 * created here: [CompanionViewModel] publishes diagnostics once per second.
 */
@Composable
fun NeuralInfoPanel(
    diagnostics: NeuralDiagnostics?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = diagnostics
    val core = d?.core
    val activity = (core?.meanAbsActivity ?: 0f).coerceIn(0f, 1f)
    val saturation = core?.saturatedUnits ?: 0
    val silent = core?.silentUnits ?: 0
    val units = core?.units ?: 0
    // Deterministic bars avoid random "activity" that would misrepresent the
    // brain. Their variation is derived from measured aggregate activity.
    val bars = remember(activity, d?.steps, d?.substrate) {
        List(18) { i ->
            (activity * (0.55f + ((i * 17 + (d?.steps ?: 0L) % 23) % 46) / 100f))
                .coerceIn(0.02f, 1f)
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .72f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.92f).padding(12.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Brain activity", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Live aggregate telemetry from Meow's behavior controller",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close brain activity")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(74.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    bars.forEach { value ->
                        Box(
                            Modifier.weight(1f).fillMaxWidth().height((8 + value * 62).dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = .45f + value * .5f)),
                        )
                    }
                }
                Text(
                    "Activity visualization · ${((activity * 100).roundToInt())}% mean signal",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NeuralInfoRow("Mode", d?.mode?.name?.lowercase() ?: "starting")
                NeuralInfoRow("Network", d?.substrate?.name?.lowercase() ?: "—")
                NeuralInfoRow("Core units", if (core == null) "—" else "$units  ·  $silent silent  ·  $saturation saturated")
                NeuralInfoRow("Simulation", if (d == null) "—" else "${d.steps} steps  ·  p95 ${d.p95StepMicros} µs")
                NeuralInfoRow("Current action", d?.currentAction?.name?.lowercase() ?: "idle")
                NeuralInfoRow("Thermal tier", d?.thermalTier?.toString() ?: "—")
                if (d?.substrate == com.example.meowmba.core.neural.NeuralSubstrate.BANC_EXPERIMENTAL) {
                    NeuralInfoRow("BANC wing power", "${((core?.extra?.get("banc.wing_power") ?: 0f) * 100).roundToInt()}%")
                    NeuralInfoRow("Circuit source", "76 neurons · 515 pairs · 3,316 counts")
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    if (d?.substrate == com.example.meowmba.core.neural.NeuralSubstrate.BANC_EXPERIMENTAL)
                        "The bars summarize the running BANC anatomical-path adapter. Its topology comes from BANC, while connection signs, timing and visual motor retargeting are synthetic. It is not FlyWire or a literal spike trace."
                    else "The bars summarize the synthetic running core; they are not a literal FlyWire spike trace. The separate BANC extract is available as an experimental network option.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NeuralInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}
