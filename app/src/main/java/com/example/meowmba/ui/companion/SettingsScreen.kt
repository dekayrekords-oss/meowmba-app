package com.example.meowmba.ui.companion

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.bridge.DesktopBridgeManager
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.launch

/**
 * Settings screen: companion voice (name, wake phrase, conversation mode)
 * and Model Provider configuration.
 */
@Composable
fun SettingsScreen(
    secureStorage: SecureStorageManager,
    companionName: String = "Meow",
    wakeWordEnabled: Boolean = false,
    conversationMode: Boolean = false,
    agentBrainEnabled: Boolean = true,
    hermesAgentUrl: String = "",
    backgroundListeningEnabled: Boolean = false,
    onCompanionNameChange: (String) -> Unit = {},
    onWakeWordChange: (Boolean) -> Unit = {},
    onConversationModeChange: (Boolean) -> Unit = {},
    onAgentBrainChange: (Boolean) -> Unit = {},
    onHermesUrlChange: (String) -> Unit = {},
    onBackgroundListeningChange: (Boolean) -> Unit = {},
    onClose: () -> Unit
) {
    var openaiKey by remember { mutableStateOf(secureStorage.getApiKey("openai") ?: "") }
    var hfToken by remember { mutableStateOf(secureStorage.getApiKey("huggingface") ?: "") }
    var ollamaEndpoint by remember { mutableStateOf(secureStorage.getEndpoint("ollama") ?: "http://10.0.2.2:11434") }
    var ollamaModel by remember { mutableStateOf(secureStorage.getModelName("ollama") ?: "gemma2") }
    var ollamaVisionModel by remember { mutableStateOf(secureStorage.getModelName("ollama_vision") ?: "gemma4:12b-it-qat") }
    var localModelPath by remember { mutableStateOf(secureStorage.getEndpoint("local") ?: "/data/local/tmp/llm/gemma.bin") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0D1117)
    ) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Companion Voice
            Text("Companion Voice", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            SettingsTextField(
                value = companionName,
                onValueChange = onCompanionNameChange,
                label = { Text("Companion name (wake phrase: \"Hey $companionName\")") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Also always answers to \"Hey Memba\" and \"Hey Meow\".",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            VoiceToggle(
                label = "Wake phrase",
                description = "Always-on listening for the wake phrase while the app is open. Runs on-device.",
                checked = wakeWordEnabled,
                onCheckedChange = onWakeWordChange
            )
            VoiceToggle(
                label = "Background wake phrase",
                description = "Keeps listening with the screen off via a persistent " +
                    "notification (foreground service). Uses more battery. Requires " +
                    "the wake phrase to be enabled.",
                checked = backgroundListeningEnabled,
                onCheckedChange = onBackgroundListeningChange
            )
            VoiceToggle(
                label = "Conversation mode",
                description = "Stay for follow-up questions after responding. Say \"stop\" to end.",
                checked = conversationMode,
                onCheckedChange = onConversationModeChange
            )
            VoiceToggle(
                label = "Agent brain",
                description = "Understands natural tool commands (\"make a file called todo.txt\") " +
                    "using the active model. Select a local model on the Hardware page for fully " +
                    "offline use — a local model file must be present.",
                checked = agentBrainEnabled,
                onCheckedChange = onAgentBrainChange
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsTextField(
                value = hermesAgentUrl,
                onValueChange = onHermesUrlChange,
                label = { Text("Hermes agent endpoint (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "OpenAI-compatible agent (hermes-agent v0.19+), e.g. http://192.168.1.10:8642. " +
                    "Leave blank for on-device routing only.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Desktop Bridge (Phase 6): pair with a computer running meowmba-bridge.py
            DesktopBridgeSection(secureStorage)

            Spacer(modifier = Modifier.height(24.dp))

            // OpenAI Configuration
            Text("OpenAI Cloud", style = MaterialTheme.typography.titleMedium, color = Color.White)
            SettingsTextField(
                value = openaiKey,
                onValueChange = { openaiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Hugging Face token (gated model downloads like Gemma)
            Text("Hugging Face", style = MaterialTheme.typography.titleMedium, color = Color.White)
            SettingsTextField(
                value = hfToken,
                onValueChange = { hfToken = it },
                label = { Text("Token (for gated models like Gemma — free at huggingface.co/settings/tokens)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Ollama Configuration
            Text("Ollama Local Network", style = MaterialTheme.typography.titleMedium, color = Color.White)
            SettingsTextField(
                value = ollamaEndpoint,
                onValueChange = { ollamaEndpoint = it },
                label = { Text("Endpoint (e.g. http://10.0.2.2:11434)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsTextField(
                value = ollamaModel,
                onValueChange = { ollamaModel = it },
                label = { Text("Model name (e.g. gemma4:12b-it-qat, memba-gemma4, hermes3-memba:8b)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsTextField(
                value = ollamaVisionModel,
                onValueChange = { ollamaVisionModel = it },
                label = { Text("Vision model for 'what do you see' (multimodal, e.g. gemma4:12b-it-qat)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Local Model Configuration
            Text("Local Model (MediaPipe)", style = MaterialTheme.typography.titleMedium, color = Color.White)
            SettingsTextField(
                value = localModelPath,
                onValueChange = { localModelPath = it },
                label = { Text("Model Path on Device") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Button(
                onClick = {
                    secureStorage.saveApiKey("openai", openaiKey)
                    secureStorage.saveApiKey("huggingface", hfToken)
                    secureStorage.saveEndpoint("ollama", ollamaEndpoint)
                    secureStorage.saveModelName("ollama", ollamaModel)
                    secureStorage.saveModelName("ollama_vision", ollamaVisionModel)
                    secureStorage.saveEndpoint("local", localModelPath)
                    onClose()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save and Close")
            }
        }
    }
}

@Composable
fun DesktopBridgeSection(secureStorage: SecureStorageManager) {
    val bridgeManager = remember { DesktopBridgeManager(secureStorage) }
    var paired by remember { mutableStateOf(bridgeManager.pairedBridge()) }
    var payload by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Text("Desktop Bridge", style = MaterialTheme.typography.titleMedium, color = Color.White)
    Spacer(modifier = Modifier.height(4.dp))

    val current = paired
    if (current != null) {
        Text("Paired: ${current.name} (${current.host}:${current.port})", color = Color.White)
        Text(
            text = "cert ${current.certSha256.take(16)}… · requests are HMAC-signed, cert pinned",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        message = bridgeManager.ping().fold(
                            onSuccess = { messageIsError = false; "Pong — desktop alive (ts $it)" },
                            onFailure = { messageIsError = true; it.message ?: "ping failed" }
                        )
                        busy = false
                    }
                }
            ) { Text(if (busy) "…" else "Ping") }
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    bridgeManager.unpair()
                    paired = null
                    message = null
                }
            ) { Text("Unpair") }
        }

        Spacer(modifier = Modifier.height(12.dp))
        BridgeWorkspaceBrowser(bridgeManager)
    } else {
        var discovered by remember {
            mutableStateOf<List<com.example.meowmba.core.bridge.DesktopBridgeProtocol.DiscoveredBridge>>(emptyList())
        }
        var scanning by remember { mutableStateOf(false) }
        var selected by remember {
            mutableStateOf<com.example.meowmba.core.bridge.DesktopBridgeProtocol.DiscoveredBridge?>(null)
        }
        var pairCode by remember { mutableStateOf("") }
        val discovery = remember { com.example.meowmba.core.bridge.BridgeDiscovery() }

        Text(
            text = "Run `meowmba-bridge.py --pair` on a computer on this network, then find it here and enter the code it shows.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            enabled = !busy && !scanning,
            onClick = {
                scope.launch {
                    scanning = true
                    message = null
                    discovered = discovery.discover()
                    if (discovered.isEmpty()) {
                        messageIsError = true
                        message = "No desktops heard — is the daemon running? Is the phone on the same Wi-Fi?"
                    }
                    scanning = false
                }
            }
        ) { Text(if (scanning) "Listening…" else "Find desktops") }

        discovered.forEach { d ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selected = d }
                    .padding(vertical = 6.dp)
            ) {
                RadioButton(selected = selected == d, onClick = null)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${d.name} · ${d.host}:${d.port}", color = Color.White, fontSize = 13.sp)
                    Text(
                        "cert ${d.certSha256.take(12)}…" + (if (d.pairingOpen) " · pairing open" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (d.pairingOpen) Color(0xFF81C784) else Color.Gray
                    )
                }
            }
        }

        selected?.let { d ->
            Spacer(modifier = Modifier.height(4.dp))
            SettingsTextField(
                value = pairCode,
                onValueChange = { pairCode = it.uppercase().take(9) },
                label = { Text("Pairing code (e.g. ABCD-1234)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                enabled = !busy && pairCode.length >= 8,
                onClick = {
                    scope.launch {
                        busy = true
                        message = bridgeManager.pairWithCode(d, pairCode.trim(), Build.MODEL ?: "phone").fold(
                            onSuccess = {
                                messageIsError = false
                                paired = bridgeManager.pairedBridge()
                                discovered = emptyList()
                                selected = null
                                "Paired with $it"
                            },
                            onFailure = { messageIsError = true; it.message ?: "pairing failed" }
                        )
                        busy = false
                    }
                }
            ) { Text(if (busy) "Pairing…" else "Pair ${d.name}") }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("Or paste the full payload manually:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        SettingsTextField(
            value = payload,
            onValueChange = { payload = it },
            label = { Text("Pairing payload") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            enabled = !busy && payload.isNotBlank(),
            onClick = {
                scope.launch {
                    busy = true
                    message = bridgeManager.pair(payload.trim(), Build.MODEL ?: "phone").fold(
                        onSuccess = { messageIsError = false; paired = bridgeManager.pairedBridge(); "Paired with $it" },
                        onFailure = { messageIsError = true; it.message ?: "pairing failed" }
                    )
                    busy = false
                }
            }
        ) { Text(if (busy) "Pairing…" else "Pair with payload") }
    }

    message?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = if (messageIsError) Color(0xFFE57373) else Color(0xFF81C784)
        )
    }
}

/**
 * Read-only browser for the paired desktop's workspace roots (P6b).
 * Directories descend; files open a size-capped utf-8 preview. Every row the
 * daemon served is shown — escapes and sensitive names never reach the list.
 */
@Composable
fun BridgeWorkspaceBrowser(bridgeManager: DesktopBridgeManager) {
    var active by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var roots by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentPath by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<DesktopBridgeManager.BridgeFileEntry>>(emptyList()) }
    var listTruncated by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<DesktopBridgeManager.BridgeFileContent?>(null) }
    val scope = rememberCoroutineScope()

    fun load(path: String) {
        scope.launch {
            busy = true
            error = null
            bridgeManager.listFiles(path).fold(
                onSuccess = {
                    currentPath = it.path
                    entries = it.entries
                    listTruncated = it.truncated
                },
                onFailure = { error = it.message ?: "list failed" }
            )
            busy = false
        }
    }

    TextButton(
        enabled = !busy,
        onClick = {
            if (!active) {
                active = true
                scope.launch {
                    busy = true
                    roots = bridgeManager.status().getOrNull()?.workspaceRoots ?: emptyList()
                    busy = false
                    load("")
                }
            } else {
                active = false
                preview = null
                error = null
            }
        }
    ) {
        Text(if (active) "Hide workspace browser" else "Browse workspace (read-only)", color = Color(0xFF58A6FF), fontSize = 13.sp)
    }

    if (active) {
        if (roots.isEmpty()) {
            Text(
                "No workspace roots declared on the desktop — add them to ~/.meowmba-bridge/config.json",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD29922)
            )
        }
        Text(currentPath, style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(4.dp))

        val floor = roots.firstOrNull()
        if (floor != null && currentPath != floor) {
            TextButton(onClick = { preview = null; load(currentPath.substringBeforeLast('/', "/")) }) {
                Text(".. (up)", color = Color(0xFF58A6FF), fontSize = 12.sp)
            }
        }

        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE57373))
        }

        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !busy) {
                        preview = null
                        if (entry.dir) {
                            load("$currentPath/${entry.name}")
                        } else {
                            scope.launch {
                                busy = true
                                error = null
                                bridgeManager.readFile("$currentPath/${entry.name}", maxBytes = 8192).fold(
                                    onSuccess = { preview = it },
                                    onFailure = { error = it.message ?: "read failed" }
                                )
                                busy = false
                            }
                        }
                    }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = if (entry.dir) "[dir] ${entry.name}" else "${entry.name} · ${entry.size / 1024} KB",
                    color = if (entry.dir) Color(0xFF58A6FF) else Color.White,
                    fontSize = 12.sp,
                    fontFamily = if (entry.dir) FontFamily.Default else FontFamily.Monospace
                )
            }
        }
        if (listTruncated) {
            Text("…listing capped by the desktop", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        preview?.let { file ->
            Spacer(modifier = Modifier.height(8.dp))
            Surface(color = Color(0xFF161B22), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            file.path.substringAfterLast('/'),
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { preview = null }) { Text("Close", fontSize = 11.sp) }
                    }
                    Text(
                        file.content.take(4000),
                        color = Color(0xFF9ECBFF),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (file.truncated || file.content.length > 4000) {
                        Text(
                            "preview capped — full file is ${file.size / 1024} KB on the desktop",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF1E1E1E),
            unfocusedContainerColor = Color(0xFF1E1E1E),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        )
    )
}
