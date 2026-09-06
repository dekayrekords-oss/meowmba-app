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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.FilterChip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
@OptIn(ExperimentalLayoutApi::class)
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
    featureColor: Long = 0xFFE8D5B9L,
    onSelectFeatureColor: (Long) -> Unit = {},
    appTheme: AppTheme = AppTheme.COMPANION,
    onSelectTheme: (AppTheme) -> Unit = {},
    appearanceMode: AppearanceMode = AppearanceMode.DARK,
    onSelectAppearance: (AppearanceMode) -> Unit = {},
    preview: @Composable () -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("App theme", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTheme.entries.forEach { option ->
                    FilterChip(selected = appTheme == option,
                        onClick = { onSelectTheme(option) }, label = { Text(option.label) })
                }
            }
            Text("Professional adds a focused workspace and coordinated colors throughout the app.",
                style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppearanceMode.entries.forEach { option ->
                    FilterChip(selected = appearanceMode == option,
                        onClick = { onSelectAppearance(option) }, label = { Text(option.label) })
                }
            }
            Text("Live preview", style = MaterialTheme.typography.titleMedium)
            Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(24.dp))
                // Was hard-coded to the old dark-mode background, so the preview
                // stayed near-black even with Light appearance selected.
                .background(MaterialTheme.colorScheme.surfaceVariant)) { preview() }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Face style",
                color = MaterialTheme.colorScheme.onSurface,
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
            AvatarModelPicker(onSelectStyle)

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Eye color",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Iris color for Pearl and Godot.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(10.dp))
            ColorSwatchRow(selected = eyeColor, onSelect = onSelectEyeColor)

            if (style == AvatarStyle.PEARL) {
                Spacer(Modifier.height(18.dp))
                Text("Cat color", style = MaterialTheme.typography.titleMedium)
                Text("Ears, eyelids, nose, mouth and whiskers.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                ColorSwatchRow(selected = featureColor, onSelect = onSelectFeatureColor)
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Glow color",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Soft halo behind the face — shows over a transparent desk.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(10.dp))
            ColorSwatchRow(selected = glowColor, onSelect = onSelectGlowColor)

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Animations",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "All styles share every animation — only the face changes. Tap one to watch it play.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (animations.isEmpty()) {
                Text(
                    text = "No animations yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EyePreview(style = option, modifier = Modifier.size(72.dp, 48.dp))
        Spacer(modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(option.label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(
                option.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AnimationRow(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = name.replaceFirstChar { it.uppercase() },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * A thumbnail of the face in each style, so they can be told apart without
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
            AvatarStyle.SIGNAL -> {
                // Jarvis Orb swatch: bright nucleus + scattered parameter dots.
                val cyan = Color(0xFF5CE1FF)
                val r = size.minDimension * 0.42f
                val c = Offset(cx, cy)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(cyan.copy(alpha = 0.22f), Color.Transparent),
                        center = c,
                        radius = r * 1.2f,
                    ),
                    radius = r * 1.2f,
                    center = c,
                )
                drawCircle(cyan.copy(alpha = 0.30f), r, c,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.05f))
                // Deterministic mini-scatter (fixed seed math, not Random —
                // previews must not shimmer on every recomposition).
                for (i in 0 until 26) {
                    val fi = i.toFloat()
                    val px = cx + kotlin.math.cos(fi * 2.39996f) * r * (0.25f + (i % 5) * 0.14f)
                    val py = cy + kotlin.math.sin(fi * 2.39996f) * r * (0.25f + ((i * 3) % 5) * 0.14f)
                    drawCircle(
                        (if (i % 6 == 0) Color(0xFFA78BFA) else cyan).copy(alpha = 0.85f),
                        r * (0.035f + (i % 3) * 0.018f),
                        Offset(px, py),
                    )
                }
                drawCircle(Color.White, r * 0.10f, c)
            }
            AvatarStyle.MODEL_3D, AvatarStyle.PEARL, AvatarStyle.NEO -> drawEyeNeo(
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
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AvatarColors.swatches.forEach { swatch ->
            val isOn = (swatch.argb and 0xFFFFFFFFL) == (selected and 0xFFFFFFFFL)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = swatch.label + if (isOn) ", selected" else "" }
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
