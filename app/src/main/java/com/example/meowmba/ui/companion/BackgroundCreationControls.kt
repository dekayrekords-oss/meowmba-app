package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.background.*
import com.example.meowmba.core.agent.runtime.TaskStatus

@Composable
internal fun BackgroundCreationControls(state: CompanionState, viewModel: CompanionViewModel) {
    var prompt by rememberSaveable { mutableStateOf("") }
    val task = state.activeTasks.filter { it.toolId == "media.generate_image" && it.args["_vending_background"] == "true" }.maxByOrNull { it.createdAt }
    val busy = task != null && !task.isTerminal
    val image = task?.takeIf { it.status == TaskStatus.COMPLETED }?.result?.let {
        com.example.meowmba.core.media.ArtifactViewer.resultsForTool("media.generate_image", it).lastOrNull()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Create a background", style = MaterialTheme.typography.titleMedium)
        Text("Uses your configured image-generation provider, which is separate from the chat model. Photo references guide the image through their analyzed description, not pixel-exact image editing. The result is a still background, not a 3D room.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(prompt, { prompt = it.take(500) }, label = { Text("Describe your scene") }, enabled = !busy)
        TextButton(onClick = { viewModel.createVendingBackground(prompt) }, enabled = prompt.isNotBlank() && !busy) { Text("Generate background") }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("${task!!.status}: ${task.title}")
            TextButton(onClick = { viewModel.cancelTask(task.id) }) { Text("Cancel") }
        }
        task?.takeIf { it.status == TaskStatus.FAILED }?.result?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        image?.let { file ->
            val selection = BackgroundSelection(BackgroundKind.IMAGE, file.toURI().toString(), 0)
            Box(Modifier.fillMaxWidth().height(180.dp)) { AppBackground(selection, null) }
            TextButton(onClick = { viewModel.setHabitatMode(false); viewModel.setBackground(selection) }) { Text("Use this background") }
            TextButton(onClick = { viewModel.showFileInBrowser(file) }) { Text("Open finished image") }
        }
    }
}
