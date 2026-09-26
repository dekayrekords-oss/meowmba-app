package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.agent.runtime.*
import com.example.meowmba.core.northstar.StarView
import com.example.meowmba.core.media.ArtifactViewer

/** One native review surface for live work and recovered receipts. No replay button for external writes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskHistorySheet(tasks: List<AgentTask>, selectedId: String?, stars: List<StarView>,
    problem: String?, notice: String?, onClose: () -> Unit, onSelect: (String?) -> Unit,
    onCancel: (String) -> Unit, onLink: (String, String) -> Unit, onFile: (java.io.File) -> Unit,
    onApproval: () -> Unit, onRemove: ((String) -> Unit)? = null) {
    var removing by remember(selectedId) { mutableStateOf(false) }
    val selected = tasks.firstOrNull { it.id == selectedId }
    var chooseGoal by remember(selectedId) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    var openError by remember(selectedId) { mutableStateOf<String?>(null) }
    val links = remember(selected?.result) { Regex("https?://[^\\s<>\"]+").findAll(selected?.result.orEmpty())
        .map { it.value.trimEnd('.', ',', ')', ']', ';') }.filter { url -> runCatching { java.net.URI(url).let { it.host != null && it.userInfo == null } }.getOrDefault(false) }
        .distinct().take(8).toList() }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp).padding(horizontal = 20.dp)
            .testTag("task_history"), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (selected == null) "Meow’s work" else "Task result", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClose) { Text("Close") }
                }
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                notice?.let { Text(it, Modifier.testTag("task_history_notice")) }
                openError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            if (selected == null) {
                item { Text("Saved task results stay here when you reopen Meow. Interrupted actions are never repeated automatically.", style = MaterialTheme.typography.bodySmall) }
                if (tasks.isEmpty()) item { Text("No saved work yet. Ask Meow to search, create a file, or use a tool.") }
                items(tasks, key = { it.id }) { task ->
                    OutlinedCard(onClick = { onSelect(task.id) }, modifier = Modifier.fillMaxWidth().testTag("work_${task.id}")) {
                        Column(Modifier.padding(14.dp)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium)
                            Text(TaskHistory.label(task), style = MaterialTheme.typography.labelLarge)
                            Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(task.createdAt)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                item {
                    TextButton(onClick = { onSelect(null) }) { Text("All work") }
                    Text(selected.title, style = MaterialTheme.typography.titleLarge)
                    Text(TaskHistory.label(selected), Modifier.testTag("task_outcome"), style = MaterialTheme.typography.labelLarge)
                    if (selected.resultTruncated) Text("Saved excerpt · open the original source or file for the full result.", style = MaterialTheme.typography.bodySmall)
                    if (selected.recovered) Text("Saved from an earlier session", style = MaterialTheme.typography.bodySmall)
                    if (selected.status == TaskStatus.CANCELLED && selected.executionStarted)
                        Text("Further steps were stopped. An action already sent may still finish; check its destination before repeating it.")
                    SelectionContainer { Text(selected.result ?: if (selected.isTerminal) "No result was recorded." else "The result will appear here when this step finishes.", Modifier.padding(vertical = 12.dp).testTag("task_result")) }
                    if (selected.log.isNotEmpty()) Text(selected.log.joinToString(" → "), style = MaterialTheme.typography.bodySmall)
                    if (!selected.isTerminal) TextButton(onClick = { onCancel(selected.id) }) { Text("Stop task") }
                    if (selected.status == TaskStatus.WAITING_APPROVAL) TextButton(onClick = onApproval) { Text("Review approval") }
                }
                val files = ArtifactViewer.resultsForTool(selected.toolId, selected.result.orEmpty())
                items(files, key = { it.absolutePath }) { file -> OutlinedButton(onClick = { onFile(file) }) { Text("Open ${file.name}") } }
                items(links) { url -> TextButton(onClick = { runCatching { uriHandler.openUri(url) }.onFailure { openError = "No app could open this source." } }) { Text(url, maxLines = 2) } }
                if (selected.isTerminal && onRemove != null) item {
                    TextButton(onClick = { removing = true }) { Text("Remove saved result") }
                }
                if (selected.isTerminal && !selected.result.isNullOrBlank() && stars.isNotEmpty()) item {
                    OutlinedButton(onClick = { chooseGoal = !chooseGoal }) { Text("Link to a North Star") }
                    if (chooseGoal) stars.forEach { view -> TextButton(onClick = { onLink(selected.id, view.star.id); chooseGoal = false }) { Text(view.star.title) } }
                }
            }
        }
    }
    if (removing && selected != null && onRemove != null) AlertDialog(onDismissRequest = { removing = false },
        title = { Text("Remove saved result?") },
        text = { Text("This removes the task record and its North Star links. Created files stay saved.") },
        confirmButton = { TextButton(onClick = { removing = false; onRemove(selected.id) }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { removing = false }) { Text("Keep") } })
}
