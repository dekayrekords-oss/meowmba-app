package com.example.meowmba.ui.companion

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.media.RemoteMusicGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Logic-like studio chrome: near-black panels, soft separators, mint transport accent. */
private val DawBg = Color(0xFF0B0D10)
private val DawPanel = Color(0xFF15181E)
private val DawRail = Color(0xFF1C2129)
private val DawBorder = Color(0xFF2A313C)
private val DawAccent = Color(0xFF00E5A8)
private val DawAccentDim = Color(0xFF0A3D32)
private val DawClipA = Color(0xFF1B6B5A)
private val DawClipB = Color(0xFF3D5A80)
private val DawClipC = Color(0xFF6B4C9A)
private val DawMuted = Color(0xFF8B949E)
private val DawText = Color(0xFFE6EDF3)

private val ClipPalette = listOf(DawClipA, DawClipB, DawClipC)

/**
 * First Music/DAW screen — Logic Pro–inspired dark studio chrome.
 *
 * Transport (play/stop + generate), a prompt field wired to
 * [RemoteMusicGenerator] when Settings has a music server URL, and an
 * arrange area that places returned WAV bytes as timeline clips.
 * Preview uses [MediaPlayer] (no ExoPlayer dependency in this project).
 */
@Composable
fun MusicDawScreen(
    musicServerUrl: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clips = remember { mutableStateListOf<DawClip>() }
    var prompt by remember { mutableStateOf("") }
    var durationSec by remember { mutableStateOf("10") }
    var status by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var playingClipId by remember { mutableStateOf<String?>(null) }
    var transportPlaying by remember { mutableStateOf(false) }
    var selectedClipId by remember { mutableStateOf<String?>(null) }

    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPlayback() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        playingClipId = null
        transportPlaying = false
    }

    fun playFile(path: String, clipId: String) {
        stopPlayback()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener {
                    playingClipId = null
                    transportPlaying = false
                    try {
                        release()
                    } catch (_: Exception) {
                    }
                    if (player === this) player = null
                }
                prepare()
                start()
            }
            player = mp
            playingClipId = clipId
            transportPlaying = true
        } catch (e: Exception) {
            status = "Playback failed: ${e.message ?: "unknown"}"
            stopPlayback()
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopPlayback() }
    }

    val serverConfigured = musicServerUrl.isNotBlank()
    val pxPerSec = 28f

    Surface(modifier = Modifier.fillMaxSize(), color = DawBg) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .fillMaxSize(),
        ) {
            // ── Title bar ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DawRail)
                    .border(1.dp, DawBorder)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.MusicNote, null, tint = DawAccent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Music / DAW",
                        color = DawText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                    Text(
                        if (serverConfigured) musicServerUrl.trimEnd(Char(47))
                        else "No music server — set URL in Settings",
                        color = if (serverConfigured) DawMuted else Color(0xFFFF8A65),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = {
                    stopPlayback()
                    onClose()
                }) {
                    Icon(Icons.Default.Close, "Close", tint = DawText)
                }
            }

            // ── Transport ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DawPanel)
                    .border(1.dp, DawBorder)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TransportButton(
                    icon = Icons.Default.PlayArrow,
                    label = "Play",
                    enabled = selectedClipId != null || clips.isNotEmpty(),
                    active = transportPlaying,
                ) {
                    val target = clips.firstOrNull { it.id == selectedClipId } ?: clips.firstOrNull()
                    if (target != null) playFile(target.filePath, target.id)
                }
                TransportButton(
                    icon = Icons.Default.Stop,
                    label = "Stop",
                    enabled = transportPlaying,
                    active = false,
                ) { stopPlayback() }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DawBg)
                        .border(1.dp, DawBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = when {
                            generating -> "Generating…"
                            transportPlaying -> "▶ Playing"
                            else -> "■ Stopped  ·  ${clips.size} clip${if (clips.size == 1) "" else "s"}"
                        },
                        color = DawMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Button(
                    onClick = {
                        if (generating) return@Button
                        if (!serverConfigured) {
                            status = "Set a Music server URL in Settings first"
                            return@Button
                        }
                        val p = prompt.trim()
                        if (p.isEmpty()) {
                            status = "Enter a prompt to generate"
                            return@Button
                        }
                        val dur = durationSec.toIntOrNull()?.coerceIn(1, 30) ?: 10
                        generating = true
                        status = "Contacting music server…"
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    RemoteMusicGenerator(musicServerUrl.trimEnd(Char(47))).generate(p, dur)
                                }.getOrElse { Result.failure(it) }
                            }
                            generating = false
                            result.fold(
                                onSuccess = { bytes ->
                                    val dir = File(context.cacheDir, "music_daw").apply { mkdirs() }
                                    val file = File(dir, "daw_${System.currentTimeMillis()}.wav")
                                    withContext(Dispatchers.IO) { file.writeBytes(bytes) }
                                    val startSec = clips.maxOfOrNull { it.startSec + it.durationSec } ?: 0f
                                    val clip = DawClip(
                                        id = "clip_${System.currentTimeMillis()}",
                                        label = p.take(48),
                                        filePath = file.absolutePath,
                                        startSec = startSec,
                                        durationSec = dur.toFloat(),
                                        trackIndex = clips.size % 3,
                                        color = ClipPalette[clips.size % ClipPalette.size],
                                    )
                                    clips.add(clip)
                                    selectedClipId = clip.id
                                    status = "Placed clip · ${bytes.size / 1024} KB"
                                },
                                onFailure = { err ->
                                    status = err.message ?: "Generation failed"
                                },
                            )
                        }
                    },
                    enabled = !generating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DawAccent,
                        contentColor = Color.Black,
                        disabledContainerColor = DawAccentDim,
                        disabledContentColor = DawMuted,
                    ),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    if (generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Generate", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // ── Prompt row ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DawBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Prompt") },
                    placeholder = { Text("lo-fi chill beat with soft piano") },
                    colors = dawFieldColors(),
                )
                OutlinedTextField(
                    value = durationSec,
                    onValueChange = { v -> if (v.length <= 2 && v.all { it.isDigit() }) durationSec = v },
                    modifier = Modifier.width(88.dp),
                    singleLine = true,
                    label = { Text("Sec") },
                    colors = dawFieldColors(),
                )
            }

            status?.let { msg ->
                Text(
                    text = msg,
                    color = if (msg.startsWith("Placed") || msg.startsWith("■") || msg.startsWith("▶"))
                        DawAccent else Color(0xFFFFAB91),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }

            // ── Arrange: track headers + timeline ──────────────────────
            val timelineWidthSec = (clips.maxOfOrNull { it.startSec + it.durationSec } ?: 30f)
                .coerceAtLeast(30f) + 4f
            val scroll = rememberScrollState()

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                // Track list / headers
                Column(
                    modifier = Modifier
                        .width(110.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DawRail)
                        .border(1.dp, DawBorder, RoundedCornerShape(8.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(DawPanel)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text("Tracks", color = DawMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    listOf("Audio 1", "Audio 2", "Audio 3").forEachIndexed { idx, name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .border(1.dp, DawBorder)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(ClipPalette[idx]),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(name, color = DawText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }

                Spacer(Modifier.width(6.dp))

                // Timeline / arrange
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DawPanel)
                        .border(1.dp, DawBorder, RoundedCornerShape(8.dp))
                        .horizontalScroll(scroll),
                ) {
                    val timelineWidth = (timelineWidthSec * pxPerSec).dp
                    // Ruler
                    Box(
                        modifier = Modifier
                            .width(timelineWidth)
                            .height(28.dp)
                            .background(DawRail),
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val secs = timelineWidthSec.toInt()
                            for (s in 0..secs) {
                                val x = s * pxPerSec
                                val major = s % 5 == 0
                                drawLine(
                                    color = if (major) DawMuted else DawBorder,
                                    start = Offset(x, if (major) size.height * 0.35f else size.height * 0.55f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1f,
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxSize()) {
                            for (s in 0..timelineWidthSec.toInt() step 5) {
                                Text(
                                    "${s}s",
                                    color = DawMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .width((5 * pxPerSec).dp)
                                        .padding(start = 2.dp, top = 2.dp),
                                )
                            }
                        }
                    }

                    // Three track lanes
                    for (track in 0 until 3) {
                        Box(
                            modifier = Modifier
                                .width(timelineWidth)
                                .height(64.dp)
                                .border(1.dp, DawBorder)
                                .background(if (track % 2 == 0) DawBg else DawPanel),
                        ) {
                            // Grid lines
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val secs = timelineWidthSec.toInt()
                                for (s in 0..secs) {
                                    val x = s * pxPerSec
                                    drawLine(
                                        color = DawBorder.copy(alpha = 0.5f),
                                        start = Offset(x, 0f),
                                        end = Offset(x, size.height),
                                        strokeWidth = 1f,
                                    )
                                }
                            }
                            clips.filter { it.trackIndex == track }.forEach { clip ->
                                val selected = clip.id == selectedClipId
                                val playing = clip.id == playingClipId
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp, bottom = 6.dp)
                                        .offsetX(clip.startSec * pxPerSec)
                                        .width((clip.durationSec * pxPerSec).coerceAtLeast(36f).dp)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(clip.color)
                                        .border(
                                            width = if (selected || playing) 2.dp else 1.dp,
                                            color = when {
                                                playing -> DawAccent
                                                selected -> Color.White.copy(alpha = 0.7f)
                                                else -> Color.Black.copy(alpha = 0.4f)
                                            },
                                            shape = RoundedCornerShape(4.dp),
                                        )
                                        .clickable {
                                            selectedClipId = clip.id
                                            playFile(clip.filePath, clip.id)
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                ) {
                                    Column {
                                        Text(
                                            clip.label,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${clip.durationSec.toInt()}s",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (clips.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .width(timelineWidth)
                                .height(80.dp)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Generate a clip to place it on the arrange timeline",
                                color = DawMuted,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            // Clip list (compact)
            if (clips.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .background(DawRail)
                        .border(1.dp, DawBorder)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Clips", color = DawMuted, fontSize = 11.sp)
                    clips.forEach { clip ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedClipId = clip.id
                                    playFile(clip.filePath, clip.id)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(clip.color),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                clip.label,
                                color = if (clip.id == selectedClipId) DawAccent else DawText,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "T${clip.trackIndex + 1} · ${clip.durationSec.toInt()}s",
                                color = DawMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) DawAccentDim else DawRail)
            .border(1.dp, if (active) DawAccent else DawBorder, RoundedCornerShape(8.dp)),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                !enabled -> DawMuted.copy(alpha = 0.4f)
                active -> DawAccent
                else -> DawText
            },
        )
    }
}

@Composable
private fun dawFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DawText,
    unfocusedTextColor = DawText,
    focusedBorderColor = DawAccent,
    unfocusedBorderColor = DawBorder,
    focusedLabelColor = DawAccent,
    unfocusedLabelColor = DawMuted,
    cursorColor = DawAccent,
    focusedContainerColor = DawPanel,
    unfocusedContainerColor = DawPanel,
    focusedPlaceholderColor = DawMuted,
    unfocusedPlaceholderColor = DawMuted,
)

/** Horizontal offset in dp from a float px value (timeline start). */
private fun Modifier.offsetX(px: Float): Modifier = this.offset(x = px.dp)

private data class DawClip(
    val id: String,
    val label: String,
    val filePath: String,
    val startSec: Float,
    val durationSec: Float,
    val trackIndex: Int,
    val color: Color,
)
