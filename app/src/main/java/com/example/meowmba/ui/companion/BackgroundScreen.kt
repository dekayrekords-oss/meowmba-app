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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun BackgroundScreen(
    selection: BackgroundSelection,
    onSelect: (BackgroundSelection) -> Unit,
    onClose: () -> Unit,
    /** Whether the Weather page's ambient scene is on; it draws in FRONT of all of this. */
    ambientWeatherOn: Boolean = false,
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
            onSelect(selection.copy(kind = BackgroundKind.IMAGE, value = uri.toString()))
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D1117)) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Background",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            Text(
                text = "What sits behind the companion.",
                color = Color(0xFF8B949E),
                fontSize = 13.sp,
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("Live")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceTile(
                    label = "None",
                    selected = selection.kind == BackgroundKind.NONE,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(selection.copy(kind = BackgroundKind.NONE, value = "")) },
                ) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF0D1117)))
                }
                ChoiceTile(
                    label = "Live weather",
                    selected = selection.kind == BackgroundKind.LIVE_WEATHER,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSelect(selection.copy(kind = BackgroundKind.LIVE_WEATHER, value = ""))
                    },
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF2E6BA8), Color(0xFF123049)),
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) { Text("🌤️", fontSize = 26.sp) }
                }
            }
            Text(
                text = "Live weather follows the real sky and the time of day.",
                color = Color(0xFF6E7681),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (ambientWeatherOn) {
                // Otherwise picking None looks broken: the weather is still there, because
                // it is a different layer owned by a different page.
                Text(
                    text = "\"Always show weather\" is on, so the weather animation plays " +
                        "in front of whatever you pick here — including None. Turn it off " +
                        "on the Weather page.",
                    color = Color(0xFFD29922),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Animations")

            // Plain rows, not a LazyVerticalGrid. A lazy grid inside a scrolling column has
            // to be given a fixed height, and every guess at that height was wrong by
            // exactly enough to clip the last row's labels.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BackgroundAnimation.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { animation ->
                            val isSelected = selection.kind == BackgroundKind.ANIMATION &&
                                selection.value == animation.id
                            ChoiceTile(
                                label = animation.label,
                                selected = isSelected,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onSelect(
                                        selection.copy(
                                            kind = BackgroundKind.ANIMATION,
                                            value = animation.id,
                                        )
                                    )
                                },
                            ) {
                                val condition = animation.condition
                                if (condition == null) {
                                    AnimationThumbnail(animation, Modifier.fillMaxSize())
                                } else {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    weatherSwatch(animation.id)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(condition.emoji, fontSize = 24.sp) }
                                }
                            }
                        }
                        // Keeps a short last row's tiles the same size as a full row's.
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Image")

            Button(
                onClick = { picker.launch(arrayOf("image/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF58A6FF))
                Spacer(Modifier.height(0.dp))
                Text(
                    text = if (selection.kind == BackgroundKind.IMAGE) {
                        "  Choose a different picture"
                    } else {
                        "  Choose a picture"
                    },
                    color = Color.White,
                )
            }

            if (selection.kind == BackgroundKind.IMAGE && selection.value.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, Color(0xFF58A6FF), RoundedCornerShape(16.dp)),
                ) {
                    AppBackground(
                        selection = selection.copy(dimPercent = 0),
                        weather = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Dim")
            Text(
                text = "Darkens whatever is behind the companion so the face stays readable.",
                color = Color(0xFF6E7681),
                fontSize = 11.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = selection.dimPercent.toFloat(),
                    onValueChange = {
                        onSelect(selection.copy(dimPercent = it.toInt().coerceIn(0, 90)))
                    },
                    valueRange = 0f..90f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF58A6FF),
                        activeTrackColor = Color(0xFF58A6FF),
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.padding(horizontal = 6.dp))
                Text("${selection.dimPercent}%", color = Color.White, fontSize = 13.sp)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Color(0xFF8B949E),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
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
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161B22))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Color(0xFF58A6FF) else Color(0xFF30363D),
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
                        .background(Color(0xFF58A6FF))
                        .padding(2.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.Black,
                        modifier = Modifier.padding(1.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFF8B949E),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
