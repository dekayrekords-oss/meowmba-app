package com.example.meowmba.ui.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
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
import com.example.meowmba.core.utils.SystemUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    state: CompanionState,
    onModelSelected: (String) -> Unit = {},
    onTestCloud: () -> Unit = {},
    onCheckLocal: () -> Unit = {},
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
    val recommendation = remember { SystemUtils.getRecommendedModel(ram) }

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
                    "Path: ${loaded.localPath}"
                ),
                status = if (loaded.activeSource == ModelSource.LOCAL) LoadStatus.ACTIVE else LoadStatus.STANDBY
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            ModelDownloadItem(
                name = "Gemma 2b",
                description = "Lightweight, fast, perfect for most mobile devices.",
                size = "1.6 GB",
                isRecommended = recommendation.contains("2b"),
                onDownload = { /* TODO */ }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            ModelDownloadItem(
                name = "Gemma 7b",
                description = "High intelligence, requires 8GB+ RAM.",
                size = "5.0 GB",
                isRecommended = recommendation.contains("7b"),
                onDownload = { /* TODO */ }
            )

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
fun ModelDownloadItem(
    name: String,
    description: String,
    size: String,
    isRecommended: Boolean,
    onDownload: () -> Unit
) {
    Surface(
        color = Color(0xFF161B22),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold)
                    if (isRecommended) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFF238636),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "REC",
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(description, color = Color.Gray, fontSize = 12.sp)
                Text(size, color = Color(0xFF58A6FF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
            }
        }
    }
}
