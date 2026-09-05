package com.example.meowmba.ui.onboarding

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
) {
    var step by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Text(
                text = "Connect your AI",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "A few quick steps to get Meow talking.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(32.dp))

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
                        SetupWiring.requestLocalModel(context, SetupWiring.LocalModelKind.TTS)
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
                else -> AdvancedSetupStep(context = context, onFinished = onFinished)
            }
        }
    }
}

@Composable
private fun AdvancedSetupStep(context: Context, onFinished: () -> Unit) {
    var hf by remember { mutableStateOf("") }
    var github by remember { mutableStateOf("") }
    var spotify by remember { mutableStateOf("") }
    var homeUrl by remember { mutableStateOf("") }
    var homeToken by remember { mutableStateOf("") }
    val storage = remember { com.example.meowmba.core.security.SecureStorageManager(context) }

    Text("Step 4 — Optional connections", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    Spacer(Modifier.height(8.dp))
    Text("Only fill in services you want Meow to use. Everything is stored securely on this phone.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    listOf(
        Triple("Hugging Face token", hf) { v: String -> hf = v },
        Triple("GitHub access token", github) { v: String -> github = v },
        Triple("Spotify access token", spotify) { v: String -> spotify = v },
        Triple("Home Assistant URL", homeUrl) { v: String -> homeUrl = v },
        Triple("Home Assistant long-lived token", homeToken) { v: String -> homeToken = v },
    ).forEach { (label, value, change) ->
        OutlinedTextField(value = value, onValueChange = change, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
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
    ) { Text("Save setup and finish") }
    TextButton(onClick = onFinished) { Text("Finish without optional connections") }
}

@Composable
private fun BrainStep(
    onSelectLocal: () -> Unit,
    onSelectCloud: (String, String) -> Unit,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var key by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    Text("Step 1 — Brain", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    Spacer(Modifier.height(16.dp))
    Text("How should Meow think?", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
    Spacer(Modifier.height(12.dp))

    SetupWiring.brainProviders().forEach { provider ->
        OutlinedButton(
            onClick = {
                if (provider.id == "local") onSelectLocal()
                else selectedId = provider.id
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Text(provider.name)
        }
    }

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
        Text("Local (on-device, no key needed)")
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onNext) { Text("Skip") }
}

@Composable
private fun AsrStep(
    context: Context,
    onSelectLocal: () -> Unit,
    onSelectCloud: (String) -> Unit,
    onSkip: () -> Unit,
) {
    var key by remember { mutableStateOf("") }

    Text("Step 3 — Hearing", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    Spacer(Modifier.height(16.dp))
    Text("How should Meow hear you?", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = onSelectLocal,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text("Local (on-device Whisper, needs a download)")
    }
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text("Gemini key (optional — cloud ASR)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
    Text(
        text = "If you leave this blank you keep the local Whisper listener. " +
            "A Gemini key lets Meow transcribe via the cloud when local Whisper is unavailable.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
    )
    Button(
        onClick = {
            val trimmed = key.trim()
            if (trimmed.isNotEmpty()) onSelectCloud(trimmed) else onSelectLocal()
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Text("Continue")
    }
    TextButton(onClick = onSkip) { Text("Skip") }
}
