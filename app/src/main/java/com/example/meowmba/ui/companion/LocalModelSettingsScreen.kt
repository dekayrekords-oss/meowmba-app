package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Card
import com.example.meowmba.ui.theme.FilterChip

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.meowmba.ui.theme.AlertDialog
import com.example.meowmba.ui.theme.DropdownMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.agent.*
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.setup.SetupDeviceProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** One home for local chat models. Browsing never changes the active AI provider. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelSettingsScreen(
    state: CompanionState,
    storage: SecureStorageManager? = null,
    onDownload: (LocalModelOption) -> Unit = {},
    onUse: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onCustomDownload: (String, String) -> Unit = { _, _ -> },
    onScan: () -> Unit = {},
    onImportFound: (DiscoveredModelFile) -> Unit = {},
    onImportUri: (Uri) -> Unit = {},
    onSavePath: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    deviceOverride: PhoneModelDevice? = null,
    searchModels: suspend (String, PhoneModelDevice, String?, String?) -> Result<PhoneModelSearchPage> =
        { query, device, token, cursor -> HuggingFaceLocalModels().search(query, device, token, cursor) },
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val links = LocalUriHandler.current
    var tab by rememberSaveable { mutableStateOf("Installed") }
    var refresh by remember { mutableIntStateOf(0) }
    val device = remember(refresh, deviceOverride) { deviceOverride ?: localModelDevice(context) }
    var query by rememberSaveable { mutableStateOf("") }
    var localQuery by rememberSaveable { mutableStateOf("") }
    var installedSort by remember { mutableStateOf(PhoneModelSort.NAME) }
    var models by remember { mutableStateOf<List<PhoneModelCandidate>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var searchedQuery by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var filters by remember { mutableStateOf(PhoneModelFilters()) }
    var sort by remember { mutableStateOf(PhoneModelSort.DOWNLOADS) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<InstalledModelFile?>(null) }
    var pendingDownload by remember { mutableStateOf<PhoneModelCandidate?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onImportUri) }
    LaunchedEffect(Unit) { onRefresh() }

    fun search(more: Boolean) {
        if (busy) return
        val requestQuery = if (more) searchedQuery else query.trim()
        val cursor = if (more) nextCursor else null
        if (!more) { models = emptyList(); nextCursor = null }
        busy = true
        searchMessage = null
        scope.launch {
            try {
                val result = searchModels(requestQuery, device, storage?.getApiKey("huggingface"), cursor)
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                result.fold({ page ->
                    models = ((if (more) models else emptyList()) + page.models).distinctBy { it.id }
                    nextCursor = page.nextCursor
                    searchedQuery = requestQuery
                    searched = true
                    searchMessage = "Checked ${page.scannedRepositories} repositories in this page. " + page.notices.joinToString(" ")
                }, { searchMessage = it.message ?: "Hugging Face is unavailable. Installed models still work." })
            } finally { busy = false }
        }
    }

    val merged = remember(models, state.installedModels) {
        models.map { model ->
            val file = state.installedModels.firstOrNull { it.fileName == model.fileName &&
                (model.sizeBytes == null || it.sizeBytes == model.sizeBytes) }
            model.copy(installedPath = file?.fileName, downloadedAtMs = file?.downloadedAtMs,
                localModifiedAtMs = file?.modifiedAtMs)
        }
    }
    val results = remember(merged, filters, sort) { PhoneModelDiscovery.filterAndSort(merged, filters, sort) }
    val installed = remember(state.installedModels, localQuery, installedSort) {
        val found = state.installedModels.filter { "${it.fileName} ${it.catalogDisplayName.orEmpty()}".contains(localQuery, true) }
        when (installedSort) {
            PhoneModelSort.SIZE -> found.sortedBy { it.sizeBytes }
            PhoneModelSort.DOWNLOADED -> found.sortedByDescending { it.downloadedAtMs ?: Long.MIN_VALUE }
            PhoneModelSort.UPDATED -> found.sortedByDescending { it.modifiedAtMs ?: Long.MIN_VALUE }
            else -> found.sortedBy { it.catalogDisplayName ?: it.fileName }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Local AI models") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to LLM") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("${PhoneModelDiscovery.formatBytes(device.ramBytes)} RAM · ${PhoneModelDiscovery.formatBytes(device.freeBytes)} free",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Private on-phone chat · no cloud key needed", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { refresh++; onRefresh() }) { Text("Refresh") }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                // All four destinations stay visible on narrow cover screens.
                listOf("Installed", "Recommended", "Hugging Face", "Settings").chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { name ->
                            FilterChip(selected = tab == name, onClick = { tab = name },
                                label = { Text(name) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    "Installed" -> {
                        item {
                            Text("Your model library", style = MaterialTheme.typography.titleLarge)
                            Text("Download complete means the file is saved. Use local AI selects it; the first reply verifies the runtime.",
                                style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(localQuery, { localQuery = it }, label = { Text("Find an installed model") },
                                singleLine = true, modifier = Modifier.fillMaxWidth())
                            ModelChoice("Sort", installedSort.label, listOf(PhoneModelSort.NAME, PhoneModelSort.SIZE,
                                PhoneModelSort.DOWNLOADED, PhoneModelSort.UPDATED).map { it.label to it }) { installedSort = it }
                            OutlinedButton(onClick = onScan, enabled = !state.modelScanRunning,
                                modifier = Modifier.fillMaxWidth()) { Text(if (state.modelScanRunning) "Scanning accessible folders…" else "Search this phone for models") }
                            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !state.modelImportRunning,
                                modifier = Modifier.fillMaxWidth()) { Text("Choose a model file") }
                            Text("Android protects other apps’ files. The system picker lets you choose a model in Downloads, an SD card or another accessible folder.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        state.modelScanMessage?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium) } }
                        if (state.modelImportRunning || state.modelScanRunning) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        if (installed.isEmpty()) item {
                            Text(if (localQuery.isNotBlank()) "No installed models match." else "No models saved here yet. Check your phone before downloading another copy.")
                            Button(onClick = { tab = "Recommended" }) { Text("Browse recommended models") }
                        }
                        items(installed, key = { it.fileName }) { file ->
                            LocalInstalledCard(file, state.loadedModels.localPath.endsWith("/${file.fileName}") &&
                                state.loadedModels.activeSource == ModelSource.LOCAL,
                                onUse = { onUse(file.fileName) }, onDelete = { pendingDelete = file })
                        }
                        items(state.discoveredModelFiles.filter { found -> state.installedModels.none { it.fileName == found.fileName } },
                            key = { it.uri }) { found ->
                            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                                Text(found.fileName, style = MaterialTheme.typography.titleSmall)
                                Text("${PhoneModelDiscovery.formatBytes(found.sizeBytes)} · found on phone", style = MaterialTheme.typography.bodySmall)
                                Text("Only this selected file will be copied into the app.", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { onImportFound(found) }, enabled = !state.modelImportRunning) { Text("Import model") }
                            } }
                        }
                    }
                    "Recommended" -> {
                        item {
                            Text("Start here", style = MaterialTheme.typography.titleLarge)
                            Text("Curated chat bundles for this phone. Smaller models usually respond faster; larger models need more memory. Performance is estimated, not benchmarked.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        val suitable = ModelCatalog.options.filter { it.runtime.canActivate && device.arm64 && !device.lowRam &&
                            it.minRamGb <= device.ramBytes / PhoneModelDiscovery.GIB.toDouble() + 0.15 }
                        if (suitable.isEmpty()) item { Text("This device is below the recommended local-model requirements. You can use OpenRouter from the LLM page instead.") }
                        items(suitable, key = { it.id }) { option ->
                            val hasSpace = device.freeBytes >= SetupDeviceProfile.requiredSpace(option)
                            CatalogModelItem(option, state, onDownload = { onDownload(option) },
                                onUse = { onUse(option.fileName) },
                                onDelete = { pendingDelete = state.installedModels.firstOrNull { it.fileName == option.fileName } },
                                downloadEnabled = hasSpace)
                            if (!hasSpace) Text("Free more storage before downloading this model.", color = MaterialTheme.colorScheme.error)
                        }
                        item { CustomModelDownload(state, onCustomDownload) }
                    }
                    "Hugging Face" -> {
                        item {
                            Text("Discover local models", style = MaterialTheme.typography.titleLarge)
                            Text("Searches public Hugging Face metadata for supported chat bundles. Your search goes to Hugging Face; no conversation or phone files are sent. Nothing downloads until you choose it.",
                                style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(query, { query = it }, label = { Text("Search Hugging Face models") },
                                placeholder = { Text("Gemma, Qwen, LiteRT…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(onClick = { search(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Text(if (busy) "Searching…" else "Search library")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ModelChoice("Sort", sort.label, PhoneModelSort.entries.map { it.label to it }) { sort = it }
                                TextButton(onClick = { filterOpen = !filterOpen }) { Text(if (filterOpen) "Hide filters" else "Filters") }
                            }
                            if (filterOpen) PhoneModelFilterPanel(filters, merged, onChange = { filters = it })
                            Text("Only eligible candidates shown by default. File format and memory checks cannot guarantee a model will initialize. Hub likes are community feedback, not a quality rating.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        searchMessage?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
                        if (searched && results.isEmpty() && !busy) item {
                            Text("No candidates match this page and your filters. Try another model name, reset filters, or load another page.")
                            TextButton(onClick = { filters = PhoneModelFilters() }) { Text("Reset filters") }
                        }
                        items(results, key = { it.id }) { model ->
                            PhoneModelCard(model, state.modelDownloads[model.id], onDownload = { pendingDownload = model },
                                onUse = { onUse(model.fileName) }, onOpen = { links.openUri(model.repositoryUrl) })
                        }
                        if (nextCursor != null) item {
                            OutlinedButton(onClick = { search(true) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Load more matching repositories") }
                        }
                    }
                    "Settings" -> item {
                        LocalModelPreferences(storage, state.loadedModels.localPath, state.modelScanMessage, onSavePath)
                    }
                }
            }
        }
    }
    pendingDelete?.let { file ->
        AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Delete this model?") },
            text = { Text("${file.fileName}\n${PhoneModelDiscovery.formatBytes(file.sizeBytes)} will be removed from this app. Other copies on the phone are not touched. You can download it again.") },
            confirmButton = { TextButton(onClick = { onDelete(file.fileName); pendingDelete = null }) { Text("Delete model") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep") } })
    }
    pendingDownload?.let { model ->
        AlertDialog(onDismissRequest = { pendingDownload = null }, title = { Text("Download ${model.displayName}?") },
            text = { Text("${model.remoteFile}\n${model.sizeBytes?.let(PhoneModelDiscovery::formatBytes) ?: "Unknown size"} · ${model.assessment.speed.label}\n${model.assessment.reason}\nLicense: ${model.license ?: "Check the model page"}. Downloading uses your network connection.") },
            confirmButton = { TextButton(onClick = { onDownload(model.toLocalModelOption()); pendingDownload = null },
                enabled = model.assessment.eligible) { Text("Download model") } },
            dismissButton = { TextButton(onClick = { pendingDownload = null }) { Text("Cancel") } })
    }
}

internal fun localModelDevice(context: Context): PhoneModelDevice = runCatching {
    val manager = context.getSystemService(ActivityManager::class.java)
    val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
    PhoneModelDevice(info.totalMem, context.filesDir.usableSpace, "arm64-v8a" in Build.SUPPORTED_ABIS,
        manager.isLowRamDevice, info.availMem)
}.getOrElse { PhoneModelDevice(0, 0, false, true) }

@Composable
private fun LocalInstalledCard(file: InstalledModelFile, active: Boolean, onUse: () -> Unit, onDelete: () -> Unit) {
    val chat = LocalChatModel.supports(java.io.File(file.fileName))
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(file.catalogDisplayName ?: file.fileName, style = MaterialTheme.typography.titleMedium)
        Text("Saved on phone · ${PhoneModelDiscovery.formatBytes(file.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
        Text(if (file.downloadedAtMs != null) "Downloaded / imported ${modelDate(file.downloadedAtMs)}"
            else "Download date unavailable · file modified ${modelDate(file.modifiedAtMs)}", style = MaterialTheme.typography.labelSmall)
        if (!chat) Text("Action-routing weights, not a conversational model.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onUse, enabled = chat && !active) { Text(if (active) "Using local AI" else "Use local AI") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    } }
}

@Composable
private fun PhoneModelCard(model: PhoneModelCandidate, progress: ModelDownloader.DownloadProgress?,
    onDownload: () -> Unit, onUse: () -> Unit, onOpen: () -> Unit) {
    var expanded by remember(model.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(model.displayName, style = MaterialTheme.typography.titleMedium)
        Text(model.remoteFile, style = MaterialTheme.typography.labelSmall)
        Text("${model.sizeBytes?.let(PhoneModelDiscovery::formatBytes) ?: "Size unknown"} · ${model.format.label}",
            style = MaterialTheme.typography.bodySmall)
        Text(model.assessment.speed.label, style = MaterialTheme.typography.labelLarge)
        if (model.description.isNotBlank()) Text(model.description.take(260), style = MaterialTheme.typography.bodySmall)
        Text(model.assessment.reason, style = MaterialTheme.typography.bodySmall,
            color = if (model.assessment.eligible) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
        Text("${model.downloads?.toString() ?: "?"} Hub downloads · ${model.likes?.toString() ?: "?"} likes",
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Less detail" else "Model details") }
        if (expanded) {
            Text("Parameters: ${model.parameters?.toString() ?: "Unknown"} (${model.parametersSource ?: "not published"})\n" +
                "Weights: ${model.quantization ?: "not published"}\n" +
                "Estimated working RAM: ${model.assessment.estimatedRamBytes?.let(PhoneModelDiscovery::formatBytes) ?: "unknown"}\n" +
                "Released: ${modelDate(model.releasedAtMs)} · Added to Hub: ${modelDate(model.createdAtMs)}\n" +
                "Updated: ${modelDate(model.modifiedAtMs)} · Downloaded: ${modelDate(model.downloadedAtMs)}\n" +
                "License: ${model.license ?: "check the model page"}" +
                if (model.gated) "\nRequires accepting the license on Hugging Face and a read-access token." else "",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onOpen) { Text("Open model page & license") }
        }
        when {
            model.installedPath != null -> Button(onClick = onUse) { Text("Use local AI") }
            progress != null && !progress.done && progress.error == null -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("${PhoneModelDiscovery.formatBytes(progress.received)} downloaded", style = MaterialTheme.typography.bodySmall)
            }
            else -> {
                progress?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = onDownload, enabled = model.assessment.eligible) { Text(if (progress?.error != null) "Retry download" else "Download") }
            }
        }
    } }
}

@Composable
private fun LocalModelPreferences(storage: SecureStorageManager?, path: String, message: String?, onSavePath: (String) -> Unit) {
    var token by remember { mutableStateOf(storage?.getApiKey("huggingface").orEmpty()) }
    var modelPath by remember(path) { mutableStateOf(path) }
    var saved by remember { mutableStateOf<String?>(null) }
    val links = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Download access", style = MaterialTheme.typography.titleLarge)
        Text("Public models need no login. For a gated model: open its page, accept its license, then create a read-access token in Hugging Face settings.",
            style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { links.openUri("https://huggingface.co/settings/tokens") }, modifier = Modifier.fillMaxWidth()) {
            Text("Get a Hugging Face token")
        }
        OutlinedTextField(token, { token = it; saved = null }, label = { Text("Hugging Face token (optional)") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            saved = runCatching { storage?.saveApiKey("huggingface", token.trim()) ?: error("Secure storage unavailable")
                "Token saved securely on this phone." }.getOrElse { "Token could not be saved. Please try again." }
        }, enabled = storage != null) { Text("Save download access") }
        saved?.let { Text(it) }
        HorizontalDivider()
        Text("Advanced local path", style = MaterialTheme.typography.titleMedium)
        Text("Usually leave this alone. Use the Installed tab to import or select a file. Saving a path does not switch you away from your current cloud provider.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(modelPath, { modelPath = it }, label = { Text("Local model path") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { onSavePath(modelPath.trim()) }, enabled = modelPath.isNotBlank()) { Text("Save local path") }
        message?.let { Text(it) }
        Text("This build uses LiteRT-LM and MediaPipe LLM bundles. GGUF, safetensors and arbitrary training checkpoints cannot be loaded as chat models.",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PhoneModelFilterPanel(value: PhoneModelFilters, models: List<PhoneModelCandidate>, onChange: (PhoneModelFilters) -> Unit) {
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(value.eligibleOnly, { onChange(value.copy(eligibleOnly = !value.eligibleOnly)) }, label = { Text("Fits this phone") })
            FilterChip(value.ungatedOnly, { onChange(value.copy(ungatedOnly = !value.ungatedOnly)) }, label = { Text("No login") })
            FilterChip(value.installedOnly, { onChange(value.copy(installedOnly = !value.installedOnly)) }, label = { Text("Downloaded") })
        }
        ModelChoice("Speed", value.speed?.label ?: "Any", listOf("Any" to null) + PhoneModelSpeed.entries.map { it.label to it }) { onChange(value.copy(speed = it)) }
        ModelChoice("Size", value.maxSizeBytes?.let(PhoneModelDiscovery::formatBytes) ?: "Up to 11 GiB",
            listOf("Any eligible size" to null) + listOf(1, 2, 4, 8, 11).map { "Up to $it GiB" to it * PhoneModelDiscovery.GIB }) { onChange(value.copy(maxSizeBytes = it)) }
        ModelChoice("Parameters", value.maxParameters?.let { "Up to ${it / 1_000_000_000.0}B" } ?: "Any",
            listOf("Any" to null) + listOf(1, 3, 7, 14).map { "Up to ${it}B" to it * 1_000_000_000L }) { onChange(value.copy(maxParameters = it)) }
        ModelChoice("Weights", value.quantization ?: "Any",
            listOf("Any" to null) + models.mapNotNull { it.quantization }.distinct().sorted().map { it to it }) { onChange(value.copy(quantization = it)) }
        ModelChoice("Format", value.format?.label ?: "Any",
            listOf("Any" to null) + PhoneModelFormat.entries.map { it.label to it }) { onChange(value.copy(format = it)) }
        ModelChoice("Community likes", value.minLikes?.let { "$it+" } ?: "Any",
            listOf("Any" to null, "10+" to 10L, "100+" to 100L, "1,000+" to 1000L)) { onChange(value.copy(minLikes = it)) }
        ModelChoice("Hub downloads", value.minDownloads?.let { "$it+" } ?: "Any",
            listOf("Any" to null, "100+" to 100L, "1,000+" to 1000L, "10,000+" to 10000L)) { onChange(value.copy(minDownloads = it)) }
        val monthAgo = remember { System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000 }
        val yearAgo = remember { System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000 }
        ModelChoice("Released", if (value.releasedAfterMs == null) "Any / unknown" else "Recent only",
            listOf("Any / unknown" to null, "Last 30 days" to monthAgo, "Last year" to yearAgo)) { onChange(value.copy(releasedAfterMs = it)) }
        ModelChoice("Downloaded", if (value.downloadedAfterMs == null) "Any / unknown" else "Last 30 days",
            listOf("Any / unknown" to null, "Last 30 days" to monthAgo)) { onChange(value.copy(downloadedAfterMs = it)) }
        Text("Filters exclude unknown values. Release date is shown only when published; Hub creation and file modification are separate dates.",
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onChange(PhoneModelFilters()) }) { Text("Reset filters") }
    }
}

@Composable
private fun <T> ModelChoice(label: String, selected: String, options: List<Pair<String, T>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label: $selected") }
        DropdownMenu(open, { open = false }) {
            options.forEach { (name, value) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(value); open = false }) }
        }
    }
}

private fun modelDate(time: Long?): String = time?.takeIf { it > 0 }?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) } ?: "Not available"
