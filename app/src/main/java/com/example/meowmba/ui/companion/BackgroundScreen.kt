package com.example.meowmba.ui.companion

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import com.example.meowmba.ui.theme.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.example.meowmba.ui.theme.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.example.meowmba.ui.theme.FilterChip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.background.BackgroundAnimation
import com.example.meowmba.core.background.BackgroundKind
import com.example.meowmba.core.background.BackgroundSelection

/**
 * The Background card: what sits behind the companion.
 *
 * Three kinds of answer, in the order people want them: nothing, a live/animated scene, or
 * one of their own pictures. Everything is applied the instant it is tapped and the screen
 * stays open — choosing a wallpaper is a comparison task, and a save button between every
 * comparison makes it a chore.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun BackgroundScreen(
    selection: BackgroundSelection,
    onSelect: (BackgroundSelection) -> Unit,
    onClose: () -> Unit,
    /** Whether the Weather page's ambient scene is on; it draws in FRONT of all of this. */
    ambientWeatherOn: Boolean = false,
    avatarScale: Float = 2f,
    onAvatarScale: (Float) -> Unit = {},
    habitatMode: Boolean = false,
    onHabitatMode: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current

    // OpenDocument, not GetContent: only OpenDocument hands back a URI we are allowed to
    // persist, and a background has to survive a reboot.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onHabitatMode(false)
            onSelect(selection.copy(kind = BackgroundKind.IMAGE, value = uri.toString()))
        }
    }

    var tab by rememberSaveable { mutableStateOf(if (habitatMode) "Habitats" else if (selection.kind == BackgroundKind.ANIMATION || selection.kind == BackgroundKind.LIVE_WEATHER) "Animated" else "Static") }
    val selectedName = when (selection.kind) {
        BackgroundKind.NONE -> "Plain background"
        BackgroundKind.IMAGE -> "Your picture"
        BackgroundKind.LIVE_WEATHER -> "Live weather"
        BackgroundKind.ANIMATION -> selection.animation?.label ?: "Background"
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = AvatarSetupTokens.page)) {
            Row(Modifier.fillMaxWidth().padding(top = AvatarSetupTokens.compact), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Set the scene", style = MaterialTheme.typography.headlineSmall)
                    Text("Backgrounds & habitats", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close backgrounds") }
            }
            SetupSection(selectedName, if (habitatMode) "Play habitat selected" else "Backdrop selected")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AvatarSetupTokens.compact)) {
                listOf("Static", "Animated", "Habitats").forEach { option ->
                    FilterChip(selected = tab == option, onClick = { tab = option }, label = { Text(option) })
                }
            }
            when (tab) {
                "Static" -> {
                    SetupSection("Keep it simple", "A quiet space or a picture of your own.")
                    ChoiceTile("Sunroom · photographic", selection.value == "asset:///backgrounds/conservatory.png" && !habitatMode,
                        onClick = { onHabitatMode(false); onSelect(BackgroundSelection(BackgroundKind.IMAGE, "asset:///backgrounds/conservatory.png", 10)) },
                        modifier = Modifier.fillMaxWidth()) {
                        AppBackground(BackgroundSelection(BackgroundKind.IMAGE, "asset:///backgrounds/conservatory.png", 0), null)
                    }
                    ChoiceTile("Plain background", selection.kind == BackgroundKind.NONE && !habitatMode,
                        onClick = { onHabitatMode(false); onSelect(selection.copy(kind = BackgroundKind.NONE, value = "")) },
                        modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                            Text("Plain", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Button(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth().padding(top = AvatarSetupTokens.item)) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Text(if (selection.kind == BackgroundKind.IMAGE) "Choose another picture" else "Choose a picture", modifier = Modifier.padding(start = AvatarSetupTokens.compact))
                    }
                    if (selection.kind == BackgroundKind.IMAGE && selection.value.isNotBlank()) {
                        Box(Modifier.fillMaxWidth().padding(top = AvatarSetupTokens.item).aspectRatio(1.6f).clip(RoundedCornerShape(AvatarSetupTokens.corner))) {
                            AppBackground(selection.copy(dimPercent = 0), weather = null, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                "Animated" -> {
                    SetupSection("A little atmosphere", "Scenery moves behind your full-size companion.")
                    ChoiceTile("Live weather", selection.kind == BackgroundKind.LIVE_WEATHER && !habitatMode,
                        onClick = { onHabitatMode(false); onSelect(selection.copy(kind = BackgroundKind.LIVE_WEATHER, value = "")) },
                        modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                            Text("Live sky", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Text("Live weather follows the real sky and time of day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = AvatarSetupTokens.item))
                    BackgroundChoiceGrid(BackgroundAnimation.entries.filter { !it.isPetEnvironment }, selection) { animation ->
                        onHabitatMode(false)
                        onSelect(selection.copy(kind = BackgroundKind.ANIMATION, value = animation.id))
                    }
                }
                else -> {
                    SetupSection("Cartoon worlds", "Tap the spring toys in the three new rooms to surprise your companion. Care is optional.")
                    BackgroundChoiceGrid(listOf(
                        BackgroundAnimation.CARTOON_SNACK_LAB,
                        BackgroundAnimation.CARTOON_LAUNDROMAT,
                        BackgroundAnimation.CARTOON_DINER,
                        BackgroundAnimation.CARTOON_NOOK,
                        BackgroundAnimation.CARTOON_GALLEY,
                    ), selection) { animation ->
                        onHabitatMode(true)
                        onSelect(selection.copy(kind = BackgroundKind.ANIMATION, value = animation.id))
                    }
                    SetupSection("More places to play", "The original rooms, gardens and escapes are here too.")
                    BackgroundChoiceGrid(BackgroundAnimation.entries.filter { it.isPetEnvironment && !it.isCartoonEnvironment }, selection) { animation ->
                        onHabitatMode(true)
                        onSelect(selection.copy(kind = BackgroundKind.ANIMATION, value = animation.id))
                    }
                }
            }
            if (ambientWeatherOn) {
                SetupSection("Weather overlay is on", "Always show weather adds a separate animation over your choice. Change it on the Weather page.")
            }
            if (!habitatMode) {
            SetupSection("Background dimming", "Make the scenery softer so your companion is easy to see.")
            Text("${selection.dimPercent}%", style = MaterialTheme.typography.titleMedium)
            Slider(value = selection.dimPercent.toFloat().coerceIn(0f, 90f),
                onValueChange = { onSelect(selection.copy(dimPercent = it.toInt().coerceIn(0, 90))) }, valueRange = 0f..90f,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Background dimming" })
            }
            SetupSizeControl(avatarScale, onAvatarScale)
            Spacer(Modifier.height(AvatarSetupTokens.section))
        }
    }
}

@Composable
private fun BackgroundChoiceGrid(animations: List<BackgroundAnimation>, selection: BackgroundSelection, onChoose: (BackgroundAnimation) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth.value / (132f * fontScale.coerceAtLeast(1f))).toInt().coerceIn(1, 4)
        Column(verticalArrangement = Arrangement.spacedBy(AvatarSetupTokens.item)) {
            animations.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(AvatarSetupTokens.item)) {
                    row.forEach { animation ->
                        ChoiceTile(animation.label, selection.kind == BackgroundKind.ANIMATION && selection.value == animation.id,
                            onClick = { onChoose(animation) }, modifier = Modifier.weight(1f)) {
                            if (animation.condition == null) AnimationThumbnail(animation, Modifier.fillMaxSize())
                            else Box(Modifier.fillMaxSize().background(Brush.verticalGradient(weatherSwatch(animation.id))), contentAlignment = Alignment.Center) {
                                Text(animation.condition.emoji, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A preview square with its name under it, and a tick when it is the active choice. */
@Composable
private fun ChoiceTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.semantics { this.selected = selected }.clickable(role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.65f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(14.dp),
                ),
        ) {
            preview()
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(2.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(1.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
