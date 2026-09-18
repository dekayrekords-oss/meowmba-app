package com.example.meowmba.ui.companion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Surface

/**
 * Overlay the current avatar with a dispensed prop and let the user drag
 * (plus scale / rotate) into place. Persists via [onSave].
 */
@Composable
fun PropPlacementEditor(
    prop: GeneratedPropForm,
    avatar: GeneratedAvatarForm?,
    onSave: (GeneratedPropForm) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var anchorX by remember(prop.id) { mutableFloatStateOf(prop.anchorX) }
    var anchorY by remember(prop.id) { mutableFloatStateOf(prop.anchorY) }
    var scale by remember(prop.id) { mutableFloatStateOf(prop.attachScale) }
    var rotation by remember(prop.id) { mutableFloatStateOf(prop.attachRotation) }
    val avatarPainter = remember { GeneratedAvatarPainter() }
    val propPainter = remember { GeneratedPropPainter() }
    val draft = remember(prop, anchorX, anchorY, scale, rotation) {
        prop.copy(anchorX = anchorX, anchorY = anchorY, attachScale = scale, attachRotation = rotation)
    }

    Surface(
        modifier = modifier.fillMaxSize().testTag("prop-placement-editor"),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Place ${prop.name}", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Drag the prop onto your avatar. Scale and rotate if needed, then save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close placement editor")
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF1A2030), RoundedCornerShape(18.dp))
                    .testTag("prop-placement-canvas")
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val w = size.width.coerceAtLeast(1)
                            val h = size.height.coerceAtLeast(1)
                            val unit = minOf(w, h) * .42f
                            if (unit > 1f) {
                                anchorX = (anchorX + drag.x / unit).coerceIn(-.5f, .5f)
                                anchorY = (anchorY + drag.y / unit).coerceIn(-.5f, .5f)
                            }
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val placement = generatedAvatarPlacement(size.width, size.height, LumenFrame(), scale = 1.35f)
                    if (avatar != null) {
                        avatarPainter.draw(this, avatar, LumenFrame(), 0f, placement.center, placement.unit)
                    } else {
                        // Soft placeholder so prop placement still works without a generated form.
                        drawCircle(Color(0xFFE8D5B9).copy(alpha = .9f), placement.unit * .38f, placement.center)
                        drawCircle(Color(0xFF182032), placement.unit * .06f, placement.center + Offset(-placement.unit * .12f, -placement.unit * .06f))
                        drawCircle(Color(0xFF182032), placement.unit * .06f, placement.center + Offset(placement.unit * .12f, -placement.unit * .06f))
                    }
                    val propCenter = Offset(
                        placement.center.x + draft.anchorX * placement.unit,
                        placement.center.y + draft.anchorY * placement.unit,
                    )
                    propPainter.draw(this, draft, propCenter, placement.unit * .55f)
                }
            }
            Text("Scale ${"%.2f".format(scale)}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = scale,
                onValueChange = { scale = it },
                valueRange = .3f..2.5f,
                modifier = Modifier.testTag("prop-placement-scale"),
            )
            Text("Rotation ${rotation.toInt()}°", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = rotation,
                onValueChange = { rotation = it },
                valueRange = -180f..180f,
                modifier = Modifier.testTag("prop-placement-rotation"),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val (x, y) = GeneratedPropForms.defaultAttach(prop.category, prop.name, prop.description)
                        anchorX = x; anchorY = y; scale = if (prop.category == GeneratedPropCategory.WEARABLE) .85f else 1f; rotation = 0f
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Reset default") }
                Button(
                    onClick = {
                        onSave(GeneratedPropForms.withPlacement(prop, anchorX, anchorY, scale, rotation))
                    },
                    modifier = Modifier.weight(1f).testTag("prop-placement-save"),
                ) { Text("Save placement") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
