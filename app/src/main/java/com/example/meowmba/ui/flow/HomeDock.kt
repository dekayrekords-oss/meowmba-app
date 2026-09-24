package com.example.meowmba.ui.flow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.ui.companion.CompanionViewModel
import com.example.meowmba.ui.companion.DrawerApps
import com.example.meowmba.ui.companion.DrawerIcon
import kotlin.math.abs
import kotlin.math.roundToInt

/** Colours for the dock on Flow's paper or the Desk's dark room. */
data class DockStyle(val background: Color, val border: Color, val handle: Color, val empty: Color) {
    companion object {
        fun flow(c: FlowColors) = DockStyle(c.paper, c.ink, c.ink2, c.ink3)
        /** Same blue grip lines as the Lumen screen's edge tab. */
        val DESK = DockStyle(Color(0xE6193039), Color(0x5558A6FF), Color(0xAA58A6FF), Color(0xFF4E6B70))
    }
}

/**
 * Edge dock with six app/tool slots on Flow and the Desk, behaving like the Lumen screen's edge tab
 * (owner, 2026-09-23): it rests hidden as a slim grip on the screen edge. Tap the grip or swipe
 * inward to slide it out; swipe outward or tap the grip again to tuck it away. Drag it up or down to
 * move it; drag it most of the way across the screen to move it to the other edge. Tap a slot to open
 * it; tap an empty slot or hold a filled one to choose what goes there.
 */
@Composable
fun HomeDock(vm: CompanionViewModel, style: DockStyle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val st by vm.homeShelves.state.collectAsState()
    var picking by remember { mutableStateOf<Int?>(null) }
    val open = !st.dockCollapsed
    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val density = LocalDensity.current
        // The grip never changes size or position when the dock opens or closes (owner: "the 3 bars
        // jump all over the side of the screen"): its position is a fraction of the grip's own travel,
        // and the panel is placed beside it, centred on the grip and kept on screen.
        val gripW = 22.dp
        val gripH = 72.dp
        val heightPx = with(density) { maxHeight.toPx() }
        val gripHpx = with(density) { gripH.toPx() }
        val gripWpx = with(density) { gripW.toPx() }
        val travel = (heightPx - gripHpx).coerceAtLeast(0f)
        val widthPx = with(density) { maxWidth.toPx() }
        val swipePx = with(density) { 36.dp.toPx() }
        var y by remember(st.dockY) { mutableFloatStateOf(st.dockY) }
        var dx by remember { mutableFloatStateOf(0f) }
        var panelHpx by remember { mutableIntStateOf(with(density) { (HomeShelves.DOCK_SLOTS * 50 + 20).dp.roundToPx() }) }
        val right = st.dockRight
        val panelShape = if (right) RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp) else RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)
        val gripShape = if (right) RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp) else RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)
        val gripTop = { travel * y }
        val panelTop = { (gripTop() + gripHpx / 2 - panelHpx / 2f).coerceIn(0f, (heightPx - panelHpx).coerceAtLeast(0f)) }

        // Drag anywhere on the grip or the panel (even starting on an icon): up/down moves the dock,
        // a short swipe inward opens it, outward tucks it away, most of the way across switches edges.
        val drag = Modifier.pointerInput(right, open, travel) {
                    // Initial pass: a drag that starts on an icon still moves or tucks the dock (icons only
                    // get taps). Nothing is consumed until the finger passes the touch slop.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var moved = Offset.Zero
                        var dragging = false
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            val d = c.positionChange()
                            moved += d
                            if (!dragging && moved.getDistance() > viewConfiguration.touchSlop) dragging = true
                            if (dragging) {
                                c.consume()
                                if (travel > 0) y = (y + d.y / travel).coerceIn(0f, 1f)
                                dx += d.x
                            }
                        }
                        if (dragging) {
                            // Inward = toward the middle of the screen.
                            val inward = if (right) -dx else dx
                            val sideways = abs(dx) > abs(moved.y)
                            when {
                                inward > widthPx / 2 -> vm.homeShelves.moveDock(!right, y)
                                sideways && inward > swipePx && !open -> { vm.homeShelves.moveDock(right, y); vm.homeShelves.setDockCollapsed(false) }
                                sideways && inward < -swipePx && open -> { vm.homeShelves.moveDock(right, y); vm.homeShelves.setDockCollapsed(true) }
                                else -> vm.homeShelves.moveDock(right, y)
                            }
                        }
                        dx = 0f
                    }
                }
        val edge = if (right) Alignment.TopEnd else Alignment.TopStart

        // The panel slides out beside the grip.
        AnimatedVisibility(visible = open,
            modifier = Modifier.align(edge).offset {
                IntOffset(dx.roundToInt() + (if (right) -gripWpx else gripWpx).roundToInt(), panelTop().roundToInt())
            },
            enter = fadeIn() + slideInHorizontally { if (right) it else -it },
            exit = fadeOut() + slideOutHorizontally { if (right) it else -it }) {
            Column(
                Modifier.onSizeChanged { panelHpx = it.height }
                    .background(style.background, panelShape).border(1.dp, style.border, panelShape)
                    .then(drag)
                    .padding(horizontal = 8.dp, vertical = 10.dp).testTag("home_dock_panel"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                st.dock.forEachIndexed { i, key -> DockSlot(key, style, i,
                    onOpen = { if (key == null) picking = i else vm.openShelfItem(context, key) },
                    onEdit = { picking = i }) }
            }
        }

        // The grip: always the same size, at the same place, open or closed.
        Box(
            Modifier.align(edge)
                .offset { IntOffset(dx.roundToInt(), gripTop().roundToInt()) }
                .size(width = gripW, height = gripH).clip(gripShape)
                .background(style.background.copy(alpha = 0.85f))
                .then(drag)
                .clickable(onClickLabel = if (open) "Tuck the dock away" else "Open the dock") { vm.homeShelves.setDockCollapsed(open) }
                .semantics { contentDescription = "App dock. Tap or swipe to " + (if (open) "tuck it away" else "open it") + ". Drag to move." }
                .testTag("home_dock_handle"),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { Box(Modifier.size(width = 3.dp, height = 14.dp).clip(CircleShape).background(style.handle)) }
            }
        }
    }
    picking?.let { slot ->
        ShelfPicker(
            title = "Dock slot ${slot + 1}",
            canRemove = st.dock.getOrNull(slot) != null,
            onPick = { key -> vm.homeShelves.setDockSlot(slot, key); picking = null },
            onDismiss = { picking = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockSlot(key: String?, style: DockStyle, index: Int, onOpen: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val label = key?.let { shelfLabel(context, it) } ?: "Empty slot"
    Box(
        Modifier.padding(vertical = 3.dp).size(44.dp).clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClickLabel = if (key == null) "Choose an app" else "Open $label", onClick = onOpen,
                onLongClickLabel = "Change this slot", onLongClick = onEdit)
            .semantics { contentDescription = if (key == null) "Empty dock slot ${index + 1}. Tap to choose" else "$label. Hold to change" }
            .testTag("home_dock_slot_$index"),
        contentAlignment = Alignment.Center,
    ) {
        if (key == null) Box(Modifier.size(36.dp).border(1.5.dp, style.empty, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Text("+", color = style.empty, fontSize = 20.sp)
        } else ShelfIcon(key, 40.dp)
    }
}

/** An app's real icon, or a Meow tool's symbol. */
@Composable
fun ShelfIcon(key: String, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    if (HomeShelves.isApp(key)) DrawerIcon(HomeShelves.packageOf(key), size, shelfLabel(context, key))
    else Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2F5E5A)), contentAlignment = Alignment.Center) {
        Icon(toolIcon(HomeShelves.toolOf(key)), contentDescription = null, tint = Color(0xFFF4EEDB), modifier = Modifier.size(size * 0.55f))
    }
}

fun shelfLabel(context: android.content.Context, key: String): String =
    if (HomeShelves.isApp(key)) {
        val pkg = HomeShelves.packageOf(key)
        DrawerApps.snapshot(context).firstOrNull { it.packageName == pkg }?.label
            ?: runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg.substringAfterLast('.'))
    } else HomeShelves.toolOf(key)

private fun toolIcon(name: String): ImageVector = when (name) {
    "Projects" -> Icons.Default.Folder; "Notes" -> Icons.Default.Book; "Weather" -> Icons.Default.WbSunny
    "Files" -> Icons.Default.FolderOpen; "Memory" -> Icons.Default.Psychology; "Computer" -> Icons.Default.Computer
    "Agents" -> Icons.Default.SmartToy; "Tools" -> Icons.Default.Build; "Brain" -> Icons.Default.Hub
    "Privacy" -> Icons.Default.Shield; "Settings" -> Icons.Default.Settings; "Chat" -> Icons.Default.Chat
    "Apps" -> Icons.Default.Apps; "Browser" -> Icons.Default.Language; "Auto" -> Icons.Default.DirectionsCar
    else -> Icons.Default.Star
}

/** Choose a Meow tool or any installed app; optional "Remove". Returns the key (null = remove). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfPicker(title: String, canRemove: Boolean, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps = remember { DrawerApps.snapshot(context).ifEmpty { DrawerApps.loadFast(context) } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("shelf_picker")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (canRemove) TextButton(onClick = { onPick(null) }) { Text("Remove") }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("Search apps and Meow tools") }, singleLine = true)
            val q = query.trim().lowercase()
            val tools = HomeShelves.TOOLS.filter { q.isEmpty() || it.lowercase().contains(q) }.map(HomeShelves::tool)
            val appKeys = apps.filter { q.isEmpty() || it.label.lowercase().contains(q) }.map { HomeShelves.app(it.packageName) }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                if (tools.isNotEmpty()) item { Text("Meow tools", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 6.dp)) }
                items(tools) { k -> PickRow(k, onPick) }
                if (appKeys.isNotEmpty()) item { Text("Apps", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 6.dp)) }
                items(appKeys) { k -> PickRow(k, onPick) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PickRow(key: String, onPick: (String?) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().clickable { onPick(key) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ShelfIcon(key, 36.dp)
        Text(shelfLabel(context, key), Modifier.padding(start = 14.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

/** A Desk drawer opened: its apps and tools as a grid; tap to open, hold to remove, + to add. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeskDrawerPanel(vm: CompanionViewModel, top: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val st by vm.homeShelves.state.collectAsState()
    val items = if (top) st.topDrawer else st.bottomDrawer
    var adding by remember { mutableStateOf(false) }
    Text(if (items.isEmpty()) "This drawer is empty. Add apps or Meow tools." else "Tap to open. Hold to take something out.",
        style = MaterialTheme.typography.bodyLarge, color = Color(0xFFBED0CD))
    LazyVerticalGrid(GridCells.Adaptive(76.dp), Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(top = 12.dp)
        .testTag(if (top) "desk_drawer_top_grid" else "desk_drawer_bottom_grid")) {
        items(items) { key ->
            Column(Modifier.padding(6.dp).combinedClickable(onClick = { onClose(); vm.openShelfItem(context, key) },
                onLongClickLabel = "Take out of the drawer", onLongClick = { vm.homeShelves.removeFromDrawer(top, key) }),
                horizontalAlignment = Alignment.CenterHorizontally) {
                ShelfIcon(key, 48.dp)
                Text(shelfLabel(context, key), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFF4EEDB), modifier = Modifier.padding(top = 4.dp))
            }
        }
        item {
            Column(Modifier.padding(6.dp).clickable { adding = true }.testTag("desk_drawer_add"), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(48.dp).border(1.5.dp, Color(0xFF819F9F), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text("+", color = Color(0xFFF4EEDB), fontSize = 24.sp)
                }
                Text("Add", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF4EEDB), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    if (adding) ShelfPicker(if (top) "Add to the top drawer" else "Add to the bottom drawer", canRemove = false,
        onPick = { key -> key?.let { vm.homeShelves.addToDrawer(top, it) }; adding = false }, onDismiss = { adding = false })
}
