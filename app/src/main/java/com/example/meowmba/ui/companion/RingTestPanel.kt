package com.example.meowmba.ui.companion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.ring.*
import kotlinx.coroutines.delay

@Composable
internal fun RingTestPanel(state: RingState, test: (RingSensor) -> Unit, stop: () -> Unit, clear: () -> Unit) {
    var preview by remember { mutableStateOf<String?>(null) }
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(state.inputSequence) {
        if (state.inputSequence > 0) { preview = null; flash = true; delay(600); flash = false }
    }
    val pulse by animateFloatAsState(if (flash || state.pressedKeys.isNotEmpty()) 1.1f else 1f, label = "ring pulse")
    val accent = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.outline
    val indication = preview ?: state.lastInput
    val label = preview?.let { "Preview: $it" } ?: state.lastInput
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Live ring tester", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(84.dp).semantics { contentDescription = "Ring input indicator: $label" }) {
                    val radius = size.minDimension * .29f * pulse
                    drawCircle(if (flash || preview != null) accent else idle, radius, style = Stroke(8.dp.toPx()))
                    if (indication.contains("Next") || indication.contains("Previous")) {
                        val direction = if (indication.contains("Next")) 1f else -1f
                        val tip = Offset(center.x, center.y + direction * radius * .55f)
                        drawLine(accent, Offset(center.x, center.y - direction * radius * .55f), tip, 3.dp.toPx())
                        drawLine(accent, Offset(tip.x - 6.dp.toPx(), tip.y - direction * 6.dp.toPx()), tip, 3.dp.toPx())
                        drawLine(accent, Offset(tip.x + 6.dp.toPx(), tip.y - direction * 6.dp.toPx()), tip, 3.dp.toPx())
                    } else if (indication.contains("tap", ignoreCase = true)) {
                        drawCircle(accent, 5.dp.toPx())
                    }
                    drawCircle(if (state.pressedKeys.isNotEmpty()) accent else idle, 6.dp.toPx(),
                        center = Offset(center.x, center.y - radius))
                }
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(if (state.pressedKeys.isEmpty()) "Released" else "${state.pressedKeys.size} key pressed",
                        style = MaterialTheme.typography.bodySmall)
                    Text("${state.gestureCounts.values.sum()} gestures this session", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(if (state.testing) "Swipe and tap the ring. Inputs are consumed without running app actions."
                else "Enable Gesture tester above for a physical test. Tap a tile below to preview its indicator.",
                style = MaterialTheme.typography.bodySmall)
            defaultRingMappings().keys.toList().chunked(2).forEach { gestures ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    gestures.forEach { gesture ->
                        OutlinedButton(onClick = { preview = gesture }, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(when (gesture) { "Next" -> "↓ Next"; "Previous" -> "↑ Previous"; else -> gesture },
                                    style = MaterialTheme.typography.labelMedium)
                                Text("${state.gestureCounts[gesture] ?: 0} seen", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            Text("Tiles are previews. Only physical ring input confirms a gesture; firmware may combine or omit events.",
                style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Sensor tests", style = MaterialTheme.typography.titleMedium)
            Text("Wear the ring snugly and keep still. HRV can take 80 seconds. Readings stay on this phone and are excluded from the shared log.",
                style = MaterialTheme.typography.bodySmall)
            RingSensor.entries.forEach { sensor ->
                val reading = state.sensors.getValue(sensor)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(sensor.label, style = MaterialTheme.typography.labelLarge)
                        Text(reading.value, style = MaterialTheme.typography.titleSmall)
                        Text(reading.status + if (reading.receivedAt > 0)
                            " · " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(reading.receivedAt)) else "",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { test(sensor) }, enabled = state.connected && state.activeSensor == null,
                        modifier = Modifier.semantics { contentDescription = "Test ${sensor.label}" }) {
                        Text(if (sensor.measurement) "Test" else "Read")
                    }
                }
            }
            if (state.activeSensor != null) OutlinedButton(onClick = stop) { Text("Stop sensor test") }
            if (!state.connected) Text("Connect the ring to enable sensor tests.", style = MaterialTheme.typography.bodySmall)
            Text("These are values computed by the ring. Raw optical / motion streams and sleep / historical records are not implemented here. No response means unverified, not proof of missing hardware.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { preview = null; clear() }, enabled = state.activeSensor == null) { Text("Clear session results") }
        }
    }
}
