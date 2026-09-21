package com.example.meowmba.ui.companion

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.agent.ModelCatalog

internal fun CompanionState.needleStatusLabel(): String {
    if (!agentBrainEnabled) return "Disabled"
    val download = modelDownloads["needle-3"]
    if (download?.error != null) return "Download failed · retry in Models"
    if (download != null && !download.done) return "Downloading"
    return needleStatus.label
}

@Composable
internal fun NeedleStatusCard(state: CompanionState, onDownload: () -> Unit) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text("Needle 3 · ${state.needleStatusLabel()}", style = MaterialTheme.typography.titleMedium)
        Text("On-device action routing. It runs when needed, separately from your conversation model.", style = MaterialTheme.typography.bodySmall)
        if (state.needleStatus == com.example.meowmba.core.agent.runtime.NeedleStatus.MISSING ||
            state.needleStatus == com.example.meowmba.core.agent.runtime.NeedleStatus.FAILED ||
            state.modelDownloads["needle-3"]?.error != null) {
            TextButton(onClick = onDownload) { Text("Download / retry Needle 3") }
        }
    }
}
