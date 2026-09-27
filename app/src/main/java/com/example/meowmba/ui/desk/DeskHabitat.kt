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
    /** Space kept clear on the left (the landscape side navigation). */
    startInset: Dp = 0.dp,
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
    /** The user's photo for the living-room picture frame; null shows the painting. */
    picture: androidx.compose.ui.graphics.ImageBitmap? = null,
    /** The user's room size: 1 = fits the screen; bigger zooms in and the view follows the cat. */
    userScale: Float = 1f,
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
    /** Landscape spreads the living room and bedroom out; set from the screen size below. */
    var layout by remember { mutableStateOf(DeskLayout.STANDARD) }
    val ambient = remember { DeskAmbient() }
    /** The cat mentions queued ideas once after "researching", not every time. */
    var toldIdea by remember { mutableStateOf(false) }
    // The room's own slow life (clouds, cursor, sticky note, TV game, the rare plane) at a low
    // frame rate, only while the Desk is on screen and the phone is cool.
    val ambientOn = !reduceMotion && visible && thermalStatus < PowerManager.THERMAL_STATUS_LIGHT
    LaunchedEffect(ambientOn) {
        if (!ambientOn) return@LaunchedEffect
        var last = withFrameNanos { it }
        var nextFlap = ambient.t + 20f + random.nextFloat() * 40f
        val planeChance = 1f / (DeskAmbient.FPS * 60f * DeskAmbient.PLANE_EVERY_MIN)
        while (isActive) {
            delay(1_000L / DeskAmbient.FPS)
            val now = withFrameNanos { it }
            ambient.t += ((now - last) / 1e9f).coerceIn(0f, .5f); last = now
            if (ambient.t >= nextFlap) { ambient.flapAt = ambient.t; nextFlap = ambient.t + 25f + random.nextFloat() * 45f }
            ambient.flyer?.let { if (ambient.t - it.start > it.duration) ambient.flyer = null }
            if (ambient.flyer == null && random.nextFloat() < planeChance)
                ambient.spawnFlyer(random.nextInt(DeskAmbient.UFO_ONE_IN) == 0, random.nextBoolean(), random.nextBoolean(), random.nextFloat())
        }
    }

    // One owner cancels idle work immediately when a question, tap, pause or heat arrives.
    LaunchedEffect(request, activity, reply?.key, animate, visible, fpsTarget, wanderEveryMs, lookRoom, layout) {
        cat.walking = false
        cat.phase = 0f
        cat.onKeys = false
        if (ambient.monitor != DeskMonitor.NORMAL) ambient.monitor = DeskMonitor.NORMAL
        if (apartment && (cat.x < layout.left - 20f || cat.x + 110f > layout.right + 20f)) {
            // Turned from landscape to portrait while the cat was in the extra space: bring it in.
            cat.x = cat.x.coerceIn(layout.left + 10f, layout.right - 120f); cat.y = DeskCatBehavior.FLOOR; cat.pose = DeskCatPose.REST
        }
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
        suspend fun leg(spot: DeskCatSpot, speed: Float? = null, forceHop: Boolean = false) {
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
            val hop = abs(dy) > 20f || forceHop
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
                val pace = speed ?: if (distance > 260f) 150f else 92f
                val ms = (distance / pace * 1000f + 260f).toLong().coerceIn(if (speed != null) 200 else 420, 6_000)
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
            // The living-room tower is tall: the cat climbs it, and leaves it, by its middle platform.
            val spots = DeskCatBehavior.spots(layout)
            val onTower = abs(cat.x - spots.livingTower.x) < 2f && abs(cat.y - spots.livingTower.y) < 2f
            if (onTower && spot != spots.livingTower) leg(spots.towerStep)
            if (spot == spots.livingTower && !onTower) {
                if (abs(cat.y - floor) > 20f && cat.y > spots.towerStep.y) leg(DeskCatSpot(cat.x, floor))
                if (abs(cat.y - spots.towerStep.y) > 2f) { leg(DeskCatSpot(spots.towerStep.x, floor)); leg(spots.towerStep) }
                leg(spot); return
            }
            if (far && cat.y < floor - 20f) leg(DeskCatSpot(cat.x, floor))
            if (far && abs(spot.y - floor) > 20f) leg(DeskCatSpot(spot.x, floor))
            leg(spot)
        }
        /** Set below: tricks and [perform] call each other. */
        var trickRunner: (suspend (DeskCatAct) -> Unit)? = null
        suspend fun perform(act: DeskCatAct, arrival: (() -> Unit)? = null) {
            if (act.trick != DeskCatTrick.NONE) { val run = trickRunner; if (animate && run != null) run(act) else moveTo(act.spot); return }
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
        /** Holds a pose where the cat is, animating paws and face as [perform] does. */
        suspend fun hold(pose: DeskCatPose, face: DeskCatFace, ms: Long, facingLeft: Boolean = cat.facingLeft) =
            perform(DeskCatAct(DeskCatSpot(cat.x, cat.y), pose, ms, face, facingLeft))
        /** A crouch, a jump straight up with a paw out, and a soft landing; [atTop] is the swat. */
        suspend fun jumpSwat(height: Float, atTop: () -> Unit) {
            crouch(.7f, 180)
            cat.pose = DeskCatPose.BAT
            val y0 = cat.y
            var swatted = false
            ease(460) { t ->
                val arc = sin(t * PI).toFloat()
                cat.y = y0 - arc * height; cat.squash = -.25f * arc; cat.phase = t * (2 * PI).toFloat()
                if (t > .45f && !swatted) { swatted = true; atTop() }
            }
            cat.y = y0; crouch(.5f, 80); crouch(0f, 160)
        }
        suspend fun trick(act: DeskCatAct) {
            val floor = DeskCatBehavior.FLOOR
            val spots = DeskCatBehavior.spots(layout)
            fun toFloor() = if (abs(cat.y - floor) > 20f) DeskCatSpot(cat.x, floor) else null
            when (act.trick) {
                DeskCatTrick.ZOOMIES -> {
                    // A sudden sprint to one end, back to the other, then a skid, a stare, and a wash as if nothing happened.
                    val lo = if (apartment) layout.left + 20f else 20f
                    val hi = if (apartment) layout.right - 130f else 290f
                    toFloor()?.let { leg(it) }
                    cat.face = DeskCatFace.PLAYFUL
                    val ends = if (random.nextBoolean()) listOf(hi, lo) else listOf(lo, hi)
                    for (x in ends + ((lo + hi) / 2 + random.nextFloat() * 120f - 60f)) { cat.face = DeskCatFace.PLAYFUL; leg(DeskCatSpot(x.coerceIn(lo, hi), floor), speed = 420f) }
                    crouch(.5f, 90); crouch(0f, 160)
                    hold(DeskCatPose.SIT, DeskCatFace.SURPRISED, 700)
                    hold(DeskCatPose.GROOM, DeskCatFace.PURR, 1_800)
                }
                DeskCatTrick.TAIL -> {
                    moveTo(act.spot)
                    cat.face = DeskCatFace.PLAYFUL
                    repeat(7) { i ->
                        cat.facingLeft = !cat.facingLeft; cat.tail = if (i % 2 == 0) 26f else -26f
                        crouch(.35f, 80); crouch(0f, 90)
                    }
                    hold(DeskCatPose.SIT, DeskCatFace.SLEEPY, 1_400)                        // a little dizzy
                }
                DeskCatTrick.MOUSE -> {
                    // A wind-up toy mouse scurries out; the cat stalks, wiggles and pounces until it catches it.
                    moveTo(act.spot)
                    val room = if (apartment) DeskRoom.of(cat.x) else DeskRoom.OFFICE
                    val lo = maxOf(room.left, layout.left) + 30f; val hi = minOf(room.right, layout.right) - 30f
                    val mid = (lo + hi) / 2
                    cat.mouseX = if (cat.x + 55f > mid) lo else hi; cat.mouseOn = true
                    cat.facingLeft = cat.mouseX < cat.x + 55f; cat.mouseLeft = !cat.facingLeft
                    cat.face = DeskCatFace.CURIOUS
                    crouch(.5f, 260); ease(900) { t -> cat.tail = sin(t * 22f) * 10f }; crouch(0f, 120)
                    repeat(3) { round ->
                        if (round < 2) {
                            val from = cat.mouseX
                            val to = if (cat.x + 55f < mid) mid + random.nextFloat() * (hi - mid) else lo + random.nextFloat() * (mid - lo)
                            cat.mouseLeft = to < from
                            ease(650) { t -> cat.mouseX = from + (to - from) * easeInOut(t) }
                        }
                        val right = cat.mouseX > cat.x + 55f
                        cat.face = DeskCatFace.PLAYFUL
                        leg(DeskCatSpot((if (right) cat.mouseX - 88f else cat.mouseX - 22f).coerceIn(lo - 20f, hi - 90f), floor), speed = 300f, forceHop = true)
                    }
                    cat.mouseOn = false
                    hold(DeskCatPose.SIT, DeskCatFace.LOVE, 1_500)
                }
                DeskCatTrick.YARN -> {
                    // Bat the ball of yarn, watch it roll, chase it down; three rounds.
                    val lo = layout.left + 30f; val hi = -40f
                    if (cat.yarnX.isNaN()) cat.yarnX = spots.yarnHome
                    cat.yarnX = cat.yarnX.coerceIn(lo, hi)
                    moveTo(DeskCatSpot((cat.yarnX - 95f).coerceIn(layout.left, hi - 110f), floor))
                    repeat(3) { i ->
                        val right = cat.yarnX > cat.x + 55f
                        cat.facingLeft = !right
                        hold(DeskCatPose.BAT, DeskCatFace.PLAYFUL, 500)
                        val dir = if (right) 1f else -1f
                        var to = cat.yarnX + dir * (60f + random.nextFloat() * 70f)
                        if (to > hi || to < lo) to = cat.yarnX - dir * (60f + random.nextFloat() * 50f)
                        to = to.coerceIn(lo, hi)
                        val from = cat.yarnX; val r0 = cat.yarnRot
                        ease(700) { t -> val e = 1f - (1f - t) * (1f - t); cat.yarnX = from + (to - from) * e; cat.yarnRot = r0 + (to - from) * e * 6.4f }
                        val ballRight = to > cat.x + 55f
                        leg(DeskCatSpot((if (ballRight) to - 95f else to - 15f).coerceIn(layout.left, hi - 60f), floor), speed = 260f, forceHop = i == 2)
                    }
                    hold(DeskCatPose.SIT, DeskCatFace.HAPPY, 1_200)
                }
                DeskCatTrick.PEN -> {
                    // The classic: nudge the pen, look right at you, and push it off the desk.
                    if (cat.penDown) { cat.penDown = false; cat.penX = PEN_X; cat.penY = PEN_Y; cat.penRot = 0f }
                    moveTo(DeskCatBehavior.pen); cat.facingLeft = false
                    hold(DeskCatPose.SIT, DeskCatFace.CURIOUS, 800)
                    repeat(2) { hold(DeskCatPose.BAT, DeskCatFace.PLAYFUL, 450); val x0 = cat.penX; ease(160) { t -> cat.penX = x0 + 5f * t } }
                    cat.gaze = 0f
                    hold(DeskCatPose.SIT, DeskCatFace.PLAYFUL, 1_100)
                    hold(DeskCatPose.BAT, DeskCatFace.PLAYFUL, 350)
                    val x0 = cat.penX
                    ease(700) { t -> cat.penY = PEN_Y + (734f - PEN_Y) * t * t; cat.penRot = 190f * t; cat.penX = x0 + 14f * t }
                    ease(220) { t -> cat.penY = 734f - sin(t * PI).toFloat() * 8f; cat.penRot = 190f - 10f * t }
                    cat.penDown = true
                    hold(DeskCatPose.SIT, DeskCatFace.HAPPY, 1_500)
                }
                DeskCatTrick.LAMP, DeskCatTrick.MOTH -> {
                    moveTo(DeskCatBehavior.lamp); cat.facingLeft = false
                    hold(DeskCatPose.SIT, DeskCatFace.CURIOUS, 900)
                    repeat(if (act.trick == DeskCatTrick.MOTH) 3 else 2) {
                        jumpSwat(64f) { if (act.trick == DeskCatTrick.MOTH) ambient.mothSwatAt = ambient.t else ambient.lampPushAt = ambient.t }
                        hold(DeskCatPose.SIT, DeskCatFace.PLAYFUL, 500)
                    }
                    hold(DeskCatPose.SIT, DeskCatFace.HAPPY, 900)
                }
                DeskCatTrick.KEYBOARD -> {
                    moveTo(DeskCatBehavior.computer)
                    cat.onKeys = true
                    ambient.monitor = DeskMonitor.KEYS; ambient.monitorSince = ambient.t
                    hold(DeskCatPose.SIT, DeskCatFace.HAPPY, act.durationMs, facingLeft = true)
                    cat.gaze = 0f
                    hold(DeskCatPose.SIT, DeskCatFace.PLAYFUL, 900, facingLeft = true)
                    ambient.monitor = DeskMonitor.NORMAL; cat.onKeys = false
                }
                DeskCatTrick.RESEARCH -> {
                    // Looks things up for your project (an animation only: nothing is searched or sent).
                    moveTo(DeskCatBehavior.computer)
                    ambient.monitor = DeskMonitor.BROWSER; ambient.monitorSince = ambient.t
                    hold(DeskCatPose.TYPE, DeskCatFace.FOCUSED, act.durationMs, facingLeft = true)
                    hold(DeskCatPose.SIT, DeskCatFace.CURIOUS, 1_000, facingLeft = true)
                    ambient.monitor = DeskMonitor.NORMAL
                    if (stateNow.lampOn && !toldIdea) { toldIdea = true; bubble = "Mulling over your ideas. They're by the lamp." }
                }
                DeskCatTrick.TOY -> {
                    moveTo(spots.towerStep); cat.facingLeft = true
                    repeat(3) { jumpSwat(36f) { ambient.toyPushAt = ambient.t }; hold(DeskCatPose.SIT, DeskCatFace.PLAYFUL, 400) }
                }
                DeskCatTrick.BIRD -> {
                    // A bird lands on the sill; the cat watches and chatters.
                    moveTo(act.spot)
                    ambient.birdUntil = ambient.t + act.durationMs / 1000f + 1.5f
                    hold(DeskCatPose.PERCH, DeskCatFace.CURIOUS, act.durationMs / 2)
                    repeat(4) { cat.face = DeskCatFace.SURPRISED; ease(160) { t -> cat.ear = sin(t * PI).toFloat() }; cat.face = DeskCatFace.CURIOUS; delay(140) }
                    hold(DeskCatPose.PERCH, DeskCatFace.CURIOUS, act.durationMs / 2)
                }
                DeskCatTrick.BOX -> {
                    // If it fits, it sits.
                    val box = DeskCatBehavior.box
                    moveTo(DeskCatSpot(box.x, floor)); leg(box)
                    hold(DeskCatPose.HIDE, DeskCatFace.PLAYFUL, act.durationMs / 2, facingLeft = true)
                    hold(DeskCatPose.HIDE, DeskCatFace.CURIOUS, act.durationMs / 2, facingLeft = true)
                    leg(DeskCatSpot(box.x - 60f, floor))
                }
                DeskCatTrick.KNEAD -> {
                    moveTo(act.spot)
                    hold(DeskCatPose.KNEAD, DeskCatFace.PURR, act.durationMs)
                    hold(DeskCatPose.SLEEP, DeskCatFace.ASLEEP, 8_000)
                }
                DeskCatTrick.ROLL -> { moveTo(act.spot); hold(DeskCatPose.ROLL, DeskCatFace.LOVE, act.durationMs) }
                DeskCatTrick.NONE -> Unit
            }
        }
        trickRunner = { trick(it) }
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
                        perform(DeskCatBehavior.atObject(obj, layout)) {
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
                        if (!stateNow.lampOn) toldIdea = false
                        // Someone tidies up the knocked-off pen while the cat is elsewhere.
                        if (cat.penDown && DeskRoom.of(cat.x) != DeskRoom.OFFICE) { cat.penDown = false; cat.penX = PEN_X; cat.penY = PEN_Y; cat.penRot = 0f }
                        val ctx = DeskIdleContext(night = stateNow.night, sunbeamX = DeskSky.sunbeamX(stateNow))
                        val act = if (!apartment) {
                            if (random.nextFloat() < .4f) (DeskCatBehavior.officeTricks(random, ctx) + DeskCatBehavior.anywhereTricks(random, DeskRoom.OFFICE)).random(random)
                            else DeskCatBehavior.idle(random, previous, stateNow.trayCount > 0, stateNow.projectTitle.isNotBlank())
                        } else DeskCatBehavior.apartmentIdle(random, previous, DeskRoom.of(cat.x), stateNow.trayCount > 0,
                                stateNow.projectTitle.isNotBlank(), stateNow.room.music?.playing == true, stateNow.room.video?.playing == true,
                                roomChance = roomTripsNow, layout = layout, ctx = ctx)
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
        val usableWidth = maxWidth - startInset
        val heightUnit = usableHeight / (VIEW_BOTTOM - VIEW_TOP)
        // Landscape fills the screen: at full height, the living room and bedroom grow outward until
        // the rooms reach both edges (bookshelf at the left, bed at the right). Bigger TV, shelf, bed.
        val landscape = wide && maxWidth > maxHeight
        val plan = if (landscape) DeskLayout.fitting((usableWidth / heightUnit - DeskRoom.APT_WIDTH) / 2) else DeskLayout.STANDARD
        layout = plan
        val sceneWidth = if (wide) plan.width else SCENE_W
        // Fit the part of the scene that has content (clock to desk), not the empty floor below it,
        // so the rooms are as large as the screen allows. The walls and floor extend to every edge.
        // The user's room size scales that; when it's bigger than the screen the view follows the cat.
        val unit = minOf(usableWidth / sceneWidth, heightUnit) * userScale.coerceIn(.5f, 2.5f) // dp per scene unit
        val overflow = (unit * sceneWidth - usableWidth).coerceAtLeast(0.dp)
        // Screen x of scene x = 0 (the office's left wall) with the office centred / the panorama centred.
        val ox = startInset + if (wide) (usableWidth - unit * plan.width) / 2 - unit * plan.left else (usableWidth - unit * SCENE_W) / 2
        val oy = maxHeight - bottomInset - unit * VIEW_BOTTOM // the desk sits just above the navigation
        val density = androidx.compose.ui.platform.LocalDensity.current
        val scale = with(density) { unit.toPx() }
        val oxPx = with(density) { ox.toPx() }
        val oyPx = with(density) { oy.toPx() }
        // Folded: the camera pans (a layer translation, no redraw) to the room in view.
        val catRoom by remember { androidx.compose.runtime.derivedStateOf { DeskRoom.of(cat.x) } }
        val panning = overflow > 0.dp
        val focus = if (!apartment || wide) DeskRoom.OFFICE else lookRoom ?: catRoom
        val camTarget = (DeskRoom.OFFICE.center - focus.center) * scale
        val halfOverflowPx = with(density) { overflow.toPx() } / 2
        val followCenter = if (wide) (plan.left + plan.right) / 2 else focus.center
        /** Zoomed in: keep the cat near the middle, never showing past the edges of the place (or room). */
        fun follow(): Float = if (!panning) 0f else (-(cat.x + 55f - followCenter) * scale).coerceIn(-halfOverflowPx, halfOverflowPx)
        val cam by androidx.compose.animation.core.animateFloatAsState(camTarget,
            if (animate) androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            else androidx.compose.animation.core.snap(), label = "desk_camera")
        // Render and hit-test through the same transform.
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = cam + follow() }) {

        // 1. Static rooms: their own graphics layer, drawn once.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            withTransform({ translate(oxPx, oyPx); scale(scale, scale, androidx.compose.ui.geometry.Offset.Zero) }) { drawRoom(plan) }
        }
        // 2. Objects: redraw only when state changes.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            withTransform({ translate(oxPx, oyPx); scale(scale, scale, androidx.compose.ui.geometry.Offset.Zero) }) { drawObjects(state, measurer, plan, picture) }
        }
        // 3. The room's own slow motion: clouds, weather, cursor, sticky note, lamp, TV game, planes.
        Canvas(Modifier.fillMaxSize().testTag("desk_ambient")) {
            withTransform({ translate(oxPx, oyPx); scale(scale, scale, androidx.compose.ui.geometry.Offset.Zero) }) {
                with(DeskAmbientPainter) { drawAmbient(state, ambient, measurer, plan, cat.pose == DeskCatPose.GAME) }
            }
        }
        // 4. Cat, and the things it plays with (the litter box's and cardboard box's fronts go over it).
        Canvas(Modifier.fillMaxSize().testTag("desk_cat").semantics {
            stateDescription = DeskCatBehavior.description(cat.pose, cat.x)
        }) {
            withTransform({ translate(oxPx, oyPx); scale(scale, scale, androidx.compose.ui.geometry.Offset.Zero) }) {
                with(DeskAmbientPainter) {
                    pen(cat.penX, cat.penY, cat.penRot)
                    if (cat.onKeys) keyboard(cat.x + 12f, cat.y + 78f)
                }
                drawCat(cat.x, cat.y, cat.walking, cat.frame, cat.blink, cat.tail, cat.gaze, cat.facingLeft, cat.pose, cat.phase, cat.face,
                    gait = if (cat.walking) cat.gait else -1f, breath = cat.breath, ear = cat.ear, squash = cat.squash)
                if (cat.pose == DeskCatPose.DIG) with(DeskApartment) { drawLitterFront() }
                with(DeskAmbientPainter) {
                    if (cat.pose == DeskCatPose.HIDE) boxFront()
                    if (apartment) {
                        val home = DeskCatBehavior.spots(plan).yarnHome
                        yarn((if (cat.yarnX.isNaN()) home else cat.yarnX).coerceIn(plan.left + 20f, -30f), home, cat.yarnRot)
                    }
                    if (cat.mouseOn) toyMouse(cat.mouseX, cat.mouseLeft)
                }
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
                bubble = if (cat.pose == DeskCatPose.ROLL) "Belly rub accepted. For now." else DeskCatBehavior.petLines.random(random)
            }
            .semantics { contentDescription = "Meow, " + DeskCatBehavior.description(cat.pose, cat.x) }
            .testTag("desk_cat_pet"))
        // Tap targets share the room transform and remain immediate, independent of walking.
        if (interactive) DeskObject.entries.filter { apartment || it.room == DeskRoom.OFFICE }.forEach { o ->
            val f = o.frame(plan)
            val window = o == DeskObject.WINDOW || o == DeskObject.BIG_WINDOW
            Box(Modifier.offset(ox + unit * f.left, oy + unit * f.top).size(unit * f.width, unit * f.height)
                .combinedClickable(role = Role.Button, onClickLabel = o.label,
                    // Easter egg: a long press on a window calls in the UFO.
                    onLongClick = if (window) ({
                        ambient.spawnFlyer(ufo = true, bedroom = o == DeskObject.BIG_WINDOW, leftToRight = random.nextBoolean(), lane = random.nextFloat() * .5f)
                        cat.face = DeskCatFace.SURPRISED
                        bubble = "Did you see that?!"
                    }) else null) {
                    bubble = lineForNow(o) ?: o.label
                    request = ((request?.first ?: 0) + 1) to o
                    onTap(o)
                }.semantics { contentDescription = describe(o, state) }.testTag("desk_" + o.name.lowercase()))
        }
        }
        // Folded, or zoomed in past the screen: step into the next room (the cat comes along).
        if (apartment && (!wide || panning) && interactive) Row(Modifier.align(Alignment.TopStart).padding(top = topInset, start = 8.dp)) {
            val here = if (wide) catRoom else focus
            val left = DeskRoom.entries.getOrNull(here.ordinal - 1)
            val right = DeskRoom.entries.getOrNull(here.ordinal + 1)
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
                            (ox + unit * (cat.x + 55f) + (cam + follow()).toDp() - bubbleWidth / 2).coerceIn(12.dp, viewportWidth - bubbleWidth - 12.dp).roundToPx(), 0)
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
    DeskObject.IDEA -> if (s.lampOn) "Lamp: Meow has an idea for you" else if (s.night) "Lamp: on for the night" else "Lamp: off"
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
    DeskObject.PICTURE -> "Picture on the wall: choose your own photo"
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
    // Props the cat plays with.
    var penX by mutableFloatStateOf(PEN_X)
    var penY by mutableFloatStateOf(PEN_Y)
    var penRot by mutableFloatStateOf(0f)
    var penDown = false
    var yarnX by mutableFloatStateOf(Float.NaN)
    var yarnRot by mutableFloatStateOf(0f)
    var mouseOn by mutableStateOf(false)
    var mouseX by mutableFloatStateOf(0f)
    var mouseLeft by mutableStateOf(false)
    var onKeys by mutableStateOf(false)
}

/** Where the pen lies on the desk, beside the monitor. */
private const val PEN_X = 212f
private const val PEN_Y = 593f

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
