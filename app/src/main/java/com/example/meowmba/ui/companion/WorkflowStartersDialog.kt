package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.meowmba.core.workflows.*

@Composable internal fun WorkflowStartersDialog(onClose: () -> Unit, onChoose: (BusinessAgent) -> Unit) {
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("30 workflow starters", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onClose) { Text("Close") }
            }
            Text("These configure real jobs, not new service connections. Add your inputs and permissions before running. Replies are saved as drafts, never auto-sent.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(query, { query = it }, label = { Text("Search workflows") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkflowStarterCatalog.all.filter { query.isBlank() || "${it.category} ${it.title} ${it.task} ${it.needs}".contains(query, ignoreCase = true) }
                    .groupBy { it.category }.forEach { (category, starters) ->
                        Text(category, style = MaterialTheme.typography.titleSmall)
                        starters.forEach { starter -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                            Text(starter.title, style = MaterialTheme.typography.titleMedium)
                            Text(starter.task, style = MaterialTheme.typography.bodySmall)
                            Text("Needs: ${starter.needs}", style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = { onChoose(starter.agent()) }) { Text("Configure this workflow") }
                        } } }
                    }
            }
        }
      }
    }
}
