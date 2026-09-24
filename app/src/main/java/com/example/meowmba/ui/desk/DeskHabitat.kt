package com.example.meowmba.ui.desk

import android.os.PowerManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.ui.companion.AvatarFrameCadence
import com.example.meowmba.ui.desk.DeskPainter.drawCat
import com.example.meowmba.ui.desk.DeskPainter.drawObjects
import com.example.meowmba.ui.desk.DeskPainter.drawRoom
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Meow's Desk scene (AppTheme.DESK mode). Draws full screen in Desk mode; the app's
 * existing mic/camera/chat controls stay outside it (no Care control, owner D2).
 *
 * Layers (master plan §9.1, same approach as IDLE_LOAD_138): the room is drawn once into
 * its own graphics layer; objects redraw only when [state] changes; the cat animates at the
 * active rate only while walking (via [AvatarFrameCadence]) and otherwise changes a few times
 * a minute (blink, tail flick). Thermal SEVERE freezes the cat; MODERATE stops wandering.
 *
 * Tap an object → eyes glance → cat walks there → [lineFor] is shown in the bubble →
 * [onArrive] (the caller opens the bottom sheet with that object's Flow items).
 */
@Composable
fun DeskHabitat(
    state: DeskState,
    modifier: Modifier = Modifier,
    thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
    fpsTarget: Int = AvatarFrameCadence.DEFAULT_ACTIVE_FPS,
    reduceMotion: Boolean = false,
    interactive: Boolean = true,
    /** Idle stroll interval; 0 disables wandering. */
    wanderEveryMs: Long = 9_000L,
    idleLines: List<String> = emptyList(),
    lineFor: (DeskObject) -> String? = { null },
    onArrive: (DeskObject) -> Unit = {},
    /** Called the moment an object is tapped (open its panel right away; the cat still walks over). */
    onTap: (DeskObject) -> Unit = {},
    /** Space kept clear at the top (status bar) and bottom (navigation) of the screen. */
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
) {
    val measurer = rememberTextMeasurer()
    val cat = remember { CatMotion() }
    val onArriveNow by rememberUpdatedState(onArrive)
    val lineForNow by rememberUpdatedState(lineFor)
    var request by remember { mutableStateOf<Pair<Float, DeskObject?>?>(null) }
    var bubble by remember { mutableStateOf<String?>(null) }

    // Walking: frame-driven only while moving, admitted by the app's cadence (thermal-aware).
    LaunchedEffect(request, thermalStatus, reduceMotion, fpsTarget) {
        val (targetX, obj) = request ?: return@LaunchedEffect
        bubble = obj?.let(lineForNow)
        cat.gaze = if (targetX < cat.x) -1f else 1f
        if (reduceMotion || thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            cat.walking = false; cat.frame = 0
            cat.x = targetX
        } else {
            val cadence = AvatarFrameCadence()
            cat.facingLeft = targetX < cat.x
            cat.walking = true
            val startX = cat.x
            val startNs = withFrameNanos { it }
            val durNs = (WALK_MS * 1_000_000L * (abs(targetX - startX) / 100f).coerceIn(0.4f, 1.4f)).toLong()
            var lastStep = startNs
            while (true) {
                val now = withFrameNanos { it }
                if (!cadence.admit(now, active = true, fpsTarget = fpsTarget, thermalStatus = thermalStatus)) continue
                val t = ((now - startNs).toFloat() / durNs).coerceIn(0f, 1f)
                cat.x = startX + (targetX - startX) * easeInOut(t)
                if (now - lastStep >= STEP_NS) { cat.frame = cat.frame xor 1; lastStep = now }
                if (t >= 1f) break
                cadence.idleNanosAfterAdmit().takeIf { it > 0 }?.let { delay(it / 1_000_000L) }
            }
            cat.walking = false; cat.frame = 0; cat.facingLeft = false
        }
        cat.gaze = 0f
        obj?.let { o ->
            bubble = lineForNow(o)
            onArriveNow(o)
        }
        request = null
    }

    // Idle life: a blink every ~5 s, a tail flick now and then, an occasional stroll. No loop otherwise.
    LaunchedEffect(reduceMotion, thermalStatus, wanderEveryMs, idleLines) {
        cat.blink = 0f; cat.tail = 0f
        if (reduceMotion || thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) return@LaunchedEffect
        var n = 0
        while (true) {
            delay(BLINK_EVERY_MS)
            if (!cat.walking) { cat.blink = 1f; delay(150); cat.blink = 0f }
            cat.tail = if (n % 2 == 0) 10f else -4f
            n++
            val strollDue = wanderEveryMs > 0 && n % (wanderEveryMs / BLINK_EVERY_MS).coerceAtLeast(1) == 0L
            if (strollDue && request == null && thermalStatus < PowerManager.THERMAL_STATUS_MODERATE) {
                request = STROLL_SPOTS[(n / 2) % STROLL_SPOTS.size] to null
                if (idleLines.isNotEmpty()) { delay(1_300); bubble = idleLines[(n / 2) % idleLines.size] }
            }
        }
    }
    LaunchedEffect(bubble) { if (bubble != null) { delay(BUBBLE_MS); bubble = null } }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF12232A)).testTag("desk_habitat")) {
        // Fit the part of the scene that has content (clock to desk), not the empty floor below it,
        // so the room is as large as the screen allows. The wall and floor extend to every edge.
        val unit = minOf(maxWidth / SCENE_W, (maxHeight - topInset - bottomInset) / (VIEW_BOTTOM - VIEW_TOP)) // dp per scene unit
        val ox = (maxWidth - unit * SCENE_W) / 2
        val oy = maxHeight - bottomInset - unit * VIEW_BOTTOM // the desk sits just above the navigation
        val density = androidx.compose.ui.platform.LocalDensity.current
        val scale = with(density) { unit.toPx() }
        val oxPx = with(density) { ox.toPx() }
        val oyPx = with(density) { oy.toPx() }
        // Render and hit-test through the same transform.
        Box(Modifier.fillMaxSize()) {

        // 1. Static room: its own graphics layer, drawn once.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            withTransform({ translate(oxPx, oyPx); scale(scale, scale, androidx.compose.ui.geometry.Offset.Zero) }) { drawRoom() }
        }
        // 2. Objects: redraw only when state changes.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            withTransform({ translate(oxPx, oyPx); scale(scale, scale, androidx.compose.ui.geometry.Offset.Zero) }) { drawObjects(state, measurer) }
        }
        // 3. Cat.
        Canvas(Modifier.fillMaxSize().testTag("desk_cat").semantics {
            stateDescription = if (cat.walking) "walking" else "resting at ${cat.x.toInt()}"
        }) {
            withTransform({ translate(oxPx, oyPx); scale(scale, scale, androidx.compose.ui.geometry.Offset.Zero) }) {
                drawCat(cat.x, CAT_Y, cat.walking, cat.frame, cat.blink, cat.tail, cat.gaze, cat.facingLeft)
            }
        }
        // Speech bubble above the cat.
        bubble?.let { text ->
            Box(
                Modifier
                    .offset(x = ox + unit * (cat.x - 40f).coerceIn(4f, SCENE_W - 214f))
                    .layout { measurable, constraints ->
                        // Bottom edge 6 scene units above the cat's head, whatever the text height.
                        val p = measurable.measure(constraints)
                        val bottom = (oyPx + (CAT_Y - 6f) * scale).toInt()
                        layout(p.width, p.height) { p.place(0, bottom - p.height) }
                    }
                    .width(unit * 210f)
                    .drawBehind { drawRoundRect(DeskPainter.INK, topLeft = androidx.compose.ui.geometry.Offset(0f, 3.dp.toPx()), size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())) }
                    .background(Color(0xFFF4EEDB), RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp))
                    .border(2.5.dp, DeskPainter.INK, RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .semantics { contentDescription = "Meow says: $text" }
                    .testTag("desk_bubble"),
            ) { BasicText(text, style = TextStyle(color = DeskPainter.INK, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)) }
        }
        // Tap targets.
        if (interactive) DeskObject.entries.forEach { o ->
            Box(
                Modifier
                    .offset(ox + unit * o.left, oy + unit * o.top)
                    .size(unit * o.width, unit * o.height)
                    .clickable(role = Role.Button, onClickLabel = o.label) {
                        bubble = lineForNow(o) ?: o.label
                        request = o.catX to o
                        onTap(o)
                    }
                    .semantics { contentDescription = describe(o, state) }
                    .testTag("desk_" + o.name.lowercase()),
            )
        }
        }
    }
}

/** Screen-reader description that states the object's data, not just its name. */
internal fun describe(o: DeskObject, s: DeskState): String = when (o) {
    DeskObject.INBOX -> if (s.trayCount == 0) "Letter tray: nothing waiting" else "Letter tray: ${s.trayCount} ${if (s.trayCount == 1) "person needs" else "people need"} you"
    DeskObject.MONEY -> s.jarNote?.let { "Jar: $it" } ?: "Jar: nothing due"
    DeskObject.TODAY -> if (s.sticky.isEmpty()) "Sticky note: nothing next" else "Sticky note: next up ${s.sticky.joinToString(" ")}"
    DeskObject.PROJECT -> if (s.projectTitle.isBlank()) "Corkboard: no project pinned" else "Corkboard: ${s.projectTitle}, ${s.projectStatus}"
    DeskObject.BUILD -> if (s.monitorLines.isEmpty()) "Monitor: nothing running" else "Monitor: " + s.monitorLines.joinToString(", ") { it.text }
    DeskObject.IDEA -> if (s.lampOn) "Lamp: Meow has an idea for you" else "Lamp: off"
    DeskObject.WINDOW -> "Window: " + (s.weatherLine ?: "weather")
    DeskObject.CLOCK -> "Clock: open alarms and timers"
    DeskObject.DRAWER_TOP -> "Top drawer: your apps and tools"
    DeskObject.DRAWER_BOTTOM -> "Bottom drawer: your apps and tools"
}

private class CatMotion {
    var x by mutableFloatStateOf(170f)
    var walking by mutableStateOf(false)
    var frame by mutableIntStateOf(0)
    var blink by mutableFloatStateOf(0f)
    var tail by mutableFloatStateOf(0f)
    var gaze by mutableFloatStateOf(0f)
    var facingLeft by mutableStateOf(false)
}

private fun easeInOut(t: Float) = if (t < .5f) 2 * t * t else 1 - (-2 * t + 2).let { it * it } / 2

private const val SCENE_W = 412f
/** Scene rows with content: from above the window/clock to the skirting under the desk. */
private const val VIEW_TOP = 44f
private const val VIEW_BOTTOM = 748f
private const val CAT_Y = 516f
private const val WALK_MS = 1_100L
private const val STEP_NS = 110_000_000L
private const val BLINK_EVERY_MS = 5_200L
private const val BUBBLE_MS = 5_200L
private val STROLL_SPOTS = listOf(215f, 150f, 250f, 170f)
