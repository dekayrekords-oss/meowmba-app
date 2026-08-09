package com.example.meowmba.ui.companion

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Live direction strip shown on the nav screen while the car is moving.
 *
 * The arrow leans the way the car is actually pushing (from the detector's
 * lateral/longitudinal split), so "turn left" reads as a real left lean, hard
 * braking pulses, and highway speed streams the chevron. It is fed by the same
 * [CompanionState.driveReaction] the face uses — one signal, two surfaces.
 */
@Composable
fun DriveDirectionStrip(
    state: CompanionState,
    modifier: Modifier = Modifier,
) {
    if (!state.driving) return

    val reaction = state.driveReaction
    val targetAngle = when (reaction) {
        DriveReaction.LEAN_LEFT -> -38f
        DriveReaction.LEAN_RIGHT -> 38f
        else -> 0f
    }
    val angle by animateFloatAsState(targetAngle, tween(180), label = "driveArrow")
    val pulse by rememberInfiniteTransition(label = "drivePulse").animateFloat(
        initialValue = if (reaction == DriveReaction.LURCH) 0.35f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "drivePulse"
    )
    val stream by rememberInfiniteTransition(label = "driveStream").animateFloat(
        initialValue = if (reaction == DriveReaction.SPEED) 0.1f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(240), RepeatMode.Reverse),
        label = "driveStream"
    )

    val label = when (reaction) {
        DriveReaction.LEAN_LEFT -> "TURN LEFT"
        DriveReaction.LEAN_RIGHT -> "TURN RIGHT"
        DriveReaction.LURCH -> "BRAKING"
        DriveReaction.SPEED -> "FULL SPEED"
        DriveReaction.NONE -> "CRUISING"
    }
    val accent = when (reaction) {
        DriveReaction.LURCH -> Color(0xFFF85149)
        DriveReaction.SPEED -> Color(0xFF3FB950)
        DriveReaction.LEAN_LEFT, DriveReaction.LEAN_RIGHT -> Color(0xFFD29922)
        DriveReaction.NONE -> Color(0xFF58A6FF)
    }

    Surface(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        color = Color(0xFF161B22),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DriveArrow(
                    angleDeg = angle,
                    pulse = pulse * stream,
                    color = accent,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    label,
                    color = accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "%.0f".format(state.driveSpeedMph),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(4.dp))
                Text("mph", color = Color(0xFF8B949E), fontSize = 11.sp)
            }
        }
    }
}

/** A single arrow that rotates with the turn and pulses with force. */
@Composable
private fun DriveArrow(
    angleDeg: Float,
    pulse: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val len = w * 0.42f * pulse.coerceIn(0.2f, 1f)
        rotate(angleDeg, pivot = Offset(cx, cy)) {
            val head = Path().apply {
                moveTo(cx, cy - len)
                lineTo(cx - w * 0.16f, cy)
                lineTo(cx + w * 0.16f, cy)
                close()
            }
            drawPath(head, color)
        }
        drawCircle(
            color = color.copy(alpha = 0.25f * pulse),
            radius = w * 0.34f,
            center = Offset(cx, cy),
            style = Stroke(width = w * 0.05f),
        )
    }
}
