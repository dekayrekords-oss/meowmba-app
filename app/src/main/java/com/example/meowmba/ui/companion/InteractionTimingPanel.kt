package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.diagnostics.InteractionTimings
import java.util.Locale

/** Passive diagnostics view. The caller owns visibility and snapshot refresh;
 * this panel starts no capture, timer, collection, renderer loop, or recording. */
@Composable
fun InteractionTimingPanel(
    snapshot: InteractionTimings.Snapshot,
    onEnabledChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null,
) {
    Surface(modifier.testTag("interaction-timing-panel"), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Interaction timings", style = MaterialTheme.typography.titleSmall)
                    Text(if (snapshot.enabled) "Recording local timing counters" else "Off · optional local diagnostics",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = snapshot.enabled, onCheckedChange = onEnabledChange,
                    modifier = Modifier.testTag("interaction-timing-enabled").semantics {
                        contentDescription = "Record interaction timings"
                    })
            }
            Text("Stores timing counters only in memory. No conversation, voice or face data.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (snapshot.enabled || snapshot.metrics.any { it.summary.sampleCount > 0 } || snapshot.frames.gaps.sampleCount > 0) {
                snapshot.metrics.forEach { row ->
                    Column {
                        Text(row.metric.label, style = MaterialTheme.typography.labelLarge)
                        Text(interactionTimingSummary(row.summary), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        snapshot.pending.firstOrNull { it.metric == row.metric }?.let { pending ->
                            Text("In progress · " + (pending.elapsedMs?.let { "${timingMs(it)} elapsed" } ?: "elapsed time unavailable"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Column {
                    Text("Visual frame gaps", style = MaterialTheme.typography.labelLarge)
                    Text(interactionTimingSummary(snapshot.frames.gaps), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(interactionFrameComparison(snapshot.frames), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Recent ${snapshot.capacity} samples per measure. First transcript is a recognizer callback, not acoustic onset. " +
                    "Speech and stop timings use backend callbacks. Frame gaps are not measured GPU jank.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onRefresh != null) TextButton(onClick = onRefresh, modifier = Modifier.testTag("interaction-timing-refresh")) {
                    Text("Refresh")
                }
                TextButton(onClick = onReset, modifier = Modifier.testTag("interaction-timing-reset")) { Text("Reset timings") }
            }
        }
    }
}

internal fun interactionTimingSummary(summary: InteractionTimings.Summary): String {
    if (summary.sampleCount == 0 || summary.medianMs == null || summary.p95Ms == null) return "No completed samples"
    val count = "${summary.sampleCount} " + if (summary.sampleCount == 1) "sample" else "samples"
    return "$count · median ${timingMs(summary.medianMs)} · p95 ${timingMs(summary.p95Ms)}"
}

internal fun interactionFrameComparison(frames: InteractionTimings.FrameSummary): String {
    val interval = frames.expectedIntervalMs
    if (interval == null || interval <= 0.0 || !interval.isFinite()) return "Vsync unavailable · relative comparison unavailable"
    val source = when (frames.cadenceSource) {
        InteractionTimings.CadenceSource.CONFIGURED -> "configured"
        InteractionTimings.CadenceSource.OBSERVED -> "observed"
        null -> "unspecified"
    }
    val cadence = String.format(Locale.ROOT, "%.1f Hz %s", 1000.0 / interval, source)
    val over = frames.gapsAboveCadence
    return if (over == null) "$cadence · no compared frames"
        else "$cadence · $over/${frames.comparedSamples} gaps above ${InteractionTimings.FRAME_GAP_MULTIPLIER}× interval"
}

private fun timingMs(ms: Double): String = String.format(Locale.ROOT, "%.1f ms", ms)
