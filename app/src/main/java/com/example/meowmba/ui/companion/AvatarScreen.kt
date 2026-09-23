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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.example.meowmba.ui.theme.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
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
import com.example.meowmba.ui.theme.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
    animal: AvatarAnimal = AvatarAnimal.NONE,
    onSelectAnimal: (AvatarAnimal) -> Unit = {},
    expressionIntensity: ExpressionIntensity = ExpressionIntensity.NATURAL,
    onExpressionIntensity: (ExpressionIntensity) -> Unit = {},
    playfulness: Playfulness = Playfulness.BALANCED,
    onPlayfulness: (Playfulness) -> Unit = {},
    diagnostics: @Composable () -> Unit = {},
    morphControls: @Composable () -> Unit = {},
    homeControls: @Composable () -> Unit = {},
    preview: @Composable () -> Unit = {},
    avatarScale: Float = 2f,
    onAvatarScale: (Float) -> Unit = {},
    habitatMode: Boolean = false,
    onHabitatMode: (Boolean) -> Unit = {},
    generatedAvatarForm: GeneratedAvatarForm? = null,
    savedGeneratedForms: List<GeneratedAvatarForm> = emptyList(),
    avatarFormGenerating: Boolean = false,
    avatarFormMessage: String? = null,
    avatarFormError: Boolean = false,
    onGenerateForm: (String, InventReasonLevel) -> Unit = { _, _ -> },
    onRefineForm: (String, InventReasonLevel) -> Unit = { _, _ -> },
    onCancelGenerateForm: () -> Unit = {},
    onSelectGeneratedForm: (String) -> Unit = {},
    onDeleteGeneratedForm: (String) -> Unit = {},
) {
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("New") }
    var expanded by remember(search, category) { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf("Look") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var feelings by rememberSaveable { mutableStateOf(false) }
    val choices = remember(animations, search, category) { avatarAnimationChoices(animations, category, search) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = AvatarSetupTokens.page)) {
            Row(Modifier.fillMaxWidth().padding(top = AvatarSetupTokens.compact), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your avatar", style = MaterialTheme.typography.headlineSmall)
                    Text("Appearance, behavior & play", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close avatar setup") }
            }
            Spacer(Modifier.height(AvatarSetupTokens.item))
            Box(Modifier.fillMaxWidth().aspectRatio(1.7f).clip(RoundedCornerShape(AvatarSetupTokens.corner))
                .background(MaterialTheme.colorScheme.surfaceVariant)) { preview() }
            Text(generatedAvatarForm?.let { "Lumen · ${it.name}" }
                ?: if (style.isLumen && style != AvatarStyle.FULL_FLY) "${style.label} · ${if (style == AvatarStyle.LUMEN_CAT) AvatarAnimal.CAT.label else animal.label}" else style.label,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = AvatarSetupTokens.item))
            Text("Selected · changes apply immediately", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.padding(top = AvatarSetupTokens.section), horizontalArrangement = Arrangement.spacedBy(AvatarSetupTokens.compact)) {
                listOf("Look", "Space", "Behavior", "Animations").forEach { option ->
                    FilterChip(selected = tab == option, onClick = { tab = option }, label = { Text(option) })
                }
            }
            if (tab == "Space") {
                homeControls()
                SetupSection("Size & play space")
                Text("Keep a large avatar on your chosen background, or let it roam a game environment.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SetupModeChooser(habitatMode, onHabitatMode)
                SetupSizeControl(avatarScale, onAvatarScale)
                Text("Size applies in both modes. Use the Background card to choose a scene. On the avatar screen, open Play to interact or manage optional care.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp))
            }
            if (tab == "Look") {
                Spacer(Modifier.height(AvatarSetupTokens.item))
                SetupSection("Choose an avatar")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarStyle.entries.forEach { option ->
                    FilterChip(selected = generatedAvatarForm == null && option == style,
                        onClick = { onSelectStyle(option) }, label = { Text(option.label) })
                }
            }
            Text(style.description, style = MaterialTheme.typography.bodySmall)

            if (style == AvatarStyle.LUMEN) {
                  Text("Choose your character", style = MaterialTheme.typography.titleMedium)
                  Text("Pick a character for Lumen, including Clippy and the classic wizard. Your choice stays saved.",
                      style = MaterialTheme.typography.bodySmall)
                  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarAnimal.entries.forEach { option ->
                        val shown = if (style == AvatarStyle.LUMEN_CAT) AvatarAnimal.CAT else animal
                        FilterChip(selected = generatedAvatarForm == null && shown == option, onClick = { onSelectAnimal(option) },
                            label = { Text(option.label) })
                    }
                  }
                val character = LumenPersonalities.forAnimal(animal)
                Text(character.title, style = MaterialTheme.typography.titleMedium)
                Text(character.voice, style = MaterialTheme.typography.bodySmall)
                Text("Signature scenes: " + character.signatures.mapNotNull { AvatarPerformanceCatalog.resolve(it)?.title }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall)
                if (generatedAvatarForm == null && style == AvatarStyle.LUMEN && (animal == AvatarAnimal.CLIPPY || animal == AvatarAnimal.WIZARD)) {
                    Text(if (animal == AvatarAnimal.CLIPPY)
                        "A springy silver paperclip with expressive eyes. Say ‘turn into Clippy’ to switch here any time."
                    else "A starry hat, flowing beard and magic wand. Say ‘become the wizard’ to switch here any time.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("Tap or stroke the avatar to interact. Use the video button to enable or disable the camera for face following; swipe down to open the card menu.",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onSelectStyle(AvatarStyle.SIGNAL) }) { Text("Turn into Jarvis orb") }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(18.dp))
            if (style != AvatarStyle.FULL_FLY && generatedAvatarForm == null) {
            SetupSection("Colors")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactColorChooser(selected = eyeColor, onSelect = onSelectEyeColor, label = "Eye color")

            if ((style == AvatarStyle.PEARL || style.isLumen) &&
                !(style == AvatarStyle.LUMEN && (animal == AvatarAnimal.CLIPPY || animal == AvatarAnimal.WIZARD))) {
                CompactColorChooser(selected = featureColor, onSelect = onSelectFeatureColor, label = "Feature color")
            }

            CompactColorChooser(selected = glowColor, onSelect = onSelectGlowColor, label = "Glow color")
            }
            Spacer(modifier = Modifier.height(18.dp))
            }
            } // Look
            if (tab == "Behavior") {
                morphControls()
                SetupSection("Movement & personality")
            Text("Expression strength", style = MaterialTheme.typography.titleMedium)
            Text("Controls how strongly the face shows an expression.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpressionIntensity.entries.forEach { option ->
                    FilterChip(selected = expressionIntensity == option, onClick = { onExpressionIntensity(option) }, label = { Text(option.label) })
                }
            }
            Text("Idle playfulness", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Playfulness.entries.forEach { option ->
                    FilterChip(selected = playfulness == option, onClick = { onPlayfulness(option) }, label = { Text(option.label) })
                }
            }
            Text("Quiet keeps small idle movements. Balanced and Lively add occasional scenes. Requested performances remain available.", style = MaterialTheme.typography.bodySmall)
                if (style.isLumen && style != AvatarStyle.FULL_FLY) {
                TextButton(onClick = { feelings = !feelings }) { Text(if (feelings) "Hide expression previews" else "Preview expressions") }
                if (feelings) {
                Text("Emotion rehearsal", style = MaterialTheme.typography.titleMedium)
                Text("Watch a feeling arrive, hold and settle. Conversation and touch also bring these expressions to life.",
                    style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompanionEmotion.entries.forEach { feeling ->
                        FilterChip(selected = false, onClick = { onPlayAnimation("emotion:${feeling.name}") },
                            label = { Text(feeling.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                }
                }
            } // Behavior
            if (tab == "Animations") {
            Text(
                text = "Animations",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Original performances adapt to each face. Lumen adds floating props and one-eye winks. Tap one to watch, or ask ‘play Warm Hello animation’.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(value = search, onValueChange = { search = it },
                label = { Text("Search all animations") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("New") + AvatarPerformanceCatalog.entries.map { it.category }.distinct() + "More").forEach { group ->
                    FilterChip(selected = category == group && search.isBlank(), onClick = { category = group; search = "" }, label = { Text(group) })
                }
            }
            TextButton(onClick = { onPlayAnimation("surprise me") }) { Text("Surprise me • a different performance") }

            if (choices.isEmpty()) {
                Text(
                    text = if (animations.isEmpty()) "No animations yet." else "No matches. Try another name or category.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                (if (expanded) choices else choices.take(12)).forEach { name ->
                    AnimationRow(name = name, onClick = { onPlayAnimation(name) })
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (choices.size > 12) TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Show fewer" else "Show all ${choices.size} animations")
                }
            }

            }
            if (tab == "Look") {
                SetupSection("App appearance")
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
            TextButton(onClick = { advanced = !advanced }, modifier = Modifier.padding(top = AvatarSetupTokens.section)) {
                Text(if (advanced) "Hide advanced settings" else "Advanced settings")
            }
            if (advanced) {
                FlipCoverSetupCard()
                SetupSection("Diagnostics")
                diagnostics()
            }
            }
            Spacer(Modifier.height(AvatarSetupTokens.section))
        }
    }
}

@Composable
private fun CompactColorChooser(selected: Long, onSelect: (Long) -> Unit, label: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Color(selected)))
        Spacer(Modifier.size(8.dp))
        Text("Choose $label")
    }
    if (open) androidx.compose.material3.AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(label) },
        text = { ColorSwatchRow(selected = selected, onSelect = { onSelect(it); open = false }, label = label) },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Done") } },
    )
}

internal fun avatarAnimationChoices(names: List<String>, category: String, query: String): List<String> {
    val term = query.trim().lowercase(java.util.Locale.ROOT)
    return names.distinct().filter { name ->
        val entry = AvatarPerformanceCatalog.resolve(name)?.takeIf { it.title == name }
        if (term.isNotEmpty()) name.lowercase(java.util.Locale.ROOT).contains(term) ||
            entry?.aliases?.any { it.lowercase(java.util.Locale.ROOT).contains(term) } == true
        else when (category) {
            "New" -> entry != null
            "More" -> entry == null
            else -> entry?.category == category
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
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
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
    val lumenPainter = remember { LumenPainter() }
    val flyPainter = remember { FullFlyPainter() }
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val eyeW = size.width * 0.52f
        val eyeH = size.height * 0.62f

        when (style) {
            AvatarStyle.DESK -> {
                drawRect(Color(0xFF12232A))
                drawRect(Color(0xFFB4875C), topLeft = Offset(0f, cy), size = androidx.compose.ui.geometry.Size(size.width, size.height / 3))
                drawCircle(Color(0xFFF4EEDB), size.minDimension * .16f, Offset(cx, cy - eyeH * .2f))
            }
            AvatarStyle.FULL_FLY -> flyPainter.draw(this, LumenFrame(), FullFlyPose(scale = 1.6f))
            AvatarStyle.LUMEN, AvatarStyle.LUMEN_CAT -> lumenPainter.draw(this,
                LumenFrame(cat = if (style == AvatarStyle.LUMEN_CAT) 1f else 0f), Color(0xFF4DBCB7), Color(0xFF4DBCB7),
                catFeatures = style == AvatarStyle.LUMEN_CAT, featureColor = Color(0xFFE8D5B9))
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
            AvatarStyle.PEARL, AvatarStyle.NEO -> drawEyeNeo(
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
@OptIn(ExperimentalLayoutApi::class)
private fun ColorSwatchRow(
    selected: Long,
    onSelect: (Long) -> Unit,
    label: String,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AvatarColors.swatches.forEach { swatch ->
            val isOn = (swatch.argb and 0xFFFFFFFFL) == (selected and 0xFFFFFFFFL)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "$label ${swatch.label}" + if (isOn) ", selected" else "" }
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
