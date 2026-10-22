package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Card
import com.example.meowmba.ui.theme.Surface

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.agent.LocalModelOption
import com.example.meowmba.core.agent.ModelCatalog
import com.example.meowmba.core.agent.OpenRouterModel
import com.example.meowmba.core.agent.OpenRouterModels
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.utils.SystemUtils
import com.example.meowmba.core.utils.SystemUtils.HardwareCapabilities
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    state: CompanionState,
    viewModel: CompanionViewModel? = null,
    onModelSelected: (String) -> Unit = {},
    onTestCloud: () -> Unit = {},
    onCheckLocal: () -> Unit = {},
    onDownloadModel: (LocalModelOption) -> Unit = {},
    onUseModel: (LocalModelOption) -> Unit = {},
    onDeleteModel: (LocalModelOption) -> Unit = {},
    onCustomDownload: (String, String) -> Unit = { _, _ -> },
    onUseInstalledFile: (String) -> Unit = {},
    onDeleteInstalledFile: (String) -> Unit = {},
    onScanPhone: () -> Unit = {},
    onImportFoundModel: (DiscoveredModelFile) -> Unit = {},
    onOpenVoiceModels: () -> Unit = {},
    secureStorage: SecureStorageManager? = null,
    onProviderSettingsSaved: () -> Unit = {},
    loadOpenRouterCatalog: suspend () -> Result<List<OpenRouterModel>> = { OpenRouterModels.fetch() },
    onClose: () -> Unit
) {
    var showLocalSettings by rememberSaveable { mutableStateOf(false) }
    if (showLocalSettings) {
        LocalModelSettingsScreen(
            state = state, storage = secureStorage,
            onDownload = onDownloadModel, onUse = onUseInstalledFile,
            onDelete = onDeleteInstalledFile, onCustomDownload = onCustomDownload,
            onScan = onScanPhone, onImportFound = onImportFoundModel,
            onImportUri = { viewModel?.importModelUri(it) },
            onSavePath = { viewModel?.saveLocalModelPath(it) },
            onRefresh = { viewModel?.refreshLocalModelLibrary() },
            onClose = { showLocalSettings = false },
        )
        return
    }
    var showProviderPicker by remember { mutableStateOf(false) }
    BackHandler { if (showProviderPicker) showProviderPicker = false else onClose() }

    if (showProviderPicker) {
        ModalBottomSheet(
            onDismissRequest = { showProviderPicker = false },
            containerColor = MaterialTheme.colorScheme.background
        ) {
            ModelSelector(
                selectedModel = state.selectedModel,
                onModelSelected = onModelSelected,
                onClose = { showProviderPicker = false }
            )
        }
    }

    val context = LocalContext.current
    val caps = remember { SystemUtils.getCapabilities(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LLM", fontFamily = FontFamily.Monospace) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close LLM") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Hardware Status Card
            HardwareStatusCard(caps)

            Spacer(modifier = Modifier.height(16.dp))

            // Speech-to-text lives on its own page: it is a separate stack of
            // models (wake word, endpointing, transcription) from the chat LLM,
            // and mixing them into this list made both harder to read.
            VoiceRecognitionEntryCard(
                backendName = state.asrBackend.displayName,
                isCloud = state.asrBackend.isCloud,
                onClick = onOpenVoiceModels,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Meta Ray-Ban glasses: link, registration, camera stream, and the
            // mic that can take over voice recognition (picked on the Voice
            // Recognition page).
            if (viewModel?.glassesController != null) {
                MetaGlassesCard(viewModel)
                Spacer(modifier = Modifier.height(24.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Currently loaded models: on-device vs. cloud server
            Text(
                "Choose your AI",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text("Tap a card to manage its models and connection.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            OutlinedButton(onClick = { showProviderPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("All AI provider options") }

            Spacer(modifier = Modifier.height(4.dp))

            val loaded = state.loadedModels
            LoadedModelCard(
                icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "Local · on this phone",
                primary = loaded.localName,
                details = listOf(
                    "Runtime: ${loaded.localRuntime}",
                    "Path: ${loaded.localPath}",
                    if (loaded.localInstalled) "File: present on disk" else "File: NOT found on disk"
                ),
                status = when { !loaded.localInstalled -> LoadStatus.NOT_CONFIGURED; loaded.activeSource == ModelSource.LOCAL -> LoadStatus.ACTIVE; else -> LoadStatus.STANDBY },
                onClick = { showLocalSettings = true },
                actionLabel = "Manage models, downloads & settings"
            )

            Spacer(modifier = Modifier.height(12.dp))

            CloudModelCard(
                provider = "openrouter", storage = secureStorage,
                modelName = loaded.networkModel, endpoint = loaded.networkEndpoint,
                keyConfigured = loaded.networkKeyConfigured,
                status = when { !loaded.networkKeyConfigured -> LoadStatus.NOT_CONFIGURED; loaded.activeSource == ModelSource.NETWORK -> LoadStatus.ACTIVE; else -> LoadStatus.STANDBY },
                onSelect = { onModelSelected("OpenRouter (Cloud)") },
                onSaved = onProviderSettingsSaved, loadCatalog = loadOpenRouterCatalog,
            )

            Spacer(modifier = Modifier.height(12.dp))

            CloudModelCard(
                provider = "openai", storage = secureStorage,
                modelName = loaded.cloudModel, endpoint = loaded.cloudEndpoint,
                keyConfigured = loaded.cloudKeyConfigured,
                status = when {
                    loaded.activeSource == ModelSource.CLOUD && (state.selectedModel == "OpenAI (Cloud)" || secureStorage?.getPreference("default_brain") == "openai") -> LoadStatus.ACTIVE
                    loaded.cloudKeyConfigured -> LoadStatus.STANDBY
                    else -> LoadStatus.NOT_CONFIGURED
                },
                onSelect = { onModelSelected("OpenAI (Cloud)") },
                onSaved = onProviderSettingsSaved, loadCatalog = loadOpenRouterCatalog,
            )

            Spacer(modifier = Modifier.height(12.dp))
            secureStorage?.let { storage ->
                listOf("anthropic", "custom", "opencode").forEach { provider ->
                    val configured = !storage.getApiKey(provider).isNullOrBlank()
                    CloudModelCard(provider, storage, storage.getModelName(provider) ?: if (provider == "opencode") "big-pickle" else "",
                        storage.getEndpoint(provider)?.takeIf { it.isNotBlank() } ?: if (provider == "anthropic") "https://api.anthropic.com" else if (provider == "opencode") "https://opencode.ai/zen" else "Set your provider URL",
                        configured,
                        if (!configured) LoadStatus.NOT_CONFIGURED else if (storage.getPreference("default_brain") == if (provider == "opencode") "bigpickle" else provider) LoadStatus.ACTIVE else LoadStatus.STANDBY,
                        onSelect = { onModelSelected(when (provider) { "anthropic" -> "Claude (Cloud)"; "opencode" -> "OpenCode Zen (Cloud)"; else -> "Custom API (Cloud)" }) },
                        onSaved = onProviderSettingsSaved)
                    Spacer(Modifier.height(12.dp))
                }
            }
            secureStorage?.let { VisionModelSettingsCard(it, onProviderSettingsSaved, loadOpenRouterCatalog) }
            Spacer(modifier = Modifier.height(12.dp))

            // Health checks: verify the local file exists and the cloud is reachable
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCheckLocal,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Check local file", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onTestCloud,
                    enabled = !state.connectionTestRunning,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (state.connectionTestRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Test cloud", fontSize = 12.sp)
                }
            }

            state.connectionTestResult?.let { result ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    result,
                    color = if (result.startsWith("Connected") || result.startsWith("Ollama reachable") || result.startsWith("Model file present")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Text("Close Manager")
            }
        }
    }
}

enum class LoadStatus { ACTIVE, STANDBY, NOT_CONFIGURED }

/** Row on the LLM page that opens [VoiceModelScreen]. */
@Composable
private fun VoiceRecognitionEntryCard(
    backendName: String,
    isCloud: Boolean,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = if (isCloud) Color(0xFFD29922) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Voice Recognition",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Speech-to-text: $backendName",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 22.sp)
        }
    }
}

@Composable
fun LoadedModelCard(
    icon: @Composable () -> Unit,
    title: String,
    primary: String,
    details: List<String>,
    status: LoadStatus,
    onClick: () -> Unit = {},
    actionLabel: String = "Tap to select",
    settings: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (status == LoadStatus.ACTIVE) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )
    ) {
        Column {
        Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                val (label, color) = when (status) {
                    LoadStatus.ACTIVE -> "ACTIVE" to MaterialTheme.colorScheme.primary
                    LoadStatus.STANDBY -> "STANDBY" to Color(0xFFD29922)
                    LoadStatus.NOT_CONFIGURED -> "NOT CONFIGURED" to MaterialTheme.colorScheme.error
                }
                Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        label,
                        color = color,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(primary, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(actionLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            details.forEach {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        settings?.let { Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { it() } }
        }
    }
}

@Composable
fun HardwareStatusCard(caps: HardwareCapabilities) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "System Resources",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("RAM", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("%.1f GB".format(caps.ramGb), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("CPU", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("${caps.cpuCores} Cores", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Android", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("API ${caps.androidApi}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("ARCH", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(if (caps.is64Bit) "64-bit" else "32-bit", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // AI accelerator row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val npuLabel = if (caps.hasNpu) "✅ NPU" else "— NPU"
                Text(npuLabel, color = if (caps.hasNpu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun CatalogModelItem(
    option: LocalModelOption,
    state: CompanionState,
    onDownload: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit,
    downloadEnabled: Boolean = true,
) {
    val installed = state.installedModels.any { it.fileName == option.fileName }
    val progress = state.modelDownloads[option.id]
    val isActive = (state.loadedModels.localPath.endsWith(option.fileName) ||
        java.io.File(state.loadedModels.localPath).name == option.fileName) &&
        state.loadedModels.activeSource == ModelSource.LOCAL

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null,
        modifier = Modifier.fillMaxWidth().clickable(enabled = installed && option.runtime.canActivate && !isActive, onClick = onUse)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    option.displayName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (option.recommended) {
                    Badge(Color(0xFF238636), "DEFAULT · AUTO")
                }
                if (option.requiresHfToken) {
                    Badge(Color(0xFFD29922), "HF TOKEN")
                }
                if (option.runtime == com.example.meowmba.core.agent.LocalModelRuntime.NEEDLE) {
                    Badge(Color(0xFF8957E5), "NEEDLE")
                }
            }
            Text(option.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(
                "${option.approxSize} · ${option.minRamGb}GB+ RAM",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                installed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge(MaterialTheme.colorScheme.primary, "INSTALLED")
                        Spacer(modifier = Modifier.width(8.dp))
                        if (!isActive && option.runtime.canActivate) {
                            TextButton(onClick = onUse) {
                                Text("Use", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            }
                        } else if (!option.runtime.canActivate) {
                            Text(
                                "Loaded automatically as the default action brain",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete model",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                progress != null && !progress.done && progress.error == null -> {
                    if (progress.fraction != null) {
                        LinearProgressIndicator(
                            progress = { progress.fraction!! },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${(progress.fraction!! * 100).toInt()}% · ${progress.received / 1_048_576} MB",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "${progress.received / 1_048_576} MB…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                        )
                    }
                }
                progress?.error != null -> {
                    Text(progress.error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    TextButton(onClick = onDownload, enabled = downloadEnabled) {
                        Text("Retry", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
                else -> {
                    OutlinedButton(onClick = onDownload, enabled = downloadEnabled, shape = RoundedCornerShape(8.dp)) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomInstalledItem(
    file: InstalledModelFile,
    ramGb: Double,
    isActive: Boolean,
    onUse: () -> Unit,
    onDelete: () -> Unit
) {
    // On-device runtimes need roughly the file size again in working RAM on
    // top of the system; files past ~45% of device RAM crash phones in practice.
    val tooLarge = file.sizeBytes > ramGb * 1_073_741_824L * 0.45

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null,
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isActive && com.example.meowmba.core.agent.LocalChatModel.supports(java.io.File(file.fileName)), onClick = onUse)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(file.fileName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text(
                "${file.sizeBytes / 1_048_576} MB · custom download",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            if (tooLarge) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Likely too large for this %.0f GB phone — loading it can crash the app.".format(ramGb),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(MaterialTheme.colorScheme.primary, "INSTALLED")
                if (tooLarge) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(MaterialTheme.colorScheme.error, "TOO LARGE")
                }
                if (isActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(MaterialTheme.colorScheme.primary, "ACTIVE")
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onUse) {
                        Text("Use", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete model",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(color: Color, label: String) {
    Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
        Text(
            label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun CustomModelDownload(
    state: CompanionState,
    onCustomDownload: (String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    val id = "custom:${fileName.ifBlank { "repository" }}"
    val progress = state.modelDownloads[id]

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    if (expanded) "Hide repository import" else "Import from Hugging Face or GitHub",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                )
            }
            if (expanded) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Model page, repository, or direct file URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Save as (optional for page/repository)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Accepts Hugging Face model pages, GitHub repositories/releases, or direct .task, .litertlm, and .cact files.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                when {
                    progress != null && !progress.done && progress.error == null -> {
                        LinearProgressIndicator(
                            progress = { progress.fraction ?: 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    progress?.error != null -> {
                        Text(progress.error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { onCustomDownload(url.trim(), fileName.trim()) },
                    enabled = url.isNotBlank() &&
                        (progress == null || progress.done || progress.error != null),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download custom model", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Meta Ray-Ban glasses control card (LLM page).
 *
 * Shows the live link state from the controller's flows, one-tap setup
 * (Meta AI registration + camera permission), connect/disconnect, and the
 * latest camera frame as proof the stream is real. Honest state only: no
 * frame, no badge.
 */
@Composable
private fun MetaGlassesCard(viewModel: CompanionViewModel) {
    val gc = viewModel.glassesController ?: return
    val connected by gc.isConnected.collectAsState()
    val streaming by gc.isStreaming.collectAsState()
    val status by gc.status.collectAsState()
    val bridge = viewModel.metaGlassesMeowBridge
    val bridgeOn by bridge.enabled.collectAsState()
    val defaultHost by bridge.defaultSshHost.collectAsState()
    var hostDraft by remember(defaultHost) { mutableStateOf(defaultHost) }
    val hasSdk = remember { gc.hasSdk() }
    val activity = LocalContext.current as? android.app.Activity
    val bridgeStatus = remember(hasSdk, connected, bridgeOn, defaultHost) {
        com.example.meowmba.core.glasses.MetaGlassesMeowBridge.statusLine(
            hasSdk = hasSdk,
            glassesConnected = connected,
            bridgeEnabled = bridgeOn,
            defaultHost = defaultHost,
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Meta Ray-Ban glasses",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when {
                        !hasSdk -> "UPDATE REQUIRED"
                        streaming -> "STREAMING"
                        connected -> "LINKED"
                        else -> "OFF"
                    },
                    color = when {
                        !hasSdk -> Color(0xFF6E7681)
                        streaming || connected -> MaterialTheme.colorScheme.primary
                        else -> Color(0xFF6E7681)
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Text(bridgeStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            Row {
                if (activity != null) {
                    TextButton(onClick = { viewModel.glassesSetup(activity) }, enabled = hasSdk) {
                        Text("Setup glasses", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
                TextButton(onClick = { viewModel.glassesConnect() }, enabled = hasSdk) {
                    Text("Connect", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
                TextButton(onClick = { viewModel.glassesDisconnect() }) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Meow bridge",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = bridgeOn,
                    onCheckedChange = { viewModel.setMetaGlassesBridgeEnabled(it) },
                )
            }
            Text(
                "When on, glasses mic / chat commands route into the same Meow tools as in-app chat " +
                    "(including ssh.meow, opencode.run, server.shell). " +
                    "Say “Hey Meow”, not “Hey Meta” — Meta’s wake word stays Meta’s assistant.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = hostDraft,
                onValueChange = { hostDraft = it },
                label = { Text("Default meow SSH host") },
                placeholder = { Text("meow") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = {
                    viewModel.setMetaGlassesDefaultSshHost(hostDraft)
                    hostDraft = viewModel.metaGlassesMeowBridge.defaultSshHost.value
                },
            ) {
                Text("Save SSH host", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
            if (!hasSdk) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Glasses camera support is not included in this installation. " +
                        "It requires an app update, not an API key in Settings. " +
                        "You can still select Meta glasses mic on Voice Recognition; " +
                        "availability depends on Bluetooth SCO. See docs/META_GLASSES_MEOW_BRIDGE.md.",
                    color = Color(0xFFD29922),
                    fontSize = 11.sp,
                )
            } else {
                Text(
                    "Pair your glasses in the Meta AI app, then tap Setup glasses and approve registration and camera access. " +
                        "Test builds also require Developer Mode or access through your Meta testing release channel.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
