package com.example.meowmba.ui.companion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

/**
 * Meowmba cat avatar — "Memba 2" face design (as seen on the LOOI desk robot),
 * rebuilt as animation-ready layered Compose artwork.
 *
 * The face is pure line-art geometry, fully resolution-independent: every
 * measurement derives from `s = min(width, height)`, so it scales cleanly
 * across phones, tablets, and landscape desk-dock mode. Rendering is driven
 * entirely by [CatAvatarStateMachine] poses; timings come from
 * [CatAnimationRegistry]; blink, ear twitch, breathing, gaze wander and
 * speech motion run as independent, interruptible animation layers.
 *
 * Interaction model (see the original spec):
 *  - Tap either ear    -> microphone privacy toggle
 *  - Tap either eye    -> camera privacy toggle
 *  - Tap the mouth     -> mute / unmute companion speech
 *  - Tap/hold forehead -> quick settings
 *  - Tap background    -> Memba tile overlay
 *  - Stroke the cat    -> it purrs (happy arcs + whisker flutter)
 *  - Swipe up/down     -> chat workspace / tile overlay
 *  - Pupils track the user's touch position, then drift back to an idle wander
 */
private val EYE_COLOR = Color(0xFFCFF9FF)
private val PUPIL_COLOR = Color(0xFF06232B)
private val NOSE_COLOR = Color(0xFFFFA6C9)
private val WHISKER_COLOR = Color(0x66CFF9FF)


/**
 * Godot face extents (relative to head radius R, from GodotCatFace):
 *   ear tip ≈ 1.10 R above center, chin ≈ 0.75 R below, whiskers ≈ 0.85 R wide.
 * Leave margins for the status/cutout band and the mic row so landscape
 * never clips the ears or stacks the mic on the mouth.
 */
/** Ear tip above the head center, in units of R. */
private const val FACE_ABOVE = 1.10f

/** Chin below the head center, in units of R. */
private const val FACE_BELOW = 0.75f

/** Widest point (whiskers) either side of center, in units of R. */
private const val FACE_HALF_WIDE = 0.85f

/**
 * Room reserved for squash & stretch. The head deforms about its own center, so
 * a fit computed from the resting silhouette alone clips the ear tips at the
 * top of every stretch.
 */
private const val STRETCH_HEADROOM = 1.12f

private fun faceMargins(w: Float, h: Float): Triple<Float, Float, Float> {
    val landscape = w > h * 1.15f
    val topMargin = h * 0.07f
    val bottomMargin = if (landscape) h * 0.12f else h * 0.15f
    val sideMargin = if (landscape) w * 0.10f else w * 0.05f
    return Triple(topMargin, bottomMargin, sideMargin)
}

/**
 * Largest head radius that keeps the whole face — ear tips included, at full
 * stretch — inside the canvas and clear of the status band and the mic row.
 *
 * The height budget is derived from the face's real extents instead of being a
 * hand-tuned constant. Landscape used to budget 1.70 R for a face that occupies
 * 1.85 R, so the ear tips were cut off on the cover display even at rest.
 */
internal fun fitGodotFaceRadius(w: Float, h: Float): Float {
    val landscape = w > h * 1.15f
    val (topMargin, bottomMargin, sideMargin) = faceMargins(w, h)
    val availW = (w - 2f * sideMargin).coerceAtLeast(1f)
    val availH = (h - topMargin - bottomMargin).coerceAtLeast(1f)
    val heightBudget = (FACE_ABOVE + FACE_BELOW) * STRETCH_HEADROOM
    val r = min(availW / (FACE_HALF_WIDE * 2f), availH / heightBudget)
    val maxFrac = if (landscape) 0.54f else 0.46f
    val ceiling = min(w, h) * maxFrac
    // Clamp to the ceiling FIRST, then apply the readability floor only if it
    // fits under it. `coerceIn(120f, ceiling)` throws whenever the canvas is
    // under ~260px — which happens for a frame during layout and in a small
    // split-screen window, taking the whole face down with it.
    return r.coerceAtMost(ceiling).coerceAtLeast(min(120f, ceiling)).coerceAtLeast(1f)
}

/**
 * Vertical center for the head at [fitGodotFaceRadius].
 *
 * Prefers the optical middle of the usable band, but the face is asymmetric
 * (more of it sits above the center than below), so on a short canvas that
 * midpoint pushes the ears off the top. Clamping to the feasible range leaves
 * portrait exactly where it was and only drops the head when it must.
 */
internal fun fitGodotFaceCenterY(w: Float, h: Float): Float {
    val landscape = w > h * 1.15f
    val (topMargin, bottomMargin, _) = faceMargins(w, h)
    val r = fitGodotFaceRadius(w, h)
    val bandMid = (topMargin + (h - bottomMargin)) * 0.5f
    val preferred = if (landscape) bandMid - h * 0.02f else bandMid
    val highest = topMargin + FACE_ABOVE * r * STRETCH_HEADROOM
    val lowest = h - bottomMargin - FACE_BELOW * r * STRETCH_HEADROOM
    // Canvas too short for the face at any position: center it and clip evenly
    // rather than slamming the head against one edge.
    if (highest > lowest) return (highest + lowest) * 0.5f
    return preferred.coerceIn(highest, lowest)
}

@Composable
fun AvatarRenderer(
    state: CompanionState,
    onZoneClick: (AvatarZone) -> Unit,
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    paused: Boolean = false,
    faceGazeX: Float? = null,
    faceGazeY: Float? = null,
    /**
     * Speech energy 0..1, as a [State] rather than a plain Float on purpose.
     * Passing the value would make every word boundary a new parameter and
     * recompose the whole avatar ~14x/sec while speaking; passing the State
     * and reading `.value` only inside the draw lambda invalidates draw
     * alone — the same trick as [drawEpoch] below.
     */
    mouthEnergy: State<Float>? = null,
    modifier: Modifier = Modifier
) {
    val basePose = CatAvatarStateMachine.resolvePose(state)
    val haptics = LocalHapticFeedback.current

    // ── Invented animation overlay ─────────────────────────────────────────
    // A clip the companion invented plays OVER the state-machine pose: sparse
    // fields interpolate across keyframes, everything else keeps reacting
    // normally. Feeding it in here means the expression blending below smooths
    // the keyframe steps for free, and the clip simply stops applying when it
    // ends (sample returns null past the duration).
    // While an overlay covers the screen the face freezes: no custom clip.
    val customAnim = if (paused) null else state.activeAnimation
    val composed = if (paused) null else state.activeComposed

    // Draw-only invalidation signal. Every per-frame producer below bumps this
    // and writes its value into a plain holder; the Canvas reads both. Because
    // the ONLY read of drawEpoch is inside the draw lambda, a bump schedules a
    // redraw without re-running composition.
    var drawEpoch by remember { mutableLongStateOf(0L) }
    // Per-frame clip time lives in a HOLDER, not composition state. Writing it
    // to a MutableState every vsync recomposed the entire avatar for the whole
    // length of every clip — the jank was worst exactly while something was
    // animating, which is the opposite of what you want. drawEpoch (already
    // read only inside the Canvas) is what invalidates the draw pass.
    val animTHolder = remember { longArrayOf(0L) }
    LaunchedEffect(customAnim) {
        if (customAnim != null) {
            var start = -1L
            while (true) {
                withFrameMillis { now ->
                    if (start < 0L) start = now
                    animTHolder[0] = now - start
                    drawEpoch++
                }
                if (animTHolder[0] > customAnim.durationMs) break
            }
        }
    }

    // Godot face / classic idle: ONE vsync-locked draw clock (not 9 infinite transitions).
    // CatMotionEngine is ONE-SHOT only for named expressions.
    val motionEngine = remember { CatMotionEngine() }
    // Same reasoning as animTHolder: the composed-expression pose is produced
    // every vsync and consumed only inside the draw lambda.
    val shotHolder = remember { arrayOfNulls<MotionPose>(1) }
    var faceRadiusPx by remember { mutableStateOf(200f) }
    val useGodotFace = state.avatarStyle == AvatarStyle.NEO
    // Single ambient clock (seconds). Advanced at ~60 fps and only while visible.
    // Reading this in the Canvas draw lambda invalidates draw only — not a
    // full-composition thrash of 9 rememberInfiniteTransition states @ vsync.
    var clockSec by remember { mutableFloatStateOf(0f) }
    // The clock survives pauses. It used to be a local of the effect below,
    // which is keyed on `paused` — so every time a panel opened or closed the
    // accumulator restarted at 0.0 and every ambient cycle (breath, sway,
    // drift) teleported to its t=0 phase. The head jumped the instant you
    // dismissed chat or the drawer, with nothing on screen to explain it.
    val clockBase = remember { doubleArrayOf(0.0) }
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var lastNs = 0L
        // Advance by real elapsed time on EVERY vsync, at whatever rate the
        // panel runs. The previous version quantised to a fixed 1/60 s step,
        // which capped the face at 60 fps on a 120 Hz display — every pose was
        // held for two vsyncs and the motion read as stepping, the exact
        // artefact the fixed step was added to cure. (It was added because a
        // Float 1f/60f threshold sat just above one 60 Hz vsync and silently
        // ticked every other one, pinning the clock at ~30 fps. Removing the
        // quantisation removes that whole class of bug: there is no threshold
        // left to straddle.) Accumulation stays in Double so a long session
        // does not lose sub-millisecond resolution; dt is capped so a resumed
        // app cannot jump the ambient cycle forward by seconds in one frame.
        while (true) {
            withFrameNanos { now ->
                if (lastNs == 0L) lastNs = now
                val dt = ((now - lastNs) / 1_000_000_000.0).coerceIn(0.0, 0.05)
                lastNs = now
                clockBase[0] += dt
                clockSec = clockBase[0].toFloat()
                drawEpoch++
            }
        }
    }
    LaunchedEffect(composed, paused) {
        if (paused || composed == null) {
            motionEngine.stop()
            shotHolder[0] = null
            drawEpoch++
            return@LaunchedEffect
        }
        motionEngine.play(composed)
        var last = 0L
        while (true) {
            var frame: MotionPose? = null
            withFrameMillis { now ->
                // Cap at 1/30 s, not 1/60: capping at one 60 Hz frame meant any
                // frame that ran long advanced the clip by less time than had
                // actually passed, so a hitch played the rest of the
                // performance in slow motion instead of dropping a frame of it.
                // Spring.update applies the same 1/30 guard internally.
                val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceIn(0f, 1f / 30f)
                last = now
                frame = motionEngine.update(dt, faceRadiusPx.coerceIn(80f, 800f))
                // Publish every vsync so one-shot keyframes move smoothly —
                // into the holder, and invalidate DRAW only.
                shotHolder[0] = frame
                drawEpoch++
            }
            if (frame == null) break
        }
        shotHolder[0] = null
        drawEpoch++
    }

    // NOTE: the live pose is assembled inside the draw lambda now (see
    // `pose`/`effectivePose` there). The blend animators below read basePose
    // directly, which is exactly equivalent: the composed pose only ever
    // differs from basePose while a clip is playing, and in that case
    // `playingClip` discards the animator outputs anyway. Feeding them
    // basePose keeps their targets stable instead of retargeting them 60-120
    // times a second to values nobody reads.

    // ── Expression blending: every pose field eases toward its target ──────
    // When a custom animation is playing, CustomAnimations.sample() already
    // interpolates between keyframes (smoothstep). Adding a 350ms expression
    // blend ON TOP causes the animated value to never reach its keyframe target
    // before the next one arrives — the motion is cancelled out. Use a near-
    // instant spec during playback so the sample values are honored directly.
    // During one-shot / custom playback the sample already interpolates —
    // animateFloatAsState on top (even at 16ms) fights the keyframes. Live
    // values only blend when idle/reacting.
    val playingClip = customAnim != null || composed != null
    // Live values ride a spring so emotion/interaction shifts settle with a
    // hint of overshoot (organic) instead of a linear ease (mechanical).
    // During clip playback the sampler already interpolates keyframes — snap.
    val blendSpec = if (playingClip) {
        tween<Float>(16)
    } else {
        spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
    }
    val eyeOpenAnim by animateFloatAsState(basePose.eyeOpen, blendSpec, label = "eyeOpen")
    val pupilSizeAnim by animateFloatAsState(basePose.pupilSize, blendSpec, label = "pupil")
    val gazeUpAnim by animateFloatAsState(basePose.pupilGazeUp, blendSpec, label = "gazeUp")
    val headTiltAnim by animateFloatAsState(basePose.headTiltDeg, blendSpec, label = "tilt")
    val earPerkAnim by animateFloatAsState(basePose.earPerk, blendSpec, label = "earPerk")
    val earDroopAnim by animateFloatAsState(basePose.earDroopDeg, blendSpec, label = "earDroop")
    // Dim on its own short tween — a spring could overshoot alpha past 1.
    val dimSpec = if (playingClip) tween<Float>(16) else tween<Float>(250)
    val faceAlphaAnim by animateFloatAsState(if (basePose.dimmed) 0.55f else 1f, dimSpec, label = "dim")


    // ── Ambient animation layers (timings from the registry) ───────────────
    val blink = remember { Animatable(1f) }
    // Single owner for the lid. Other layers (the saccade loop) ask for a blink
    // by sending here rather than driving the Animatable themselves — two
    // writers cancel each other's animations and whichever loses its
    // animateTo call dies of the resulting CancellationException.
    val blinkRequests = remember {
        kotlinx.coroutines.channels.Channel<Unit>(
            capacity = 1,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
        )
    }
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        val spec = CatAnimationRegistry.spec("idle.blink")
        while (true) {
            // Whichever comes first: the ambient interval, or someone asking.
            val requested = kotlinx.coroutines.withTimeoutOrNull(
                Random.nextLong(spec.minDelayMs.toLong(), spec.maxDelayMs.toLong())
            ) { blinkRequests.receive() } != null
            blink.animateTo(0.06f, tween(90))
            blink.animateTo(1f, tween(110))
            // Occasional double-blink, like a real cat. Never on a requested
            // blink — that one is hiding a gaze jump and must be a single beat.
            if (!requested && Random.nextInt(4) == 0) {
                blink.animateTo(0.06f, tween(80))
                blink.animateTo(1f, tween(100))
            }
        }
    }

    val earTwitchL = remember { Animatable(0f) }
    val earTwitchR = remember { Animatable(0f) }
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        val spec = CatAnimationRegistry.spec("idle.ear_twitch")
        while (true) {
            delay(Random.nextLong(spec.minDelayMs.toLong(), spec.maxDelayMs.toLong()))
            val ear = if (Random.nextBoolean()) earTwitchL else earTwitchR
            ear.animateTo(9f, tween(70))
            ear.animateTo(0f, tween(120))
            if (Random.nextInt(3) == 0) {
                ear.animateTo(6f, tween(60))
                ear.animateTo(0f, tween(110))
            }
        }
    }

    // Hold last face sample; never fall through to idle drift while tracking —
    // drift is a second motion source and made the head thrash.
    var heldFaceGaze by remember { mutableStateOf<Offset?>(null) }
    var recentlyTracking by remember { mutableStateOf(false) }
    LaunchedEffect(faceGazeX, faceGazeY) {
        if (faceGazeX != null && faceGazeY != null) {
            heldFaceGaze = Offset(faceGazeX, faceGazeY)
            recentlyTracking = true
        } else {
            delay(550)
            if (faceGazeX == null && faceGazeY == null) {
                heldFaceGaze = null
                recentlyTracking = false
            }
        }
    }
    val trackingNow = faceGazeX != null || heldFaceGaze != null || recentlyTracking

    // ── Idle gaze: real saccades (quick fixate → hold), not constant sine
    //    drift — a perpetual smooth wander reads as a mechanical search loop.
    val saccadeX = remember { Animatable(0f) }
    val saccadeY = remember { Animatable(0f) }
    LaunchedEffect(paused, trackingNow) {
        if (paused) return@LaunchedEffect
        if (trackingNow) {
            // Retire the idle wander, but GLIDE it out rather than snapping.
            // snapTo moved the gaze several pixels in a single frame the instant
            // a face was noticed — and again every time the detector re-noticed
            // it — while trackingWeight was still ramping in over 450ms. Two
            // mechanisms disagreeing about when the handover happens is what
            // made acquiring a face look like a jerk.
            launch { saccadeX.animateTo(0f, tween(450, easing = FastOutSlowInEasing)) }
            launch { saccadeY.animateTo(0f, tween(450, easing = FastOutSlowInEasing)) }
            return@LaunchedEffect
        }
        while (true) {
            // A cat stares at one spot, then glances at something else.
            delay(Random.nextLong(1100L, 3800L))
            val tx = (Random.nextFloat() * 2f - 1f) * 0.7f
            val ty = (Random.nextFloat() * 2f - 1f) * 0.45f
            val jump = hypot(tx - saccadeX.value, ty - saccadeY.value)
            // Anticipation: the eyes wind back a hair against a big glance
            // before making it. Two frames' worth of counter-move, far too
            // brief to read as its own motion — it just makes the glance land.
            if (jump > 0.5f) {
                val backX = saccadeX.value - (tx - saccadeX.value) * 0.10f
                val backY = saccadeY.value - (ty - saccadeY.value) * 0.10f
                val antX = launch { saccadeX.animateTo(backX, tween(70)) }
                val antY = launch { saccadeY.animateTo(backY, tween(70)) }
                antX.join(); antY.join()
            }
            // Saccadic blink: a real eye blinks through a large gaze shift, and
            // the blink is what hides the jump. REQUESTED, not performed here —
            // an Animatable has one owner, and a second coroutine calling
            // animateTo cancels the first one's animation, which throws
            // CancellationException inside the ambient blink loop and kills it
            // for good. The eyes then stopped blinking entirely, or froze
            // half-closed, at a random moment with nothing on screen to explain
            // it. The ambient loop owns the lid; this only asks.
            if (jump > 0.75f) blinkRequests.trySend(Unit)
            val moveX = launch { saccadeX.animateTo(tx, tween(140)) }
            val moveY = launch { saccadeY.animateTo(ty, tween(140)) }
            moveX.join()
            moveY.join()
        }
    }

    // ── Gaze: pupils follow the user's touch, then wander off ──────────────
    var touchGaze by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(touchGaze) {
        if (touchGaze != null) {
            delay(3200)
            touchGaze = null
        }
    }
    // Idle ambient drift is applied in the draw scope from clockSec so we do
    // not recompose 60×/s just to wander the pupils.
    val gazeTargetX = faceGazeX
        ?: heldFaceGaze?.x
        ?: touchGaze?.x
        ?: 0f
    val gazeTargetY = faceGazeY
        ?: heldFaceGaze?.y
        ?: touchGaze?.y
        ?: 0f
    // FaceTrack already publishes screen-space offsets (see VisionManager), so
    // there is nothing to rotate here. The swap that used to live at this line
    // keyed off the *window's* orientation while the camera's own targetRotation
    // was frozen at bind time — two different notions of "rotated" that
    // disagreed on every fold and after every turn, which is what broke
    // horizontal follow. One correction, at the source, is the whole fix.
    val screenGazeX = gazeTargetX
    val screenGazeY = gazeTargetY
    // Continuous spring (not a short tween) so each face sample glides into
    // the next — short tweens restarting every publish looked like dropped frames.
    // StiffnessMedium (not MediumLow) so the head actually tracks the user's
    // motion instead of floating behind it with a visible chase delay.
    val gazeSpec = if (trackingNow) {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )
    } else {
        tween(500)
    }
    val gazeX by animateFloatAsState(screenGazeX, gazeSpec, label = "gazeX")
    val gazeY by animateFloatAsState(screenGazeY, gazeSpec, label = "gazeY")
    // ── Eyes lead, head follows ────────────────────────────────────────────
    // The same value used to drive the pupils AND the head pan, so both
    // arrived together and the whole face moved as one rigid plate. Living
    // things point their eyes first and bring the skull around after — the
    // delay is small (~150ms) but its absence is most of why procedural faces
    // read as mechanical. A softer spring on the head gives the lag plus a
    // little settle at the end of the turn for free.
    val headFollowSpec = spring<Float>(dampingRatio = 0.70f, stiffness = Spring.StiffnessLow)
    val headFollowX by animateFloatAsState(screenGazeX, headFollowSpec, label = "headFollowX")
    val headFollowY by animateFloatAsState(screenGazeY, headFollowSpec, label = "headFollowY")
    // Ramped tracking weight. A face appearing/disappearing used to flip the
    // pan scale (0.06↔0.32) and kill idle gaze instantly, snapping the whole
    // head several px in one frame every time the detector toggled. Easing the
    // weight makes enter/leave-follow glide instead of step.
    val trackingWeight by animateFloatAsState(
        targetValue = if (trackingNow) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "trackingWeight"
    )

    // ── Follow-through: the ears trail the skull ───────────────────────────
    // Driven off head travel, not clip content, so it applies to every motion
    // the head makes — tracking a face, an idle weight shift, a performance.
    val earTrail = remember { Trailing(stiffness = 88f, bounce = 0.28f) }
    var earLag by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f
                else ((now - last) / 1_000_000_000f).coerceIn(1e-4f, 1f / 30f)
                last = now
                earLag = earTrail.update(headFollowX, dt, gain = 0.32f)
            }
        }
    }

    // ── Petting: stroking the cat makes it purr ────────────────────────────
    var lastPetAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lastPetAt) {
        if (lastPetAt != 0L) {
            delay(2600)
            lastPetAt = 0L
        }
    }
    val petted = lastPetAt != 0L

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { sz ->
                // Match draw-time fit so CatMotion scales stay consistent.
                faceRadiusPx = fitGodotFaceRadius(sz.width.toFloat(), sz.height.toFloat())
            }
            .semantics {
                contentDescription =
                    "Memba cat avatar, currently ${state.interactionState.name.lowercase()}"
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        touchGaze = normalizeGaze(offset, size.width, size.height)
                        onZoneClick(zoneAt(offset, size.width, size.height))
                    },
                    onLongPress = { offset ->
                        val zone = zoneAt(offset, size.width, size.height)
                        if (zone == AvatarZone.FOREHEAD) {
                            onZoneClick(AvatarZone.FOREHEAD)
                        } else {
                            // Long stroke anywhere else counts as a pet
                            lastPetAt = System.currentTimeMillis()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                    },
                    onDragEnd = {
                        // A decisive vertical drag is a navigation swipe, not a pet
                        if (abs(totalY) > abs(totalX) && abs(totalY) > 140f) {
                            if (totalY < 0) onSwipeUp() else onSwipeDown()
                        } else if (abs(totalX) > abs(totalY) && abs(totalX) > 280f) {
                            // Long decisive horizontal swipe = launcher navigation.
                            // Shorter strokes stay petting.
                            if (totalX < 0) onSwipeLeft() else onSwipeRight()
                        }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    totalX += dragAmount.x
                    totalY += dragAmount.y
                    touchGaze = normalizeGaze(change.position, size.width, size.height)
                    // Horizontal-ish stroking on the face = petting
                    if (abs(totalX) > abs(totalY) && abs(totalX) > 60f) {
                        lastPetAt = System.currentTimeMillis()
                    }
                }
            }
    ) {
        // Subscribe draw pass to the single vsync-locked clock (not composition).
        val _epoch = drawEpoch
        val tSec = clockSec

        // ── The live pose, assembled HERE rather than in composition ───────
        // Everything below reads per-frame values (shotHolder, animTHolder)
        // that change at vsync. Computing them in composition meant every
        // frame of every clip re-ran the whole composable and restarted seven
        // animateFloatAsState animations whose output was then discarded.
        // Reading them inside onDraw keeps the cost to a draw pass.
        val pose = when {
            shotHolder[0] != null -> shotHolder[0]!!.applyTo(basePose, faceRadiusPx)
            customAnim != null -> customAnim.let { clip ->
                CustomAnimations.sample(clip, animTHolder[0])
                    ?.let { with(CustomAnimations) { basePose.overlay(it) } }
            } ?: basePose
            else -> basePose
        }
        val effectivePose = if (petted) pose.copy(
            happyEyes = true,
            whiskerFlutter = true,
            earDroopDeg = 12f,
            mouth = if (pose.mouth == MouthMode.SPEAKING) MouthMode.SPEAKING else MouthMode.HAPPY,
        ) else pose
        // A clip's sampler already interpolates, so honour it directly; only
        // the idle path wants the spring blend.
        val eyeOpen = if (playingClip) pose.eyeOpen else eyeOpenAnim
        val pupilSize = if (playingClip) pose.pupilSize else pupilSizeAnim
        val gazeUp = if (playingClip) pose.pupilGazeUp else gazeUpAnim
        val headTilt = if (playingClip) pose.headTiltDeg else headTiltAnim
        val earPerk = if (playingClip) pose.earPerk else earPerkAnim
        val earDroop = if (playingClip) pose.earDroopDeg else earDroopAnim
        val faceAlpha = if (playingClip) (if (pose.dimmed) 0.55f else 1f) else faceAlphaAnim
        // Triangle / sine waves matching CatAnimationRegistry periods.
        fun triWave(periodSec: Float, from: Float, to: Float, phaseSec: Float = 0f): Float {
            if (periodSec <= 0f) return (from + to) * 0.5f
            val phase = (((tSec + phaseSec) / periodSec) % 1f)
            val tri = if (phase < 0.5f) phase * 2f else 2f - phase * 2f
            // Ease the triangle. A raw triangle moves at constant velocity and
            // turns a hard corner at each peak — no slow-in, no slow-out — and
            // that is the single loudest "this is generated" tell in ambient
            // motion. smoothstep gives every caller (breath, talk, mouth
            // wobble, halo) organic acceleration for free.
            return from + (to - from) * smoothStep01(tri)
        }
        fun sinWave(periodSec: Float, from: Float, to: Float, phaseSec: Float = 0f): Float {
            if (periodSec <= 0f) return (from + to) * 0.5f
            val mid = (from + to) * 0.5f
            val amp = (to - from) * 0.5f
            return mid + amp * kotlin.math.sin((tSec + phaseSec) * (2f * Math.PI.toFloat() / periodSec))
        }
        // Per-channel phase offsets break the phase-lock: every ambient motion
        // started at t=0, so the cycles ran in sync and read as one rigid,
        // metronomic loop. Offsets de-correlate them into independent motions.
        // Breath rides the skewed curve (slow inhale, faster exhale) rather than
        // a symmetric wave, and at ±2.6% instead of the old ±2% — measured on
        // device, the old amplitude sat under the threshold where the eye reads
        // it as motion at all, so the face looked held rather than resting.
        val breathe = 1f + (breathSkew(tSec / 3.7f) - 0.5f) * 0.052f
        val talk = triWave(0.16f, 0.15f, 1f)
        val whiskerSway = sinWave(1.5f, -1f, 1f, phaseSec = 2.1f)
        val mouthWobble = triWave(1.9f, 0.88f, 1.12f, phaseSec = 0.7f)
        val haloPulse = triWave(1.0f, 0.25f, 0.6f, phaseSec = 1.3f)
        val tailSway = sinWave(3.6f, -0.14f, 0.14f, phaseSec = 3.9f)
        val godotClockT = tSec  // seconds; Godot face uses sin(t * k)

        val w = size.width
        val h = size.height
        val s = min(w, h)

        // ── Ambient life: always on, never gated ───────────────────────────
        // Pure function of the vsync clock, so it costs no state and no
        // recomposition. This runs UNDER everything — resting face, one-shot
        // performances, speech — because a cat holding a pose is still
        // breathing. Previously the only always-on motion was a ±2% triangle
        // wave, and it was switched off entirely whenever a clip played.
        val ambR = fitGodotFaceRadius(w, h)
        val ambientEnergy = when {
            state.emotion == CompanionEmotion.SLEEPY -> 0.45f
            state.interactionState == InteractionState.LISTENING -> 1.15f
            state.interactionState == InteractionState.THINKING -> 0.85f
            else -> 1f
        }
        val ambient = Motion().also { ambientLife(tSec, ambR, ambientEnergy, it) }

        // Face center: gaze shifts the whole face slightly (with a tiny
        // constant drift so an always-on desk display never burns in).
        // body_bounce shifts the whole face up/down during animations. Clamped
        // exactly like its siblings (tailWag/tongue/sparkle) so no out-of-range or
        // non-finite value can ever reach the canvas transforms.
        val bounceY = effectivePose.bodyBounce.coerceIn(-1f, 1f) * s * 0.06f
        val leanShift = effectivePose.leanX.coerceIn(-1f, 1f) * s * 0.055f
        // Classic path: larger body pan when face-tracking so the whole face follows.
        // Pan scale ramps with trackingWeight so enter/leave follow glides.
        val bodyGaze = 0.03f + 0.07f * trackingWeight
        // Idle gaze wander only when not face-tracking. Driven by saccades
        // (fixate → hold → glance) instead of continuous sine drift; the
        // movement itself prevents burn-in on always-on displays.
        val idleGazeX = saccadeX.value * (1f - trackingWeight)
        val idleGazeY = saccadeY.value * (1f - trackingWeight)
        val cx = w / 2f + (gazeX + idleGazeX) * s * bodyGaze + leanShift + ambient.offX
        val cy = h * 0.47f + (gazeY + idleGazeY) * s * (0.02f + 0.04f * trackingWeight) -
            bounceY + ambient.offY

        val eyeDX = s * 0.26f
        val eyeW = s * 0.19f
        val baseEyeH = s * 0.24f
        val eyeH = (baseEyeH * eyeOpen * blink.value * breathe).coerceAtLeast(s * 0.012f)
        val eyeCy = cy - s * 0.06f
        val stroke = s * 0.014f

        // ── Godot Cat Assistant face (exact look + CatMotion) ───────────────
        if (useGodotFace) {
            // Transparent canvas — system wallpaper / desk shows through.
            // Fit full head (ears + chin) inside the canvas with room for
            // status bar / cutout and the mic controls — landscape used to
            // clip ear tips and stack the mic on the mouth.
            val R = ambR
            val faceCy = fitGodotFaceCenterY(w, h)
            // Breath and weight-shift now come from the shared ambient layer so
            // they are identical in both face styles and survive clip playback.
            val idleBob = ambient.offY
            val idleSway = ambient.offX
            // Face-track active → move the WHOLE head (pan + tilt), not just pupils.
            // Pupils only glance a little; the body carries the follow.
            val tracking = faceGazeX != null || heldFaceGaze != null || recentlyTracking
            // Whole-head follow scaled by the ramped tracking weight: entering
            // and leaving follow glides the head instead of snapping (the old
            // 0.42/18° hard toggles read as stepping).
            // The head rides headFollow* (the lagged value), the pupils ride
            // gaze* — that difference IS the eyes-lead-head read.
            val bodyPanX = headFollowX * R * (0.06f + 0.26f * trackingWeight)
            // Arc: a head that pans dead flat across the screen looks like a
            // slider. Lifting it slightly at the extremes puts the travel on a
            // shallow curve, as if pivoting on a neck.
            val bodyPanY = headFollowY * R * (0.04f + 0.18f * trackingWeight) -
                arcRise(headFollowX) * R * trackingWeight
            val bodyTilt = (headFollowX * 12f).coerceIn(-12f, 12f) * trackingWeight
            // Pupils track hard — body pan carries the head, iris must still
            // glance toward the user inside the sockets (0.50 looked static).
            // Blend on trackingWeight instead of branching on a boolean.
            //
            // Every other follow term (body pan, tilt, idle fade) already ramps
            // over ~450ms, but these two flipped instantly the moment the
            // detector changed its mind — so the eyes visibly clicked into and
            // out of follow mode, several times a second when a face was at the
            // edge of detection. A hard toggle inside a system of ramps is
            // exactly what "tracking is erratic" looks like.
            fun lerp(from: Float, to: Float, k: Float) = from + (to - from) * k
            val pupilGazeX = lerp(
                gazeX + idleGazeX * 0.55f, gazeX * 1.15f, trackingWeight,
            ).coerceIn(-1f, 1f)
            val pupilGazeY = lerp(
                gazeY + idleGazeY * 0.55f, gazeY * 1.15f, trackingWeight,
            ).coerceIn(-1f, 1f)
            // Voice drive: talk cycle is always running; only apply when listening/speaking.
            val speaking = effectivePose.mouth == MouthMode.SPEAKING
            val listening = state.interactionState ==
                com.example.meowmba.ui.companion.InteractionState.LISTENING
            val voice = talk.coerceIn(0f, 1f)
            val voiceEyeOpen = when {
                speaking -> (eyeOpen * (0.88f + 0.28f * voice)).coerceIn(0.15f, 1.35f)
                listening -> (eyeOpen * (1.0f + 0.18f * voice)).coerceIn(0.15f, 1.35f)
                else -> eyeOpen
            }
            val voicePupil = when {
                speaking -> (pupilSize * (0.85f + 0.45f * voice)).coerceIn(0.08f, 0.95f)
                listening -> (pupilSize * (0.95f + 0.35f * voice)).coerceIn(0.08f, 0.95f)
                else -> pupilSize
            }
            val liveTalking = speaking || listening
            // The resting face: ambient life + blink + ear twitch + voice drive
            // + face-tracking pan. Always built, never skipped.
            val restingPose = MotionPose(
                eyeOpen = (voiceEyeOpen * blink.value).coerceIn(0.12f, 1.35f),
                pupilSize = voicePupil,
                eyeWidthScale = if (speaking) 1f + 0.04f * voice else 1f,
                eyeHeightScale = if (speaking) 1f - 0.06f * voice else 1f,
                // Ramped like every other follow term, not toggled.
                eyeTiltDeg = gazeX * 4f * trackingWeight,
                lowerLid = if (effectivePose.happyEyes) 0.35f else if (speaking) 0.12f + 0.15f * voice else 0.10f,
                earPitchDeg = (earPerk - 1f) * 36f + if (listening) voice * 8f else 0f,
                earSplayDeg = 10f + earDroop * 0.35f,
                // Follow-through: the ears trail the skull and overshoot when
                // it stops, on top of their own ambient twitch.
                earSplitDeg = earTwitchL.value - earTwitchR.value + earLag * 26f,
                jawOpen = when {
                    speaking -> (0.28f + voice * 0.62f).coerceIn(0.2f, 0.95f)
                    listening -> 0.04f + voice * 0.06f // tiny reactive part
                    effectivePose.mouth == MouthMode.HAPPY -> 0.12f
                    else -> 0f
                },
                mouthWidth = if (speaking) 0.95f + 0.25f * voice else 1f,
                lipCurl = 0f,
                offsetX = idleSway + idleGazeX * R * 0.12f + effectivePose.leanX * R * 0.06f + bodyPanX,
                offsetY = idleBob + idleGazeY * R * 0.08f + effectivePose.bodyBounce * R * 0.08f + bodyPanY,
                headTiltDeg = headTilt + bodyTilt + ambient.tiltDeg,
                scaleDelta = ambient.scale,
                squash = ambient.squash,
            )

            // Compose the performance ON TOP of the resting face rather than
            // swapping to it. Two bugs died here:
            //  - a playing clip used to replace the whole pose, so for its full
            //    duration the head stopped breathing, stopped blinking and
            //    stopped tracking the user's face — the performance itself was
            //    the only thing moving, which is what made held expressions
            //    read as a still frame;
            //  - the clip used to be discarded outright while speaking or
            //    listening, so the face went expressionless at exactly the
            //    moment expression matters most.
            // Now the channels the clip owns (eyes, ears, jaw shape) win, and
            // everything ambient keeps running underneath it.
            val gPose = shotHolder[0]?.let { shot ->
                shot.copy(
                    // A cat holding a pose still blinks.
                    eyeOpen = (shot.eyeOpen * blink.value).coerceIn(0.05f, 1.6f),
                    // While the voice is live the jaw belongs to speech, but a
                    // clip that opens wider than the syllable keeps its shape.
                    jawOpen = if (liveTalking) {
                        kotlin.math.max(shot.jawOpen, restingPose.jawOpen)
                    } else {
                        shot.jawOpen
                    },
                    mouthWidth = if (liveTalking) restingPose.mouthWidth else shot.mouthWidth,
                    // Ambient ear twitch rides on top of the clip's ear split.
                    earSplitDeg = shot.earSplitDeg + restingPose.earSplitDeg,
                    // Breath, weight shift, gaze pan and face-follow are additive.
                    offsetX = shot.offsetX + restingPose.offsetX,
                    offsetY = shot.offsetY + restingPose.offsetY,
                    headTiltDeg = shot.headTiltDeg + bodyTilt + ambient.tiltDeg,
                    scaleDelta = shot.scaleDelta + ambient.scale,
                    squash = shot.squash + ambient.squash,
                )
            } ?: restingPose
            // Anchor at screen center band; pose offsets carry the whole face.
            val center = Offset(
                w / 2f + gPose.offsetX + effectivePose.leanX * R * 0.02f,
                faceCy + gPose.offsetY,
            )
            // Squash & stretch, on its own channel. scaleDelta stays uniform —
            // it means "nearer / further", and deforming the head for a lean-in
            // would read as the head growing taller rather than approaching.
            // The squash channel is the deformation: it flattens and widens on
            // an impact or the breath's compression, lengthens and narrows on
            // the rise, conserving the silhouette's area either way.
            val (squashX, squashY) = squashStretch(gPose.squash.coerceIn(-0.3f, 0.3f))
            rotate(gPose.headTiltDeg, pivot = center) {
                scale(scaleX = squashX, scaleY = squashY, pivot = center) {
                drawGodotCatFace(
                    origin = center,
                    R = R * (1f + gPose.scaleDelta).coerceIn(0.9f, 1.15f),
                    pose = gPose,
                    clock = GodotFaceClock(
                        t = godotClockT,
                        blink = blink.value,
                        earTwitchL = earTwitchL.value,
                        earTwitchR = earTwitchR.value,
                        gazeX = pupilGazeX,
                        gazeY = pupilGazeY - gazeUp * 0.25f,
                        talk = talk,
                        faceAlpha = faceAlpha,
                    ),
                    showTail = false,
                    eyeColor = Color(state.avatarEyeColor),
                    glowColor = Color(state.avatarGlowColor),
                    glowStrength = if (effectivePose.glowPulse) 0.42f else 0.28f,
                )
                }
            }
            return@Canvas
        }


        // ── LAYER -1: Tail (behind the face, wags during animations) ────────
        // Tail is ALWAYS drawn: at rest it sways gently via tailSway, during
        // clips the keyframed wag drives it. Invisible-at-rest is why the user
        // never saw a tail before.
        val restingSway = if (customAnim != null) 0f else tailSway
        val wag = (effectivePose.tailWag + restingSway).coerceIn(-1f, 1f)
        run {
            val tailBaseX = cx + s * 0.32f
            val tailBaseY = cy + s * 0.18f
            val tailLen = s * 0.22f
            // The tail hangs DOWNWARD from its base, curling slightly outward;
            // wag swings it left/right around the base (user asked for down).
            val tailAngle = 12f + wag * 35f  // gentle outward lean, ±35° wag
            val tailColor = EYE_COLOR.copy(alpha = 0.7f * faceAlpha)
            rotate(tailAngle, pivot = Offset(tailBaseX, tailBaseY)) {
                val tail = Path().apply {
                    moveTo(tailBaseX, tailBaseY)
                    quadraticBezierTo(
                        tailBaseX + tailLen * 0.45f, tailBaseY + tailLen * 0.7f,
                        tailBaseX + tailLen * 0.15f, tailBaseY + tailLen
                    )
                }
                drawPath(tail, tailColor, style = Stroke(width = s * 0.018f, cap = StrokeCap.Round))
            }
        }

        // ── LAYER 0: Halo glow ─────────────────────────────────────────────
        val glowColor = Color(effectivePose.glowColorHex)
        val glowAlpha = if (effectivePose.glowPulse) haloPulse else 0.22f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor.copy(alpha = glowAlpha * 0.5f), Color.Transparent),
                center = Offset(cx, cy),
                radius = s * 0.85f
            ),
            radius = s * 0.85f,
            center = Offset(cx, cy)
        )

        val eyeColor = EYE_COLOR.copy(alpha = EYE_COLOR.alpha * faceAlpha)

        // ── LAYER 1: Ears (with mic-privacy marks) ─────────────────────────
        drawEar(-1f, earTwitchL.value, cx, eyeCy, s, eyeDX, eyeW, baseEyeH,
            earPerk, earDroop, eyeColor, effectivePose.micOffMark)
        drawEar(1f, earTwitchR.value, cx, eyeCy, s, eyeDX, eyeW, baseEyeH,
            earPerk, earDroop, eyeColor, effectivePose.micOffMark)

        // ── LAYER 2: Eyes (happy arcs, or open eyes with tracking pupils) ──
        // The eyes are the only thing the style changes: same pose, same gaze,
        // same blink clock, so switching mid-animation is seamless.
        if (state.avatarStyle == AvatarStyle.NEO) {
            drawEyeNeo(-1f, -headTilt, cx, eyeCy, s, eyeDX, eyeW, baseEyeH, eyeH,
                blink.value, effectivePose, pupilSize, (gazeX + idleGazeX * 0.55f).coerceIn(-1f, 1f),
                (gazeY + idleGazeY * 0.55f).coerceIn(-1f, 1f), gazeUp, eyeColor)
            drawEyeNeo(1f, headTilt, cx, eyeCy, s, eyeDX, eyeW, baseEyeH, eyeH,
                blink.value, effectivePose, pupilSize, (gazeX + idleGazeX * 0.55f).coerceIn(-1f, 1f),
                (gazeY + idleGazeY * 0.55f).coerceIn(-1f, 1f), gazeUp, eyeColor)
        } else {
            drawEye(-1f, -headTilt, cx, eyeCy, s, eyeDX, eyeW, baseEyeH, eyeH,
                blink.value, effectivePose, pupilSize, (gazeX + idleGazeX * 0.55f).coerceIn(-1f, 1f),
                (gazeY + idleGazeY * 0.55f).coerceIn(-1f, 1f), gazeUp, eyeColor)
            drawEye(1f, headTilt, cx, eyeCy, s, eyeDX, eyeW, baseEyeH, eyeH,
                blink.value, effectivePose, pupilSize, (gazeX + idleGazeX * 0.55f).coerceIn(-1f, 1f),
                (gazeY + idleGazeY * 0.55f).coerceIn(-1f, 1f), gazeUp, eyeColor)
        }

        // ── LAYER 3: Nose ──────────────────────────────────────────────────
        val noseY = cy + s * 0.115f
        val noseS = s * 0.032f
        val nose = Path().apply {
            moveTo(cx - noseS, noseY - noseS * 0.7f)
            lineTo(cx + noseS, noseY - noseS * 0.7f)
            lineTo(cx, noseY + noseS * 0.9f)
            close()
        }
        drawPath(nose, NOSE_COLOR.copy(alpha = NOSE_COLOR.alpha * faceAlpha))

        // ── LAYER 3b: Paw (visible when head tilts down, e.g. paw lick) ────
        // The paw fades in proportionally with head tilt — a 10° tilt shows
        // it at full alpha, below that it cross-fades from invisible.
        val pawAlpha = ((headTilt / 10f).coerceIn(0f, 1f) * faceAlpha)
        if (pawAlpha > 0.01f) {
            val pawCx = cx + s * 0.18f * (if (headTilt > 0) 1f else -1f)
            val pawCy = cy + s * 0.28f
            val pawS = s * 0.055f
            val pawColor = eyeColor.copy(alpha = pawAlpha)
            // Main pad (oval)
            drawOval(
                color = pawColor,
                topLeft = Offset(pawCx - pawS * 0.7f, pawCy - pawS * 0.4f),
                size = Size(pawS * 1.4f, pawS * 0.9f),
            )
            // Three toe beans
            for (i in -1..1) {
                drawCircle(
                    color = pawColor,
                    radius = pawS * 0.28f,
                    center = Offset(pawCx + i * pawS * 0.5f, pawCy - pawS * 0.7f),
                )
            }
        }

        // ── LAYER 4: Whiskers (slow sway, fast flutter while talking) ──────
        val energetic = effectivePose.whiskerFlutter
        val swayAmp = s * (if (energetic) 0.016f else 0.009f)
        val sway = if (energetic) talk * 2f - 1f else whiskerSway
        for (side in listOf(-1f, 1f)) {
            for (i in -1..1) {
                val y0 = noseY + i * s * 0.028f
                val phase = sway * (1f - 0.3f * (i + 1))
                drawLine(
                    color = WHISKER_COLOR.copy(alpha = WHISKER_COLOR.alpha * faceAlpha),
                    start = Offset(cx + side * s * 0.10f, y0),
                    end = Offset(
                        cx + side * (s * 0.40f + phase * s * 0.004f),
                        y0 + i * s * 0.035f + phase * swayAmp * side
                    ),
                    strokeWidth = s * 0.006f,
                    cap = StrokeCap.Round
                )
            }
        }

        // ── LAYER 5: Mouth (speech energy from TTS word boundaries) ────────
        val mouthY = noseY + s * 0.055f
        drawMouth(
            effectivePose.mouth, cx, mouthY, s, stroke, talk, mouthWobble, eyeColor,
            // Read inside the draw lambda only — see the param's doc.
            mouthEnergy?.value ?: 0f
        )

        // ── LAYER 6: Tongue (licking animations) ────────────────────────────
        val tongue = effectivePose.tongueOut.coerceIn(0f, 1f)
        if (tongue > 0.01f) {
            val tongueColor = Color(0xFFFF6B8A).copy(alpha = tongue * faceAlpha)
            val tongueLen = s * 0.09f * tongue
            val tongueW = s * 0.028f * tongue
            val tongueTop = mouthY + s * 0.01f
            val tonguePath = Path().apply {
                moveTo(cx - tongueW, tongueTop)
                quadraticBezierTo(
                    cx - tongueW * 1.2f, tongueTop + tongueLen * 0.6f,
                    cx, tongueTop + tongueLen
                )
                quadraticBezierTo(
                    cx + tongueW * 1.2f, tongueTop + tongueLen * 0.6f,
                    cx + tongueW, tongueTop
                )
                close()
            }
            drawPath(tonguePath, tongueColor)
        }

        // ── LAYER 7: Sparkle particles ──────────────────────────────────────
        val sparkleIntensity = effectivePose.sparkle.coerceIn(0f, 1f)
        if (sparkleIntensity > 0.01f) {
            val sparkleColor = Color(0xFFFFF3BF).copy(alpha = sparkleIntensity * faceAlpha)
            val seed = (animTHolder[0] / 120).toInt()  // changes every 120ms for twinkling
            val count = (sparkleIntensity * 8).toInt().coerceAtLeast(2)
            for (i in 0 until count) {
                val hash = (seed * 31 + i * 7) and 0x7FFFFFFF
                val sx = cx + (((hash % 1000) / 1000f) - 0.5f) * s * 0.7f
                val sy = cy + (((hash / 1000 % 1000) / 1000f) - 0.5f) * s * 0.7f
                val radius = s * (0.005f + (hash % 100) / 100f * 0.008f)
                drawCircle(color = sparkleColor, radius = radius, center = Offset(sx, sy))
            }
        }
    }
}

// ── Geometry helpers shared by drawing and hit-testing ──────────────────────

private fun faceCenter(w: Float, h: Float): Pair<Float, Float> =
    Pair(w / 2f, h * 0.47f)

/** Convert a screen position into a normalized gaze target in [-1, 1]. */
private fun normalizeGaze(offset: Offset, w: Int, h: Int): Offset {
    val s = min(w.toFloat(), h.toFloat())
    val (cx, cy) = faceCenter(w.toFloat(), h.toFloat())
    return Offset(
        ((offset.x - cx) / (s * 0.5f)).coerceIn(-1f, 1f),
        ((offset.y - cy) / (s * 0.5f)).coerceIn(-1f, 1f)
    )
}

/**
 * Map a tap position to an avatar privacy/interaction zone using the exact
 * same geometry the face is drawn with, so zones always line up with pixels.
 */
private fun zoneAt(offset: Offset, w: Int, h: Int): AvatarZone {
    val s = min(w.toFloat(), h.toFloat())
    val (cx, cy) = faceCenter(w.toFloat(), h.toFloat())
    val eyeDX = s * 0.26f
    val eyeW = s * 0.19f
    val baseEyeH = s * 0.24f
    val eyeCy = cy - s * 0.06f
    val earBaseY = eyeCy - baseEyeH * 0.75f
    val noseY = cy + s * 0.115f
    val mouthY = noseY + s * 0.055f

    // Ears
    if (hypot(offset.x - (cx - eyeDX), offset.y - (earBaseY - s * 0.08f)) < s * 0.24f) {
        return AvatarZone.EAR_LEFT
    }
    if (hypot(offset.x - (cx + eyeDX), offset.y - (earBaseY - s * 0.08f)) < s * 0.24f) {
        return AvatarZone.EAR_RIGHT
    }
    // Eyes
    if (hypot(offset.x - (cx - eyeDX), offset.y - eyeCy) < eyeW * 0.85f) {
        return AvatarZone.EYE_LEFT
    }
    if (hypot(offset.x - (cx + eyeDX), offset.y - eyeCy) < eyeW * 0.85f) {
        return AvatarZone.EYE_RIGHT
    }
    // Mouth
    if (abs(offset.x - cx) < s * 0.13f && abs(offset.y - mouthY) < s * 0.08f) {
        return AvatarZone.MOUTH
    }
    // Forehead (between the ears, above the eyes)
    if (hypot(offset.x - cx, offset.y - (cy - s * 0.30f)) < s * 0.15f) {
        return AvatarZone.FOREHEAD
    }
    return AvatarZone.BACKGROUND
}

// ── Drawing layers ──────────────────────────────────────────────────────────

private fun DrawScope.drawEar(
    side: Float,
    twitchDeg: Float,
    cx: Float,
    eyeCy: Float,
    s: Float,
    eyeDX: Float,
    eyeW: Float,
    baseEyeH: Float,
    earPerk: Float,
    earDroop: Float,
    eyeColor: Color,
    micOffMark: Boolean
) {
    val baseY = eyeCy - baseEyeH * 0.75f
    val innerX = cx + side * (eyeDX - eyeW * 0.45f)
    val outerX = cx + side * (eyeDX + eyeW * 0.75f)
    val tipX = cx + side * (eyeDX + eyeW * 0.35f)
    val tipY = baseY - s * 0.20f * earPerk
    val stroke = s * 0.014f

    rotate(side * (earDroop + twitchDeg), pivot = Offset((innerX + outerX) / 2f, baseY)) {
        val ear = Path().apply {
            moveTo(innerX, baseY)
            lineTo(tipX, tipY)
            lineTo(outerX, baseY + s * 0.02f)
        }
        drawPath(ear, eyeColor, style = Stroke(width = stroke, cap = StrokeCap.Round))

        val inner = Path().apply {
            moveTo(innerX + side * eyeW * 0.18f, baseY - s * 0.008f)
            lineTo(tipX, tipY + s * 0.045f)
            lineTo(outerX - side * eyeW * 0.18f, baseY + s * 0.008f)
            close()
        }
        drawPath(inner, NOSE_COLOR.copy(alpha = 0.35f))

        // Mic privacy: explicit "X" mark on each ear
        if (micOffMark) {
            val ex = tipX - side * (eyeW * 0.1f)
            val ey = tipY + s * 0.08f
            val xs = s * 0.015f
            drawLine(WHISKER_COLOR, Offset(ex - xs, ey - xs), Offset(ex + xs, ey + xs), stroke)
            drawLine(WHISKER_COLOR, Offset(ex + xs, ey - xs), Offset(ex - xs, ey + xs), stroke)
        }
    }
}

private fun DrawScope.drawEye(
    side: Float,
    tiltDeg: Float,
    cx: Float,
    eyeCy: Float,
    s: Float,
    eyeDX: Float,
    eyeW: Float,
    baseEyeH: Float,
    eyeH: Float,
    blink: Float,
    pose: AvatarPose,
    pupilSize: Float,
    gazeX: Float,
    gazeY: Float,
    gazeUp: Float,
    eyeColor: Color
) {
    val ecx = cx + side * eyeDX
    val corner = CornerRadius(eyeW * 0.5f, eyeW * 0.5f)

    rotate(tiltDeg, pivot = Offset(ecx, eyeCy)) {
        if (pose.happyEyes && blink > 0.5f) {
            // Happy "^^" arcs
            drawArc(
                color = eyeColor,
                startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(ecx - eyeW / 2f, eyeCy - baseEyeH * 0.1f),
                size = Size(eyeW, baseEyeH * 0.28f),
                style = Stroke(width = eyeW * 0.3f, cap = StrokeCap.Round)
            )
        } else {
            // Soft outer glow plate
            drawRoundRect(
                color = eyeColor.copy(alpha = 0.18f * eyeColor.alpha),
                topLeft = Offset(ecx - eyeW * 0.62f, eyeCy - eyeH / 2f - eyeW * 0.12f),
                size = Size(eyeW * 1.24f, eyeH + eyeW * 0.24f),
                cornerRadius = corner
            )
            drawRoundRect(
                color = eyeColor,
                topLeft = Offset(ecx - eyeW / 2f, eyeCy - eyeH / 2f),
                size = Size(eyeW, eyeH),
                cornerRadius = corner
            )
            if (blink > 0.3f) {
                val pw = eyeW * pupilSize
                val ph = eyeH * 0.82f
                // Pupils track the gaze target (touch position or idle wander);
                // THINKING adds an upward bias.
                val px = ecx + gazeX * eyeW * 0.22f
                val py = eyeCy + gazeY * eyeH * 0.14f - gazeUp * eyeH * 0.3f
                drawRoundRect(
                    color = PUPIL_COLOR,
                    topLeft = Offset(px - pw / 2f, py - ph / 2f),
                    size = Size(pw, ph),
                    cornerRadius = CornerRadius(pw / 2f, pw / 2f)
                )

                // Camera privacy: explicit "X" inside the pupil
                if (pose.cameraOffMark) {
                    val xSize = pw * 0.6f
                    drawLine(
                        color = eyeColor.copy(alpha = 0.9f * eyeColor.alpha),
                        start = Offset(px - xSize, py - xSize),
                        end = Offset(px + xSize, py + xSize),
                        strokeWidth = s * 0.008f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = eyeColor.copy(alpha = 0.9f * eyeColor.alpha),
                        start = Offset(px + xSize, py - xSize),
                        end = Offset(px - xSize, py + xSize),
                        strokeWidth = s * 0.008f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawMouth(
    mode: MouthMode,
    cx: Float,
    mouthY: Float,
    s: Float,
    stroke: Float,
    talk: Float,
    mouthWobble: Float,
    eyeColor: Color,
    mouthEnergy: Float = 0f
) {
    when (mode) {
        MouthMode.SPEAKING -> {
            // Mouth rides the base talk cycle, amplified by real speech energy
            // (TTS word boundaries). With no boundary callbacks (older API or
            // mock pipeline) the energy floor keeps the motion alive.
            val energy = 0.35f + 0.65f * mouthEnergy
            val mh = s * (0.015f + 0.05f * talk * energy)
            val mw = s * 0.14f
            drawRoundRect(
                color = eyeColor,
                topLeft = Offset(cx - mw / 2f, mouthY - mh / 2f + s * 0.02f),
                size = Size(mw, mh),
                cornerRadius = CornerRadius(mh / 2f, mh / 2f)
            )
        }
        MouthMode.MUTED -> {
            // Closed mouth + explicit muted "X"
            val xs = s * 0.02f
            drawLine(eyeColor.copy(alpha = 0.6f * eyeColor.alpha),
                Offset(cx - xs, mouthY + s * 0.01f - xs),
                Offset(cx + xs, mouthY + s * 0.01f + xs), stroke, cap = StrokeCap.Round)
            drawLine(eyeColor.copy(alpha = 0.6f * eyeColor.alpha),
                Offset(cx + xs, mouthY + s * 0.01f - xs),
                Offset(cx - xs, mouthY + s * 0.01f + xs), stroke, cap = StrokeCap.Round)
        }
        MouthMode.HAPPY, MouthMode.IDLE -> {
            val arcW = s * 0.062f * (2f - mouthWobble).coerceIn(0.9f, 1.1f)
            val arcH = s * (if (mode == MouthMode.HAPPY) 0.055f else 0.038f) * mouthWobble
            for (leftEdge in listOf(cx - arcW, cx)) {
                drawArc(
                    color = eyeColor,
                    startAngle = 0f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(leftEdge, mouthY),
                    size = Size(arcW, arcH),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
    }
}
