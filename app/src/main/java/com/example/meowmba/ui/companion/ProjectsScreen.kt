package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Card
import com.example.meowmba.ui.theme.OutlinedCard
import com.example.meowmba.ui.theme.Surface

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import com.example.meowmba.ui.theme.AlertDialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.work.*

/** Persistent project evidence, distinct from the transient chat and avatar stage. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectsScreen(
    state: CompanionState, onOpen: (Long) -> Unit, onDelete: (Long) -> Unit, onClose: () -> Unit,
    onCreate: (String, String) -> Unit = { _, _ -> },
    onSavePlan: (String, List<String>, String) -> Unit = { _, _, _ -> },
    onDraftPlan: () -> Unit = {}, onCancelPlan: () -> Unit = {},
    onStepDone: (String, Boolean) -> Unit = { _, _ -> },
    onContinue: (String?) -> Unit = {}, onFiles: () -> Unit = {},
    onArtifact: (String) -> Unit = {}, onCancelTask: (String) -> Unit = {}, onRefresh: () -> Unit = {},
) {
    var detail by rememberSaveable { mutableStateOf(state.activeProjectId != null) }
    var create by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ProjectUi?>(null) }
    val project = state.projects.firstOrNull { it.id == state.activeProjectId }
    val work = state.projectWork?.takeIf { it.projectId == state.activeProjectId }
    fun back() { if (detail) detail = false else onClose() }
    BackHandler { back() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to projects") }
                Column(Modifier.weight(1f)) {
                    Text(if (detail) project?.name ?: state.activeProjectName ?: "Project" else "Projects",
                        style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (detail) "Plan · activity · files" else "Your work, in one place", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close projects") }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!detail) {
                    item {
                        Text("Start with what you want to achieve. Keep the brief, plan, conversation and finished files together.")
                        Button(onClick = { create = true }, Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New project")
                        }
                    }
                    if (state.projects.isEmpty()) item { Text("No projects yet. Try “help me with a project about my business launch”.") }
                    items(state.projects, key = { it.id }) { p ->
                        OutlinedCard(onClick = { detail = true; onOpen(p.id) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, null)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${p.kindLabel} · ${p.whenLabel}", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { deleting = p }) { Icon(Icons.Default.DeleteOutline, "Remove ${p.name} from projects") }
                            }
                        }
                    }
                } else {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onContinue(null) }) { Text("Project chat") }
                            OutlinedButton(onClick = onFiles) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("Open project files") }
                            TextButton(onClick = onRefresh) { Text("Refresh") }
                        }
                    }
                    state.projectWorkMessage?.let { message -> item { Text(message, style = MaterialTheme.typography.bodyMedium) } }
                    if (work == null) item {
                        Text("Loading saved project…")
                    } else {
                        item {
                            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Project brief", style = MaterialTheme.typography.titleLarge)
                                Text(work.objective.ifBlank { "What should we make, and what does a good result look like?" })
                                if (work.notes.isNotBlank()) Text(work.notes, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { editing = true }, enabled = !state.projectWorkBusy) { Text("Edit brief & plan") }
                            } }
                        }
                        item {
                            Text("Right now", style = MaterialTheme.typography.titleLarge)
                            Text(when {
                                state.projectExecuting -> "Working through this step. Actions still use your normal approval controls; review the saved results before marking it complete."
                                state.projectWorkBusy -> "Drafting your plan with the selected AI. No project actions are running from this draft."
                                work.currentTasks.isNotEmpty() -> "${work.currentTasks.size} active action(s). Review approvals before anything sensitive runs."
                                else -> "No project action is running. Choose a plan step or continue in chat."
                            }, style = MaterialTheme.typography.bodyMedium)
                            if (state.projectWorkBusy) TextButton(onClick = onCancelPlan) { Text(if (state.projectExecuting) "Stop project work" else "Stop planning") }
                        }
                        items(work.currentTasks, key = { "live-${it.id}" }) { task -> ProjectTaskCard(task, onCancelTask) }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Plan", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                TextButton(onClick = { if (work.steps.isEmpty()) onDraftPlan() else editing = true }, enabled = !state.projectWorkBusy && work.objective.isNotBlank()) {
                                    Text(if (work.steps.isEmpty()) "Draft with AI" else "Edit plan")
                                }
                            }
                            Text("${work.steps.count { it.completedByUser }} of ${work.steps.size} steps confirmed by you · ${work.completedTaskCount} tool actions succeeded",
                                style = MaterialTheme.typography.bodySmall)
                            if (work.steps.isEmpty()) Text("No plan yet. Draft one with AI or add your own in Edit brief & plan.")
                        }
                        items(work.steps, key = { "step-${it.id}" }) { step ->
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = step.completedByUser, onCheckedChange = { onStepDone(step.id, it) })
                                        Text(step.title, Modifier.weight(1f))
                                    }
                                    Text(if (step.completedByUser) "Confirmed done by you" else "Planned · not yet confirmed", style = MaterialTheme.typography.labelSmall)
                                    if (!step.completedByUser) TextButton(onClick = { onContinue(step.title) }, enabled = !state.projectWorkBusy) { Text("Work on this step") }
                                }
                            }
                        }
                        item {
                            Text("Files & results", style = MaterialTheme.typography.titleLarge)
                            Text("Files → Projects → ${work.name}\nNew outputs: ${work.outputFolder}", style = MaterialTheme.typography.bodySmall)
                            Text("Saved privately on this phone. Use Files to import or export; uninstalling removes private files.", style = MaterialTheme.typography.bodySmall)
                            if (work.artifacts.isEmpty()) Text("No finished files found yet. A chat reply is not a saved file.")
                            if (work.scanTruncated) Text("This is a partial file index. Open project files to browse more.")
                        }
                        items(work.artifacts, key = { "file-${it.path}" }) { file ->
                            OutlinedCard(onClick = { onArtifact(file.path) }, modifier = Modifier.fillMaxWidth(), enabled = file.available) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(file.name, style = MaterialTheme.typography.titleSmall)
                                    Text("${file.category} · ${assetSize(file.bytes)}${if (!file.available) " · Missing" else ""}", style = MaterialTheme.typography.bodySmall)
                                    Text(file.path, style = MaterialTheme.typography.labelSmall)
                                    if (!file.inProjectFolder) Text("Linked result · original location unchanged", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        item { Text("Activity history", style = MaterialTheme.typography.titleLarge)
                            Text("Reported by the tool runtime. A successful action is not proof the whole project is finished.", style = MaterialTheme.typography.bodySmall) }
                        if (work.tasks.isEmpty()) item { Text("No tool actions recorded for this project yet.") }
                        items(work.tasks.filter { it !in work.currentTasks }, key = { "history-${it.id}" }) { task -> ProjectTaskCard(task, onCancelTask) }
                        items(work.warnings) { warning -> Text(warning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
    if (create) ProjectBriefDialog(onDismiss = { create = false }, onSave = { name, goal, _, _ ->
        create = false; detail = true; onCreate(name, goal)
    })
    if (editing && work != null) ProjectBriefDialog(work, onDismiss = { editing = false }, onSave = { _, goal, steps, notes ->
        editing = false; onSavePlan(goal, steps, notes)
    })
    deleting?.let { p -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Remove ${p.name}?") },
        text = { Text("Removes the project from this list. Its saved files remain in Files. Active project actions must be stopped first.") },
        confirmButton = { TextButton(onClick = { onDelete(p.id); deleting = null }) { Text("Remove from list") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } }) }
}

@Composable private fun ProjectTaskCard(task: ProjectTaskRecord, onCancel: (String) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(task.title, style = MaterialTheme.typography.titleSmall)
        Text("${task.status.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }} · ${task.toolId}", style = MaterialTheme.typography.labelMedium)
        if (task.resultSummary.isNotBlank()) Text(task.resultSummary, style = MaterialTheme.typography.bodySmall)
        if (task.status in setOf(ProjectTaskStatus.RUNNING, ProjectTaskStatus.QUEUED, ProjectTaskStatus.WAITING_APPROVAL))
            TextButton(onClick = { onCancel(task.id) }) { Text("Stop action") }
    } }
}

@Composable private fun ProjectBriefDialog(work: ProjectWorkSnapshot? = null, onDismiss: () -> Unit,
    onSave: (String, String, List<String>, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(work?.name.orEmpty()) }
    var objective by rememberSaveable { mutableStateOf(work?.objective.orEmpty()) }
    var steps by rememberSaveable { mutableStateOf(work?.steps?.joinToString("\n") { it.title }.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(work?.notes.orEmpty()) }
    val parsed = steps.lines().map { it.trim() }.filter { it.isNotBlank() }
    val valid = name.isNotBlank() && objective.isNotBlank() && parsed.size <= 12 && parsed.all { it.length <= 240 }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp)) {
                Text(if (work == null) "New project" else "Edit brief & plan", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(20.dp))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (work == null) OutlinedTextField(name, { name = it.take(80) }, modifier = Modifier.fillMaxWidth(), label = { Text("Project name") }, singleLine = true)
            OutlinedTextField(objective, { objective = it.take(2000) }, modifier = Modifier.fillMaxWidth(), label = { Text("What do you want to achieve?") }, minLines = 2)
            if (work != null) {
                OutlinedTextField(steps, { steps = it.take(4000) }, modifier = Modifier.fillMaxWidth(), label = { Text("Plan · one step per line") }, minLines = 3)
                Text("Up to 12 steps, 240 characters each. Editing a step resets its confirmation.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(notes, { notes = it.take(3000) }, modifier = Modifier.fillMaxWidth(), label = { Text("Decisions & constraints to remember") }, minLines = 2)
            } else Text("Creates a private project folder and drafts a plan using your selected AI. It does not run the plan automatically.", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onSave(name.trim(), objective.trim(), parsed, notes.trim()) }, enabled = valid, modifier = Modifier.weight(1f)) {
                        Text(if (work == null) "Create & plan" else "Save brief & plan")
                    }
                }
            }
        }
    }
}
