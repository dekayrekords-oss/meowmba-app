package com.example.meowmba.ui.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset as GeomOffset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.car.TripStats
import java.util.Locale

/**
 * The drive card: a glanceable heads-up that Meow is driving with the user.
 *
 * Big speed, a "who says we're driving" reason line, live G-force, and the trip's
 * distance/time/top-speed. Only shown while actually driving; the Settings toggle is
 * what turns the whole feature on, so the card never has to.
 */
@Composable
fun DriveCard(
    speedMph: Float,
    gForce: Float,
    reason: String,
    manual: Boolean,
    trip: TripStats?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    roadStatus: String? = null,
    driverCamera: (@Composable () -> Unit)? = null,
    obdLine: String? = null,
    vehicleLine: String? = null,
    cameraLabels: List<String> = emptyList(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        DrivePalette.top.copy(alpha = 0.34f),
                        DrivePalette.top.copy(alpha = 0.14f),
                        Color.Transparent,
                    ),
                ),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🚗", fontSize = 30.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "%.0f".format(Locale.US, speedMph),
                        color = DrivePalette.onSurface,
                        style = shadowed(52.sp, FontWeight.Light),
                    )
                    Text(
                        text = "mph",
                        color = DrivePalette.onSurface.copy(alpha = 0.8f),
                        style = shadowed(15.sp),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    text = if (manual) "drive mode (manual)" else "driving — $reason",
                    color = DrivePalette.onSurface.copy(alpha = 0.85f),
                    style = shadowed(13.sp),
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text("✕", color = DrivePalette.onSurface.copy(alpha = 0.7f), fontSize = 14.sp)
            }
        }

        roadStatus?.let { Text(it, color = DrivePalette.onSurface, style = shadowed(12.sp)) }
        vehicleLine?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = DrivePalette.onSurface.copy(alpha = 0.85f), style = shadowed(11.sp))
        }
        obdLine?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = DrivePalette.onSurface, style = shadowed(12.sp))
        }
        if (cameraLabels.isNotEmpty()) {
            Text(
                "Cameras: " + cameraLabels.joinToString(" · "),
                color = DrivePalette.onSurface.copy(alpha = 0.8f),
                style = shadowed(10.sp),
            )
        }
        driverCamera?.let { cam ->
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            ) { cam() }
        }
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DriveStat("Speed", "%.0f".format(Locale.US, speedMph) + " mph")
            DriveStat("G-force", "%.1f g".format(Locale.US, gForce))
            trip?.let {
                DriveStat("Trip", "%.1f mi".format(Locale.US, it.distanceMiles))
                DriveStat("Time", "${it.durationMinutes}m")
                DriveStat("Top", "%.0f".format(Locale.US, it.maxSpeedMph) + " mph")
            } ?: DriveStat("Trip", "just started")
        }
    }
}

/** Red-tinted alert card shown when the detector thinks there was a collision. */
@Composable
fun CrashAlertCard(
    message: String,
    onTextContact: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF7F1D1D).copy(alpha = 0.92f),
                        Color(0xFF7F1D1D).copy(alpha = 0.72f),
                        Color(0xFF7F1D1D).copy(alpha = 0.0f),
                    ),
                ),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚠️", fontSize = 30.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "Possible crash",
                    color = Color(0xFFFFE4E6),
                    style = shadowed(20.sp, FontWeight.SemiBold),
                )
                Text(
                    text = message,
                    color = Color(0xFFFFE4E6).copy(alpha = 0.92f),
                    style = shadowed(13.sp),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text("I'm OK", color = Color(0xFFFFE4E6), fontSize = 13.sp)
            }
            TextButton(onClick = onTextContact) {
                Text("Text my contact", color = Color(0xFFFFE4E6), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun DriveStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = DrivePalette.onSurface, style = shadowed(16.sp, FontWeight.Medium))
        Text(
            label.uppercase(),
            color = DrivePalette.onSurface.copy(alpha = 0.75f),
            style = shadowed(9.sp),
        )
    }
}

private object DrivePalette {
    val top = Color(0xFF1E3A5F)
    val onSurface = Color(0xFFEAF2FB)
}

private fun shadowed(size: androidx.compose.ui.unit.TextUnit, weight: FontWeight? = null) =
    TextStyle(
        fontSize = size,
        fontWeight = weight,
        shadow = Shadow(
            color = Color(0xCC000000),
            offset = GeomOffset(0f, 1.5f),
            blurRadius = 6f,
        ),
    )
