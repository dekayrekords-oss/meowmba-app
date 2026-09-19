package com.example.meowmba.ui.companion

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.meowmba.core.computer.*
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun OpenCodeComputerPanel(onOpenFiles: () -> Unit = {}) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var client by remember { mutableStateOf<OpenCodeComputer?>(null) }
    var session by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Checking saved Computer connection…") }
    var snapshot by remember { mutableStateOf<OpenCodeComputer.Snapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var artifactPath by remember { mutableStateOf("") }
    var missingSession by remember { mutableStateOf(false) }
    var savedResult by remember(session) { mutableStateOf<ComputerArtifactStore.Saved?>(null) }
    var showProgress by remember(session) { mutableStateOf(false) }
    var refreshGeneration by remember { mutableIntStateOf(0) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }
    val previewLocation = remember { BringIntoViewRequester() }
    val feedbackLocation = remember { BringIntoViewRequester() }
    LaunchedEffect(savedResult) {
        if (savedResult != null) { withFrameNanos { }; previewLocation.bringIntoView() }
    }
    LaunchedEffect(actionFeedback) {
        if (actionFeedback != null) { withFrameNanos { }; feedbackLocation.bringIntoView() }
    }
    val answers = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(Unit) {
        try {
        withContext(Dispatchers.IO) {
            val storage = SecureStorageManager(context)
            OpenCodeLocalConnection.restore(storage)
            val key = storage.getApiKey(OpenCodeLocalConnection.CREDENTIAL).orEmpty()
            client = key.takeIf { it.isNotBlank() }?.let { OpenCodeComputer(it) }
            session = storage.getPreference(OpenCodeComputer.SESSION_KEY).orEmpty()
            prompt = storage.getPreference("computer_task_draft").orEmpty()
        }
        status = if (client == null) "Finish Setup computer first. Your existing OpenCode model/account is used; Meow does not select a paid model for you." else "Computer · OpenCode. Review its configured model before running; provider charges may apply."
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { client = null; status = "Secure connection storage is unavailable. Unlock the phone and reopen Computer setup. Nothing was sent." }
    }
    LaunchedEffect(session, client, refreshGeneration) {
        val api = client ?: return@LaunchedEffect
        if (session.isBlank()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val cadence = ComputerPollCadence()
            while (isActive) {
                var observedStatus: String? = null
                try { snapshot = api.snapshot(session); missingSession = false; observedStatus = snapshot!!.status; status = "Computer: $observedStatus" }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    missingSession = e.message?.contains("HTTP 404") == true
                    status = e.message ?: "Computer unavailable. Task may still be running."
                }
                delay(cadence.after(observedStatus))
            }
        }
    }
    fun action(block: suspend (OpenCodeComputer) -> Unit) {
        val api = client ?: return
        busy = true
        scope.launch {
            try { block(api) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { actionFeedback = e.message ?: "Computer action failed; refresh before retrying." }
            finally { busy = false; refreshGeneration++ }
        }
    }
    fun saveResult(path: String) = action { api ->
        val ownerSession = session
        val relative = OpenCodeComputer.artifactPath(path.trim())
        actionFeedback = "Retrieving $relative…"
        val bytes = api.artifact(ownerSession, relative)
        val saved = withContext(Dispatchers.IO) {
            ComputerArtifactStore(java.io.File(context.filesDir, "memba_workspace"))
                .save(ownerSession, relative, bytes)
        }
        savedResult = saved
        actionFeedback = "Saved and verified in Meow Files."
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Computer", style = MaterialTheme.typography.titleLarge)
        Text(status)
        if (client != null && session.isNotBlank()) {
            TextButton(onClick = { refreshGeneration++ }, enabled = !busy) { Text("Refresh status") }
        }
        actionFeedback?.let { Text(it, modifier = Modifier.bringIntoViewRequester(feedbackLocation)) }
        savedResult?.let { saved ->
            val mime = com.example.meowmba.core.media.ArtifactViewer.mimeFor(saved.file)
            val media = remember(saved) { GeneratedMedia(saved.file.path, when {
                mime.startsWith("image/") -> GeneratedMedia.MediaType.IMAGE
                mime.startsWith("video/") -> GeneratedMedia.MediaType.VIDEO
                mime.startsWith("audio/") -> GeneratedMedia.MediaType.AUDIO
                else -> GeneratedMedia.MediaType.DOCUMENT
            }, saved.sourcePath.substringAfterLast('/')) }
            ArtifactResultCard(media, "${saved.bytes} bytes · ${saved.libraryPath}",
                onOpen = { actionFeedback = com.example.meowmba.core.media.ArtifactViewer.open(context, saved.file)
                    .getOrElse { it.message ?: "Saved in Meow Files; no compatible viewer is available." } },
                onFiles = onOpenFiles, onClose = { savedResult = null },
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).bringIntoViewRequester(previewLocation))
        }
        OutlinedTextField(prompt, { prompt = it }, label = { Text("What should Computer do?") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { action { api ->
            // A fresh task gets a fresh permission-gated session. Persist before
            // dispatch, so a lost HTTP response cannot erase the recovery handle.
            val key = session.ifBlank { api.create("Meow Computer: $prompt") }
            withContext(Dispatchers.IO) { SecureStorageManager(context).savePreference(OpenCodeComputer.SESSION_KEY, key) }
            session = key
            api.send(key, prompt)
            prompt = ""
            withContext(Dispatchers.IO) { SecureStorageManager(context).savePreference("computer_task_draft", "") }
            actionFeedback = "Task submitted. Waiting for OpenCode's progress…"
        } }, enabled = client != null && !busy && prompt.isNotBlank() && (session.isBlank() || snapshot != null) &&
            snapshot?.status !in listOf("busy", "retry") && snapshot?.permissions.orEmpty().isEmpty() && snapshot?.questions.orEmpty().isEmpty()) { Text("Run with Computer") }
        if (session.isNotBlank()) {
            OutlinedButton(onClick = { action { it.stop(session); actionFeedback = "Stop requested; checking worker status…" } }, enabled = !busy) { Text("Stop task") }
            TextButton(onClick = { action {
                withContext(Dispatchers.IO) { SecureStorageManager(context).savePreference(OpenCodeComputer.SESSION_KEY, "") }
                session = ""; snapshot = null; missingSession = false; actionFeedback = "New task. Previous session remains in OpenCode."
            } }, enabled = !busy && (snapshot?.status == "idle" || missingSession) && snapshot?.permissions.orEmpty().isEmpty() && snapshot?.questions.orEmpty().isEmpty()) { Text("New task") }
        }
        snapshot?.let { snap ->
            Text("Workspace: ${snap.directory}", style = MaterialTheme.typography.bodySmall)
            snap.permissions.forEach { request ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text("Approval required: ${request["permission"]?.jsonPrimitive?.content}")
                        SelectionContainer { Text(request["patterns"].toString().take(4_000)) }
                        SelectionContainer { Text(request["metadata"].toString().take(8_000)) }
                        Row {
                            TextButton(onClick = { action { it.approve(session, request["id"]!!.jsonPrimitive.content, false) } }, enabled = !busy) { Text("Deny") }
                            TextButton(onClick = { action { it.approve(session, request["id"]!!.jsonPrimitive.content, true) } }, enabled = !busy && request["metadata"].toString().length <= 8_000 && request["patterns"].toString().length <= 4_000) { Text("Allow once") }
                        }
                    }
                }
            }
            snap.questions.forEach { request ->
                val requestId = request["id"]!!.jsonPrimitive.content
                val questions = request["questions"]!!.jsonArray
                Text("Computer needs your input", style = MaterialTheme.typography.titleMedium)
                questions.forEachIndexed { index, question ->
                    val q = question.jsonObject
                    val key = "$requestId:$index"
                    Text(q["question"]!!.jsonPrimitive.content)
                    (q["options"] as? JsonArray).orEmpty().forEach { option ->
                        val label = option.jsonObject["label"]!!.jsonPrimitive.content
                        TextButton(onClick = { answers[key] = label }) { Text(label) }
                    }
                    OutlinedTextField(answers[key].orEmpty(), { answers[key] = it }, label = { Text("Your answer") })
                }
                Button(onClick = { action { it.answer(session, requestId, questions.indices.map { answers["$requestId:$it"].orEmpty() }) } },
                    enabled = !busy && questions.indices.all { !answers["$requestId:$it"].isNullOrBlank() }) { Text("Send answers") }
            }
            val resultPaths = snap.resultPaths()
            if (resultPaths.isNotEmpty()) {
                Text("Results", style = MaterialTheme.typography.titleMedium)
                Text("These files were reported by completed tools. Save a copy to verify and preview it.",
                    style = MaterialTheme.typography.bodySmall)
                resultPaths.forEach { path ->
                    ComputerReportedResultCard(path, busy, onSave = { saveResult(path) })
                }
            }
            if (snap.todos.isNotEmpty()) {
                Text("Plan", style = MaterialTheme.typography.titleMedium)
                snap.todos.forEach { todo -> Text("${todo.jsonObject["status"]?.jsonPrimitive?.content}: ${todo.jsonObject["content"]?.jsonPrimitive?.content}") }
            }
            TextButton(onClick = { showProgress = !showProgress }) {
                Text(if (showProgress) "Hide detailed progress" else "Show detailed progress")
            }
            if (showProgress) {
                SelectionContainer { Text(snap.transcript(), modifier = Modifier.heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())) }
            }
            OutlinedTextField(artifactPath, { artifactPath = it }, label = { Text("Result file path from Computer") },
                supportingText = { Text("Relative to the workspace above; files up to approximately 1.5 MB can be copied into Meow Files.") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { saveResult(artifactPath) }, enabled = !busy && artifactPath.isNotBlank()) {
                Text("Save & preview result")
            }
        }
    }
}

@Composable
internal fun ComputerReportedResultCard(path: String, busy: Boolean, onSave: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(path.substringAfterLast('/'), style = MaterialTheme.typography.titleSmall)
            Text(path, style = MaterialTheme.typography.bodySmall)
            Text("Reported output · not a verified local copy", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onSave, enabled = !busy) { Text("Save & preview") }
        }
    }
}
