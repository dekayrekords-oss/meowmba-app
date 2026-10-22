package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.ring.*

@Composable
internal fun RingModeSettings(state: RingState, driver: AndroidSmartRing) {
    var modeMenu by remember { mutableStateOf(false) }
    var appGesture by remember { mutableStateOf<String?>(null) }
    var appChoices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var commandGesture by remember { mutableStateOf<String?>(null) }
    var commandText by remember { mutableStateOf("") }
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text("How the ring works", style = MaterialTheme.typography.titleMedium)
    Box {
        OutlinedButton(onClick = { modeMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Mode: ${state.mode.label}") }
        DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
            RingMode.entries.forEach { mode ->
                DropdownMenuItem(text = { Text(mode.label) }, onClick = { driver.setMode(mode); modeMenu = false })
            }
        }
    }
    Text(state.mode.help, style = MaterialTheme.typography.bodySmall)
    Text("Mode and binding changes pause controls. Confirm each gesture in the tester, turn the tester off, then resume. Your custom bindings are saved when switching modes.", style = MaterialTheme.typography.bodySmall)
    Text("Gesture actions", style = MaterialTheme.typography.titleMedium)
    state.mappings.forEach { (gesture, action) ->
        var expanded by remember(gesture) { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text("$gesture: ${action.label}") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                RingAction.entries.forEach { choice ->
                    DropdownMenuItem(text = { Text(choice.label) }, onClick = { driver.setMapping(gesture, choice); expanded = false })
                }
            }
        }
        if (action == RingAction.OPEN_APP) {
            Text(state.customApps[gesture]?.ifBlank { "No app selected" } ?: "No app selected", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { appChoices = driver.allowedAppChoices(); appGesture = gesture }) { Text("Choose app for $gesture") }
        }
        if (action == RingAction.MEOW_COMMAND) {
            Text(state.customCommands[gesture]?.ifBlank { "No request saved" } ?: "No request saved", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { commandText = state.customCommands[gesture].orEmpty(); commandGesture = gesture }) { Text("Edit Meow request for $gesture") }
        }
    }
    appGesture?.let { gesture ->
        AlertDialog(onDismissRequest = { appGesture = null }, title = { Text("Open an app · $gesture") }, text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(appChoices, key = { it.first }) { (packageName, name) ->
                    TextButton(onClick = { driver.setCustomApp(gesture, packageName); appGesture = null }) { Text(name) }
                }
            }
        }, confirmButton = { TextButton(onClick = { appGesture = null }) { Text("Cancel") } })
    }
    commandGesture?.let { gesture ->
        AlertDialog(onDismissRequest = { commandGesture = null }, title = { Text("Ask Meow · $gesture") }, text = {
            Column {
                OutlinedTextField(value = commandText, onValueChange = { commandText = it }, label = { Text("Saved request") },
                    minLines = 3, maxLines = 6, isError = commandText.trim().length > 2_000)
                Text("${commandText.trim().length}/2,000 characters. Runs as a typed request using your selected AI provider and normal approvals. Stored on this phone; avoid secrets.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(enabled = commandText.isNotBlank() && commandText.trim().length <= 2_000,
            onClick = { driver.setCustomCommand(gesture, commandText); commandGesture = null }) { Text("Save and pause") } },
            dismissButton = { TextButton(onClick = { commandGesture = null }) { Text("Cancel") } })
    }
}
