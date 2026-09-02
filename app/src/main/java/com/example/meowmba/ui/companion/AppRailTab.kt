package com.example.meowmba.ui.companion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How many extra apps the right-edge tab holds. Two more pins already sit on
 * the face itself, so a pin list of six fills both surfaces.
 */
const val RAIL_APP_LIMIT = 4

/** Horizontal travel that counts as a swipe on the tab (px). */
private const val RAIL_SWIPE_PX = 36f

/**
 * Right-edge app tab on the companion face: up to [RAIL_APP_LIMIT] pinned apps
 * in a vertical strip down the right side, collapsed to a grip when closed.
 *
 * Both directions are a LEFT swipe, as asked: a leftward drag pulls the strip
 * out of the edge, and the next leftward drag puts it back. The grip is also
 * tappable, because a closed tab is too small a target to be swipe-only.
 *
 * The strip owns the horizontal drags that start on it (it consumes them), so
 * the face's own swipe-left-to-browser never fires from the tab area.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRailTab(
    apps: List<String>,
    onLaunch: (String) -> Unit,
    onAdd: () -> Unit,
    onUnpin: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.pointerInput(open) {
            var travelled = 0f
            detectHorizontalDragGestures(
                onDragStart = { travelled = 0f },
                onDragEnd = {
                    if (travelled < -RAIL_SWIPE_PX) open = !open
                    travelled = 0f
                },
                onDragCancel = { travelled = 0f },
            ) { change, dragAmount ->
                change.consume()
                travelled += dragAmount
            }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = open,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
        ) {
            Column(
                modifier = Modifier
                    .background(
                        color = Color(0xCC0D1117),
                        shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0x3358A6FF),
                        shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (apps.isEmpty()) {
                    Text(
                        "Long-press an app\nin the drawer\nto add it here",
                        color = Color(0xFF6E7681),
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(64.dp)
                            .padding(bottom = 10.dp),
                    )
                }
                apps.forEach { pkg ->
                    Box(
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { onLaunch(pkg) },
                                onLongClick = { onUnpin(pkg) },
                            )
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        DrawerIcon(packageName = pkg, size = 40.dp, label = "app shortcut")
                    }
                }
                if (apps.size < RAIL_APP_LIMIT) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x1F58A6FF))
                            .combinedClickable(onClick = onAdd),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add an app to the tab",
                            tint = Color(0xFF58A6FF),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // The grip: tap it, or swipe left anywhere on the tab.
        Box(
            modifier = Modifier
                .size(width = 22.dp, height = if (open) 96.dp else 72.dp)
                .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                .background(Color(0x990D1117))
                .combinedClickable(onClick = { open = !open })
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(width = 3.dp, height = 14.dp)
                            .clip(CircleShape)
                            .background(Color(0xAA58A6FF))
                    )
                }
            }
        }
    }
}
