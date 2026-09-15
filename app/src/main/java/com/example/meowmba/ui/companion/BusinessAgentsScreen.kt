package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Card
import com.example.meowmba.ui.theme.Surface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.meowmba.ui.theme.AlertDialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.meowmba.core.workflows.*
import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.agent.RemoteModelProvider
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BusinessAgentsLauncher() {
    var open by remember { mutableStateOf(false) }
    Button(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("Business mode") }
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BusinessAgentsScreen { open = false }
    }
}

@Composable
internal fun BusinessAgentsScreen(onOpenFiles: (() -> Unit)? = null, onOpenBugReports: (() -> Unit)? = null, onClose: () -> Unit) {
    val context = LocalContext.current
    val loaded = remember { runCatching { WorkflowRuntime.get(context) } }
    val runtime = loaded.getOrNull()
    if (runtime == null) {
        Surface(Modifier.fillMaxSize()) { Column(Modifier.safeDrawingPadding().padding(24.dp)) {
            Text("Saved agent data could not be loaded. It has not been reset or deleted.")
            Text(loaded.exceptionOrNull()?.message.orEmpty())
            Button(onClick = onClose) { Text("Close") }
        } }
        return
    }
    val snapshot by runtime.store.state.collectAsState()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<BusinessAgent?>(null) }
    var create by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf(false) }
    var mcpSetup by remember { mutableStateOf(false) }
    var starters by remember { mutableStateOf(false) }
    val notifyPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (!granted) error = "Notifications are off. Check Job history here for approvals and results." }
    fun change(action: () -> Unit) { scope.launch { try { withContext(Dispatchers.IO) { action() } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message } } }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Business mode", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onClose) { Text("Close") }
            }
            Text("Agents share one execution slot to limit heat. Jobs and approvals are saved on this phone. Files are in Files → business.")
            Text("Scheduled jobs run when Android permits; force-stopping Meow pauses them until you reopen it.", style = MaterialTheme.typography.bodySmall)
            if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                TextButton(onClick = { notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) { Text("Enable job and approval notifications") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            BusinessModeOverview(BusinessReadiness.from(snapshot),
                onConfigureBrief = {
                    // Opens the existing editor. It grants nothing and creates
                    // no job or recurring schedule until the user saves it.
                    editing = WorkflowStarterCatalog.all.first { it.id == "daily-brief" }.agent()
                    create = true
                },
                onSchedulesPaused = { paused -> change {
                    runtime.store.change { it.copy(schedulesPaused = paused) }
                    runtime.ensureSchedules()
                } }, onOpenFiles = onOpenFiles, onOpenBugReports = onOpenBugReports)
            Button(onClick = { editing = null; create = true }, modifier = Modifier.fillMaxWidth()) { Text("Create agent") }
            OutlinedButton(onClick = { starters = true }, modifier = Modifier.fillMaxWidth()) { Text("Browse 30 workflow starters") }
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("CEO + specialist team", style = MaterialTheme.typography.titleMedium)
                Text("Adds a chief of staff and three specialists. Local AI by default; edit each agent to allow cloud or grant a connected service. No schedules or email sending are enabled automatically.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { change {
                    WorkflowStarterCatalog.executiveTeam().filter { runtime.store.agent(it.id) == null }.forEach(runtime.store::saveAgent)
                } }) { Text("Add executive team") }
            } }
            Text("Reply outbox: Files → business → drafts. Each source/event gets one saved draft to avoid duplicate replies. Nothing is sent automatically.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { setup = !setup }, modifier = Modifier.fillMaxWidth()) { Text("Connect OpenRouter") }
            if (setup) BusinessCloudSetup()
            BusinessLocalSetup(runtime)
            OutlinedButton(onClick = { mcpSetup = !mcpSetup }, modifier = Modifier.fillMaxWidth()) { Text("Connect a business service (MCP)") }
            if (mcpSetup) BusinessMcpSetup(runtime)
            snapshot.connections.forEach { connection ->
                var readSetup by remember(connection.id) { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text("${connection.name} · ${connection.tools.size} discovered tools · ${if (connection.enabled) "linked" else "disabled"}")
                    Text(connection.endpoint, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { change { runtime.store.change { it.copy(connections = it.connections.map { old -> if (old.id == connection.id) old.copy(enabled = !old.enabled) else old }) } } }) { Text(if (connection.enabled) "Disable connection" else "Enable connection") }
                    if (connection.tools.any { it.readOnlyHint }) TextButton(onClick = { readSetup = !readSetup }) { Text("Scheduled read permissions") }
                    if (readSetup) {
                        Text("Only enable automatic reads for a service you trust, preferably with a read-only token. These tools are labeled read-only by the server. All other service calls require approval.", style = MaterialTheme.typography.bodySmall)
                        connection.tools.filter { it.readOnlyHint }.forEach { spec -> Row {
                            Checkbox(spec.name in connection.automaticReadTools, { granted -> change { runtime.store.change { state -> state.copy(connections = state.connections.map { old ->
                                if (old.id == connection.id) old.copy(automaticReadTools = if (granted) old.automaticReadTools + spec.name else old.automaticReadTools - spec.name) else old
                            }) } } })
                            Text("Allow automatic ${spec.name}", style = MaterialTheme.typography.bodySmall)
                        } }
                    }
                } }
            }
            OutlinedButton(onClick = { change {
                WorkflowTemplates.operationsTeam().forEach(runtime.store::saveAgent)
            } }, modifier = Modifier.fillMaxWidth()) { Text("Add operations team template") }
            snapshot.agents.forEach { agent ->
                var goal by remember(agent.id) { mutableStateOf(agent.scheduledGoal) }
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(agent.name, style = MaterialTheme.typography.titleMedium)
                    Text("${agent.provider} · ${agent.tools.size} tools · ${agent.delegates.size} delegates" + if (agent.intervalMinutes > 0) " · every ${agent.intervalMinutes} min" else " · manual", style = MaterialTheme.typography.bodySmall)
                    Text(agent.instructions, style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { editing = agent; create = true }) { Text("Edit") }
                        TextButton(onClick = { change { runtime.store.saveAgent(agent.copy(enabled = !agent.enabled)); runtime.ensureSchedules() } }) { Text(if (agent.enabled) "Disable" else "Enable") }
                    }
                    OutlinedTextField(goal, { goal = it }, label = { Text("What should this agent do?") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { val requested = goal; change { runtime.enqueue(agent.id, requested) } }, enabled = agent.enabled && goal.isNotBlank()) { Text("Run job") }
                } }
            }
            Text("Job history", style = MaterialTheme.typography.titleLarge)
            snapshot.runs.asReversed().take(20).forEach { run ->
                var trace by remember(run.id) { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${snapshot.agents.find { it.id == run.agentId }?.name ?: "Agent"} · ${run.status}", style = MaterialTheme.typography.titleMedium)
                    Text(run.goal)
                    Text(run.note, style = MaterialTheme.typography.bodySmall)
                    if (run.status == "APPROVAL") run.steps.lastOrNull()?.let { step ->
                        Text("Requested action: ${step.tool}", style = MaterialTheme.typography.titleSmall)
                        if (step.approvalExpiresAt > 0) Text("Service actions expire 60 seconds after the request. An expired action must be denied and requested again.", style = MaterialTheme.typography.bodySmall)
                        step.args.forEach { (key, value) -> Text("$key: $value", style = MaterialTheme.typography.bodySmall) }
                        Row {
                            Button(onClick = { change { runtime.store.approve(run.id, step.id, true); runtime.wake() } }) { Text("Approve action") }
                            TextButton(onClick = { change { runtime.store.approve(run.id, step.id, false) } }) { Text("Deny") }
                        }
                    }
                    Row {
                        TextButton(onClick = { trace = !trace }) { Text(if (trace) "Hide steps" else "Show steps") }
                        if (run.status !in WorkflowStore.TERMINAL) TextButton(onClick = { change { runtime.store.cancel(run.id) } }) { Text("Stop job") }
                        if (run.status in WorkflowStore.TERMINAL) TextButton(onClick = { change { runtime.exportAndRemove(run.id) } }) { Text("Archive to Files") }
                    }
                    if (trace) run.steps.forEach { step -> Text("${step.agentId.take(8)} · ${step.tool} · ${step.status}\n${step.output}", style = MaterialTheme.typography.bodySmall) }
                } }
            }
        }
    }
    if (starters) WorkflowStartersDialog(onClose = { starters = false }, onChoose = { editing = it; starters = false; create = true })
    if (create) AgentEditor(editing, snapshot.agents, runtime.tools.definitions().map { it.id }, onClose = { create = false }) { agent ->
        withContext(Dispatchers.IO) { runtime.store.saveAgent(agent); runtime.ensureSchedules() }
    }
}

@Composable
internal fun AgentEditor(existing: BusinessAgent?, team: List<BusinessAgent>, toolIds: List<String>, onClose: () -> Unit, save: suspend (BusinessAgent) -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var instructions by remember { mutableStateOf(existing?.instructions.orEmpty()) }
    var tools by remember { mutableStateOf(existing?.tools ?: setOf("files.list", "files.read")) }
    var delegates by remember { mutableStateOf(existing?.delegates ?: emptySet()) }
    var completionTools by remember { mutableStateOf(existing?.completionTools ?: emptySet()) }
    var cloud by remember { mutableStateOf(existing?.provider == "openrouter") }
    var consent by remember { mutableStateOf(existing?.cloudConsent ?: false) }
    var model by remember { mutableStateOf(existing?.model.orEmpty()) }
    var interval by remember { mutableStateOf((existing?.intervalMinutes ?: 0).toString()) }
    var scheduledGoal by remember { mutableStateOf(existing?.scheduledGoal.orEmpty()) }
    val minutes = interval.toLongOrNull()
    AlertDialog(onDismissRequest = { if (!saving) onClose() }, title = { Text(if (existing == null) "Create agent" else "Edit agent") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("Name") })
            OutlinedTextField(instructions, { instructions = it.take(8000) }, label = { Text("Role and instructions") }, minLines = 3)
            Row { Checkbox(cloud, { cloud = it }); Text("Use OpenRouter instead of the local model") }
            if (cloud) {
                Row { Checkbox(consent, { consent = it }); Text("Allow this agent's instructions and tool results to be sent to OpenRouter. Usage charges may apply.") }
                OutlinedTextField(model, { model = it }, label = { Text("Model ID (blank uses Settings)") })
            }
            Text("Allowed tools")
            toolIds.forEach { id -> Row { Checkbox(id in tools, { tools = if (it) tools + id else tools - id }); Text(id) } }
            Text("Required proof before completing a job")
            tools.forEach { id -> Row { Checkbox(id in completionTools, { completionTools = if (it) completionTools + id else completionTools - id }); Text("Successful $id") } }
            Text("Can delegate to")
            team.filter { it.id != existing?.id }.forEach { agent -> Row { Checkbox(agent.id in delegates, { delegates = if (it) delegates + agent.id else delegates - agent.id }); Text(agent.name) } }
            OutlinedTextField(interval, { interval = it }, label = { Text("Repeat minutes (0 = manual, minimum 15)") })
            if ((minutes ?: 0) > 0) OutlinedTextField(scheduledGoal, { scheduledGoal = it.take(8000) }, label = { Text("Scheduled goal") })
            if (tools.size > 16 || delegates.size > 8) Text("Use at most 16 tools and 8 delegates. Split larger teams into specialists.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { Column {
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = {
            val candidate = (existing ?: BusinessAgent(name = name, instructions = instructions, tools = tools)).copy(name = name, instructions = instructions, tools = tools, delegates = delegates, completionTools = completionTools.intersect(tools), provider = if (cloud) "openrouter" else "local", model = model.trim(), cloudConsent = cloud && consent, intervalMinutes = minutes!!, scheduledGoal = scheduledGoal)
            saving = true; saveError = null
            scope.launch {
                try { save(candidate); onClose() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { saveError = "Could not save: ${e.message ?: "storage unavailable"}. Your entries are still here." }
                finally { saving = false }
            }
        }, enabled = !saving && tools.size <= 16 && delegates.size <= 8 && name.isNotBlank() && instructions.isNotBlank() && (!cloud || consent) && minutes != null && (minutes == 0L || minutes in 15..525_600 && scheduledGoal.isNotBlank())) { Text(if (saving) "Saving…" else "Save agent") }
    } }, dismissButton = { TextButton(onClick = onClose, enabled = !saving) { Text("Cancel") } })
}

@Composable
private fun BusinessCloudSetup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Row { Checkbox(consent, { consent = it }, enabled = !busy); Text("Allow two synthetic planning tests through OpenRouter. Your key is encrypted on this phone; model usage charges may apply.") }
    TextButton(onClick = {
        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://openrouter.ai/keys"))) }
            .onFailure { status = "Open a browser and visit openrouter.ai/keys" }
    }) { Text("Get an OpenRouter key") }
    OutlinedTextField(key, { key = it }, enabled = !busy, label = { Text("OpenRouter key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    Button(onClick = { val supplied = key.trim(); busy = true; scope.launch {
        try { withContext(Dispatchers.IO) {
            val storage = SecureStorageManager(context)
            val provider = RemoteModelProvider("OpenRouter", "https://openrouter.ai/api", supplied, storage.getModelName("openrouter") ?: "openrouter/free")
            try { com.example.meowmba.core.setup.PlannerReadiness.verify(provider) } finally { provider.close() }
            storage.saveVerifiedApiKey("openrouter", supplied)
        }; key = ""; status = "Navigation and follow-up checks passed. Key saved. Grant cloud consent separately for each agent." }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { status = e.message ?: "Connection failed" }
        finally { busy = false }
    } }, enabled = consent && key.isNotBlank() && !busy) { Text(if (busy) "Testing model…" else "Test model and save key") }
    Text(status, style = MaterialTheme.typography.bodySmall)
}
