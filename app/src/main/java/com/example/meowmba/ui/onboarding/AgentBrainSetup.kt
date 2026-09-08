package com.example.meowmba.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.agent.*
import com.example.meowmba.core.download.DownloadService
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.setup.*
import com.example.meowmba.core.workflows.WorkflowRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Local-first, verified setup. Cloud can be connected while the local
 * download continues; it never receives user content during these probes. */
@Composable
internal fun AgentBrainSetup(onContinue: () -> Unit) {
    val context = LocalContext.current
    val storage = remember { SecureStorageManager(context) }
    val runtime = remember { WorkflowRuntime.get(context) }
    val scope = rememberCoroutineScope()
    val option = remember { ModelCatalog.options.first { it.id == "gemma-4-e2b" } }
    val directory = remember { File(context.filesDir, "models") }
    val download by DownloadService.status.collectAsState()
    var localStatus by remember { mutableStateOf("") }
    var cloudStatus by remember { mutableStateOf("") }
    var localBusy by remember { mutableStateOf(false) }
    var cloudBusy by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(SetupWiring.isBrainReady(context)) }
    var pendingLocal by rememberSaveable { mutableStateOf(false) }
    var consent by rememberSaveable { mutableStateOf(storage.getPreference("brain_provider") == "openrouter" && SetupWiring.isBrainReady(context)) }
    var allowPaid by rememberSaveable { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var models by remember { mutableStateOf(emptyList<OpenRouterModel>()) }
    var selected by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var cloudSelected by rememberSaveable { mutableStateOf(storage.getPreference("brain_provider") == "openrouter" && SetupWiring.isBrainReady(context)) }

    fun verifyLocal(file: File) {
        localBusy = true; pendingLocal = false; localStatus = "Testing navigation and follow-up context on this phone…"
        scope.launch {
            try {
                withContext(Dispatchers.IO) { runtime.mutex.withLock { runtime.verifyAndSelectLocal(file.absolutePath) } }
                storage.savePreference("planner_verified_local", file.absolutePath)
                if (!cloudSelected) { SetupWiring.setBrain(context, "local", null, null); ready = SetupWiring.isBrainReady(context) }
                localStatus = "Local model passed both planning checks. Downloaded and selected for local agents."
            } catch (e: TimeoutCancellationException) { localStatus = "Test timed out. Try again when the phone is cool, or connect OpenRouter below." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { localStatus = e.message ?: "Local model test failed. Connect OpenRouter or try another model in Hardware." }
            finally { localBusy = false }
        }
    }
    LaunchedEffect(download, pendingLocal) {
        if (pendingLocal && !download.active) {
            if (download.fileName == option.fileName && download.error != null) { localStatus = download.error.orEmpty(); pendingLocal = false }
            else File(directory, option.fileName).takeIf { it.isFile && it.length() > 0 }?.let { verifyLocal(it) }
        }
    }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("1 · Download your on-phone AI", style = MaterialTheme.typography.titleMedium)
        Text("${option.displayName} · ${option.approxSize}. Private local planning and conversation, not just Needle's tiny action router. Wi-Fi and at least 4 GB of free storage are recommended. Complex tasks can still need a stronger cloud model.")
        Button(enabled = !localBusy && !download.active, modifier = Modifier.fillMaxWidth(), onClick = {
            val installed = File(directory, option.fileName).takeIf { it.isFile && it.length() > 0 }
            if (installed != null) verifyLocal(installed)
            else runCatching {
                check(context.filesDir.usableSpace > 4_000_000_000L) { "Free at least 4 GB, or use OpenRouter below." }
                pendingLocal = true
                DownloadService.start(context, option.downloadUrl, option.fileName, option.id, null, directory)
                localStatus = "Downloading. You may connect cloud below while this continues."
            }.onFailure { pendingLocal = false; localStatus = it.message.orEmpty() }
        }) { Text(if (localBusy) "Testing local model…" else "Download or verify local AI") }
        if (download.active) {
            val total = download.total
            if (total != null && total > 0) LinearProgressIndicator(progress = { (download.received.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("${download.fileName}: ${download.received / 1_000_000} MB downloaded", style = MaterialTheme.typography.bodySmall)
        }
        Text(localStatus, style = MaterialTheme.typography.bodySmall)
    } }
    Spacer(Modifier.height(12.dp))
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("2 · Stronger AI for multi-app tasks", style = MaterialTheme.typography.titleMedium)
        Text("For tasks inside Reddit, Instagram, X, ChatGPT, Gemini and other apps, connect a capable OpenRouter tool-calling model here. Local AI remains an option, but small phone models can struggle with unfamiliar layouts and long plans. Another AI app's subscription is not an OpenRouter API key.")
        Text("OpenRouter requires your own account and key. If selected, conversations and approved task context leave this phone. Free models have limits; paid models can incur charges. No automatic switch to paid models.")
        Row { Checkbox(consent, {
            consent = it
            if (!it && cloudSelected) {
                cloudSelected = false; SetupWiring.setBrain(context, "local", null, null)
                ready = SetupWiring.isBrainReady(context); cloudStatus = "Cloud deselected. Verify a local model to continue."
            }
        }, enabled = !cloudBusy); Text("I allow OpenRouter access, two synthetic planning tests, and cloud AI for my requests.", style = MaterialTheme.typography.bodySmall) }
        if (consent) {
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://openrouter.ai/settings/keys"))) }
                    .onFailure { cloudStatus = "Open openrouter.ai/settings/keys in your browser, then return here with a key." }
            }) { Text("Open OpenRouter → create API key") }
            OutlinedTextField(key, { key = it }, enabled = !cloudBusy, label = { Text("Paste your OpenRouter key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row { Checkbox(allowPaid, { allowPaid = it; selected = "" }, enabled = !cloudBusy); Text("Also show paid models (usage charges may apply)", style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(enabled = !cloudBusy, onClick = {
                cloudBusy = true
                scope.launch {
                    try {
                        models = OpenRouterModels.fetch().getOrThrow().filter { it.tools }
                        selected = models.firstOrNull { it.free }?.id.orEmpty()
                        cloudStatus = if (models.isEmpty()) "No tool-capable models returned. Retry the catalog." else "Choose a model; we test its actual action protocol before selecting it. Free does not guarantee enough reasoning quality for every app."
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { cloudStatus = "Could not load models. Check internet and retry." }
                    finally { cloudBusy = false }
                }
            }) { Text("Find current tool-capable models") }
            Box {
                OutlinedButton(onClick = { expanded = true }, enabled = models.isNotEmpty() && !cloudBusy) { Text(selected.ifBlank { "Choose model" }) }
                DropdownMenu(expanded, { expanded = false }) {
                    models.filter { it.free || allowPaid }.forEach { model ->
                        DropdownMenuItem(text = { Text("${if (model.free) "Free" else "Paid"} · ${model.name}") }, onClick = { selected = model.id; expanded = false })
                    }
                }
            }
            Button(enabled = !cloudBusy && key.isNotBlank() && selected.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                val candidateKey = key.trim(); val candidateModel = selected
                cloudBusy = true; cloudStatus = "Testing navigation and context before saving…"
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            RemoteModelProvider("OpenRouter", "https://openrouter.ai/api", candidateKey, candidateModel).use { PlannerReadiness.verify(it) }
                            storage.saveVerifiedApiKey("openrouter", candidateKey)
                            SetupWiring.setBrain(context, "openrouter", candidateKey, candidateModel)
                            storage.savePreference("planner_verified_openrouter", PlannerReadiness.cloudStamp(candidateModel, candidateKey))
                        }
                        cloudSelected = true; ready = SetupWiring.isBrainReady(context); key = ""
                        cloudStatus = "Both checks passed. OpenRouter selected; local downloads can continue separately."
                    } catch (e: TimeoutCancellationException) { cloudStatus = "Model timed out; your previous setup was kept. Try another model." }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { cloudStatus = e.message ?: "Connection failed; check your key and model availability." }
                    finally { cloudBusy = false }
                }
            }) { Text(if (cloudBusy) "Checking…" else "Verify, save securely & use cloud") }
        }
        Text(cloudStatus, style = MaterialTheme.typography.bodySmall)
    } }
    Spacer(Modifier.height(12.dp))
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("3 · Enable the controls you need", style = MaterialTheme.typography.titleMedium)
        Text("Phone apps: enable Meow Accessibility, then sign into the apps you want it to use. Meow reads exposed controls, not hidden app data. Passwords, CAPTCHA, pairing codes and OS permission prompts stay with you. Posting, sharing and sending—even to another AI—require verification.")
        TextButton(onClick = { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }) { Text("Open Accessibility setup") }
        Text("Bluetooth: allow Nearby devices, then ask 'connect Bluetooth device …'. Android handles pairing; special device features still need a compatible integration.")
        TextButton(onClick = { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) } }) { Text("Open Bluetooth setup") }
        Text("Computer: run the desktop bridge, pair once in Settings → Desktop Bridge, and approve the computer's Accessibility/Screen Recording access. A cable alone does not install or authorize the helper. Disconnection no longer forgets pairing. Desktop app control and Blender require the corresponding enabled bridge capabilities.")
        Text("Change brains later: ask 'list your models' and 'switch your model to local <filename>' or choose the verified OpenRouter model. Switching asks permission and tests the model first; new paid choices stay in setup.")
    } }
    Button(onClick = onContinue, enabled = ready && !localBusy && !cloudBusy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("AI verified · Continue") }
    Text("Passing these small checks is not a guarantee of every task. Meow will still ask for missing permissions and stop when it cannot verify a result.", style = MaterialTheme.typography.bodySmall)
}
