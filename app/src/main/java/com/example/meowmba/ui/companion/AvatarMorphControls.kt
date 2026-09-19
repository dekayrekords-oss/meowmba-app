package com.example.meowmba.ui.companion

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AvatarMorphControls(state: CompanionState, viewModel: CompanionViewModel) {
    Column(Modifier.padding(vertical = 12.dp)) {
        VoiceToggle("Change characters while idle", "Your chosen main avatar stays saved. Automatic changes pause during driving and conversation.", state.idleMorphEnabled, viewModel::setIdleMorphEnabled)
        Text("Change every ${state.idleMorphIntervalSec} seconds")
        Slider(state.idleMorphIntervalSec.toFloat(), { viewModel.setIdleMorphIntervalSec(it.toInt()) },
            valueRange = 20f..180f, enabled = state.idleMorphEnabled)
        Text("Randomness: ${(state.features.idleMorphRandomness * 100).toInt()}%")
        Slider(state.features.idleMorphRandomness, viewModel::setIdleMorphRandomness,
            enabled = state.idleMorphEnabled)
        Text("0% cycles in order; 100% shuffles. Every available character gets a turn before repeats.", style = MaterialTheme.typography.bodySmall)
        VoiceToggle("Include invented characters", "Use your saved generated forms too.", state.idleMorphIncludeGenerated, viewModel::setIdleMorphIncludeGenerated)
    }
}
