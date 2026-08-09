package com.example.meowmba.ui.companion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Avatar card: which face the companion wears, and a shelf of animations to
 * play on demand.
 *
 * The style is applied the moment it is tapped and the card stays open, because
 * picking a face is a comparison task — a save button between every comparison
 * makes it a chore. Playing an animation is the opposite: it closes everything,
 * since the whole point is to watch the face.
 */
@Composable
fun AvatarScreen(
    style: AvatarStyle,
    onSelectStyle: (AvatarStyle) -> Unit,
    animations: List<String>,
    onPlayAnimation: (String) -> Unit,
    onClose: () -> Unit,
    eyeColor: Long = AvatarColors.DEFAULT_EYE,
    glowColor: Long = AvatarColors.DEFAULT_GLOW,
    onSelectEyeColor: (Long) -> Unit = {},
    onSelectGlowColor: (Long) -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D1117)) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Avatar",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Face style",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(10.dp))

            AvatarStyle.entries.forEach { option ->
                StyleRow(
                    option = option,
                    selected = option == style,
                    onClick = { onSelectStyle(option) },
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Eye color",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Tint of the Godot face eyes.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(10.dp))
            ColorSwatchRow(selected = eyeColor, onSelect = onSelectEyeColor)

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Glow color",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Soft halo behind the face — shows over a transparent desk.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(10.dp))
            ColorSwatchRow(selected = glowColor, onSelect = onSelectGlowColor)

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Animations",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Both styles share every animation — the eyes are the only " +
                    "thing that changes. Tap one to watch it play.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (animations.isEmpty()) {
                Text(
                    text = "No animations yet.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                animations.forEach { name ->
                    AnimationRow(name = name, onClick = { onPlayAnimation(name) })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun StyleRow(
    option: AvatarStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFF14251C) else Color(0xFF161B22))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color(0xFF3FB950) else Color(0xFF30363D),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EyePreview(style = option, modifier = Modifier.size(72.dp, 48.dp))
        Spacer(modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(option.label, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                option.description,
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(0xFF3FB950))
        }
    }
}

@Composable
private fun AnimationRow(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF161B22))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF3FB950))
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = name.replaceFirstChar { it.uppercase() },
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * A thumbnail of one eye in each style, so the two can be told apart without
 * closing the card. The Neo half reuses the real renderer; the classic half is a
 * capsule drawn here rather than reaching into [AvatarRenderer]'s private eye —
 * at this size it is a swatch, not a rehearsal of the face.
 */
@Composable
private fun EyePreview(style: AvatarStyle, modifier: Modifier = Modifier) {
    val eyeColor = Color(0xFF3FB950)
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val eyeW = size.width * 0.52f
        val eyeH = size.height * 0.62f

        when (style) {
            AvatarStyle.NEO -> drawEyeNeo(
                side = 1f,
                tiltDeg = 0f,
                cx = cx,
                eyeCy = cy,
                s = size.minDimension * 3f,
                eyeDX = 0f,
                eyeW = eyeW,
                baseEyeH = eyeH,
                eyeH = eyeH,
                blink = 1f,
                pose = AvatarPose(),
                pupilSize = 0.45f,
                gazeX = 0f,
                gazeY = 0f,
                gazeUp = 0f,
                eyeColor = eyeColor,
            )

            AvatarStyle.CLASSIC -> {
                val corner = CornerRadius(eyeW * 0.5f, eyeW * 0.5f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(eyeColor.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = eyeW * 1.6f,
                    ),
                )
                drawRoundRect(
                    color = eyeColor,
                    topLeft = Offset(cx - eyeW / 2f, cy - eyeH / 2f),
                    size = Size(eyeW, eyeH),
                    cornerRadius = corner,
                )
                val pw = eyeW * 0.45f
                val ph = eyeH * 0.82f
                drawRoundRect(
                    color = Color(0xFF06232B),
                    topLeft = Offset(cx - pw / 2f, cy - ph / 2f),
                    size = Size(pw, ph),
                    cornerRadius = CornerRadius(pw / 2f, pw / 2f),
                )
            }
        }
    }
}

@Composable
private fun ColorSwatchRow(
    selected: Long,
    onSelect: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AvatarColors.swatches.forEach { swatch ->
            val isOn = (swatch.argb and 0xFFFFFFFFL) == (selected and 0xFFFFFFFFL)
            Box(
                modifier = Modifier
                    .size(if (isOn) 36.dp else 32.dp)
                    .clip(CircleShape)
                    .background(Color(swatch.argb))
                    .border(
                        width = if (isOn) 2.5.dp else 1.dp,
                        color = if (isOn) Color.White else Color(0xFF30363D),
                        shape = CircleShape,
                    )
                    .clickable { onSelect(swatch.argb) },
            )
        }
    }
}

