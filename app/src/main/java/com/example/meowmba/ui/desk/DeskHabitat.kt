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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

/**
 * Meow's Desk scene (AppTheme.DESK mode). Draws full screen in Desk mode; the app's
 * existing mic/camera/chat controls stay outside it (no Care control, owner D2).
 *
 * Layers (master plan §9.1, same approach as IDLE_LOAD_138): the room is drawn once into
 * its own graphics layer; objects redraw only when [state] changes; the cat animates at the
 * active rate only during a finite performance (via [AvatarFrameCadence]). Rests sleep between
 * irregular blinks. Pause/background or thermal MODERATE stops decorative motion.
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
    /** Centre of the random idle-rest range; 0 disables idle performances. */
    wanderEveryMs: Long = 9_000L,
    lineFor: (DeskObject) -> String? = { null },
    onArrive: (DeskObject) -> Unit = {},
    /** Called the moment an object is tapped (open its panel right away; the cat still walks over). */
    onTap: (DeskObject) -> Unit = {},
    /** Space kept clear at the top (status bar) and bottom (navigation) of the screen. */
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    activity: DeskCatActivity = DeskCatActivity.IDLE,
    reply: DeskCatReply? = null,
) {
    val measurer = rememberTextMeasurer()
    val cat = remember { CatMotion() }
    val onArriveNow by rememberUpdatedState(onArrive)
    val lineForNow by rememberUpdatedState(lineFor)
    var request by remember { mutableStateOf<Pair<Int, DeskObject>?>(null) }
    var bubble by remember { mutableStateOf<String?>(null) }
    var dismissedReply by remember { mutableStateOf<String?>(null) }
    val random = remember { Random(System.nanoTime()) }
    val stateNow by rememberUpdatedState(state)
    val lifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val visible = lifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    val animate = !reduceMotion && thermalStatus < PowerManager.THERMAL_STATUS_MODERATE

    // One owner cancels idle work immediately when a question, tap, pause or heat arrives.
    LaunchedEffect(request, activity, reply?.key, animate, visible, fpsTarget, wanderEveryMs) {
        cat.walking = false
        cat.phase = 0f
        cat.blink = 0f
        if ((!visible || !animate) && cat.pose == DeskCatPose.WALK) {
            // A suspended hop must not leave a frozen cat hanging between the desk and floor.
            cat.y = if (cat.y > 590f) 656f else 516f
            cat.pose = DeskCatPose.REST
        }
        if (!visible) return@LaunchedEffect
        suspend fun frames(ms: Long, step: (Float) -> Unit) {
            val cadence = AvatarFrameCadence()
            val startNs = withFrameNanos { it }
            while (isActive) {
                val now = withFrameNanos { it }
                if (!cadence.admit(now, active = true, fpsTarget = fpsTarget, thermalStatus = thermalStatus)) continue
                val t = ((now - startNs).toFloat() / (ms * 1_000_000L)).coerceIn(0f, 1f)
                step(t)
                if (t >= 1f) break
                cadence.idleNanosAfterAdmit().takeIf { it > 0 }?.let { delay(it / 1_000_000L) }
            }
        }
        suspend fun moveTo(spot: DeskCatSpot) {
            if (!animate) { cat.x = spot.x; cat.y = spot.y; return }
            val sx = cat.x; val sy = cat.y
            val distance = kotlin.math.hypot(spot.x - sx, spot.y - sy)
            if (distance < 1f) return
            cat.pose = DeskCatPose.WALK; cat.walking = true
            cat.facingLeft = spot.x < sx
            val ms = (distance * 7).toLong().coerceIn(550, 2_200)
            frames(ms) { t ->
                val eased = easeInOut(t)
                cat.x = sx + (spot.x - sx) * eased
                // A little hop between desk/floor/perches; level travel has a walking gait.
                cat.y = sy + (spot.y - sy) * eased - if (abs(spot.y - sy) > 20f) sin(t * PI).toFloat() * 32f else 0f
                cat.frame = ((t * ms / 110).toInt() % 2)
                cat.tail = sin(t * PI * 4).toFloat() * 8f
            }
            cat.x = spot.x; cat.y = spot.y; cat.walking = false; cat.frame = 0
        }
        suspend fun perform(act: DeskCatAct, arrival: (() -> Unit)? = null) {
            moveTo(act.spot)
            cat.pose = act.pose
            cat.facingLeft = act.pose == DeskCatPose.TYPE || act.pose == DeskCatPose.WRITE
            arrival?.invoke()
            if (animate) frames(act.durationMs) { t ->
                cat.phase = t * (2 * PI).toFloat() * 3
                cat.tail = sin(cat.phase * .5f) * 8f
                cat.blink = if (t in .44f.. .48f) 1f else 0f
            }
        }
        suspend fun rest(ms: Long) {
            var remaining = ms
            while (remaining > 0 && isActive) {
                val wait = minOf(remaining, random.nextLong(2_700, 6_300))
                delay(wait); remaining -= wait
                if (animate) { cat.blink = 1f; delay(140); cat.blink = 0f }
            }
        }
        try {
            when (activity) {
                DeskCatActivity.THINKING -> {
                    // A visual performance accompanies actual inference; it does not run tools.
                    perform(DeskCatAct(DeskCatBehavior.computer, DeskCatPose.TYPE, 3_600))
                    while (animate && isActive) {
                        rest(random.nextLong(900, 2_000))
                        perform(DeskCatAct(DeskCatBehavior.computer, DeskCatPose.TYPE, random.nextLong(2_500, 4_500)))
                    }
                }
                DeskCatActivity.LISTENING, DeskCatActivity.SPEAKING -> {
                    val pose = if (activity == DeskCatActivity.LISTENING) DeskCatPose.LISTEN else DeskCatPose.TALK
                    perform(DeskCatAct(DeskCatBehavior.home, pose, 2_000))
                    while (animate && isActive) perform(DeskCatAct(DeskCatBehavior.home, pose, 2_000))
                }
                DeskCatActivity.IDLE -> {
                    request?.takeIf { it.first != cat.handledRequest }?.let { (id, obj) ->
                        perform(DeskCatBehavior.atObject(obj)) {
                            cat.handledRequest = id
                            bubble = lineForNow(obj); onArriveNow(obj)
                        }
                    }
                    reply?.takeIf { it.key != cat.handledReply }?.let {
                        cat.handledReply = it.key
                        perform(DeskCatAct(DeskCatBehavior.home, DeskCatPose.TALK, 3_600))
                    }
                    if (!animate) return@LaunchedEffect
                    // Leave an elevated prop with a visible hop before putting it away.
                    if (cat.y < 516f) moveTo(DeskCatBehavior.home)
                    cat.pose = DeskCatPose.REST
                    var previous = DeskCatPose.REST
                    while (isActive) {
                        rest(DeskCatBehavior.restMs(random, wanderEveryMs.takeIf { it > 0 } ?: 20_000))
                        if (wanderEveryMs <= 0) continue
                        val act = DeskCatBehavior.idle(random, previous, stateNow.trayCount > 0, stateNow.projectTitle.isNotBlank())
                        previous = act.pose
                        perform(act)
                        if (cat.y < 516f) moveTo(DeskCatBehavior.home)
                        cat.pose = DeskCatPose.REST
                    }
                }
            }
        } finally { cat.walking = false; cat.blink = 0f }
    }
    LaunchedEffect(bubble) { if (bubble != null) { delay(BUBBLE_MS); bubble = null } }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF12232A)).testTag("desk_habitat")) {
        val viewportWidth = maxWidth
        val viewportHeight = maxHeight
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
            stateDescription = DeskCatBehavior.description(cat.pose, cat.x)
        }) {
            withTransform({ translate(oxPx, oyPx); scale(scale, scale, androidx.compose.ui.geometry.Offset.Zero) }) {
                drawCat(cat.x, cat.y, cat.walking, cat.frame, cat.blink, cat.tail, cat.gaze, cat.facingLeft, cat.pose, cat.phase)
            }
        }
        // Tap targets share the room transform and remain immediate, independent of walking.
        if (interactive) DeskObject.entries.forEach { o ->
            Box(Modifier.offset(ox + unit * o.left, oy + unit * o.top).size(unit * o.width, unit * o.height)
                .clickable(role = Role.Button, onClickLabel = o.label) {
                    bubble = lineForNow(o) ?: o.label
                    request = ((request?.first ?: 0) + 1) to o
                    onTap(o)
                }.semantics { contentDescription = describe(o, state) }.testTag("desk_" + o.name.lowercase()))
        }
        // Actual replies have priority over decorative object hints and can be read in full.
        val shownReply = reply?.takeIf { it.key != dismissedReply && it.text.isNotBlank() }
        val shownText = shownReply?.text ?: bubble
        shownText?.let { text ->
            val bubbleWidth = minOf(if (shownReply != null) 320.dp else unit * 210f, viewportWidth - 24.dp)
            val bubbleHeight = minOf(300.dp, (viewportHeight - topInset - bottomInset) * .42f)
            Box(
                Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            (ox + unit * (cat.x + 55f) - bubbleWidth / 2).coerceIn(12.dp, viewportWidth - bubbleWidth - 12.dp).roundToPx(), 0)
                    }
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints)
                        val top = (oyPx + (cat.y - 8f) * scale).toInt() - p.height
                        val minTop = with(density) { (topInset + 8.dp).roundToPx() }
                        val maxTop = with(density) { (viewportHeight - bottomInset - 8.dp).roundToPx() } - p.height
                        layout(p.width, p.height) { p.place(0, top.coerceIn(minTop, maxOf(minTop, maxTop))) }
                    }
                    .width(bubbleWidth).heightIn(max = bubbleHeight)
                    .drawBehind { drawRoundRect(DeskPainter.INK, topLeft = androidx.compose.ui.geometry.Offset(0f, 3.dp.toPx()), size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())) }
                    .background(Color(0xFFF4EEDB), RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp))
                    .border(2.5.dp, DeskPainter.INK, RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .semantics { contentDescription = "Meow says: $text" }
                    .testTag("desk_bubble"),
            ) {
                Column {
                    if (shownReply != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BasicText("Meow", Modifier.weight(1f), style = TextStyle(color = DeskPainter.INK, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        BasicText("Close", Modifier.clickable(role = Role.Button) { dismissedReply = shownReply.key }
                            .padding(horizontal = 8.dp, vertical = 10.dp).testTag("desk_reply_close"),
                            style = TextStyle(color = DeskPainter.INK, fontSize = 13.sp))
                    }
                    BasicText(text, modifier = if (shownReply != null) Modifier.verticalScroll(rememberScrollState()).testTag("desk_reply_text") else Modifier,
                        style = TextStyle(color = DeskPainter.INK, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium))
                }
            }
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
    var y by mutableFloatStateOf(516f)
    var pose by mutableStateOf(DeskCatPose.REST)
    var phase by mutableFloatStateOf(0f)
    var handledRequest: Int = 0
    var handledReply: String? = null
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
private const val BUBBLE_MS = 5_200L
