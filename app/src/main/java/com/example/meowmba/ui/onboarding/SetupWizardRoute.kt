package com.example.meowmba.ui.onboarding

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        color = Color(0xFF0C1117),
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
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "A few quick steps to get Meow talking.",
                color = Color.Gray,
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
                        onFinished()
                    },
                    onSelectCloud = { key ->
                        SetupWiring.setVoice(context, SetupWiring.Path.PAID_CLOUD, key)
                        onFinished()
                    },
                    onSkip = { onFinished() },
                )
            }
        }
    }
}

@Composable
private fun BrainStep(
    onSelectLocal: () -> Unit,
    onSelectCloud: (String, String) -> Unit,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var key by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    Text("Step 1 — Brain", color = Color(0xFF58A6FF), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    Spacer(Modifier.height(16.dp))
    Text("How should Meow think?", color = Color.White, fontSize = 16.sp)
    Spacer(Modifier.height(12.dp))

    SetupWiring.brainProviders().forEach { provider ->
        OutlinedButton(
            onClick = {
                if (provider.id == "local") onSelectLocal()
                else selectedId = provider.id
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Text(provider.name, color = Color.White)
        }
    }

    val provider = selectedId?.let { SetupWiring.brainProvider(it) }
    if (provider != null) {
        Spacer(Modifier.height(16.dp))
        Text("Key for ${provider.name}", color = Color.Gray, fontSize = 13.sp)
        Text(provider.blurb, color = Color.Gray, fontSize = 12.sp)
        provider.getKeyUrl?.let { url ->
            TextButton(onClick = {
                ctx.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(url)
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) {
                Text("Get a key →", color = Color(0xFF58A6FF), fontSize = 13.sp)
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
    Text("Step 2 — Voice", color = Color(0xFF58A6FF), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    Spacer(Modifier.height(16.dp))
    Text("How should Meow speak?", color = Color.White, fontSize = 16.sp)
    Spacer(Modifier.height(12.dp))
    // Only LOCAL is currently wired (Android's TTS engine). Honesty: no fake cloud TTS.
    OutlinedButton(onClick = onSelectLocal, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Local (on-device, no key needed)", color = Color.White)
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onNext) { Text("Skip", color = Color.Gray) }
}

@Composable
private fun AsrStep(
    context: Context,
    onSelectLocal: () -> Unit,
    onSelectCloud: (String) -> Unit,
    onSkip: () -> Unit,
) {
    var key by remember { mutableStateOf("") }

    Text("Step 3 — Hearing", color = Color(0xFF58A6FF), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    Spacer(Modifier.height(16.dp))
    Text("How should Meow hear you?", color = Color.White, fontSize = 16.sp)
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = onSelectLocal,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text("Local (on-device Whisper, needs a download)", color = Color.White)
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
        color = Color.Gray,
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
    TextButton(onClick = onSkip) { Text("Skip", color = Color.Gray) }
}
