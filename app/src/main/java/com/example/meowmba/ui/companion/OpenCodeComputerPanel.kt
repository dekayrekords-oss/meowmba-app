package com.example.meowmba.ui.companion

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
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
internal fun OpenCodeComputerPanel() {
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
    val answers = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(Unit) {
        try {
        withContext(Dispatchers.IO) {
            val storage = SecureStorageManager(context)
            val key = storage.getApiKey(OpenCodeLocalConnection.CREDENTIAL).orEmpty()
            client = key.takeIf { it.isNotBlank() }?.let { OpenCodeComputer(it) }
            session = storage.getPreference(OpenCodeComputer.SESSION_KEY).orEmpty()
            prompt = storage.getPreference("computer_task_draft").orEmpty()
        }
        status = if (client == null) "Finish Setup computer first. Your existing OpenCode model/account is used; Meow does not select a paid model for you." else "Computer · OpenCode. Review its configured model before running; provider charges may apply."
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { client = null; status = "Secure connection storage is unavailable. Unlock the phone and reopen Computer setup. Nothing was sent." }
    }
    LaunchedEffect(session, client) {
        val api = client ?: return@LaunchedEffect
        if (session.isBlank()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                try { snapshot = api.snapshot(session); missingSession = false; status = "Computer: ${snapshot!!.status}" }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    missingSession = e.message?.contains("HTTP 404") == true
                    status = e.message ?: "Computer unavailable. Task may still be running."
                }
                delay(if (snapshot?.status == "idle") 10_000 else 3_000)
            }
        }
    }
    fun action(block: suspend (OpenCodeComputer) -> Unit) {
        val api = client ?: return
        busy = true
        scope.launch {
            try { block(api) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { status = e.message ?: "Computer action failed; refresh before retrying." }
            finally { busy = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Computer", style = MaterialTheme.typography.titleLarge)
        Text(status)
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
            status = "Task submitted. Waiting for OpenCode's progress…"
        } }, enabled = client != null && !busy && prompt.isNotBlank() && (session.isBlank() || snapshot != null) &&
            snapshot?.status !in listOf("busy", "retry") && snapshot?.permissions.orEmpty().isEmpty() && snapshot?.questions.orEmpty().isEmpty()) { Text("Run with Computer") }
        if (session.isNotBlank()) {
            OutlinedButton(onClick = { action { it.stop(session); status = "Stop requested; checking worker status…" } }, enabled = !busy) { Text("Stop task") }
            TextButton(onClick = { action {
                withContext(Dispatchers.IO) { SecureStorageManager(context).savePreference(OpenCodeComputer.SESSION_KEY, "") }
                session = ""; snapshot = null; missingSession = false; status = "New task. Previous session remains in OpenCode."
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
            if (snap.todos.isNotEmpty()) {
                Text("Plan", style = MaterialTheme.typography.titleMedium)
                snap.todos.forEach { todo -> Text("${todo.jsonObject["status"]?.jsonPrimitive?.content}: ${todo.jsonObject["content"]?.jsonPrimitive?.content}") }
            }
            Text("Progress & results", style = MaterialTheme.typography.titleMedium)
            SelectionContainer { Text(snap.transcript()) }
            snap.resultPaths().forEach { path ->
                TextButton(onClick = { artifactPath = path }) { Text("Result: $path") }
            }
            OutlinedTextField(artifactPath, { artifactPath = it }, label = { Text("Result file path from Computer") },
                supportingText = { Text("Relative to the workspace above; files up to approximately 1.5 MB can be copied into Meow Files.") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { action { api ->
                val path = OpenCodeComputer.artifactPath(artifactPath.trim())
                val bytes = api.artifact(session, path)
                val file = withContext(Dispatchers.IO) {
                    val folder = java.io.File(context.filesDir, "memba_workspace/projects/opencode/${OpenCodeComputer.id(session)}").apply { mkdirs() }
                    java.io.File(folder, "${System.currentTimeMillis()}-${com.example.meowmba.core.files.AssetLibrary.safeName(path.substringAfterLast('/'))}").also { it.writeBytes(bytes) }
                }
                status = com.example.meowmba.core.media.ArtifactViewer.open(context, file).getOrElse { "Saved to Meow Files: ${file.name}. No compatible viewer is available." }
            } }, enabled = !busy && artifactPath.isNotBlank()) { Text("Save result to Meow Files & open") }
        }
    }
}
