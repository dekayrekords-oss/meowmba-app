package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun SongRecognitionControls(state: CompanionState, viewModel: CompanionViewModel) {
    var token by remember { mutableStateOf("") }
    Text("Identify a song nearby", style = MaterialTheme.typography.titleMedium)
    Text("On request, listen for 10 seconds and send that audio to AudD to find a matching song. Requires internet and your own AudD account. Samples are not saved on this phone. Your previous microphone setting is restored afterward.",
        style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(token, { token = it }, label = { Text("AudD API key") },
        placeholder = { Text(if (viewModel.hasSongRecognitionKey()) "Key saved" else "Enter privately here") },
        visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { viewModel.saveSongRecognitionKey(token); token = "" }, enabled = token.isNotBlank()) { Text("Save key") }
        if (viewModel.hasSongRecognitionKey()) TextButton(onClick = { viewModel.saveSongRecognitionKey("") }) { Text("Remove key") }
    }
    TextButton(onClick = viewModel::requestSongIdentification) { Text("Identify a song") }
    state.songRecognitionStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
internal fun SongRecognitionDialog(state: CompanionState, viewModel: CompanionViewModel) {
    if (!state.songRecognitionPrompt) return
    AlertDialog(onDismissRequest = { viewModel.cancelSongIdentification() },
        title = { Text("Identify a song") },
        text = { Column {
            Text(if (viewModel.hasSongRecognitionKey())
                "Listen with the microphone for 10 seconds, then send that sample to AudD for a song match. Your microphone setting returns to its previous state."
                else "Add your AudD API key in Avatar settings → Identify a song nearby. Your key stays in encrypted storage.")
            state.songRecognitionStatus?.let { Text(it, Modifier.padding(top = 12.dp)) }
        } },
        confirmButton = {
            if (viewModel.hasSongRecognitionKey()) TextButton(onClick = viewModel::identifyNearbySong,
                enabled = !state.songRecognitionBusy) { Text("Listen and identify") }
            else TextButton(onClick = viewModel::openSongRecognitionSettings) { Text("Set up AudD") }
        },
        dismissButton = { TextButton(onClick = { viewModel.cancelSongIdentification() }) { Text(if (state.songRecognitionBusy) "Cancel" else "Close") } })
}
