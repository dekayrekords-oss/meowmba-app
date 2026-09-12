package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.meowmba.core.behavior.PetCareState
import kotlin.math.roundToInt

/** User-opened compact game controls; scrolling keeps every action reachable at large text sizes. */
@Composable
fun PetCarePanel(
    care: PetCareState,
    motionPaused: Boolean = false,
    onCareChanged: (Boolean) -> Unit,
    onAction: (HabitatAction) -> Unit,
    onFeedback: (Boolean) -> Unit,
    onLearningChanged: (Boolean) -> Unit,
    learningEnabled: Boolean,
    onResetLearning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (!expanded) {
        FilledTonalButton(onClick = { expanded = true }, modifier = modifier) { Text("Play") }
        return
    }
    Surface(
        modifier = modifier.widthIn(max = 300.dp).heightIn(max = 360.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        color = androidx.compose.ui.graphics.Color(0xff20332f).copy(alpha = .97f),
        contentColor = androidx.compose.ui.graphics.Color(0xfff0f5ef),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Play & care", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { expanded = false }) { Text("Close") }
            }
            if (motionPaused) {
                Text("Motion is resting while the phone settles. Care controls still work.", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Care mode")
                Switch(checked = care.careEnabled, onCheckedChange = onCareChanged, modifier = Modifier.semantics { contentDescription = "Care mode" })
            }
            Text(
                if (care.careEnabled) "Feed, play and rest together. Needs stay gentle; your companion never dies."
                else "Self-sufficient: your companion stays well without care. You can still play together.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (care.careEnabled) {
                Text("Food ${percent(care.food)}% · Energy ${percent(care.energy)}% · Happiness ${percent(care.happiness)}%", style = MaterialTheme.typography.bodySmall)
            }
            val actions = listOf(
                "Feed" to HabitatAction.FEED, "Play" to HabitatAction.PLAY,
                "Rest" to HabitatAction.REST, "Read" to HabitatAction.READ,
                "Drink" to HabitatAction.DRINK, "Nudge" to HabitatAction.NUDGE,
                "Put down" to HabitatAction.RELEASE, "Explore" to HabitatAction.EXPLORE,
            )
            actions.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { (label, action) ->
                        FilledTonalButton(onClick = { onAction(action) }, modifier = Modifier.weight(1f)) { Text(label) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Learn preferences", modifier = Modifier.weight(1f))
                Switch(checked = learningEnabled, onCheckedChange = onLearningChanged, modifier = Modifier.semantics { contentDescription = "Learn preferences" })
            }
            Text("Teach which recent behaviors you enjoy.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = { onFeedback(true) }, enabled = learningEnabled, modifier = Modifier.weight(1f)) { Text("Like this") }
                TextButton(onClick = { onFeedback(false) }, enabled = learningEnabled, modifier = Modifier.weight(1f)) { Text("Less like this") }
            }
            TextButton(onClick = onResetLearning) { Text("Reset learned preferences") }
        }
    }
}

private fun percent(value: Float): Int = if (value.isFinite()) (value.coerceIn(0f, 1f) * 100).roundToInt() else 100
