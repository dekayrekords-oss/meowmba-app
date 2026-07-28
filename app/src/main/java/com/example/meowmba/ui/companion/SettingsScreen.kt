package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.security.SecureStorageManager

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
