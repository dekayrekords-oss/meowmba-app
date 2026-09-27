@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.meowmba.ui.desk

import android.os.PowerManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.clipToBounds
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
    /**
     * Meow's whole place: living room left, office, bedroom right. Unfolded (wide) screens show all
     * three rooms; the folded cover screen shows one room and follows the cat between them.
     * False keeps the original single office.
     */
    apartment: Boolean = true,
    /** Chance an idle act takes the cat to another room (Desk setting "visits other rooms"). */
    roomTrips: Float = DeskRoomTrips.DEFAULT.chance,
    /** Long-press on the cat (opens its routine settings). */
    onCatLongPress: (() -> Unit)? = null,
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
    val roomTripsNow by rememberUpdatedState(roomTrips)
    val lifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val visible = lifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    val animate = !reduceMotion && thermalStatus < PowerManager.THERMAL_STATUS_MODERATE
    var petting by remember { mutableIntStateOf(0) }
    /** A room the user asked to look at; the camera shows it until the cat gets there. */
    var lookRoom by remember { mutableStateOf<DeskRoom?>(null) }
    var wideLayout by remember { mutableStateOf(false) }

    // One owner cancels idle work immediately when a question, tap, pause or heat arrives.
    LaunchedEffect(request, activity, reply?.key, animate, visible, fpsTarget, wanderEveryMs, lookRoom) {
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
        // Low-power resting life (breathing, ears, tail, glances) only when the phone is cool.
        val lively = animate && thermalStatus < PowerManager.THERMAL_STATUS_LIGHT
        suspend fun ease(ms: Long, step: (Float) -> Unit) = if (animate) frames(ms, step) else step(1f)
        /** Crouch before a jump (or to stand up), 0 → [depth]. */
        suspend fun crouch(depth: Float, ms: Long) { val from = cat.squash; ease(ms) { t -> cat.squash = from + (depth - from) * easeInOut(t) } }
        suspend fun standUp() {
            if (cat.pose !in setOf(DeskCatPose.SIT, DeskCatPose.LOAF, DeskCatPose.SLEEP)) return
            // Getting up is a small event: a stretch, then a settle onto four paws.
            cat.pose = DeskCatPose.REST
            crouch(.45f, 220); crouch(-.25f, 260); crouch(0f, 180)
        }
        suspend fun leg(spot: DeskCatSpot) {
            val sx = cat.x; val sy = cat.y
            val dx = spot.x - sx; val dy = spot.y - sy
            val distance = kotlin.math.hypot(dx, dy)
            if (distance < 1f) return
            standUp()
            val left = dx < 0f
            if (left != cat.facingLeft && abs(dx) > 2f) {
                // Turning around: a quick shuffle rather than an instant mirror.
                crouch(.25f, 110); cat.facingLeft = left; crouch(0f, 120)
            }
            cat.pose = DeskCatPose.WALK; cat.walking = true
            if (cat.face == DeskCatFace.ASLEEP || cat.face == DeskCatFace.SLEEPY) cat.face = DeskCatFace.NEUTRAL
            val hop = abs(dy) > 20f
            if (hop) {
                // Anticipation, flight with a stretched body, and a soft landing.
                crouch(.8f, 200)
                val ms = (170 + abs(dy) * 1.6f + abs(dx) * 2.2f).toLong().coerceIn(320, 900)
                val arc = maxOf(28f, abs(dy) * .45f + 12f)
                val g0 = cat.gait
                ease(ms) { t ->
                    cat.x = sx + dx * t
                    cat.y = sy + dy * t - sin(t * PI).toFloat() * arc
                    cat.squash = if (t < .15f) .8f * (1f - t / .15f) - .3f * (t / .15f) else -.3f + .3f * ((t - .15f) / .85f)
                    cat.gait = g0 + t * 1.5f
                    cat.tail = -10f + 16f * t
                }
                cat.x = spot.x; cat.y = spot.y
                crouch(.6f, 90); crouch(0f, 200)
            } else {
                // Stroll nearby, trot across rooms. Legs are driven by distance, so paws never slide.
                val speed = if (distance > 260f) 150f else 92f
                val ms = (distance / speed * 1000f + 260f).toLong().coerceIn(420, 6_000)
                val g0 = cat.gait
                ease(ms) { t ->
                    val e = smoothWalk(t)
                    cat.x = sx + dx * e
                    cat.y = sy + dy * e
                    cat.gait = g0 + (distance * e / STRIDE) * (2 * PI).toFloat()
                    cat.tail = -6f + sin(cat.gait * .5f) * 7f
                }
                cat.x = spot.x; cat.y = spot.y
            }
            cat.walking = false; cat.frame = 0
        }
        suspend fun moveTo(spot: DeskCatSpot) {
            if (!animate) { cat.x = spot.x; cat.y = spot.y; cat.pose = DeskCatPose.REST; return }
            // Going far (another room, across the office): hop down, walk the floor, hop up.
            val far = DeskRoom.of(cat.x) != DeskRoom.of(spot.x) || abs(spot.x - cat.x) > 200f
            val floor = DeskCatBehavior.FLOOR
            if (far && cat.y < floor - 20f) leg(DeskCatSpot(cat.x, floor))
            if (far && abs(spot.y - floor) > 20f) leg(DeskCatSpot(spot.x, floor))
            leg(spot)
        }
        suspend fun perform(act: DeskCatAct, arrival: (() -> Unit)? = null) {
            moveTo(act.spot)
            cat.pose = act.pose
            cat.face = act.face ?: DeskCatBehavior.defaultFace(act.pose)
            cat.facingLeft = act.facingLeft ?: (act.pose == DeskCatPose.TYPE || act.pose == DeskCatPose.WRITE)
            arrival?.invoke()
            if (animate) frames(act.durationMs) { t ->
                cat.phase = t * (2 * PI).toFloat() * 3
                cat.tail = sin(cat.phase * .5f) * 8f
                cat.breath = sin(t * act.durationMs / 1000f * (2 * PI).toFloat() / 3.2f)
                cat.blink = if (t in .44f.. .48f) 1f else 0f
            }
        }
        /**
         * Resting is not frozen: the cat breathes, twitches an ear, flicks its tail, glances about,
         * blinks, and after a while sits down and later lies in a loaf. Runs at a low frame rate
         * ([IDLE_FPS]); when the phone is warm it falls back to still rests with occasional blinks.
         */
        suspend fun rest(ms: Long) {
            if (!lively) {
                var remaining = ms
                while (remaining > 0 && isActive) {
                    val wait = minOf(remaining, random.nextLong(2_700, 6_300))
                    delay(wait); remaining -= wait
                    if (animate && cat.pose != DeskCatPose.SLEEP) { cat.blink = 1f; delay(140); cat.blink = 0f }
                }
                return
            }
            val cadence = AvatarFrameCadence()
            val start = withFrameNanos { it }
            var restedSince = if (cat.pose in setOf(DeskCatPose.REST, DeskCatPose.SIT, DeskCatPose.LOAF)) cat.restedMs else 0L
            var next = random.nextLong(900, 2_600)
            var event = 0; var eventAt = 0L; var eventMs = 1L
            var gazeFrom = cat.gaze; var gazeTo = cat.gaze
            val breathPeriod = if (cat.pose == DeskCatPose.SLEEP || cat.pose == DeskCatPose.LOAF) 4.4f else 3.2f
            while (isActive) {
                val now = withFrameNanos { it }
                if (!cadence.admit(now, active = true, fpsTarget = minOf(fpsTarget, IDLE_FPS), thermalStatus = thermalStatus)) continue
                val el = (now - start) / 1_000_000L
                if (el >= ms) break
                cat.restedMs = restedSince + el
                cat.breath = sin(el / 1000f * (2 * PI).toFloat() / breathPeriod)
                val asleep = cat.pose == DeskCatPose.SLEEP
                if (event == 0 && el >= next) {
                    event = if (asleep) 1 else 1 + random.nextInt(6); eventAt = el
                    eventMs = when (event) { 1 -> 340L; 2 -> 700L; 3 -> random.nextLong(900, 2_200); 4 -> 150L; else -> 1L }
                    if (event == 3) { gazeFrom = cat.gaze; gazeTo = if (random.nextInt(3) == 0) 0f else random.nextFloat() * 2f - 1f }
                    when (event) {
                        5 -> when (random.nextInt(8)) {   // a passing mood
                            0 -> cat.face = DeskCatFace.YAWN
                            1 -> cat.face = DeskCatFace.PLAYFUL
                            2 -> cat.face = DeskCatFace.PURR
                            3 -> cat.face = DeskCatFace.CURIOUS
                            else -> cat.face = if (cat.pose == DeskCatPose.LOAF) DeskCatFace.SLEEPY else DeskCatFace.NEUTRAL
                        }
                        6 -> when {                        // settling: stand → sit → loaf
                            cat.pose == DeskCatPose.REST && cat.restedMs > 5_000 -> { cat.pose = DeskCatPose.SIT; cat.squash = .3f }
                            cat.pose == DeskCatPose.SIT && cat.restedMs > 18_000 && random.nextBoolean() -> { cat.pose = DeskCatPose.LOAF; cat.face = DeskCatFace.SLEEPY; cat.squash = .3f }
                        }
                    }
                }
                if (event != 0) {
                    val t = ((el - eventAt).toFloat() / eventMs).coerceIn(0f, 1f)
                    when (event) {
                        1 -> cat.ear = sin(t * PI).toFloat()                              // ear twitch
                        2 -> cat.tail = sin(t * (2 * PI).toFloat() * 1.5f) * 16f * (1f - t) // tail flick
                        3 -> cat.gaze = gazeFrom + (gazeTo - gazeFrom) * easeInOut((t * 4f).coerceAtMost(1f)) // glance and hold
                        4 -> cat.blink = sin(t * PI).toFloat()                            // blink
                    }
                    if (cat.squash > 0f && event == 6) cat.squash = (cat.squash - .05f).coerceAtLeast(0f)
                    if (el - eventAt >= eventMs) {
                        if (event == 5 && cat.face == DeskCatFace.YAWN) cat.face = DeskCatFace.NEUTRAL
                        if (event == 6) cat.squash = 0f
                        cat.ear = 0f; cat.blink = 0f
                        event = 0; next = el + random.nextLong(if (asleep) 3_000 else 1_100, if (asleep) 7_000 else 3_400)
                    }
                }
                cadence.idleNanosAfterAdmit().takeIf { it > 0 }?.let { delay(it / 1_000_000L) }
            }
            cat.ear = 0f; cat.blink = 0f
        }
        try {
            when (activity) {
                DeskCatActivity.THINKING -> {
                    // A visual performance accompanies actual inference; it does not run tools.
                    perform(DeskCatAct(DeskCatBehavior.computer, DeskCatPose.TYPE, 3_600, DeskCatFace.FOCUSED))
                    while (animate && isActive) {
                        rest(random.nextLong(900, 2_000))
                        perform(DeskCatAct(DeskCatBehavior.computer, DeskCatPose.TYPE, random.nextLong(2_500, 4_500), DeskCatFace.FOCUSED))
                    }
                }
                DeskCatActivity.LISTENING, DeskCatActivity.SPEAKING -> {
                    val pose = if (activity == DeskCatActivity.LISTENING) DeskCatPose.LISTEN else DeskCatPose.TALK
                    val face = if (activity == DeskCatActivity.LISTENING) DeskCatFace.CURIOUS else DeskCatFace.HAPPY
                    perform(DeskCatAct(DeskCatBehavior.home, pose, 2_000, face))
                    while (animate && isActive) perform(DeskCatAct(DeskCatBehavior.home, pose, 2_000, face))
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
                        perform(DeskCatAct(DeskCatBehavior.home, DeskCatPose.TALK, 3_600, DeskCatFace.HAPPY))
                    }
                    lookRoom?.takeIf { apartment && it != DeskRoom.of(cat.x) }?.let { room ->
                        // The user looked into another room: the cat trots over to join them.
                        moveTo(DeskCatSpot(room.center - 55f, DeskCatBehavior.FLOOR))
                        cat.pose = DeskCatPose.REST; cat.face = DeskCatFace.HAPPY
                    }
                    lookRoom = null
                    if (!animate) return@LaunchedEffect
                    // Leave an elevated prop with a visible hop before putting it away.
                    fun settle() = if (DeskRoom.of(cat.x) == DeskRoom.OFFICE || !apartment) DeskCatBehavior.home else DeskCatSpot(cat.x, DeskCatBehavior.FLOOR)
                    if (cat.y < 516f) moveTo(settle())
                    if (cat.pose !in setOf(DeskCatPose.SLEEP, DeskCatPose.SIT, DeskCatPose.LOAF)) cat.pose = DeskCatPose.REST
                    var previous = DeskCatPose.REST
                    while (isActive) {
                        rest(DeskCatBehavior.restMs(random, wanderEveryMs.takeIf { it > 0 } ?: 20_000))
                        if (wanderEveryMs <= 0) continue
                        val act = if (!apartment) DeskCatBehavior.idle(random, previous, stateNow.trayCount > 0, stateNow.projectTitle.isNotBlank())
                            else DeskCatBehavior.apartmentIdle(random, previous, DeskRoom.of(cat.x), stateNow.trayCount > 0,
                                stateNow.projectTitle.isNotBlank(), stateNow.room.music?.playing == true, stateNow.room.video?.playing == true,
                                roomChance = roomTripsNow)
                        previous = act.pose
                        perform(act)
                        if (cat.y < 516f) moveTo(settle())
                        // Sleeping cats stay curled up until the next thing to do.
                        if (cat.pose != DeskCatPose.SLEEP) { cat.pose = DeskCatPose.REST; cat.face = DeskCatFace.NEUTRAL }
                        cat.restedMs = 0
                    }
                }
            }
        } finally { cat.walking = false; cat.blink = 0f }
    }
    LaunchedEffect(bubble) { if (bubble != null) { delay(BUBBLE_MS); bubble = null } }
    LaunchedEffect(petting) {
        if (petting == 0) return@LaunchedEffect
        // Petting: a startled blink, then a happy face (a sleeping cat yawns awake first).
        val wasAsleep = cat.pose == DeskCatPose.SLEEP
        cat.face = if (wasAsleep) DeskCatFace.YAWN else DeskCatFace.SURPRISED
        delay(if (wasAsleep) 1_200 else 450)
        cat.face = listOf(DeskCatFace.HAPPY, DeskCatFace.PURR, DeskCatFace.LOVE).random(random)
        delay(2_400)
        if (cat.pose == DeskCatPose.SLEEP) cat.face = DeskCatFace.ASLEEP
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF12232A)).clipToBounds().testTag("desk_habitat")) {
        val viewportWidth = maxWidth
        val viewportHeight = maxHeight
        val usableHeight = maxHeight - topInset - bottomInset
        // Unfolded (or any wide screen): all three rooms side by side. Folded: one room, following the cat.
        val wide = apartment && (maxWidth >= usableHeight || maxWidth >= 600.dp)
        wideLayout = wide
        val sceneWidth = if (wide) DeskRoom.APT_WIDTH else SCENE_W
        // Fit the part of the scene that has content (clock to desk), not the empty floor below it,
        // so the rooms are as large as the screen allows. The walls and floor extend to every edge.
        val unit = minOf(maxWidth / sceneWidth, usableHeight / (VIEW_BOTTOM - VIEW_TOP)) // dp per scene unit
        // Screen x of scene x = 0 (the office's left wall) with the office centred / the panorama centred.
        val ox = if (wide) (maxWidth - unit * DeskRoom.APT_WIDTH) / 2 - unit * DeskRoom.APT_LEFT else (maxWidth - unit * SCENE_W) / 2
        val oy = maxHeight - bottomInset - unit * VIEW_BOTTOM // the desk sits just above the navigation
        val density = androidx.compose.ui.platform.LocalDensity.current
        val scale = with(density) { unit.toPx() }
        val oxPx = with(density) { ox.toPx() }
        val oyPx = with(density) { oy.toPx() }
        // Folded: the camera pans (a layer translation, no redraw) to the room in view.
        val catRoom by remember { androidx.compose.runtime.derivedStateOf { DeskRoom.of(cat.x) } }
        val focus = if (!apartment || wide) DeskRoom.OFFICE else lookRoom ?: catRoom
        val camTarget = (DeskRoom.OFFICE.center - focus.center) * scale
        val cam by androidx.compose.animation.core.animateFloatAsState(camTarget,
            if (animate) androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            else androidx.compose.animation.core.snap(), label = "desk_camera")
        // Render and hit-test through the same transform.
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = cam }) {

        // 1. Static rooms: their own graphics layer, drawn once.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            withTransform({ translate(oxPx, oyPx); scale(scale, scale, androidx.compose.ui.geometry.Offset.Zero) }) { drawRoom() }
        }
        // 2. Objects: redraw only when state changes.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            withTransform({ translate(oxPx, oyPx); scale(scale, scale, androidx.compose.ui.geometry.Offset.Zero) }) { drawObjects(state, measurer) }
        }
        // 3. Cat (and the litter box's front lip when it's inside).
        Canvas(Modifier.fillMaxSize().testTag("desk_cat").semantics {
            stateDescription = DeskCatBehavior.description(cat.pose, cat.x)
        }) {
            withTransform({ translate(oxPx, oyPx); scale(scale, scale, androidx.compose.ui.geometry.Offset.Zero) }) {
                drawCat(cat.x, cat.y, cat.walking, cat.frame, cat.blink, cat.tail, cat.gaze, cat.facingLeft, cat.pose, cat.phase, cat.face,
                    gait = if (cat.walking) cat.gait else -1f, breath = cat.breath, ear = cat.ear, squash = cat.squash)
                if (cat.pose == DeskCatPose.DIG) with(DeskApartment) { drawLitterFront() }
            }
        }
        // The cat's head can be petted. It sits under the room's objects, so the cat never blocks one.
        if (interactive) Box(Modifier
            .offset { androidx.compose.ui.unit.IntOffset((oxPx + (cat.x + 46f) * scale).toInt(), (oyPx + (cat.y + 8f) * scale).toInt()) }
            .size(unit * 58f, unit * 62f)
            .combinedClickable(role = Role.Button, onClickLabel = "Pet Meow",
                onLongClickLabel = if (onCatLongPress != null) "Meow's routine" else null,
                onLongClick = onCatLongPress) {
                petting++
                bubble = DeskCatBehavior.petLines.random(random)
            }
            .semantics { contentDescription = "Meow, " + DeskCatBehavior.description(cat.pose, cat.x) }
            .testTag("desk_cat_pet"))
        // Tap targets share the room transform and remain immediate, independent of walking.
        if (interactive) DeskObject.entries.filter { apartment || it.room == DeskRoom.OFFICE }.forEach { o ->
            Box(Modifier.offset(ox + unit * o.left, oy + unit * o.top).size(unit * o.width, unit * o.height)
                .clickable(role = Role.Button, onClickLabel = o.label) {
                    bubble = lineForNow(o) ?: o.label
                    request = ((request?.first ?: 0) + 1) to o
                    onTap(o)
                }.semantics { contentDescription = describe(o, state) }.testTag("desk_" + o.name.lowercase()))
        }
        }
        // Folded: step into the next room (the cat comes along).
        if (apartment && !wide && interactive) Row(Modifier.align(Alignment.TopStart).padding(top = topInset, start = 8.dp)) {
            val left = DeskRoom.entries.getOrNull(focus.ordinal - 1)
            val right = DeskRoom.entries.getOrNull(focus.ordinal + 1)
            left?.let { r -> RoomStep("‹ ${r.label}", "desk_room_left") { lookRoom = r } }
            right?.let { r -> RoomStep("${r.label} ›", "desk_room_right") { lookRoom = r } }
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
                            (ox + unit * (cat.x + 55f) + cam.toDp() - bubbleWidth / 2).coerceIn(12.dp, viewportWidth - bubbleWidth - 12.dp).roundToPx(), 0)
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

@Composable
private fun RoomStep(label: String, tag: String, onClick: () -> Unit) {
    BasicText(label, Modifier
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 12.dp)
        .testTag(tag),
        style = TextStyle(color = Color(0xFFF4EEDB), fontSize = 14.sp, fontWeight = FontWeight.Bold))
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
    DeskObject.BIG_WINDOW -> "Big window: " + (s.weatherLine ?: "the view")
    DeskObject.ARCADE -> "Old TV and game console: open the arcade"
    DeskObject.TOWER_BED -> "Cat tower by the window"
    DeskObject.BED -> "Bed: nap time"
    DeskObject.FOOD -> "Food tray"
    DeskObject.LITTER -> "Litter box"
    DeskObject.BOOKSHELF -> "Bookshelf: " + (s.room.audiobook?.let { "listening to ${it.title}" } ?: "audiobooks and the Meow manual")
    DeskObject.BIG_TV -> "TV: " + (s.room.video?.let { "${it.title}${it.subtitle?.let { c -> ", $c" } ?: ""}" } ?: "nothing on YouTube")
    DeskObject.STEREO -> "Stereo: " + (s.room.music?.let { "${it.title}${it.subtitle?.let { a -> " by $a" } ?: ""}" } ?: "nothing playing")
    DeskObject.COUCH -> "Couch"
    DeskObject.TOWER_LIVING -> "Cat tower"
}

private class CatMotion {
    var x by mutableFloatStateOf(180f)
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
    var face by mutableStateOf(DeskCatFace.NEUTRAL)
    var gait by mutableFloatStateOf(0f)
    var breath by mutableFloatStateOf(0f)
    var ear by mutableFloatStateOf(0f)
    var squash by mutableFloatStateOf(0f)
    /** How long the cat has been resting in place (drives sitting down, then lying down). */
    var restedMs: Long = 0
}

/** Ease-in/out for walking: a gentle start and stop, steady in the middle. */
private fun smoothWalk(t: Float): Float = when {
    t < .2f -> 2.5f * t * t
    t > .8f -> 1f - 2.5f * (1f - t) * (1f - t)
    else -> .1f + (t - .2f) * (.8f / .6f)
}

/** Scene units per full walk cycle (both front paws step once). */
private const val STRIDE = 34f
/** Resting life (breathing, ears, tail) runs at most this often: low and cool. */
private const val IDLE_FPS = 12

private fun easeInOut(t: Float) = if (t < .5f) 2 * t * t else 1 - (-2 * t + 2).let { it * it } / 2

private const val SCENE_W = 412f
/** Scene rows with content: from above the window/clock to the skirting under the desk. */
private const val VIEW_TOP = 44f
private const val VIEW_BOTTOM = 748f
private const val BUBBLE_MS = 5_200L
