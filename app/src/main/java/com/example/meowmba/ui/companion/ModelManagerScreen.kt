package com.example.meowmba.ui.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.meowmba.core.utils.SystemUtils
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    state: CompanionState,
    onModelSelected: (String) -> Unit = {},
    onTestCloud: () -> Unit = {},
    onCheckLocal: () -> Unit = {},
    onDownloadModel: (LocalModelOption) -> Unit = {},
    onUseModel: (LocalModelOption) -> Unit = {},
    onDeleteModel: (LocalModelOption) -> Unit = {},
    onCustomDownload: (String, String) -> Unit = { _, _ -> },
    onUseInstalledFile: (String) -> Unit = {},
    onDeleteInstalledFile: (String) -> Unit = {},
    onClose: () -> Unit
) {
    var showProviderPicker by remember { mutableStateOf(false) }

    if (showProviderPicker) {
        ModalBottomSheet(
            onDismissRequest = { showProviderPicker = false },
            containerColor = Color(0xFF121212)
        ) {
            ModelSelector(
                selectedModel = state.selectedModel,
                onModelSelected = onModelSelected,
                onClose = { showProviderPicker = false }
            )
        }
    }

    val context = LocalContext.current
    val ram = remember { SystemUtils.getTotalRamGb(context) }
    val cores = remember { SystemUtils.getCpuCores() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LLM Manager", fontFamily = FontFamily.Monospace) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D1117),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0D1117)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Hardware Status Card
            HardwareStatusCard(ram, cores)

            Spacer(modifier = Modifier.height(24.dp))

            // Currently loaded models: on-device vs. cloud server
            Text(
                "Currently Loaded",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Active provider: ${state.selectedModel}",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showProviderPicker = true }) {
                    Text("Change", color = Color(0xFF58A6FF), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val loaded = state.loadedModels
            LoadedModelCard(
                icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color(0xFF58A6FF)) },
                title = "Local · on this phone",
                primary = loaded.localName,
                details = listOf(
                    "Runtime: ${loaded.localRuntime}",
                    "Path: ${loaded.localPath}",
                    if (loaded.localInstalled) "File: present on disk" else "File: NOT found on disk"
                ),
                status = if (loaded.activeSource == ModelSource.LOCAL) LoadStatus.ACTIVE else LoadStatus.STANDBY
            )

            Spacer(modifier = Modifier.height(12.dp))

            LoadedModelCard(
                icon = { Icon(Icons.Default.Cloud, contentDescription = null, tint = Color(0xFF3FB950)) },
                title = "Network server · LAN",
                primary = "Ollama · ${loaded.networkModel}",
                details = listOf("Endpoint: ${loaded.networkEndpoint}"),
                status = if (loaded.activeSource == ModelSource.NETWORK) LoadStatus.ACTIVE else LoadStatus.STANDBY
            )

            Spacer(modifier = Modifier.height(12.dp))

            LoadedModelCard(
                icon = { Icon(Icons.Default.Cloud, contentDescription = null, tint = Color(0xFFD29922)) },
                title = "Cloud server",
                primary = "${loaded.cloudProvider} · ${loaded.cloudModel}",
                details = listOf(
                    "Endpoint: ${loaded.cloudEndpoint}",
                    if (loaded.cloudKeyConfigured) "API key: configured (Keystore)" else "API key: not set — add it in Settings"
                ),
                status = when {
                    loaded.activeSource == ModelSource.CLOUD -> LoadStatus.ACTIVE
                    loaded.cloudKeyConfigured -> LoadStatus.STANDBY
                    else -> LoadStatus.NOT_CONFIGURED
                }
            )

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
                            color = Color(0xFF58A6FF)
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
                        Color(0xFF3FB950)
                    } else {
                        Color(0xFFF85149)
                    },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Local Intelligence",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Honest state banner when nothing is installed on-device
            if (!state.loadedModels.localInstalled) {
                Surface(
                    color = Color(0xFFD29922).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFD29922).copy(alpha = 0.5f))
                ) {
                    Text(
                        "No local model installed. Download one below to chat and " +
                            "run the agent brain fully on-device — or load a Hermes " +
                            "fine-tune via a custom URL.",
                        color = Color(0xFFD29922),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Curated catalog
            ModelCatalog.options.forEach { option ->
                CatalogModelItem(
                    option = option,
                    state = state,
                    onDownload = { onDownloadModel(option) },
                    onUse = { onUseModel(option) },
                    onDelete = { onDeleteModel(option) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Custom installs: real bundles on disk that are not in the catalog
            val customInstalled = state.installedModels.filter { it.catalogDisplayName == null }
            if (customInstalled.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Custom installs",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                customInstalled.forEach { file ->
                    CustomInstalledItem(
                        file = file,
                        ramGb = ram,
                        isActive = state.loadedModels.activeSource == ModelSource.LOCAL &&
                            java.io.File(state.loadedModels.localPath).name == file.fileName,
                        onUse = { onUseInstalledFile(file.fileName) },
                        onDelete = { onDeleteInstalledFile(file.fileName) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Any model via custom URL
            CustomModelDownload(state = state, onCustomDownload = onCustomDownload)

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

@Composable
fun LoadedModelCard(
    icon: @Composable () -> Unit,
    title: String,
    primary: String,
    details: List<String>,
    status: LoadStatus
) {
    Surface(
        color = Color(0xFF161B22),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (status == LoadStatus.ACTIVE) Color(0xFF3FB950).copy(alpha = 0.6f)
            else Color.White.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                val (label, color) = when (status) {
                    LoadStatus.ACTIVE -> "ACTIVE" to Color(0xFF3FB950)
                    LoadStatus.STANDBY -> "STANDBY" to Color(0xFFD29922)
                    LoadStatus.NOT_CONFIGURED -> "NOT CONFIGURED" to Color(0xFFF85149)
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

            Text(primary, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            details.forEach {
                Text(it, color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun HardwareStatusCard(ram: Double, cores: Int) {
    Surface(
        color = Color(0xFF161B22),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFF58A6FF))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "System Resources",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("RAM", color = Color.Gray, fontSize = 12.sp)
                    Text("%.1f GB".format(ram), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("CPU", color = Color.Gray, fontSize = 12.sp)
                    Text("$cores Cores", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("STATUS", color = Color.Gray, fontSize = 12.sp)
                    Text("OPTIMAL", color = Color(0xFF3FB950), fontWeight = FontWeight.Bold)
                }
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
    onDelete: () -> Unit
) {
    val installed = state.installedModels.any { it.fileName == option.fileName }
    val progress = state.modelDownloads[option.id]
    val isActive = (state.loadedModels.localPath.endsWith(option.fileName) ||
        java.io.File(state.loadedModels.localPath).name == option.fileName) &&
        state.loadedModels.activeSource == ModelSource.LOCAL

    Surface(
        color = Color(0xFF161B22),
        shape = RoundedCornerShape(12.dp),
        border = if (isActive) BorderStroke(1.dp, Color(0xFF3FB950).copy(alpha = 0.6f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    option.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (option.recommended) {
                    Badge(Color(0xFF238636), "REC")
                }
                if (option.requiresHfToken) {
                    Badge(Color(0xFFD29922), "HF TOKEN")
                }
            }
            Text(option.description, color = Color.Gray, fontSize = 12.sp)
            Text(
                "${option.approxSize} · ${option.minRamGb}GB+ RAM",
                color = Color(0xFF58A6FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                installed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge(Color(0xFF3FB950), "INSTALLED")
                        Spacer(modifier = Modifier.width(8.dp))
                        if (!isActive) {
                            TextButton(onClick = onUse) {
                                Text("Use", color = Color(0xFF58A6FF), fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete model",
                                tint = Color.Gray,
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
                            color = Color.Gray, fontSize = 11.sp
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "${progress.received / 1_048_576} MB…",
                            color = Color.Gray, fontSize = 11.sp
                        )
                    }
                }
                progress?.error != null -> {
                    Text(progress.error, color = Color(0xFFF85149), fontSize = 12.sp)
                    TextButton(onClick = onDownload) {
                        Text("Retry", color = Color(0xFF58A6FF), fontSize = 12.sp)
                    }
                }
                else -> {
                    OutlinedButton(onClick = onDownload, shape = RoundedCornerShape(8.dp)) {
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
        color = Color(0xFF161B22),
        shape = RoundedCornerShape(12.dp),
        border = if (isActive) BorderStroke(1.dp, Color(0xFF3FB950).copy(alpha = 0.6f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(file.fileName, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "${file.sizeBytes / 1_048_576} MB · custom download",
                color = Color(0xFF58A6FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            if (tooLarge) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Likely too large for this %.0f GB phone — loading it can crash the app.".format(ramGb),
                    color = Color(0xFFF85149),
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(Color(0xFF3FB950), "INSTALLED")
                if (tooLarge) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(Color(0xFFF85149), "TOO LARGE")
                }
                if (isActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(Color(0xFF3FB950), "ACTIVE")
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onUse) {
                        Text("Use", color = Color(0xFF58A6FF), fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete model",
                        tint = Color.Gray,
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
    val id = "custom:$fileName"
    val progress = state.modelDownloads[id]

    Surface(
        color = Color(0xFF161B22),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    if (expanded) "Hide custom download" else "Any model (custom URL)",
                    color = Color(0xFF58A6FF),
                    fontSize = 13.sp
                )
            }
            if (expanded) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Download URL (https://…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Save as (e.g. hermes3-3b.task)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Only .task bundles run on the current on-device runtime. " +
                        "Use Wi-Fi — model files are large.",
                    color = Color.Gray,
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
                        Text(progress.error, color = Color(0xFFF85149), fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { onCustomDownload(url.trim(), fileName.trim()) },
                    enabled = url.isNotBlank() && fileName.isNotBlank() &&
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
