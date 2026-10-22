package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.meowmba.ui.theme.FilterChip

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeControlSettings(state: CompanionState, viewModel: CompanionViewModel) {
    val settings = state.features
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Home controls", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show Care button", Modifier.weight(1f))
            Switch(settings.careVisible, { viewModel.setHomeControls(it, settings.carePosition, settings.neuralFeedbackVisible) })
        }
        if (settings.careVisible) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Top left", "Top right", "Bottom left", "Bottom right").forEach { position ->
                FilterChip(selected = settings.carePosition == position,
                    onClick = { viewModel.setHomeControls(true, position, settings.neuralFeedbackVisible) },
                    label = { Text(position) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show live neural feedback", Modifier.weight(1f))
            Switch(settings.neuralFeedbackVisible, { viewModel.setHomeControls(settings.careVisible, settings.carePosition, it) })
        }
        Text("Hides the feedback label only. Neural reactions, privacy indicators and task approvals remain active.",
            style = MaterialTheme.typography.bodySmall)
    }
}
