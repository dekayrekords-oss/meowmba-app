package com.example.meowmba.ui.onboarding

import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Card
import com.example.meowmba.ui.theme.OutlinedCard

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import com.example.meowmba.ui.theme.DropdownMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meowmba.core.setup.*
import kotlinx.coroutines.launch

/** Download first if desired, but a working cloud connection can finish independently. */
@Composable
internal fun AgentBrainSetup(onContinue: () -> Unit, modelOverride: BrainSetupModel? = null) {
    val context = LocalContext.current
    val backend = remember { if (modelOverride == null) AndroidBrainSetup(context) else null }
    val model = modelOverride ?: viewModel<BrainSetupModel>(key = "onboarding-brain", factory = remember {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = BrainSetupModel(checkNotNull(backend)) as T
        }
    })
    val state by model.ui.collectAsState()
    val download by model.downloads.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val scope = rememberCoroutineScope()
    val cloudStart = remember { BringIntoViewRequester() }
    var importing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && backend != null) scope.launch {
            importing = true; model.message("Importing the selected model…")
            try { model.chooseLocal(backend.importModel(uri)); model.refresh() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { model.message(e.message ?: "Could not import this file") }
            finally { importing = false }
        }
    }
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { model.message("Open $url in your browser, then return to Meow.") }
    }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Your phone", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = model::refresh) { Text("Scan again") }
        }
        if (state.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.scan?.let { scan ->
            Text("%.1f GB RAM · %d CPU cores · %.1f GB storage free".format(scan.profile.ramGb, scan.profile.cores, scan.profile.freeBytes / 1_000_000_000.0))
            if (scan.profile.preferClassic) Text("Lightweight Classic suggested. You can change avatars later.")
            Text(when {
                !scan.profile.supportsLocal -> "OpenRouter recommended: limited local-AI hardware."
                scan.recommended == null -> "Low storage: reuse a model or connect OpenRouter."
                else -> "Suggested: ${scan.recommended.displayName} (${scan.recommended.approxSize}), based on RAM and free space."
            }, style = MaterialTheme.typography.bodySmall)
        }
        Text("Default brain: OpenRouter. Connect your key below, or choose local-only AI if you prefer.")
        Button(onClick = { scope.launch { cloudStart.bringIntoView() } }, modifier = Modifier.fillMaxWidth()) { Text("Set up OpenRouter (recommended) ↓") }
    } }
    Spacer(Modifier.height(12.dp))
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Optional · Local AI on this phone", style = MaterialTheme.typography.titleMedium)
        Text("Reuse an existing model or download one. Cloud can be connected separately.")
        state.scan?.models?.take(8)?.forEach { file ->
            OutlinedCard(onClick = { model.chooseLocal(file) }, modifier = Modifier.fillMaxWidth(), enabled = !state.localBusy && !importing) {
                Row(Modifier.padding(12.dp)) {
                    RadioButton(selected = state.localFile == file, onClick = null)
                    Column { Text(file.name); Text("Downloaded · ${file.length() / 1_000_000} MB", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Text(state.localStatus, style = MaterialTheme.typography.bodyMedium)
        state.localFile?.let { file ->
            Button(onClick = { model.verifyLocal(file) }, enabled = !state.localBusy && !importing && !state.plannerBusy,
                modifier = Modifier.fillMaxWidth()) { Text(if (state.localBusy) "Testing downloaded model…" else "Verify & use this local model") }
        }
        if (state.localFile == null && state.scan?.recommended != null) {
            Button(onClick = model::downloadLocal, enabled = !download.active && !state.localBusy && !state.scanning,
                modifier = Modifier.fillMaxWidth()) { Text("Download ${state.scan?.recommended?.displayName}") }
        }
        if (download.active) {
            val total = download.total
            if (total != null && total > 0) LinearProgressIndicator(progress = { (download.received.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("${download.fileName}: ${download.received / 1_000_000} MB${total?.let { " / ${it / 1_000_000} MB" }.orEmpty()}")
        }
        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !importing && !state.localBusy, modifier = Modifier.fillMaxWidth()) {
            Text(if (importing) "Importing…" else "Find an existing model in Files")
        }
        Text("Scans Meow's accessible folders. Use Files for a .task or .litertlm model in Downloads or another protected folder.", style = MaterialTheme.typography.bodySmall)
    } }
    Spacer(Modifier.height(12.dp))
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Default · Connect OpenRouter", style = MaterialTheme.typography.titleMedium, modifier = Modifier.bringIntoViewRequester(cloudStart))
        Text("Recommended for stronger reasoning and multi-app tasks. Your OpenRouter API key is separate from a ChatGPT or other AI subscription.")
        Button(onClick = { open("https://openrouter.ai/settings/keys") }, modifier = Modifier.fillMaxWidth()) { Text("Get an OpenRouter API key ↗") }
        Text("1. Tap the button above and sign in or create an account.\n2. Choose Create Key, name it Meow, and copy the full secret key.\n3. Return here, allow cloud use below, paste the key, then tap Save key & connect OpenRouter.", style = MaterialTheme.typography.bodySmall)
        Row {
            Checkbox(state.consent, model::setConsent, enabled = !state.cloudBusy)
            Text("I allow OpenRouter to process my requests and synthetic setup tests. Conversations and approved task context leave the phone. Free models have limits; paid models can incur charges.", style = MaterialTheme.typography.bodySmall)
        }
        if (state.consent) {
            if (state.saved.keyPresent) Text("API key saved securely on this phone. Leave the field empty to reuse it.", color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(state.key, model::setKey, enabled = !state.cloudBusy,
                label = { Text("Paste your OpenRouter key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(onClick = model::connectCloud, enabled = state.canConnect, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.cloudBusy) "Connecting…" else if (state.saved.keyPresent && state.key.isBlank()) "Retry / verify saved OpenRouter key" else "Save key & connect OpenRouter")
            }
            if (!state.canConnect && !state.cloudBusy) Text("Paste your complete API key to enable Save. No model selection is required.", style = MaterialTheme.typography.bodySmall)
            Text("Automatic setup tries free tool-capable models. The key stays saved if a model is busy or unavailable; retry without pasting again. OpenRouter becomes the default; local downloads stay optional.", style = MaterialTheme.typography.bodySmall)
            var showOptions by remember { mutableStateOf(false) }
            TextButton(onClick = { showOptions = !showOptions; if (showOptions && state.models.isEmpty()) model.loadModels() }, enabled = !state.cloudBusy) { Text(if (showOptions) "Hide model options" else "Choose a different model (optional)") }
            if (showOptions) {
                Row { Checkbox(state.allowPaid, model::setPaid, enabled = !state.cloudBusy); Text("Show paid models (charges may apply)") }
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, enabled = !state.cloudBusy) { Text(state.selected.ifBlank { "Automatic · free models only" }) }
                    DropdownMenu(expanded, { expanded = false }) {
                        DropdownMenuItem(text = { Text("Automatic · free models only") }, onClick = { model.selectModel(""); expanded = false })
                        state.models.filter { it.free || state.allowPaid }.forEach { choice ->
                            DropdownMenuItem(text = { Text("${if (choice.free) "Free" else "Paid"} · ${choice.name}") }, onClick = { model.selectModel(choice.id); expanded = false })
                        }
                    }
                }
                TextButton(onClick = model::loadModels, enabled = !state.cloudBusy) { Text("Refresh model list") }
            }
        }
        Text(state.cloudStatus, style = MaterialTheme.typography.bodyMedium)
    } }
    Spacer(Modifier.height(12.dp))
    if (state.saved.ready) {
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI ready · ${if (state.saved.cloudSelected) "OpenRouter" else "Local"}", style = MaterialTheme.typography.titleMedium)
            Text("A real chat reply was verified. Advanced app control is a separate capability check, not a condition for saving your key.")
            OutlinedButton(onClick = model::testPlanner, enabled = !state.localBusy && !state.cloudBusy && !state.plannerBusy) {
                Text(if (state.plannerBusy) "Testing advanced planning…" else "Test advanced app control (optional)")
            }
            Text(state.plannerStatus, style = MaterialTheme.typography.bodySmall)
        } }
    }
    Button(onClick = onContinue, enabled = state.canContinue, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("AI ready · Continue") }
    if (!state.saved.ready) Text("Continue unlocks after a local or OpenRouter model answers the test. A saved key or completed download alone is not a working AI.", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }) { Text("Optional: enable phone app control") }
    Text("Enable Meow in Accessibility and sign in to the apps you want to use. Posting and sending still require confirmation. Logins, CAPTCHA, payments, deletion and Android permissions stay under your control.", style = MaterialTheme.typography.bodySmall)
}
