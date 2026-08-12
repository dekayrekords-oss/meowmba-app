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
internal fun fitGodotFaceRadius(w: Float, h: Float): Float {
    val landscape = w > h * 1.15f
    // Landscape has little vertical room — use tight margins so the head fills
    // the short axis; portrait keeps mic/status headroom.
    val topMargin = if (landscape) h * 0.07f else h * 0.07f
    val bottomMargin = if (landscape) h * 0.12f else h * 0.15f
    val sideMargin = if (landscape) w * 0.10f else w * 0.05f
    val availW = (w - 2f * sideMargin).coerceAtLeast(1f)
    val availH = (h - topMargin - bottomMargin).coerceAtLeast(1f)
    // Full face ~1.85R tall (ears+chin). Landscape uses a tighter budget so R
    // is not crushed by over-padding (was 2.05 + 42% margins → tiny head).
    val heightBudget = if (landscape) 1.70f else 1.92f
    val r = min(availW / 1.70f, availH / heightBudget)
    val maxFrac = if (landscape) 0.54f else 0.46f
    return r.coerceIn(120f, min(w, h) * maxFrac)
}

internal fun fitGodotFaceCenterY(w: Float, h: Float): Float {
    val landscape = w > h * 1.15f
    val topMargin = if (landscape) h * 0.07f else h * 0.07f
    val bottomMargin = if (landscape) h * 0.12f else h * 0.15f
    // Slightly above geometric mid so the mic row doesn't crowd the chin.
    val bandMid = (topMargin + (h - bottomMargin)) * 0.5f
    return if (landscape) bandMid - h * 0.02f else bandMid
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
    faceYawDeg: Float? = null,
    facePitchDeg: Float? = null,
    faceRollDeg: Float? = null,
    faceScale: Float? = null,
    /** Lateral force on the phone, -1..1 (+ = pushed right). Turns lean the head. */
    motionLean: Float = 0f,
    /** Longitudinal force, -1..1 (+ = braking throw). Braking lurches the head. */
    motionLurch: Float = 0f,
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
    var animT by remember { mutableLongStateOf(0L) }
    LaunchedEffect(customAnim) {
        if (customAnim != null) {
            var start = -1L
            while (true) {
                withFrameMillis { now ->
                    if (start < 0L) start = now
                    animT = now - start
                }
                if (animT > customAnim.durationMs) break
            }
        }
    }

    // Godot face / classic idle: ONE vsync-locked draw clock (not 9 infinite transitions).
    // CatMotionEngine is ONE-SHOT only for named expressions.
    val motionEngine = remember { CatMotionEngine() }
    var shotPose by remember { mutableStateOf<MotionPose?>(null) }
    var faceRadiusPx by remember { mutableStateOf(200f) }
    val useGodotFace = state.avatarStyle == AvatarStyle.NEO
    // Single ambient clock (seconds). Advanced at ~60 fps and only while visible.
    // Reading this in the Canvas draw lambda invalidates draw only — not a
    // full-composition thrash of 9 rememberInfiniteTransition states @ vsync.
    var clockSec by remember { mutableFloatStateOf(0f) }
    var drawEpoch by remember { mutableLongStateOf(0L) }
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var lastNs = 0L
        var accum = 0.0
        var baseSec = 0.0
        // Frame-lock to the display refresh (~60Hz on this panel). At 30fps each
        // pose held for two vsyncs and motion read steppy; at 60fps every pose
        // change lands on a vsync so the face moves as smoothly as the screen.
        // Accumulation is in Double with an epsilon: the Float 1f/60f boundary
        // sits just above a single 60Hz vsync, which silently ticked every 2nd
        // vsync and kept the clock at ~30fps.
        val frame = 1.0 / 60.0
        var ticks = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNs == 0L) lastNs = now
                val dt = ((now - lastNs) / 1_000_000_000.0).coerceIn(0.0, 0.05)
                lastNs = now
                accum += dt
                // One tick per vsync, carrying the remainder so a hitch never
                // permanently slows the ambient cycle.
                if (accum >= frame - 1e-7) {
                    baseSec += frame
                    clockSec = baseSec.toFloat()
                    drawEpoch++
                    accum -= frame
                    ticks++
                    if (ticks % 120 == 0L) {
                        android.util.Log.i("AV", "clock=${"%.3f".format(clockSec)} ticks=$ticks")
                    }
                }
            }
        }
    }
    LaunchedEffect(composed, paused) {
        if (paused || composed == null) {
            motionEngine.stop()
            shotPose = null
            return@LaunchedEffect
        }
        motionEngine.play(composed)
        var last = 0L
        while (true) {
            var frame: MotionPose? = null
            withFrameMillis { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceIn(0f, 1f / 60f)
                last = now
                frame = motionEngine.update(dt, faceRadiusPx.coerceIn(80f, 800f))
                // Publish every vsync so one-shot keyframes move smoothly.
                shotPose = frame
            }
            if (frame == null) break
        }
        shotPose = null
    }

    val pose = when {
        shotPose != null -> shotPose!!.applyTo(basePose, faceRadiusPx)
        customAnim != null -> customAnim.let { clip ->
            CustomAnimations.sample(clip, animT)?.let { with(CustomAnimations) { basePose.overlay(it) } }
        } ?: basePose
        else -> basePose
    }

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
    val eyeOpenAnim by animateFloatAsState(pose.eyeOpen, blendSpec, label = "eyeOpen")
    val pupilSizeAnim by animateFloatAsState(pose.pupilSize, blendSpec, label = "pupil")
    val gazeUpAnim by animateFloatAsState(pose.pupilGazeUp, blendSpec, label = "gazeUp")
    val headTiltAnim by animateFloatAsState(pose.headTiltDeg, blendSpec, label = "tilt")
    val earPerkAnim by animateFloatAsState(pose.earPerk, blendSpec, label = "earPerk")
    val earDroopAnim by animateFloatAsState(pose.earDroopDeg, blendSpec, label = "earDroop")
    // Dim on its own short tween — a spring could overshoot alpha past 1.
    val dimSpec = if (playingClip) tween<Float>(16) else tween<Float>(250)
    val faceAlphaAnim by animateFloatAsState(if (pose.dimmed) 0.55f else 1f, dimSpec, label = "dim")
    val eyeOpen = if (playingClip) pose.eyeOpen else eyeOpenAnim
    val pupilSize = if (playingClip) pose.pupilSize else pupilSizeAnim
    val gazeUp = if (playingClip) pose.pupilGazeUp else gazeUpAnim
    val headTilt = if (playingClip) pose.headTiltDeg else headTiltAnim
    val earPerk = if (playingClip) pose.earPerk else earPerkAnim
    val earDroop = if (playingClip) pose.earDroopDeg else earDroopAnim
    val faceAlpha = if (playingClip) (if (pose.dimmed) 0.55f else 1f) else faceAlphaAnim

    // ── Ambient animation layers (timings from the registry) ───────────────
    val blink = remember { Animatable(1f) }
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        val spec = CatAnimationRegistry.spec("idle.blink")
        while (true) {
            delay(Random.nextLong(spec.minDelayMs.toLong(), spec.maxDelayMs.toLong()))
            blink.animateTo(0.06f, tween(90))
            blink.animateTo(1f, tween(110))
            // Occasional double-blink, like a real cat
            if (Random.nextInt(4) == 0) {
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
    // Held rotation + distance, in sync with the held gaze (same 550ms hold).
    var heldYaw by remember { mutableStateOf(0f) }
    var heldPitch by remember { mutableStateOf(0f) }
    var heldRoll by remember { mutableStateOf(0f) }
    var heldScale by remember { mutableStateOf(1f) }
    LaunchedEffect(faceGazeX, faceGazeY, faceYawDeg, facePitchDeg, faceRollDeg, faceScale) {
        if (faceGazeX != null && faceGazeY != null) {
            heldFaceGaze = Offset(faceGazeX, faceGazeY)
            heldYaw = faceYawDeg ?: heldYaw
            heldPitch = facePitchDeg ?: heldPitch
            heldRoll = faceRollDeg ?: heldRoll
            heldScale = faceScale ?: heldScale
            recentlyTracking = true
        } else {
            delay(550)
            if (faceGazeX == null && faceGazeY == null) {
                heldFaceGaze = null
                recentlyTracking = false
                heldYaw = 0f
                heldPitch = 0f
                heldRoll = 0f
                heldScale = 1f
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
            // While face-tracked, pin idle gaze at center so it can't snap back
            // to a mid-saccade value when tracking ends.
            saccadeX.snapTo(0f)
            saccadeY.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            // A cat stares at one spot, then glances at something else.
            delay(Random.nextLong(1100L, 3800L))
            val tx = (Random.nextFloat() * 2f - 1f) * 0.7f
            val ty = (Random.nextFloat() * 2f - 1f) * 0.45f
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
    val gazeX by animateFloatAsState(gazeTargetX, gazeSpec, label = "gazeX")
    val gazeY by animateFloatAsState(gazeTargetY, gazeSpec, label = "gazeY")
    // Ramped tracking weight. A face appearing/disappearing used to flip the
    // pan scale (0.06↔0.32) and kill idle gaze instantly, snapping the whole
    // head several px in one frame every time the detector toggled. Easing the
    // weight makes enter/leave-follow glide instead of step.
    val trackingWeight by animateFloatAsState(
        targetValue = if (trackingNow) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "trackingWeight"
    )

    // ── 6-DOF: yaw/pitch/roll/distance glide in like gaze does ──────────────
    // Each face sample moves the spring target; the spring keeps the head from
    // stepping per detector frame. Values are normalized (-1..1) and the
    // tracking weight decides how hard the head follows.
    val rotSpec = if (trackingNow) {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )
    } else {
        tween(450)
    }
    val yawN by animateFloatAsState(
        com.example.meowmba.core.vision.FacePose.normalizedYaw(heldYaw), rotSpec, label = "yawN"
    )
    val pitchN by animateFloatAsState(
        com.example.meowmba.core.vision.FacePose.normalizedPitch(heldPitch), rotSpec, label = "pitchN"
    )
    val rollN by animateFloatAsState(
        com.example.meowmba.core.vision.FacePose.normalizedRoll(heldRoll), rotSpec, label = "rollN"
    )
    val headScale by animateFloatAsState(heldScale, rotSpec, label = "headScale")

    // ── Accelerometer forces: turn lean + brake lurch ──────────────────────
    // Already low-passed in MotionSense and normalized in the ViewModel; a
    // spring here just keeps per-sample steps from reading as jitter. These
    // do NOT gate on face tracking — the phone moves whether or not it sees
    // a face.
    val forceSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val leanAnim by animateFloatAsState(motionLean, forceSpec, label = "motionLean")
    val lurchAnim by animateFloatAsState(motionLurch, forceSpec, label = "motionLurch")

    // ── Petting: stroking the cat makes it purr ────────────────────────────
    var lastPetAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lastPetAt) {
        if (lastPetAt != 0L) {
            delay(2600)
            lastPetAt = 0L
        }
    }
    val petted = lastPetAt != 0L
    val effectivePose = if (petted) pose.copy(
        happyEyes = true,
        whiskerFlutter = true,
        earDroopDeg = 12f,
        mouth = if (pose.mouth == MouthMode.SPEAKING) MouthMode.SPEAKING else MouthMode.HAPPY
    ) else pose

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
        // Triangle / sine waves matching CatAnimationRegistry periods.
        fun triWave(periodSec: Float, from: Float, to: Float, phaseSec: Float = 0f): Float {
            if (periodSec <= 0f) return (from + to) * 0.5f
            val phase = (((tSec + phaseSec) / periodSec) % 1f)
            val tri = if (phase < 0.5f) phase * 2f else 2f - phase * 2f
            return from + (to - from) * tri
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
        val breathe = triWave(2.6f, 0.98f, 1.02f)
        val talk = triWave(0.16f, 0.15f, 1f)
        val whiskerSway = sinWave(1.5f, -1f, 1f, phaseSec = 2.1f)
        val mouthWobble = triWave(1.9f, 0.88f, 1.12f, phaseSec = 0.7f)
        val haloPulse = triWave(1.0f, 0.25f, 0.6f, phaseSec = 1.3f)
        val tailSway = sinWave(3.6f, -0.14f, 0.14f, phaseSec = 3.9f)
        val godotClockT = tSec  // seconds; Godot face uses sin(t * k)

        val w = size.width
        val h = size.height
        val s = min(w, h)

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
        val cx = w / 2f + (gazeX + idleGazeX) * s * bodyGaze + leanShift +
            yawN * trackingWeight * s * 0.08f + leanAnim * s * 0.09f
        val cy = h * 0.47f + (gazeY + idleGazeY) * s * (0.02f + 0.04f * trackingWeight) -
            bounceY + pitchN * trackingWeight * s * 0.06f + lurchAnim * s * 0.05f

        // 6-DOF: roll rotates the classic head through the same headTilt channel
        // the eyes already use; distance scales the head geometry up/down.
        // Accelerometer forces ride along: turns lean+roll, braking throws forward.
        val headRotate = headTilt + rollN * 14f * trackingWeight + leanAnim * 12f
        val headSizeFactor = com.example.meowmba.core.vision.FacePose.headSizeFactor(headScale) *
            (1f + lurchAnim * 0.08f).coerceIn(0.92f, 1.08f)

        val eyeDX = s * 0.26f * headSizeFactor
        val eyeW = s * 0.19f * headSizeFactor
        val baseEyeH = s * 0.24f * headSizeFactor
        val eyeH = (baseEyeH * eyeOpen * blink.value * breathe).coerceAtLeast(s * 0.012f)
        val eyeCy = cy - s * 0.06f * headSizeFactor
        val stroke = s * 0.014f * headSizeFactor

        // ── Godot Cat Assistant face (exact look + CatMotion) ───────────────
        if (useGodotFace) {
            // Transparent canvas — system wallpaper / desk shows through.
            // Fit full head (ears + chin) inside the canvas with room for
            // status bar / cutout and the mic controls — landscape used to
            // clip ear tips and stack the mic on the mouth.
            val R = fitGodotFaceRadius(w, h)
            val faceCy = fitGodotFaceCenterY(w, h)
            val idleBob = (breathe - 1f) * R * 2.2f
            // Whole-head sway kept faint — the saccades (idleGaze) carry the
            // "looking around" motion now; a constant sine sway reads fake.
            val idleSway = kotlin.math.sin(godotClockT * 0.72f + 1.9f) * R * 0.014f
            // Face-track active → move the WHOLE head (pan + tilt), not just pupils.
            // Pupils only glance a little; the body carries the follow.
            val tracking = faceGazeX != null || heldFaceGaze != null || recentlyTracking
            // Whole-head follow scaled by the ramped tracking weight: entering
            // and leaving follow glides the head instead of snapping (the old
            // 0.42/18° hard toggles read as stepping).
            // 6-DOF: yaw turns the head sideways (pan + slight lean), pitch nods
            // it (vertical pan), roll actually rotates it, scale leans in/out.
            // Accelerometer forces are independent of face tracking: a turn
            // slides + rolls the head, braking throws it forward and closer.
            val bodyPanX = gazeX * R * (0.06f + 0.26f * trackingWeight) +
                yawN * trackingWeight * R * 0.22f + leanAnim * R * 0.30f
            val bodyPanY = gazeY * R * (0.04f + 0.18f * trackingWeight) +
                pitchN * trackingWeight * R * 0.16f + lurchAnim * R * 0.20f
            val bodyTilt = ((gazeX * 12f + rollN * 16f).coerceIn(-20f, 20f) * trackingWeight +
                leanAnim * 14f).coerceIn(-24f, 24f)
            val godotHeadSize = com.example.meowmba.core.vision.FacePose.headSizeFactor(headScale) *
                (1f + lurchAnim * 0.08f).coerceIn(0.92f, 1.08f)
            // Pupils track hard — body pan carries the head, iris must still
            // glance toward the user inside the sockets (0.50 looked static).
            val pupilGazeX = if (tracking) {
                (gazeX * 1.15f).coerceIn(-1f, 1f)
            } else {
                (gazeX + idleGazeX * 0.55f).coerceIn(-1f, 1f)
            }
            val pupilGazeY = if (tracking) {
                (gazeY * 1.15f).coerceIn(-1f, 1f)
            } else {
                (gazeY + idleGazeY * 0.55f).coerceIn(-1f, 1f)
            }
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
            // Prefer live talking pose over a finished one-shot so the jaw always
            // moves while TTS/mic is active (shotPose was freezing the mouth).
            val liveTalking = speaking || listening
            val gPose = if (shotPose != null && !liveTalking) shotPose!! else MotionPose(
                eyeOpen = (voiceEyeOpen * blink.value).coerceIn(0.12f, 1.35f),
                pupilSize = voicePupil,
                eyeWidthScale = if (speaking) 1f + 0.04f * voice else 1f,
                eyeHeightScale = if (speaking) 1f - 0.06f * voice else 1f,
                eyeTiltDeg = if (tracking) gazeX * 4f else 0f,
                lowerLid = if (effectivePose.happyEyes) 0.35f else if (speaking) 0.12f + 0.15f * voice else 0.10f,
                earPitchDeg = (earPerk - 1f) * 36f + if (listening) voice * 8f else 0f,
                earSplayDeg = 10f + earDroop * 0.35f,
                earSplitDeg = earTwitchL.value - earTwitchR.value,
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
                headTiltDeg = headTilt + bodyTilt,
                scaleDelta = 0f,
            )
            // Anchor at screen center band; pose offsets carry the whole face.
            val center = Offset(
                w / 2f + gPose.offsetX + effectivePose.leanX * R * 0.02f,
                faceCy + gPose.offsetY,
            )
            rotate(gPose.headTiltDeg, pivot = center) {
                drawGodotCatFace(
                    origin = center,
                    R = R * godotHeadSize * (1f + gPose.scaleDelta).coerceIn(0.9f, 1.15f),
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
        drawEar(-1f, earTwitchL.value, cx, eyeCy, s * headSizeFactor, eyeDX, eyeW, baseEyeH,
            earPerk, earDroop, eyeColor, effectivePose.micOffMark)
        drawEar(1f, earTwitchR.value, cx, eyeCy, s * headSizeFactor, eyeDX, eyeW, baseEyeH,
            earPerk, earDroop, eyeColor, effectivePose.micOffMark)

        // ── LAYER 2: Eyes (happy arcs, or open eyes with tracking pupils) ──
        // The eyes are the only thing the style changes: same pose, same gaze,
        // same blink clock, so switching mid-animation is seamless.
        if (state.avatarStyle == AvatarStyle.NEO) {
            drawEyeNeo(-1f, -headRotate, cx, eyeCy, s, eyeDX, eyeW, baseEyeH, eyeH,
                blink.value, effectivePose, pupilSize, (gazeX + idleGazeX * 0.55f).coerceIn(-1f, 1f),
                (gazeY + idleGazeY * 0.55f).coerceIn(-1f, 1f), gazeUp, eyeColor)
            drawEyeNeo(1f, headRotate, cx, eyeCy, s, eyeDX, eyeW, baseEyeH, eyeH,
                blink.value, effectivePose, pupilSize, (gazeX + idleGazeX * 0.55f).coerceIn(-1f, 1f),
                (gazeY + idleGazeY * 0.55f).coerceIn(-1f, 1f), gazeUp, eyeColor)
        } else {
            drawEye(-1f, -headRotate, cx, eyeCy, s, eyeDX, eyeW, baseEyeH, eyeH,
                blink.value, effectivePose, pupilSize, (gazeX + idleGazeX * 0.55f).coerceIn(-1f, 1f),
                (gazeY + idleGazeY * 0.55f).coerceIn(-1f, 1f), gazeUp, eyeColor)
            drawEye(1f, headRotate, cx, eyeCy, s, eyeDX, eyeW, baseEyeH, eyeH,
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
        val pawAlpha = ((headRotate / 10f).coerceIn(0f, 1f) * faceAlpha)
        if (pawAlpha > 0.01f) {
            val pawCx = cx + s * 0.18f * (if (headRotate > 0) 1f else -1f)
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
            state.mouthEnergy
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
            val seed = (animT / 120).toInt()  // changes every 120ms for twinkling
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
