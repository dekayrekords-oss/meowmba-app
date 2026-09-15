package com.example.meowmba.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.*
import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.Card
import com.example.meowmba.ui.theme.FilterChip
import com.example.meowmba.ui.theme.OutlinedButton
import kotlin.math.roundToInt

/** Routes and capabilities that are available in this build, with setup dependencies in plain sight. */
internal val welcomeFeatures = listOf(
    "Your living companion" to "Choose a character, play animations, invent forms and props with a configured AI, and explore habitats. Change the look anytime in Avatar.",
    "Talk, type & remember" to "Use voice or chat, save memories and named places, and report possible bugs. Voice and location need permission; cloud transcription needs a configured provider.",
    "Your Computer" to "Use the browser, Terminal and projects. Set up Termux for Linux on this phone, or pair a computer for remote tools and SSH. Connections must be verified.",
    "Agents for your business" to "Create specialist teams, assign jobs and review results. Configured specialists take turns on a shared task. Approvals and the audit trail stay in Business and Agents.",
    "Connect your AI & apps" to "Choose local AI, OpenAI, Claude, OpenRouter, OpenCode Zen or a custom API. LLM has model lists and connection checks; Plugins has app integrations. Each service needs its own setup.",
    "An optional neural brain" to "Brain shows neural activity. Choose a local network or pair a full fly-graph server in Neural / Lumen settings. Shadow observes; Neural can guide the avatar. Physical robot control remains unavailable.",
    "Creative tools & daily help" to "Find files, keep projects, use reminders and supported media tools. Logic controls need a paired Mac helper. Drive alerts depend on regional sources; full automatic mixing and universal app control are still developing.",
)

@Composable
internal fun SetupFeaturesPage(onNext: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("A companion with things to do", style = MaterialTheme.typography.headlineMedium)
        Text("Start with your avatar. Connect the tools you want when you’re ready.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        welcomeFeatures.forEach { (title, body) ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Button(onNext, Modifier.fillMaxWidth()) { Text("Choose my avatar") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SetupAvatarPage(
    style: AvatarStyle, animal: AvatarAnimal, confirmed: Boolean,
    onStyle: (AvatarStyle) -> Unit, onAnimal: (AvatarAnimal) -> Unit, onNext: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Choose your companion", style = MaterialTheme.typography.headlineMedium)
        Text("Tap a look or character to choose it. Each animal keeps its own eyes and floating features.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        AvatarWelcomePreview(style, animal, .85f, Modifier.fillMaxWidth().height(180.dp))
        Text("Looks", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AvatarStyle.entries.forEach { option ->
                FilterChip(confirmed && style == option, { onStyle(option) }, { Text(option.label) })
            }
        }
        Text("Characters for Lumen", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AvatarAnimal.entries.forEach { option ->
                FilterChip(confirmed && style.isLumen && style != AvatarStyle.FULL_FLY &&
                    (if (style == AvatarStyle.LUMEN_CAT) AvatarAnimal.CAT else animal) == option,
                    { onAnimal(option) }, { Text(option.label) })
            }
        }
        Text(if (confirmed) "Selected: ${welcomeAvatarName(style, animal)}" else "Choose one to continue.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onNext, Modifier.fillMaxWidth(), enabled = confirmed) { Text("Fit to my screen") }
    }
}

private fun welcomeAvatarName(style: AvatarStyle, animal: AvatarAnimal) =
    if (style == AvatarStyle.LUMEN) "Lumen · ${animal.label}" else style.label

@Composable
private fun AvatarWelcomePreview(style: AvatarStyle, animal: AvatarAnimal, scale: Float, modifier: Modifier) {
    Box(modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        // One native resting frame. Setup never starts camera, microphone, inference or a neural connection.
        key(style, animal) {
            AvatarRenderer(CompanionState(avatarStyle = style, avatarAnimal = animal, avatarScale = scale),
                onZoneClick = {}, paused = true, restingPreview = true, pointerEnabled = false,
                modifier = Modifier.fillMaxSize().semantics { contentDescription = "${welcomeAvatarName(style, animal)} preview" })
        }
    }
}

@Composable
internal fun AvatarFitPage(
    style: AvatarStyle, animal: AvatarAnimal, scale: Float, automaticScale: Float,
    screenWidth: Float, screenHeight: Float, adjusting: Boolean,
    onAdjust: () -> Unit, onScale: (Float) -> Unit, onAuto: () -> Unit, onNext: () -> Unit,
) {
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    if (fullScreen) androidx.compose.ui.window.Dialog(
        onDismissRequest = { fullScreen = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AvatarWelcomePreview(style, animal, scale, Modifier.fillMaxSize())
            Card(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(12.dp)) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Full-size preview", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    TextButton({ fullScreen = false }) { Text("Back to sizing") }
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Fitted to your screen", style = MaterialTheme.typography.headlineMedium)
        Text("We’ve sized ${welcomeAvatarName(style, animal)} with room to move and reach your controls. Want to change it?", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AvatarWelcomePreview(style, animal, scale, Modifier.heightIn(max = 300.dp)
                .widthIn(max = 480.dp).aspectRatio((screenWidth / screenHeight.coerceAtLeast(1f)).coerceIn(.25f, 4f)))
        }
        Text("Screen preview · ${screenWidth.roundToInt()} × ${screenHeight.roundToInt()} dp · ${(scale * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton({ fullScreen = true }) { Text("View at full size") }
        if (adjusting) {
            Text("Avatar size", style = MaterialTheme.typography.titleMedium)
            Slider(value = scale.coerceIn(.5f, 3f), onValueChange = { onScale(it.coerceIn(.5f, 3f)) }, valueRange = .5f..3f,
                modifier = Modifier.fillMaxWidth().testTag("onboarding-avatar-size").semantics { contentDescription = "Avatar size" })
            if (scale > automaticScale + .05f) Text("Larger sizes may crop features while your avatar moves.", style = MaterialTheme.typography.bodySmall)
            TextButton(onAuto) { Text("Auto-fit again") }
        } else {
            OutlinedButton(onAdjust, Modifier.fillMaxWidth()) { Text("Adjust size") }
        }
        Button(onNext, Modifier.fillMaxWidth()) { Text(if (adjusting) "Use this size" else "Keep this size") }
        Text("Change it later in Avatar → Space. Automatic fitting updates here if you rotate or unfold your phone.", style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SetupProvidersPage(storage: SecureStorageManager, onNext: () -> Unit, onLater: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connect your AI", style = MaterialTheme.typography.headlineMedium)
        Text("Choose a provider, or do this later in LLM. Keys are stored securely. A chat subscription does not include API credit; free models can have limits.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("local" to "On this phone", "openai" to "OpenAI", "anthropic" to "Claude", "openrouter" to "OpenRouter", "opencode" to "OpenCode Zen", "custom" to "Custom API").forEach { (id, label) ->
                FilterChip(selected == id, { selected = id }, { Text(label) })
            }
        }
        selected?.let { provider -> key(provider) {
            if (provider == "local") AgentBrainSetup(onContinue = onNext)
            else Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) { CloudProviderSettings(provider, storage, onSaved = {}) }
            }
        } }
        Button(onNext, Modifier.fillMaxWidth()) { Text("Continue setup") }
        TextButton(onLater) { Text("Set up AI later") }
    }
}
