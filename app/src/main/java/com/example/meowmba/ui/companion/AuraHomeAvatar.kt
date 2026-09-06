package com.example.meowmba.ui.companion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot

/**
 * Aura Home — tap zones for the Jarvis orb face plus a parked homescreen
 * card (greeting, status pill, quick actions). The card is currently NOT
 * shown — the face runs full-screen per user request — but the zone mapping
 * below is live: the dome has no ears/eyes, so cat-face geometry would map
 * taps to invisible features.
 *
 * Trust boundary: every string here is derived from real [CompanionState] —
 * interaction state, privacy flags, model name, active task. Nothing is faked:
 * OFFLINE/MUTED/MIC OFF states dim the orb and say so in text.
 *
 * The pure logic lives in [AuraHomeContent] (no Compose imports) so it is
 * unit-testable on the JVM. Drawing + layout are thin shells over it.
 */
object AuraHomeContent {

    /** Time-aware greeting. Hour is 0..23; out-of-range clamps rather than crashing. */
    fun greetingFor(hour24: Int): String = when (hour24.coerceIn(0, 23)) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..22 -> "Good evening"
        else -> "Up late"
    }

    /** Single status line for the pill. Mute wins over the pipeline state. */
    fun statusTitle(interaction: InteractionState, isMuted: Boolean): String {
        if (isMuted) return "Muted"
        return when (interaction) {
            InteractionState.IDLE -> "Ready"
            InteractionState.LISTENING -> "Listening"
            InteractionState.THINKING -> "Thinking"
            InteractionState.SPEAKING -> "Speaking"
            InteractionState.MUTED -> "Muted"
            InteractionState.OFFLINE -> "Offline"
        }
    }

    /** Pill dot color as packed ARGB. Matches the orb ring color mapping. */
    fun statusDotArgb(interaction: InteractionState, isMuted: Boolean): Long {
        if (isMuted) return 0xFF8B949EL
        return when (interaction) {
            InteractionState.IDLE -> 0xFF3FB950L
            InteractionState.LISTENING -> 0xFF58A6FFL
            InteractionState.THINKING -> 0xFFE3B341L
            InteractionState.SPEAKING -> 0xFFA371F7L
            InteractionState.MUTED -> 0xFF8B949EL
            InteractionState.OFFLINE -> 0xFFF85149L
        }
    }

    /**
     * Honest second line: model + privacy flags + running task. Empty privacy
     * means everything is on — say nothing rather than claiming protection.
     */
    fun subtitleFor(
        selectedModel: String,
        isMicEnabled: Boolean,
        isCameraEnabled: Boolean,
        isMuted: Boolean,
        activeTask: String?,
    ): String {
        val flags = buildList {
            if (!isMicEnabled) add("Mic off")
            if (isCameraEnabled) add("Camera on")
            if (isMuted) add("Speech muted")
        }
        val base = selectedModel.ifBlank { "On-device" }
        val task = activeTask?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
        return if (flags.isEmpty()) "$base$task" else "$base · ${flags.joinToString(" · ")}$task"
    }

    /**
     * Dome tap zone for the orb. The dome has no ears/eyes, so cat-face
     * geometry would map taps to invisible features; instead the rule is one
     * shortcut: touching the orb toggles the microphone (the strongest
     * privacy action, and the one clients keep asking to bind). It reuses the
     * EAR_LEFT tap contract so re-enabling the mic also restores the right
     * listening mode. Taps outside the orb fall through to the tile menu, and
     * long-press still opens quick settings.
     *
     * @param tap tap position in px, [center] orb center in px, [ringRadius] outer ring radius in px.
     */
    fun auraZoneAt(tap: Offset, center: Offset, ringRadius: Float): AvatarZone {
        if (ringRadius <= 0f) return AvatarZone.BACKGROUND
        val dist = hypot(tap.x - center.x, tap.y - center.y)
        return if (dist <= ringRadius * 1.15f) AvatarZone.EAR_LEFT else AvatarZone.BACKGROUND
    }

    /** Long-press anywhere on the orb opens quick settings (mirrors forehead). */
    fun auraLongPressZone(): AvatarZone = AvatarZone.FOREHEAD
}

/**
 * Professional orb drawing: soft halo, ticked status ring, voice-reactive
 * inner ring, solid core with specular. Reacts to [interaction] (sweep angle
 * + pulse), [voiceLevel] 0..1 (talk energy / mouthEnergy), and privacy flags
 * (dim + desaturate — never a fake "everything is fine" glow).
 */
fun DrawScope.drawAuraHomeOrb(
    center: Offset,
    ringRadius: Float,
    interaction: InteractionState,
    isMuted: Boolean,
    dimmed: Boolean,
    voiceLevel: Float,
    clockSec: Float,
    glowColor: Color,
    faceAlpha: Float,
) {
    val voice = voiceLevel.coerceIn(0f, 1f)
    val live = interaction == InteractionState.LISTENING ||
        interaction == InteractionState.SPEAKING
    val idleBreath = kotlin.math.sin(clockSec * 1.7f) * 0.5f + 0.5f
    val energy = if (live) (0.55f + 0.45f * voice) else (0.35f + 0.20f * idleBreath)
    val alpha = (faceAlpha * if (dimmed || isMuted) 0.55f else 1f).coerceIn(0f, 1f)
    val ring = if (interaction == InteractionState.OFFLINE) Color(0xFFF85149) else glowColor

    // Soft halo underneath everything.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(ring.copy(alpha = 0.30f * alpha), Color.Transparent),
            center = center,
            radius = ringRadius * 1.55f,
        ),
        radius = ringRadius * 1.55f,
        center = center,
    )
    // Faint filled disc so the orb reads as one object on light wallpapers.
    drawCircle(
        color = ring.copy(alpha = 0.07f * alpha),
        radius = ringRadius,
        center = center,
    )
    // Outer status ring.
    drawCircle(
        color = ring.copy(alpha = 0.30f * alpha),
        radius = ringRadius,
        center = center,
        style = Stroke(width = ringRadius * 0.055f, cap = StrokeCap.Round),
    )
    // Activity sweep: listening draws a rotating arc, thinking a slow pulse
    // arc, speaking follows voice energy. Idle/offline/muted stay a calm ring.
    val sweep: ClosedFloatingPointRange<Float>? = when (interaction) {
        InteractionState.LISTENING -> {
            val start = (clockSec * 160f) % 360f - 90f
            start..(start + 110f)
        }
        InteractionState.THINKING -> {
            val arc = 70f + 50f * idleBreath
            -90f..(-90f + arc)
        }
        InteractionState.SPEAKING -> {
            val arc = 60f + 140f * voice
            -90f..(-90f + arc)
        }
        else -> null
    }
    if (sweep != null && !isMuted && interaction != InteractionState.OFFLINE) {
        drawArc(
            color = ring.copy(alpha = 0.85f * alpha),
            startAngle = sweep.start,
            sweepAngle = (sweep.endInclusive - sweep.start).coerceIn(10f, 300f),
            useCenter = false,
            style = Stroke(width = ringRadius * 0.075f, cap = StrokeCap.Round),
        )
    }
    // Ticks around the ring — 12 quiet marks, like a watch face. Static, so
    // the face stays calm when idle; the sweep above carries the motion.
    for (i in 0 until 12) {
        val a = Math.toRadians((i * 30f).toDouble())
        val dx = kotlin.math.cos(a).toFloat()
        val dy = kotlin.math.sin(a).toFloat()
        val major = i % 3 == 0
        val r0 = ringRadius * if (major) 0.86f else 0.90f
        val r1 = ringRadius * 0.94f
        drawLine(
            color = ring.copy(alpha = (if (major) 0.50f else 0.28f) * alpha),
            start = center + Offset(dx * r0, dy * r0),
            end = center + Offset(dx * r1, dy * r1),
            strokeWidth = ringRadius * if (major) 0.028f else 0.018f,
            cap = StrokeCap.Round,
        )
    }
    // Inner voice-reactive ring.
    val innerR = ringRadius * (0.60f + energy * 0.08f)
    drawCircle(
        color = ring.copy(alpha = 0.60f * alpha),
        radius = innerR,
        center = center,
        style = Stroke(width = ringRadius * 0.040f, cap = StrokeCap.Round),
    )
    // Solid core + specular highlight.
    val coreR = ringRadius * (0.20f + energy * 0.07f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.95f * alpha),
                ring.copy(alpha = 0.90f * alpha),
            ),
            center = center + Offset(-coreR * 0.25f, -coreR * 0.30f),
            radius = coreR * 1.6f,
        ),
        radius = coreR,
        center = center,
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.90f * alpha),
        radius = coreR * 0.30f,
        center = center + Offset(-coreR * 0.28f, -coreR * 0.32f),
    )
}

/** Homescreen header + quick actions shown under the orb when Aura Home is active. */
enum class AuraQuickAction(
    val label: String,
    val icon: ImageVector,
    val description: String,
) {
    TALK("Talk", Icons.Default.Mic, "Start voice input"),
    CHAT("Chat", Icons.AutoMirrored.Filled.Chat, "Open chat"),
    AGENTS("Agents", Icons.Default.Person, "Open agents"),
    SETTINGS("Setup", Icons.Default.Settings, "Open settings"),
}

/**
 * The Aura Home homescreen card: greeting, live status pill, honest subtitle,
 * and four large quick actions. Sits below the orb so the face stays the hero;
 * pointer-transparent outside the buttons (the Canvas keeps its own gestures).
 */
@Composable
fun AuraHomeCard(
    companionName: String,
    interaction: InteractionState,
    isMuted: Boolean,
    subtitle: String,
    hour24: Int,
    onAction: (AuraQuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = AuraHomeContent.statusTitle(interaction, isMuted)
    val dot = Color(AuraHomeContent.statusDotArgb(interaction, isMuted))
    Surface(
        color = Color(0xB314151A),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = "${AuraHomeContent.greetingFor(hour24)}, $companionName",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(color = dot, radius = size.minDimension / 2f)
                }
                Text(
                    text = "  $title",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "   $subtitle",
                    color = Color(0xFF9AA4B2),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AuraQuickAction.entries.forEach { action ->
                    FilledTonalButton(
                        onClick = { onAction(action) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.description,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = action.label, fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap dome for menu · mouth mutes speech · long-press for settings",
                color = Color(0xFF6E7681),
                fontSize = 11.sp,
            )
        }
    }
}
