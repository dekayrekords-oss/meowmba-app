package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.audio.AsrBackend
import com.example.meowmba.core.audio.AsrBackendStatus

/**
 * LLM → Voice Recognition.
 *
 * Picks which speech-to-text engine hears the user. Styling deliberately
 * mirrors [ModelManagerScreen] (same dark palette, monospace title, card
 * shape) because this is the same "what is loaded where" question, for ears
 * instead of the brain.
 *
 * Per the house honesty rule an unavailable backend is shown, greyed, WITH the
 * reason — never hidden and never silently substituted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceModelScreen(
    state: CompanionState,
    onSelectBackend: (AsrBackend) -> Unit = {},
    onDownloadWhisper: () -> Unit,
    onRepairStreamingModel: () -> Unit,
    onDownloadLocalVoices: () -> Unit,
    onSelectLocalVoice: (String) -> Unit,
    onPreviewLocalVoice: (String) -> Unit,
    onUseSystemVoice: () -> Unit,
    onClose: () -> Unit,
) {
    val statuses = state.asrBackendStatuses.ifEmpty {
        // Nothing evaluated yet (screen opened before the ViewModel refreshed):
        // show every option as unknown rather than an empty page.
        AsrBackend.entries.map { AsrBackendStatus(it, available = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice & Conversation", fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    TextButton(onClick = onClose) {
                        Text("Back", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Speaking voice", style = MaterialTheme.typography.titleLarge)
            Text("Four distinct neural voices. Once downloaded, speech is generated entirely on this phone. No voice API or server.",
                style = MaterialTheme.typography.bodySmall)
            if (!state.localVoicesReady) {
                Button(onClick = onDownloadLocalVoices, enabled = state.localVoiceProgress == null) {
                    Text("Download & enable offline voices · 349 MB")
                }
                Text("Needs 1.2 GB free during installation. Keep this screen open until complete.", style = MaterialTheme.typography.bodySmall)
            }
            state.localVoiceProgress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            state.localVoiceMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            val playbackError by com.example.meowmba.core.audio.LocalVoiceRuntime.lastError.collectAsState()
            playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            com.example.meowmba.core.audio.LocalVoices.all.forEach { voice ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.localVoiceEnabled && state.localVoiceId == voice.id,
                        enabled = state.localVoicesReady, onClick = { onSelectLocalVoice(voice.id) })
                    Column(Modifier.weight(1f)) {
                        Text("${voice.name} · ${voice.gender}")
                        Text(voice.description, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(enabled = state.localVoicesReady, onClick = { onPreviewLocalVoice(voice.id) }) { Text("Preview") }
                }
            }
            TextButton(onClick = onUseSystemVoice) { Text("Use offline Android voice instead") }
            Text("Kokoro neural voices · AI-generated speech, not human recordings. Preview to choose your preferred sound.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(
                "Listening · which engine turns your speech into text",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )

            state.voiceRecognitionStatus?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))

            statuses.forEach { status ->
                VoiceBackendCard(
                    status = status,
                    selected = status.backend == state.asrBackend,
                    cloudModel = state.asrCloudModel,
                    onSelect = { onSelectBackend(status.backend) },
                )
                Spacer(Modifier.height(12.dp))
                if (status.backend == AsrBackend.WHISPER_LOCAL && !status.available) {
                    Button(onClick = onDownloadWhisper, enabled = state.voicePackProgress == null, modifier = Modifier.fillMaxWidth()) {
                        Text("Download or repair Whisper")
                    }
                    if (status.reason.contains("streaming", ignoreCase = true)) {
                        OutlinedButton(onClick = onRepairStreamingModel, enabled = state.voicePackProgress == null, modifier = Modifier.fillMaxWidth()) {
                            Text("Repair live-listening model")
                        }
                    }
                    state.voicePackLabel?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    state.voicePackProgress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                    Spacer(Modifier.height(12.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // The cloud option sends microphone audio off the device. That is a
            // privacy-relevant fact, so it is stated on the page itself rather
            // than buried in a tooltip.
            if (state.asrBackend.isCloud) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Audio leaves this device",
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (state.asrBackend == AsrBackend.OPENAI_CLOUD)
                                "Recorded turns are sent to OpenAI's ${com.example.meowmba.core.audio.OpenAiAsrEngine.MODEL}. An OpenAI API key and paid API usage are required. In always-on mode, captured speech can be sent without a wake phrase."
                            else "Recorded turns are sent to OpenRouter and transcribed by ${state.asrCloudModel}. " +
                                "In always-on mode, captured speech can be sent without a wake phrase.",
                            // Deliberately NOT onSurfaceVariant: this card's container is a
                            // fixed dark amber, so a themed (near-black in Light mode) text
                            // colour measured ~1.9:1 against it — unreadable, on a privacy
                            // disclosure of all things. The card never flips brightness, so
                            // its text must not either. This cream reads ~13:1 on the amber.
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Text(
                "How it works",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "The cloud option still uses the on-device model to notice when you start " +
                    "and stop talking, so partial text appears instantly. The finished " +
                    "sentence is then re-transcribed for accuracy. If the network fails, " +
                    "the on-device text is used instead of dropping the turn.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VoiceBackendCard(
    status: AsrBackendStatus,
    selected: Boolean,
    cloudModel: String,
    onSelect: () -> Unit,
) {
    val enabled = status.available
    val accent = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, accent),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when {
                    !enabled && status.backend.isCloud -> Icons.Default.CloudOff
                    status.backend.isCloud -> Icons.Default.Cloud
                    else -> Icons.Default.Mic
                },
                contentDescription = null,
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    status.backend.isCloud -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    status.backend.displayName,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    status.backend.subtitle,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                if (!enabled && status.reason.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        status.reason,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 11.sp,
                    )
                }
            }
            if (selected && enabled) {
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "SELECTED",
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
