package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Card
import com.example.meowmba.ui.theme.FilterChip

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.agent.OpenRouterModel
import com.example.meowmba.core.agent.ProviderModels
import com.example.meowmba.core.agent.RemoteModelProvider
import com.example.meowmba.core.agent.OpenRouterModels
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.*
import androidx.lifecycle.repeatOnLifecycle

/** Provider edits stay local until explicit Save; viewing a card never changes
 * the active brain, sends a secret, or claims that a connection was tested. */
@Composable
internal fun CloudModelCard(
    provider: String, storage: SecureStorageManager?, modelName: String, endpoint: String,
    keyConfigured: Boolean, status: LoadStatus, onSelect: () -> Unit, onSaved: () -> Unit,
    loadCatalog: suspend () -> Result<List<OpenRouterModel>> = { OpenRouterModels.fetch() },
    loadProviderCatalog: suspend (String, String, String, Boolean) -> Result<List<OpenRouterModel>> = { p, e, k, a -> ProviderModels.fetch(p, e, k, a) },
) {
    val label = providerLabel(provider)
    var expanded by rememberSaveable { mutableStateOf(false) }
    LoadedModelCard(
        icon = { Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary) },
        title = "$label · cloud", primary = modelName,
        details = listOf("Endpoint: $endpoint", if (keyConfigured) "API key saved securely" else "No key saved yet"),
        status = status,
        onClick = { if (keyConfigured) onSelect() else expanded = true },
        actionLabel = if (keyConfigured) "Tap to use $label" else "Tap to set up $label",
        settings = {
            OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(if (expanded) "Hide $label settings" else "$label settings")
            }
            if (expanded) {
                if (storage != null) CloudProviderSettings(provider, storage, onSaved, loadCatalog, loadProviderCatalog)
                else Text("Settings are unavailable in this preview.")
            }
        },
    )
}

@Composable
internal fun CloudProviderSettings(
    provider: String, storage: SecureStorageManager, onSaved: () -> Unit,
    loadCatalog: suspend () -> Result<List<OpenRouterModel>> = { OpenRouterModels.fetch() },
    loadProviderCatalog: suspend (String, String, String, Boolean) -> Result<List<OpenRouterModel>> = { p, e, k, a -> ProviderModels.fetch(p, e, k, a) },
) {
    val router = provider == "openrouter"
    val label = providerLabel(provider)
    val custom = provider == "custom"
    val zen = provider == "opencode"
    val routeId = if (zen) "bigpickle" else provider
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(storage.getApiKey(provider).orEmpty()) }
    var model by remember { mutableStateOf(storage.getModelName(provider) ?: if (router) "openrouter/free" else if (provider == "openai") "gpt-4o-mini" else if (zen) "big-pickle" else "") }
    var endpoint by remember { mutableStateOf(storage.getEndpoint(provider).orEmpty()) }
    var anthropic by remember { mutableStateOf(provider == "anthropic" || storage.getPreference("${provider}_api_format") == "anthropic" && (custom || zen)) }
    var showKey by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(storage.getPreference("brain_provider") == routeId) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!custom) Button(onClick = {
            val url = if (zen) "https://opencode.ai/auth" else if (provider == "anthropic") "https://platform.claude.com/settings/keys" else if (router) "https://openrouter.ai/settings/keys" else "https://platform.openai.com/api-keys"
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                .onFailure { note = "Open $url in your browser, then return here." }
        }, modifier = Modifier.fillMaxWidth()) { Text("Get an $label API key ↗") }
        Text("Sign in, create an API key, copy the full secret, then return and paste it below. API billing is separate from chat subscriptions.", style = MaterialTheme.typography.bodySmall)
        ApiKeyField(key, { key = it; note = "" }, "$label API key", showKey, { showKey = !showKey })
        if (router) ModelCatalogField("OpenRouter chat model", model, { model = it; note = "" }, loadCatalog)
        else {
            if (custom || zen) {
                Text("API format", style = MaterialTheme.typography.labelLarge)
                Row {
                    FilterChip(!anthropic, { anthropic = false }, label = { Text("OpenAI compatible") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(anthropic, { anthropic = true }, label = { Text("Claude Messages") })
                }
            }
            LiveProviderCatalog(provider, endpoint, key, anthropic, model, { model = it; note = "" }, loadProviderCatalog)
        }
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced endpoint" else "Advanced endpoint") }
        if (advanced || custom) ProviderEndpointField(endpoint, { endpoint = it; note = "" }, if (custom) "$label base URL (required)" else "$label base URL (optional)",
            if (custom) "Required HTTPS base URL. This server receives your key and requests. A trailing /v1 is accepted." else "Leave blank for the official service. A custom server receives your key and requests.")
        Row {
            Checkbox(consent, { consent = it }, enabled = !busy)
            Text("Use $label for my requests. Approved conversation/task context leaves the phone; the selected model may incur charges.", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = {
            val savedKey = key.trim(); val savedModel = model.trim(); val savedEndpoint = endpoint.trim().trimEnd('/')
            busy = true; note = "Saving…"
            scope.launch {
                try {
                    require(savedEndpoint.isBlank() || runCatching {
                        val uri = java.net.URI(savedEndpoint)
                        uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null
                    }.getOrDefault(false)) { "Enter an http(s) base URL without credentials, query or fragment." }
                    if (!router) ProviderModels.base(provider, savedEndpoint)
                    withContext(Dispatchers.IO) {
                        storage.saveSetupSelection(mapOf("brain_provider" to routeId, "default_brain" to routeId,
                            "selected_model" to "Omniroute (Local router)") + (if (custom || zen) mapOf("${provider}_api_format" to (if (anthropic) "anthropic" else "openai")) else emptyMap()), provider, savedKey, savedModel, savedEndpoint)
                    }
                    onSaved()
                    note = "$label settings saved · selected as default. Connection not tested; use Test model reply to verify."
                } catch (e: CancellationException) { throw e }
                catch (e: IllegalArgumentException) { note = if (router) "Enter a valid base URL." else "Enter an HTTPS base URL without credentials, query or fragment." }
                catch (e: Exception) { note = "Could not save settings. Your draft is kept; retry." }
                finally { busy = false }
            }
        }, enabled = !busy && consent && key.isNotBlank() && model.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Saving…" else "Save & use $label")
        }
        OutlinedButton(onClick = {
            busy = true; note = "Testing model reply…"
            scope.launch {
                try {
                    val base = if (router) endpoint.ifBlank { "https://openrouter.ai/api" } else ProviderModels.base(provider, endpoint)
                    val result = withContext(Dispatchers.IO) {
                        RemoteModelProvider(label, base, key.trim(), model.trim(),
                            if (anthropic) RemoteModelProvider.ApiStyle.ANTHROPIC else RemoteModelProvider.ApiStyle.OPENAI_COMPATIBLE).use {
                            withTimeout(30_000) { it.generate("Reply with OK only.") }
                        }
                    }
                    note = when (result) {
                        is com.example.meowmba.core.agent.ModelResponse.Complete -> if (result.text.isBlank()) "The provider returned an empty reply." else "Reply received from $label. Save to use these settings."
                        is com.example.meowmba.core.agent.ModelResponse.Error -> result.message
                        else -> "The provider did not finish its reply."
                    }
                } catch (e: TimeoutCancellationException) { note = "Reply test timed out. Check the connection and model." }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { note = "Could not test this connection. Check the key, HTTPS endpoint and model." }
                finally { busy = false }
            }
        }, enabled = !busy && key.isNotBlank() && model.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Test model reply (uses API)") }
        if (zen) Text("OpenCode Zen is the cloud model service. OpenCode CLI is a separate desktop coding agent. This list shows models for the selected API format; Responses-only and Google-format models are not yet supported here.", style = MaterialTheme.typography.bodySmall)
        Text("This model can use Meow's available phone, hardware and desktop tools. Device permissions, approvals and audit history still apply. Claude chat does not provide speech transcription.", style = MaterialTheme.typography.bodySmall)
        if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodySmall)
    }
}

/** Public, keyless catalog is loaded only when its settings are opened. The
 * manually entered ID remains usable even if catalog retrieval fails. */
@Composable
internal fun ModelCatalogField(label: String, selected: String, onSelect: (String) -> Unit,
    loadCatalog: suspend () -> Result<List<OpenRouterModel>>, visionOnly: Boolean = false) {
    var models by remember { mutableStateOf<List<OpenRouterModel>>(emptyList()) }
    var note by remember { mutableStateOf("Loading available models…") }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        try {
            loadCatalog().fold(onSuccess = {
                models = it.filter { model -> !visionOnly || model.vision }
                note = "${models.size} ${if (visionOnly) "image-capable " else ""}models. Custom IDs are also supported."
            }, onFailure = { note = "Catalog unavailable. Enter a model ID below or refresh." })
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { note = "Catalog unavailable. Enter a model ID below or refresh." }
    }
    OpenRouterModelPicker(label, selected, models, onSelect)
    Text(note, style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { note = "Loading available models…"; reload++ }) { Text("Refresh $label list") }
}

@Composable
internal fun VisionModelSettingsCard(storage: SecureStorageManager, onSaved: () -> Unit,
    loadCatalog: suspend () -> Result<List<OpenRouterModel>> = { OpenRouterModels.fetch() }) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Vision", style = MaterialTheme.typography.titleMedium)
            Text("Models for understanding photos and screens. Image generation is separate.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(if (expanded) "Hide vision settings" else "Vision settings")
            }
            if (expanded) VisionModelSettings(storage, onSaved, loadCatalog)
        }
    }
}

@Composable
internal fun VisionModelSettings(storage: SecureStorageManager, onSaved: () -> Unit,
    loadCatalog: suspend () -> Result<List<OpenRouterModel>>) {
    var routerModel by remember { mutableStateOf(storage.getModelName("openrouter_vision") ?: "google/gemini-2.5-flash") }
    var openaiModel by remember { mutableStateOf(storage.getModelName("openai_vision") ?: "gpt-4o-mini") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ModelCatalogField("OpenRouter vision model", routerModel, { routerModel = it; note = "" }, loadCatalog, visionOnly = true)
    OutlinedTextField(openaiModel, { openaiModel = it; note = "" }, label = { Text("OpenAI vision model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("Uses the matching provider's key and endpoint from the cards above. Select an image-capable model; saving does not test it or send a photo. Cloud vision may incur charges.", style = MaterialTheme.typography.bodySmall)
    if (!storage.getEndpoint("network_vision").isNullOrBlank()) Text("Your existing server vision connection keeps its routing priority.", style = MaterialTheme.typography.bodySmall)
    Button(onClick = {
        val choices = mapOf("openrouter_vision" to routerModel.trim(), "openai_vision" to openaiModel.trim())
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { storage.saveSetupSelection(emptyMap(), models = choices) }
                onSaved(); note = "Vision models saved. Your chat model and API keys are unchanged."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { note = "Could not save vision settings. Retry." }
            finally { busy = false }
        }
    }, enabled = !busy && routerModel.isNotBlank() && openaiModel.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text(if (busy) "Saving…" else "Save vision settings")
    }
    if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodySmall)
}

internal fun providerLabel(provider: String): String = when (provider) {
    "openrouter" -> "OpenRouter"
    "anthropic" -> "Claude / Anthropic"
    "custom" -> "Custom API"
    "opencode" -> "OpenCode Zen"
    else -> "OpenAI"
}

/** Refresh on opening, key/endpoint changes (debounced), and every five minutes
 * while visible. Lifecycle cancellation avoids background catalog polling. */
@Composable
private fun LiveProviderCatalog(provider: String, endpoint: String, apiKey: String, anthropic: Boolean,
                                selected: String, onSelect: (String) -> Unit,
                                load: suspend (String, String, String, Boolean) -> Result<List<OpenRouterModel>>) {
    var models by remember(provider, endpoint, apiKey, anthropic) { mutableStateOf<List<OpenRouterModel>>(emptyList()) }
    var note by remember { mutableStateOf("Add a key to load models.") }
    var reload by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(provider, endpoint, apiKey, anthropic, reload, lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            delay(800)
            if (apiKey.isBlank()) { note = "Add a key to load models."; return@repeatOnLifecycle }
            while (isActive) {
                note = "Refreshing models…"
                load(provider, endpoint, apiKey, anthropic).fold(
                    onSuccess = { models = it; note = "${it.size} models · updates automatically. Selection is preserved. Model availability does not verify chat compatibility." },
                    onFailure = { note = (it.message ?: "Catalog unavailable.") + " You can enter a model ID manually." }
                )
                delay(300_000)
            }
        }
    }
    OpenRouterModelPicker("${providerLabel(provider)} chat model", selected, models, onSelect)
    Text(note, style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { reload++ }) { Text("Refresh model list") }
}
