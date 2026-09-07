package com.example.meowmba.ui.onboarding

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.setup.SetupWiring

/**
 * Full-screen onboarding wizard that walks a brand-new user through
 * connecting their AI (brain, speech-out, speech-in).
 *
 * Shown by [com.example.meowmba.MainActivity] when
 * [SetupWiring.needsOnboarding] is true, or on demand from Settings.
 */
@Composable
fun SetupWizardRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onEnablePermissions: () -> Unit = {},
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val wizardScroll = rememberScrollState()
    LaunchedEffect(step) { wizardScroll.scrollTo(0) }
    BackHandler { if (step > 0) step-- else onFinished() }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(wizardScroll)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Welcome to Meow",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Choose what you need. You can change everything later in Settings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { (step + 1) / 5f }, modifier = Modifier.fillMaxWidth())
            Text("Step ${step + 1} of 5", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { step-- }, enabled = step > 0) { Text("Back") }
                TextButton(onClick = onFinished) { Text("Explore first") }
            }
            Text("Local models download separately. Keep Wi-Fi on; see Hardware and Voice recognition for progress or retry. Basic app-opening commands do not require a cloud key.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))

            when (step) {
                0 -> BrainStep(
                    onSelectLocal = {
                        SetupWiring.setBrain(context, "local", null, null)
                        step = 1
                    },
                    onSelectCloud = { id, key ->
                        SetupWiring.setBrain(context, id, key, null)
                        step = 1
                    },
                )
                1 -> TtsStep(
                    onSelectLocal = {
                        SetupWiring.setTts(context, SetupWiring.Path.LOCAL, null)
                        step = 2
                    },
                    onNext = { step = 2 },
                )
                2 -> AsrStep(
                    context = context,
                    onSelectLocal = {
                        SetupWiring.setVoice(context, SetupWiring.Path.LOCAL, null)
                        SetupWiring.requestLocalModel(context, SetupWiring.LocalModelKind.ASR)
                        step = 3
                    },
                    onSelectCloud = { key ->
                        SetupWiring.setVoice(context, SetupWiring.Path.PAID_CLOUD, key)
                        step = 3
                    },
                    onSkip = { step = 3 },
                )
                3 -> {
                    val storage = remember { com.example.meowmba.core.security.SecureStorageManager(context) }
                    ImageGenerationSetup(storage)
                    OutlinedButton(onClick = { step = 4 }, modifier = Modifier.fillMaxWidth()) { Text("Continue to permissions & connections") }
                    Text("Image setup is optional. Continue without changing your existing choice, or tap Use this option above to save a new one.", style = MaterialTheme.typography.bodySmall)
                }
                else -> AdvancedSetupStep(context = context, onFinished = onFinished, onEnablePermissions = onEnablePermissions)
            }
        }
    }
}

@Composable
private fun AdvancedSetupStep(context: Context, onFinished: () -> Unit, onEnablePermissions: () -> Unit) {
    var showConnections by rememberSaveable { mutableStateOf(false) }
    var hf by remember { mutableStateOf("") }
    var github by remember { mutableStateOf("") }
    var spotify by remember { mutableStateOf("") }
    var homeUrl by remember { mutableStateOf("") }
    var homeToken by remember { mutableStateOf("") }
    val storage = remember { com.example.meowmba.core.security.SecureStorageManager(context) }

    Text("Make it yours", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    Spacer(Modifier.height(8.dp))
    Text("Android permissions are optional. Microphone enables voice; camera enables photos; location supports nearby features; calendar supports events; Bluetooth connects accessories; notifications show background activity. You can decline any request and keep exploring.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = onEnablePermissions, modifier = Modifier.fillMaxWidth()) { Text("Review Android permissions") }
    Text("Controlling buttons in other apps needs Accessibility enabled separately in Settings. Meow asks for approval before sensitive actions.", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { showConnections = !showConnections }) { Text(if (showConnections) "Hide optional connections" else "Add optional connections") }
    if (showConnections) {
    Text("Only add services you use. Keys are stored securely on this phone. Saving a key does not verify the connection; test it in Plugins & MCP Connectors.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    listOf(
        Triple("Hugging Face token", hf) { v: String -> hf = v },
        Triple("GitHub access token", github) { v: String -> github = v },
        Triple("Spotify access token", spotify) { v: String -> spotify = v },
        Triple("Home Assistant URL", homeUrl) { v: String -> homeUrl = v },
        Triple("Home Assistant long-lived token", homeToken) { v: String -> homeToken = v },
    ).forEach { (label, value, change) ->
        OutlinedTextField(value = value, onValueChange = change, label = { Text(label) }, singleLine = true,
            visualTransformation = if (label.endsWith("URL")) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
    }
    if (homeUrl.isBlank() != homeToken.isBlank()) Text("Home Assistant needs both its URL and token.", color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = {
            if (hf.isNotBlank()) storage.saveApiKey("huggingface", hf.trim())
            if (github.isNotBlank()) storage.saveApiKey("connector_github", github.trim())
            if (spotify.isNotBlank()) storage.saveApiKey("connector_spotify", spotify.trim())
            if (homeUrl.isNotBlank() && homeToken.isNotBlank()) {
                storage.saveApiKey("connector_home_assistant", homeUrl.trimEnd('/') + "\n" + homeToken.trim())
            }
            onFinished()
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = homeUrl.isBlank() == homeToken.isBlank(),
    ) { Text("Save and open Meow") }
    Text("Try: Open Chrome • Open Files • Play a happy bop. Tap the background to find tiles. Beta help is in Settings → Help & feedback.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun BrainStep(
    onSelectLocal: () -> Unit,
    onSelectCloud: (String, String) -> Unit,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var key by remember { mutableStateOf("") }
    var showCloud by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current

    Text("Your AI", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    Spacer(Modifier.height(16.dp))
    Text("How should Meow think?", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
    Spacer(Modifier.height(12.dp))

    Text("Start with on-phone actions, or connect a cloud provider for conversation. Cloud requests leave your phone and may have usage charges.", style = MaterialTheme.typography.bodySmall)
    SetupWiring.brainProviders().filter { it.id == "local" || showCloud }.forEach { provider ->
        OutlinedButton(
            onClick = {
                if (provider.id == "local") onSelectLocal()
                else { key = ""; selectedId = provider.id }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Text(provider.name)
        }
    }
    TextButton(onClick = { showCloud = !showCloud; selectedId = null; key = "" }) { Text(if (showCloud) "Hide cloud providers" else "Connect a cloud provider") }

    val provider = selectedId?.let { SetupWiring.brainProvider(it) }
    if (provider != null) {
        Spacer(Modifier.height(16.dp))
        Text("Key for ${provider.name}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(provider.blurb, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        provider.getKeyUrl?.let { url ->
            TextButton(onClick = {
                ctx.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(url)
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) {
                Text("Get a key →", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
        }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val trimmed = key.trim()
                if (trimmed.isNotEmpty()) onSelectCloud(provider.id, trimmed)
            },
            enabled = key.trim().isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save and continue")
        }
    }
}

@Composable
private fun TtsStep(
    onSelectLocal: () -> Unit,
    onNext: () -> Unit,
) {
    Text("Step 2 — Voice", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    Spacer(Modifier.height(16.dp))
    Text("How should Meow speak?", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
    Spacer(Modifier.height(12.dp))
    // Only LOCAL is currently wired (Android's TTS engine). Honesty: no fake cloud TTS.
    OutlinedButton(onClick = onSelectLocal, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Use phone voice — no API key")
    }
    Text("Uses the phone's speech engine. Four optional neural voices are available in voice settings after a ~350 MB download. Their speed depends on your phone.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onNext) { Text("Set up later") }
}

@Composable
private fun AsrStep(
    context: Context,
    onSelectLocal: () -> Unit,
    onSelectCloud: (String) -> Unit,
    onSkip: () -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var showCloud by rememberSaveable { mutableStateOf(false) }

    Text("Step 3 — Hearing", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    Spacer(Modifier.height(16.dp))
    Text("How should Meow hear you?", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = onSelectLocal,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text("Use Whisper on this phone")
    }
    Text("Whisper needs roughly 160 MB of model files plus the voice runtime. It is ready only after the download completes. You can type while it downloads.", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { showCloud = !showCloud; key = "" }) { Text(if (showCloud) "Hide cloud transcription" else "Optional cloud transcription") }
    if (showCloud) {
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text("Gemini key (optional — cloud ASR)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
    Text(
        text = "Gemini cloud transcription sends audio to Google. It needs internet access and may have usage charges. Leave this off to keep transcription on your phone.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
    )
    Button(
        onClick = {
            val trimmed = key.trim()
            if (trimmed.isNotEmpty()) onSelectCloud(trimmed)
        },
        enabled = key.trim().isNotEmpty(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Text("Save cloud transcription")
    }
    }
    TextButton(onClick = onSkip) { Text("Use text for now") }
}
