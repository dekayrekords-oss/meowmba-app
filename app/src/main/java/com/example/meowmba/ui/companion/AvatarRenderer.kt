package com.example.meowmba.ui.companion

import androidx.compose.ui.graphics.graphicsLayer

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
import com.example.meowmba.core.behavior.BehaviorBias
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.meowmba.core.config.FeatureFlags
import com.example.meowmba.core.thermal.ThermalGovernor
import com.example.meowmba.core.lumen.ReflexSensoryFeed
import com.example.meowmba.ui.companion.lumen.LumenReflexBridge
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
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
    /** Stroking the avatar. Reported outward so the drive layer feels it too. */
    onPet: () -> Unit = {},
    onPlay: () -> Unit = {},
    neuralGaze: () -> Offset? = { null },
    onHabitatAction: (HabitatAction) -> Unit = {},
    onFly3DFrame: ((Fly3DFrame) -> Unit)? = null,
    paused: Boolean = false,
    /** Setup-only settled anatomy. Used with paused=true; does not alter live morphs or privacy. */
    restingPreview: Boolean = false,
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
    faceYawDeg: Float? = null,
    facePitchDeg: Float? = null,
    faceRollDeg: Float? = null,
    chaseHard: Boolean = false,
    faceScale: Float? = null,
    /** Lateral force on the phone, -1..1 (+ = pushed right). Turns lean the head. */
    motionLean: Float = 0f,
    /**
     * Whether the face consumes touches at all. `false` turns the avatar into a
     * statue: no taps, no drags, no petting — the parent surface decides what a
     * touch means (the Professional workspace binds the stage to the mic).
     */
    pointerEnabled: Boolean = true,
    /** Longitudinal force, -1..1 (+ = braking throw). Braking lurches the head. */
    motionLurch: Float = 0f,
    /** Sample forces during drawing, without recomposing the complete avatar per sensor event. */
    motionLeanState: State<Float>? = null,
    motionLurchState: State<Float>? = null,
    /** Monotonic sensor time, read during draw so a heartbeat does not recompose the face. */
    motionObservedAtMs: State<Long>? = null,
    motionScreenLean: State<Float>? = null,
    displayRotation: Int = 0,
    shakeCount: Int = 0,
    /**
     * Continuous drives, as a function rather than a value or a StateFlow on
     * purpose: the drives change every tick, so observing them as state would
     * recompose the whole avatar several times a second for a handful of floats
     * that only the draw lambda needs. Same reasoning as [mouthEnergy].
     * Defaults to the resting bias, which is a no-op.
     */
    nervousBias: () -> BehaviorBias = { REST_BIAS },
    /** Experimental BANC wing-power activity, sampled only while drawing. */
    neuralWingPower: () -> Float = { 0f },
    neuralFlyMotor: () -> com.example.meowmba.core.neural.FlyMotorIntent = { com.example.meowmba.core.neural.FlyMotorIntent.NONE },
    /** Rich vision/social/speech/touch/motion feed for Lumen reflex v2 (read during draw). */
    reflexFeed: () -> ReflexSensoryFeed = { ReflexSensoryFeed() },
    reflexGain: Float = 1f,
    onReflexSummary: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    timings: com.example.meowmba.core.diagnostics.InteractionTimings? = null,
    displayRefreshRate: Float? = null,
) {
    // The HUD's held maneuver label is not an animation clock. Continuous
    // sensor-driven inertia below owns the reaction, including its recovery.
    val basePose = CatAvatarStateMachine.resolvePose(state.copy(driveReaction = DriveReaction.NONE))
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
    val lumenClipTilt = remember(customAnim) { lumenPerformanceTilt(customAnim?.name) }
    val lumenPerformance = remember(customAnim) { AvatarPerformanceCatalog.fromSpec(customAnim) }

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

    // Godot face / classic idle: ONE vsync-locked draw clock (not 9 infinite transitions).
    // CatMotionEngine is ONE-SHOT only for named expressions.
    val motionEngine = remember { CatMotionEngine() }
    // Jarvis particle orb sim + text measurer for its param labels. Both are
    // draw-pass only: the sim holds slot order + flyers across frames, the
    // measurer lays out a handful of monospace labels per frame.
    val particleSim = remember { ParticleOrbSim() }
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    // Same reasoning as animTHolder: the composed-expression pose is produced
    // every vsync and consumed only inside the draw lambda.
    val shotHolder = remember { arrayOfNulls<MotionPose>(1) }
    var faceRadiusPx by remember { mutableStateOf(200f) }
    val useGodotFace = state.avatarStyle == AvatarStyle.NEO || state.avatarStyle == AvatarStyle.PEARL
    val useSignalOrb = state.avatarStyle == AvatarStyle.SIGNAL
    val useLumen = state.avatarStyle.isLumen || customAnim?.props?.isNotEmpty() == true
    val lumenRig = remember { LumenRig() }
    val lumenPainter = remember { LumenPainter() }
    val generatedPainter = remember { GeneratedAvatarPainter() }
    val generatedScenePainter = remember { InventedScenePainter() }
    val idleGenerated = state.idleMorphGeneratedId?.let { id ->
                state.savedGeneratedForms.firstOrNull { it.id == id }
            }
            val generatedForm = (idleGenerated ?: state.generatedAvatarForm).takeIf { state.avatarStyle == AvatarStyle.LUMEN }
    val touchGeneratedForm by rememberUpdatedState(generatedForm)
    val generatedPlacement = remember { arrayOfNulls<GeneratedAvatarPlacement>(1) }
    val fullFlyPainter = remember { FullFlyPainter() }
    val habitatWorld = remember(state.habitatBackgroundId, state.avatarStyle, state.habitatMode) { PetHabitatWorld() }
    val fly3DWorld = remember(state.habitatBackgroundId, state.avatarStyle, state.habitatMode) { Fly3DWorld() }
    val lastFly3DFrame = remember(fly3DWorld) { arrayOfNulls<Fly3DFrame>(1) }
    val avatarScale = if (state.avatarScale.isFinite()) state.avatarScale.coerceIn(.5f, 3f) else 2f
    val avatarHitTransform = remember { arrayOf(AvatarHitTransform()) }
    val habitatEnabled = state.habitatMode
    val cartoonGagKind = if (habitatEnabled) CartoonGagKind.forBackground(state.habitatBackgroundId) else null
    val cartoonGag = remember(cartoonGagKind, habitatEnabled) { cartoonGagKind?.let(::CartoonHabitatGag) }
    val latestCartoonGag by rememberUpdatedState(cartoonGag)
    val latestPaused by rememberUpdatedState(paused)
    val touchFly3DFrame by rememberUpdatedState(lastFly3DFrame)
    val lastFullFlyPose = remember { arrayOf(FullFlyPose()) }
    val canvasSize = remember { floatArrayOf(0f, 0f) }
    val latestHabitatAction by rememberUpdatedState(onHabitatAction)
    val latestHabitatEnabled by rememberUpdatedState(habitatEnabled)
    val lumenContext = LocalContext.current
    val lumenReflexV2 = remember(lumenContext) { FeatureFlags.from(lumenContext).lumenReflexV2 }
    val avatarFpsTarget = FeatureFlags.from(lumenContext).avatarFpsTarget
    val lumenReflexBridge = remember { LumenReflexBridge() }
    val lumenLastFrame = remember { arrayOf(LumenFrame()) }
    val privacyColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    val orbOrientation = rememberOrbOrientation(useSignalOrb && !paused)
    // Single ambient clock (seconds). Advanced at Avatar FPS target and only while visible.
    // Reading this in the Canvas draw lambda invalidates draw only — not a
    // full-composition thrash of 9 rememberInfiniteTransition states @ vsync.
    var clockSec by remember { mutableFloatStateOf(0f) }
    // The clock survives pauses. It used to be a local of the effect below,
    // which is keyed on `paused` — so every time a panel opened or closed the
    // accumulator restarted at 0.0 and every ambient cycle (breath, sway,
    // drift) teleported to its t=0 phase. The head jumped the instant you
    // dismissed chat or the drawer, with nothing on screen to explain it.
    val frameClock = remember { AvatarFrameClock() }
    androidx.compose.runtime.DisposableEffect(timings) { onDispose { timings?.pauseFrames() } }
    val latestAnimation by rememberUpdatedState(customAnim)
    val latestComposed by rememberUpdatedState(composed)
    val latestPerformanceSequence by rememberUpdatedState(state.avatarPerformanceSequence)
    val latestFaceRadius by rememberUpdatedState(faceRadiusPx)
    LaunchedEffect(paused, timings, displayRefreshRate, avatarFpsTarget) {
        timings?.setFrameCadence(displayRefreshRate?.takeIf { it.isFinite() && it > 0f }?.let { (1_000_000_000.0 / it).toLong() })
        if (paused) {
            timings?.pauseFrames()
            frameClock.pause()
            motionEngine.stop()
            shotHolder[0] = null
            return@LaunchedEffect
        }
        var runningSequence: Long? = null
        var runningExpression: ComposedExpression? = null
        val cadence = AvatarFrameCadence()
        while (true) {
            withFrameNanos { now ->
                val clip = latestAnimation
                val expression = latestComposed
                // Performed clips / live expressions admit at Avatar FPS target when cool
                // (default ~30). Idle uses half; LIGHT halves further; MODERATE+ pauses
                // via AvatarWorkPolicy and cadence refuse.
                val performing = clip != null || expression != null
                if (!cadence.admit(
                        now,
                        performing,
                        fpsTarget = avatarFpsTarget,
                        thermalStatus = ThermalGovernor.currentStatus(),
                    )
                ) return@withFrameNanos
                timings?.framePresented(now)
                val id = if (performing) latestPerformanceSequence else null
                val tick = frameClock.step(now, id)
                clockSec = tick.seconds
                animTHolder[0] = tick.performanceMillis
                if (id != runningSequence || expression != runningExpression) {
                    motionEngine.stop()
                    if (expression != null) motionEngine.play(expression)
                    runningSequence = id
                    runningExpression = expression
                }
                // The actor, custom clip and ambient face share this draw owner.
                // Elapsed time remains continuous while redraws are capped at Avatar FPS when cool.
                shotHolder[0] = if (expression != null)
                    motionEngine.update(tick.dt, latestFaceRadius.coerceIn(80f, 800f)) else null
                drawEpoch++
            }
        }
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
    LaunchedEffect(paused, useLumen) {
        if (paused || useLumen) return@LaunchedEffect
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
    LaunchedEffect(paused, useLumen) {
        if (paused || useLumen) return@LaunchedEffect
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

    // Vision owns the bounded hold; do not add a second hold after its expiry.
    var heldFaceGaze by remember { mutableStateOf<Offset?>(null) }
    var recentlyTracking by remember { mutableStateOf(false) }
    // Rotation and distance clear alongside gaze; the visual springs settle them.
    var heldYaw by remember { mutableStateOf(0f) }
    var heldPitch by remember { mutableStateOf(0f) }
    var heldRoll by remember { mutableStateOf(0f) }
    var heldScale by remember { mutableStateOf(1f) }
    LaunchedEffect(faceGazeX, faceGazeY, faceYawDeg, facePitchDeg, faceRollDeg, faceScale, state.isCameraEnabled) {
        if (!state.isCameraEnabled) {
            heldFaceGaze = null
            recentlyTracking = false
            heldYaw = 0f
            heldPitch = 0f
            heldRoll = 0f
            heldScale = 1f
            return@LaunchedEffect
        }
        if (faceGazeX != null && faceGazeY != null) {
            heldFaceGaze = Offset(faceGazeX, faceGazeY)
            heldYaw = faceYawDeg ?: heldYaw
            heldPitch = facePitchDeg ?: heldPitch
            heldRoll = faceRollDeg ?: heldRoll
            heldScale = faceScale ?: heldScale
            recentlyTracking = true
            return@LaunchedEffect
        }
        // Brief detector gaps: keep the last gaze so Lumen/classic do not snap
        // to idle wander while ML Kit blinks. Clear after a short grace.
        delay(450)
        heldFaceGaze = null
        recentlyTracking = false
        heldYaw = 0f
        heldPitch = 0f
        heldRoll = 0f
        heldScale = 1f
    }
    val trackingNow = state.isCameraEnabled && (faceGazeX != null || heldFaceGaze != null || recentlyTracking)

    // ── Idle gaze: real saccades (quick fixate → hold), not constant sine
    //    drift — a perpetual smooth wander reads as a mechanical search loop.
    val saccadeX = remember { Animatable(0f) }
    val saccadeY = remember { Animatable(0f) }
    LaunchedEffect(paused, trackingNow, useLumen) {
        if (paused || useLumen) return@LaunchedEffect
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
            delay(Random.nextLong(2800L, 6500L))
            val tx = (Random.nextFloat() * 2f - 1f) * 0.42f
            val ty = (Random.nextFloat() * 2f - 1f) * 0.24f
            val jump = hypot(tx - saccadeX.value, ty - saccadeY.value)
            // Gaze travels directly to its fixation without a reverse twitch.
            // Saccadic blink: a real eye blinks through a large gaze shift, and
            // the blink is what hides the jump. REQUESTED, not performed here —
            // an Animatable has one owner, and a second coroutine calling
            // animateTo cancels the first one's animation, which throws
            // CancellationException inside the ambient blink loop and kills it
            // for good. The eyes then stopped blinking entirely, or froze
            // half-closed, at a random moment with nothing on screen to explain
            // it. The ambient loop owns the lid; this only asks.
            if (jump > 0.75f) blinkRequests.trySend(Unit)
            val moveX = launch { saccadeX.animateTo(tx, tween(280, easing = FastOutSlowInEasing)) }
            val moveY = launch { saccadeY.animateTo(ty, tween(280, easing = FastOutSlowInEasing)) }
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
    val gazeTargetX = faceGazeX.takeIf { state.isCameraEnabled }
        ?: heldFaceGaze?.x
        ?: touchGaze?.x
        ?: neuralGaze()?.x
        ?: 0f
    val gazeTargetY = faceGazeY.takeIf { state.isCameraEnabled }
        ?: heldFaceGaze?.y
        ?: touchGaze?.y
        ?: neuralGaze()?.y
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
    // lumen_reflex_v2 may prefer a slightly snappier gaze spring; flag-off
    // keeps the historical stiffness / settle times unchanged.
    val gazeSpec = if (trackingNow && chaseHard) {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (lumenReflexV2) 210f else 180f,
        )
    } else if (trackingNow) {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (lumenReflexV2) 150f else 120f,
        )
    } else {
        tween(if (lumenReflexV2) 380 else 500)
    }
    val gazeX by animateFloatAsState(screenGazeX, gazeSpec, label = "gazeX")
    val gazeY by animateFloatAsState(screenGazeY, gazeSpec, label = "gazeY")
    // Skull follows the live box. GazeSettle used to snap X to 0 inside a
    // 0.14 deadzone, which read as chop each time the face crossed it.
    // EMA + this spring is the jitter filter.
    val headFollowSpec = spring<Float>(
        dampingRatio = 0.90f,
        stiffness = when {
            chaseHard -> Spring.StiffnessHigh
            lumenReflexV2 -> 2200f // between Medium (1500) and High; flag-off stays Medium
            else -> Spring.StiffnessMedium
        },
    )
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
    LaunchedEffect(paused, avatarFpsTarget) {
        if (paused) return@LaunchedEffect
        var last = 0L
        val cadence = AvatarFrameCadence()
        while (true) {
            withFrameNanos { now ->
                if (!cadence.admit(
                        now,
                        fpsTarget = avatarFpsTarget,
                        thermalStatus = ThermalGovernor.currentStatus(),
                    )
                ) return@withFrameNanos
                val dt = if (last == 0L) 0.016f
                else ((now - last) / 1_000_000_000f).coerceIn(1e-4f, 1f / 30f)
                last = now
                earLag = earTrail.update(headFollowX, dt, gain = 0.32f)
            }
        }
    }

    // ── 6-DOF: yaw/pitch/roll/distance glide in like gaze does ──────────────
    // Each face sample moves the spring target; the spring keeps the head from
    // stepping per detector frame. Values are normalized (-1..1) and the
    // tracking weight decides how hard the head follows.
    val rotSpec = if (trackingNow && chaseHard) {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )
    } else if (trackingNow) {
        spring<Float>(dampingRatio = 0.92f, stiffness = Spring.StiffnessLow)
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
    val driveRig = remember { DriveVisualRig() }
    fun currentDriveFrame(): DriveVisualFrame {
        val relativeLean = com.example.meowmba.core.sensor.MotionMath.displayRelativeLean(
            motionScreenLean?.value ?: 0f, displayRotation)
        val boost = state.driving && state.driveEmotionBoost
        val leanIn = DriveEmotionBoost.amplifyImu(motionLeanState?.value ?: motionLean, boost)
        val lurchIn = DriveEmotionBoost.amplifyImu(motionLurchState?.value ?: motionLurch, boost)
        val frame = driveRig.step(clockSec, android.os.SystemClock.elapsedRealtime(),
            motionObservedAtMs?.value ?: 0L, state.driving, leanIn, lurchIn,
            (-relativeLean * 0.35f).coerceIn(-14f, 14f))
        if (!boost) return frame
        val emotionAdd = DriveEmotionBoost.emotionBraceAdd(
            ConversationAffect.valence(state.emotion), driving = true)
        val brace = DriveEmotionBoost.combineBrace(frame.brace, emotionAdd, state.obdExcitement)
        return frame.copy(brace = brace)
    }

    // ── Petting: stroking the cat makes it purr ────────────────────────────
    var petPulse by remember { mutableLongStateOf(0L) }
    val petCallback by rememberUpdatedState(onPet)
    val playCallback by rememberUpdatedState(onPlay)
    val touchStyle by rememberUpdatedState(state.avatarStyle)
    val touchFlyWorld by rememberUpdatedState(fly3DWorld)
    val touchHabitatWorld by rememberUpdatedState(habitatWorld)
    LaunchedEffect(petPulse) {
        if (petPulse != 0L) {
            // Reported outward, not just consumed by the local `petted` pose:
            // being stroked is a real social event and should move the drives,
            // not merely swap one authored pose for another.
            petCallback()
            delay(2600)
            petPulse = 0L
        }
    }
    val petted = petPulse != 0L

    fun playCartoonGag(point: Offset, width: Float, height: Float, accessibleAction: Boolean = false): Boolean {
        val gag = latestCartoonGag ?: return false
        val gpuFrame = touchFly3DFrame[0].takeIf { touchStyle == AvatarStyle.FULL_FLY }
        val avatarOwnsTap = when {
            accessibleAction -> false // This names the toy directly; it is not an ambiguous screen tap.
            gpuFrame != null -> cartoonFlyOwnsPoint(point, width, height, gpuFrame)
            touchStyle == AvatarStyle.FULL_FLY -> {
                val pose = lastFullFlyPose[0]
                val unit = minOf(minOf(width, height) * .32f * pose.scale.coerceIn(.35f, 1.8f), minOf(width, height) * .46f)
                val at = Offset((width * pose.x).coerceIn(unit * 1.05f, width - unit * 1.05f),
                    (height * pose.y).coerceIn(unit * .88f, height - unit * .73f))
                hypot(point.x - at.x, point.y - at.y) <= unit * 1.05f
            }
            touchStyle.isLumen -> {
                if (touchGeneratedForm != null) generatedPlacement[0]?.owns(point) == true else {
                    val local = avatarHitTransform[0].inverse(point)
                    lumenZoneAt(local, width.toInt(), height.toInt(), lumenLastFrame[0]) != AvatarZone.BACKGROUND
                }
            }
            else -> {
                val transform = avatarHitTransform[0]
                val radius = minOf(width, height) * .64f * transform.scale
                hypot(point.x - transform.destination.x, point.y - transform.destination.y) <= radius
            }
        }
        val propOwnsTap = !accessibleAction && if (gpuFrame != null) {
            gpuFrame.props.orEmpty().any { prop ->
                cartoonProjectHabitatPoint(prop.x, prop.y + .10f, prop.z, width, height)?.let { at ->
                    hypot(point.x - at.x, point.y - at.y) <= maxOf(42f, minOf(width, height) * .09f)
                } == true
            }
        } else touchHabitatWorld.actionAt(point.x, point.y, width, height) != null
        if (!gag.tap(point, width, height, clockSec,
                active = latestHabitatEnabled && !latestPaused &&
                    ThermalGovernor.currentStatus() < android.os.PowerManager.THERMAL_STATUS_SEVERE,
                avatarOwnsTap = avatarOwnsTap, propOwnsTap = propOwnsTap)) return false
        // One event enters the existing care, affect and neural TOUCH pathway.
        // Normal petting is skipped for this tap to avoid counting it twice.
        latestHabitatAction(HabitatAction.PLAY)
        drawEpoch++
        return true
    }

    Canvas(
        modifier = {
            val base = modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Reads the existing active draw clock, not a new timer. A
                    // lost sensor feed eases the whole-stage roll back to level.
                    // A roaming Lumen rotates about its own root. Rotating the
                    // whole screen sends an off-center character out of bounds.
                    rotationZ = if (state.avatarStyle == AvatarStyle.SIGNAL || state.avatarStyle.isLumen) 0f else currentDriveFrame().tilt
                    clip = false
                }
                .onSizeChanged { sz ->
                    // Match draw-time fit so CatMotion scales stay consistent.
                    faceRadiusPx = fitGodotFaceRadius(sz.width.toFloat(), sz.height.toFloat())
                    canvasSize[0] = sz.width.toFloat(); canvasSize[1] = sz.height.toFloat()
                }
                .semantics {
                    contentDescription =
                        "${state.avatarStyle.label} avatar, currently ${state.interactionState.name.lowercase()}" +
                            (if (state.isCameraEnabled) ", camera enabled" else ", camera off") +
                            (if (!state.isMicEnabled) ", microphone off" else "") +
                            (if (state.isMuted) ", speech muted" else "") +
                            (cartoonGagKind?.let { ". ${it.description}" } ?: "")
                    if (cartoonGagKind != null && pointerEnabled) customActions = listOf(
                        CustomAccessibilityAction(cartoonGagKind.description) {
                            val layout = cartoonGagLayout(canvasSize[0], canvasSize[1])
                            layout != null && playCartoonGag(layout.center, canvasSize[0], canvasSize[1], accessibleAction = true)
                        }
                    )
                }
            if (!pointerEnabled) base
            else
                base
                    .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        touchGaze = normalizeGaze(offset, size.width, size.height)
                        if (playCartoonGag(offset, size.width.toFloat(), size.height.toFloat())) return@detectTapGestures
                        petPulse += 1L
                        if (latestHabitatEnabled) {
                            if (touchStyle == AvatarStyle.FULL_FLY) {
                                touchFlyWorld.tap(offset.x / size.width, offset.y / size.height)
                                latestHabitatAction(HabitatAction.EXPLORE)
                            } else {
                                touchHabitatWorld.actionAt(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())
                                    ?.let { latestHabitatAction(it) }
                            }
                        }
                    },
                    onDoubleTap = { offset ->
                        touchGaze = normalizeGaze(offset, size.width, size.height)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        playCallback()
                    },
                    onLongPress = { offset ->
                        touchGaze = normalizeGaze(offset, size.width, size.height)
                        petPulse += 1L
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                        } else {
                            petPulse += 1L
                        }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    totalX += dragAmount.x
                    totalY += dragAmount.y
                    touchGaze = normalizeGaze(change.position, size.width, size.height)
                    // Emit one social event at stroke end; navigation never feeds learning.
                }
            }
            }()
    ) {
        // Subscribe draw pass to the single vsync-locked clock (not composition).
        val _epoch = drawEpoch
        val tSec = clockSec
        val driveFrame = currentDriveFrame()
        val leanAnim = driveFrame.lean
        val lurchAnim = driveFrame.lurch
        fun DrawScope.fixedPrivacyMarks() {
            // Older classic/Godot modes retain their existing outer driving roll.
            // Cancel that roll only for status marks, keeping them fixed on screen.
            val cancelRoll = if (state.avatarStyle == AvatarStyle.SIGNAL || state.avatarStyle.isLumen) 0f else -driveFrame.tilt
            rotate(cancelRoll, pivot = center) {
                lumenPainter.draw(this, LumenFrame(presence = 0f), Color(state.avatarEyeColor), Color(state.avatarGlowColor),
                    cameraOff = !state.isCameraEnabled, micOff = !state.isMicEnabled,
                    speakerOff = state.isMuted, statusColor = privacyColor)
            }
        }

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
        val poseEyeOpen = if (playingClip) pose.eyeOpen else eyeOpenAnim
        val eyeOpen = if (!state.isCameraEnabled || playingClip) poseEyeOpen
            else (poseEyeOpen + driveFrame.brace * 0.20f).coerceAtMost(1.35f)
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
        val earBreath = sinWave(4.9f, -1f, 1f, phaseSec = 0.8f)
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
        // Face lock must not slide the whole cat. Only a waved hand (chaseHard)
        // is allowed to pull the body; otherwise the pupils glance and the
        // head stays planted.
        val bodyGaze = if (chaseHard) 0.06f + 0.12f * trackingWeight else 0f
        // Idle gaze wander only when not face-tracking. Driven by saccades
        // (fixate → hold → glance) instead of continuous sine drift; the
        // movement itself prevents burn-in on always-on displays.
        val idleGazeX = saccadeX.value * (1f - trackingWeight)
        val idleGazeY = saccadeY.value * (1f - trackingWeight)
        val cx = w / 2f + (gazeX + idleGazeX) * s * bodyGaze + leanShift + ambient.offX +
            leanAnim * s * 0.09f
        val cy = h * 0.47f - bounceY + ambient.offY + lurchAnim * s * 0.05f

        // 6-DOF: roll rotates the classic head through the same headTilt channel
        // the eyes already use. Face-track distance must NOT scale the head —
        // only the IMU lurch (brake / shove) may push it forward or back.
        val headRotate = headTilt + rollN * 14f * trackingWeight + leanAnim * 12f
        val headSizeFactor = (1f + lurchAnim * 0.08f).coerceIn(0.92f, 1.08f)

        val eyeDX = s * 0.26f * headSizeFactor
        val eyeW = s * 0.19f * headSizeFactor
        val baseEyeH = s * 0.24f * headSizeFactor
        val eyeH = (baseEyeH * eyeOpen * blink.value * breathe).coerceAtLeast(s * 0.012f)
        val eyeCy = cy - s * 0.06f * headSizeFactor
        val stroke = s * 0.014f * headSizeFactor

        // ── Jarvis particle orb (Meow OS avatar) ───────────────────────────
        // Ported from jarvis_mode.js on the MEOW server: every dot a model
        // parameter on a Fibonacci sphere. Voice level swells the sphere and
        // THINKING ejects particles toward the reply, driven by the same
        // pose/clock inputs as every other style — so switching faces
        // mid-animation stays seamless. (The Aura Home card in
        // AuraHomeAvatar.kt is parked — the face runs full-screen.)
        // The toy shares this clock and paints under Canvas avatars/movable props.
        // Full-fly uses a separate GPU surface; foreground ownership is guarded above.
        // No ambient timer is added, and Android's disabled animator setting gets a still toy.
        cartoonGag?.draw(this, tSec, reducedMotion = !android.animation.ValueAnimator.areAnimatorsEnabled())
        val flyPose = if (habitatEnabled && !(state.avatarStyle == AvatarStyle.FULL_FLY && onFly3DFrame != null)) {
            val bias = nervousBias()
            val care = state.petCare
            val caredBias = if (care.careEnabled) bias.copy(
                arousal = (bias.arousal * (.5f + .5f * care.energy)).coerceIn(0f, 1f),
                restWeight = bias.restWeight + (1f - care.energy) * .5f + (1f - care.hygiene) * .15f,
                valence = (bias.valence * .5f + (care.happiness - .5f) * .4f + (care.affection - .5f) * .3f).coerceIn(-1f, 1f),
                followGain = (bias.followGain * (.7f + .3f * care.affection)).coerceIn(0f, 1.5f),
                blinkRateScale = (bias.blinkRateScale * (.85f + .3f * (1f - care.hygiene))).coerceIn(.5f, 1.8f),
            ) else bias
            habitatWorld.step(tSec, state.habitatCommand, caredBias, w, h, state.petPlayPreference).also {
                habitatWorld.drawProps(this, state.avatarStyle == AvatarStyle.FULL_FLY)
            }
        } else null
        if (useLumen) {
            val progress = if (customAnim != null) animTHolder[0].toFloat() / customAnim.durationMs.coerceAtLeast(1) else 0f
            val isCat = state.avatarStyle == AvatarStyle.LUMEN_CAT
            val neuralLook = neuralGaze()
            val lumenGazeX = faceGazeX.takeIf { state.isCameraEnabled } ?: heldFaceGaze?.x ?: touchGaze?.x ?: neuralLook?.x
            val lumenGazeY = faceGazeY.takeIf { state.isCameraEnabled } ?: heldFaceGaze?.y ?: touchGaze?.y ?: neuralLook?.y
            val composedLumenInput = lumenInventedSceneInput(lumenPerformanceInput(LumenInput(
                gazeX = lumenGazeX, gazeY = lumenGazeY,
                // Touch is a local gaze target, not camera capture permission.
                cameraEnabled = state.isCameraEnabled || touchGaze != null || neuralLook != null,
                emotion = state.emotion, interaction = state.interactionState,
                animal = if (generatedForm != null) AvatarAnimal.NONE else
                    state.performanceAnimal ?: state.idleMorphAnimal
                    ?: if (isCat) AvatarAnimal.CAT else state.avatarAnimal,
                openness = effectivePose.eyeOpen, pupil = if (playingClip) effectivePose.pupilSize else .5f,
                tilt = if (playingClip) effectivePose.headTiltDeg else 0f,
                bounce = if (playingClip) effectivePose.bodyBounce else 0f,
                lean = leanAnim, lurch = lurchAnim,
                driveWeight = driveFrame.driving, brace = driveFrame.brace,
                screenRoll = driveFrame.tilt,
                happy = if (playingClip) effectivePose.mouth == MouthMode.HAPPY else effectivePose.happyEyes,
                speaking = if (state.interactionState == InteractionState.SPEAKING) mouthEnergy?.value ?: 0f else 0f,
                performing = playingClip,
                affection = petted, emotionPreview = state.emotionPreview,
                expressionPlan = state.expressionPlan,
                lowerLid = shotHolder[0]?.lowerLid ?: 0f,
                earAsymmetry = (shotHolder[0]?.earSplitDeg ?: 0f) * .22f,
                performanceLean = if (playingClip) effectivePose.leanX else 0f,
                performanceJaw = shotHolder[0]?.jawOpen ?: if (playingClip && effectivePose.mouth == MouthMode.SPEAKING) .65f else 0f,
                gazeUp = if (playingClip) effectivePose.pupilGazeUp else 0f,
                earPerk = if (playingClip) effectivePose.earPerk else 1f,
                earDroop = if (playingClip) effectivePose.earDroopDeg else 0f,
                lidTilt = shotHolder[0]?.eyeTiltDeg ?: lumenClipTilt,
                whiskerFlutter = effectivePose.whiskerFlutter,
                tailWag = effectivePose.tailWag,
                bias = nervousBias(),
                wingDrive = neuralWingPower(),
            ), lumenPerformance, progress), customAnim, animTHolder[0].toFloat())
            val gatedLumenInput = if (lumenReflexV2) {
                val modulated = lumenReflexBridge.modulate(
                    composedLumenInput,
                    tSec,
                    feed = reflexFeed(),
                    gain = reflexGain,
                )
                onReflexSummary?.invoke(lumenReflexBridge.summaryLabel())
                modulated
            } else composedLumenInput
            val frame = if (paused && restingPreview) restingAvatarFrame(state.avatarStyle, state.avatarAnimal)
                else lumenRig.step(tSec, gatedLumenInput)
            lumenLastFrame[0] = frame
            if (generatedForm != null) {
                val placement = generatedAvatarPlacement(w, h, frame, avatarScale, flyPose)
                generatedPlacement[0] = placement
                generatedPainter.draw(this, generatedForm, frame, tSec, placement.center, placement.unit)
                state.equippedProp?.let { generatedPainter.drawProp(this, it, frame, tSec, placement.center, placement.unit) }
                if (customAnim != null) generatedScenePainter.draw(this, customAnim,
                    animTHolder[0].toFloat(), placement.center, placement.unit)
                // Model-authored shapes cannot cover or impersonate privacy state.
                lumenPainter.draw(this, frame.copy(presence = 0f), Color(state.avatarEyeColor), Color(state.avatarGlowColor),
                    cameraOff = !state.isCameraEnabled, micOff = !state.isMicEnabled,
                    speakerOff = state.isMuted, statusColor = privacyColor)
                return@Canvas
            }
            if (state.avatarStyle == AvatarStyle.FULL_FLY && onFly3DFrame != null) {
                // A complete immutable frame equality gate permits paused appearance changes
                // while still breaking TextureView's draw/swap/invalidate feedback loop.
                val bias = nervousBias()
                val care = state.petCare
                val adjusted = if (care.careEnabled) bias.copy(
                    arousal = bias.arousal * (.5f + .5f * care.energy),
                    valence = (bias.valence * .5f + (care.happiness - .5f) * .3f + (care.affection - .5f) * .3f).coerceIn(-1f, 1f),
                ) else bias
                val world = if (habitatEnabled) fly3DWorld.step(tSec, state.habitatCommand, adjusted,
                    state.petPlayPreference, state.habitatBackgroundId, neuralWingPower(), neuralFlyMotor()) else null
                val p = world?.pose
                val radians = (kotlin.math.PI / 180.0).toFloat()
                val published = Fly3DFrame(timeSeconds = world?.elapsed ?: tSec,
                    x=p?.x ?: 0f,y=p?.y ?: 2.1f,z=p?.z ?: 0f,
                    yaw=(p?.yaw ?: (frame.headX * 12f))*radians,
                    pitch=(p?.pitch ?: (frame.headY * 12f))*radians,roll=(p?.roll ?: frame.roll)*radians,
                    flight=p?.flight ?: .15f,walkPhase=p?.walkPhase ?: if (playingClip) tSec * 5f else 0f,
                    gazeX=frame.gazeX,gazeY=frame.gazeY,
                    wingPhase=p?.wingPhase, leftBlink=(1f-frame.leftOpen).coerceIn(0f,1f),
                    rightBlink=(1f-frame.rightOpen).coerceIn(0f,1f), smile=frame.smile, concern=frame.concern,
                    antennaLift=frame.earLift, antennaLag=frame.earLag,
                    grooming=p?.grooming ?: 0f,
                    blink=(1f-(frame.leftOpen+frame.rightOpen)*.5f).coerceIn(0f,1f),mouth=frame.mouthOpen,
                    reach=p?.reach ?: 0f,holding=p?.holding,backgroundId=state.habitatBackgroundId,props=world?.props,
                    handX=p?.handX,handY=p?.handY,handZ=p?.handZ,
                    avatarScale=avatarScale,stageMode=!habitatEnabled,active=!paused)
                if (lastFly3DFrame[0] != published) {
                    lastFly3DFrame[0] = published
                    onFly3DFrame(published)
                }
                lumenPainter.draw(this, frame.copy(presence=0f), Color(state.avatarEyeColor), Color(state.avatarGlowColor),
                    cameraOff=!state.isCameraEnabled,micOff=!state.isMicEnabled,speakerOff=state.isMuted,statusColor=privacyColor)
                return@Canvas
            }
            if (state.avatarStyle == AvatarStyle.FULL_FLY) {
                val fullPose = (flyPose ?: FullFlyPose(scale = 1.8f)).let { it.copy(scale = it.scale * avatarScale * if (habitatEnabled) HABITAT_AVATAR_MAGNIFICATION else 1f) }
                lastFullFlyPose[0] = fullPose
                fullFlyPainter.draw(this, frame, fullPose)
                // Shared renderer keeps privacy marks fixed; hide only its face.
                lumenPainter.draw(this, frame.copy(presence = 0f), Color(state.avatarEyeColor), Color(state.avatarGlowColor),
                    cameraOff = !state.isCameraEnabled, micOff = !state.isMicEnabled,
                    speakerOff = state.isMuted, statusColor = privacyColor)
                return@Canvas
            }
            if (flyPose != null) {
                val original = lumenCenter(w, h, frame)
                val destination = Offset(flyPose.x * w, flyPose.y * h)
                avatarHitTransform[0] = AvatarHitTransform(original, destination, habitatFaceScale(avatarScale))
                translate(destination.x - original.x, destination.y - original.y) {
                    scale(habitatFaceScale(avatarScale), habitatFaceScale(avatarScale), original) {
                        lumenPainter.draw(this, frame, Color(state.avatarEyeColor), Color(state.avatarGlowColor),
                            featureColor = Color(state.avatarFeatureColor), performance = lumenPerformance,
                            progress = progress, inventedScene = customAnim, inventedTimeMs = animTHolder[0].toFloat())
                    }
                }
                state.equippedProp?.let { generatedPainter.drawProp(this, it, frame, tSec,
                    destination, lumenUnit(w, h, frame) * habitatFaceScale(avatarScale)) }
                lumenPainter.draw(this, frame.copy(presence = 0f), Color(state.avatarEyeColor), Color(state.avatarGlowColor),
                    cameraOff = !state.isCameraEnabled, micOff = !state.isMicEnabled,
                    speakerOff = state.isMuted, statusColor = privacyColor)
            } else {
                val center = lumenCenter(w, h, frame)
                avatarHitTransform[0] = AvatarHitTransform(center, center, avatarScale)
                scale(avatarScale, avatarScale, center) {
                    lumenPainter.draw(this, frame, Color(state.avatarEyeColor), Color(state.avatarGlowColor),
                        featureColor = Color(state.avatarFeatureColor), performance = lumenPerformance,
                        progress = progress, inventedScene = customAnim, inventedTimeMs = animTHolder[0].toFloat())
                }
                state.equippedProp?.let { generatedPainter.drawProp(this, it, frame, tSec,
                    center, lumenUnit(w, h, frame) * avatarScale) }
                lumenPainter.draw(this, frame.copy(presence = 0f), Color(state.avatarEyeColor), Color(state.avatarGlowColor),
                    cameraOff = !state.isCameraEnabled, micOff = !state.isMicEnabled,
                    speakerOff = state.isMuted, statusColor = privacyColor)
            }
            return@Canvas
        } else if (useSignalOrb) {
            val R = ambR
            val faceCy = fitGodotFaceCenterY(w, h)
            val center = flyPose?.let { Offset(it.x * w, it.y * h) } ?: Offset(
                w / 2f + ambient.offX + leanAnim * R * 0.22f,
                faceCy + ambient.offY + lurchAnim * R * 0.12f,
            )
            val orbSpeaking = effectivePose.mouth == MouthMode.SPEAKING
            val sceneScale = if (flyPose != null) habitatFaceScale(avatarScale) else avatarScale
            avatarHitTransform[0] = AvatarHitTransform(center, center, sceneScale)
            scale(sceneScale, sceneScale, center) {
            particleSim.draw(
                scope = this,
                measurer = textMeasurer,
                center = center,
                domeRadius = R * 0.816f,
                viewRotation = orbOrientation.value,
                shakeCount = shakeCount,
                forceLean = (leanAnim + (touchGaze?.x ?: 0f) * 0.35f).coerceIn(-1f, 1f),
                forceLurch = (lurchAnim + (touchGaze?.y ?: 0f) * 0.35f).coerceIn(-1f, 1f),
                thinking = state.interactionState == InteractionState.THINKING,
                level = mouthEnergy?.value ?: if (
                    orbSpeaking ||
                    state.interactionState == InteractionState.LISTENING
                ) talk else 0f,
                clockSec = tSec,
                ringColor = if (petted) Color(0xFFFFB9DF) else Color(effectivePose.glowColorHex),
                alpha = faceAlpha * if (effectivePose.dimmed) 0.55f else 1f,
                offline = state.interactionState == InteractionState.OFFLINE,
            )
            }
            fixedPrivacyMarks()
            // The classic cat layers below must not paint over the orb.
            return@Canvas
        // ── Godot Cat Assistant face (exact look + CatMotion) ───────────────
        } else if (useGodotFace) {
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
            val tracking = faceGazeX != null || heldFaceGaze != null || recentlyTracking
            // A locked face used to pan the WHOLE head by up to ~0.32 R — box
            // jitter of 0.2 looked like the cat jumping around the screen.
            // Face lock used to plant the skull and only flick the pupils —
            // looked like tracking was off. Head now follows the live box;
            // a hand wave still throws farther (chaseHard).
            val bodyK = trackingWeight
            val throwX = if (chaseHard) 0.34f else 0.16f
            val throwY = if (chaseHard) 0.10f else 0.02f
            val bodyPanX = headFollowX * R * (throwX * bodyK) + leanAnim * R * 0.30f
            val bodyPanY = headFollowY * R * (throwY * bodyK) + lurchAnim * R * 0.20f
            val bodyTilt = ((headFollowX * (if (chaseHard) 14f else 8f)).coerceIn(-16f, 16f) * bodyK +
                leanAnim * 14f).coerceIn(-22f, 22f)
            val godotHeadSize = (1f + lurchAnim * 0.08f).coerceIn(0.92f, 1.08f)
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
            // Eye expression follows the emotional pose, not syllable energy.
            // Speech drives the mouth; pulsing the pupils reads as a twitch.
            val liveTalking = speaking || listening
            // The resting face: ambient life + blink + ear twitch + voice drive
            // + face-tracking pan. Always built, never skipped.
            val restingPose = MotionPose(
                eyeOpen = eyeOpen.coerceIn(0.12f, 1.35f),
                pupilSize = pupilSize,
                eyeWidthScale = 1f,
                eyeHeightScale = 1f,
                // Ramped like every other follow term, not toggled.
                eyeTiltDeg = gazeX * 4f * trackingWeight,
                lowerLid = if (effectivePose.happyEyes) 0.35f else 0.10f,
                // Head-tracking inertia belongs on PITCH, symmetrically: both
                // ears hang behind the skull together. It used to be summed
                // into earSplitDeg, which the ear draw applies with opposite
                // signs per side — so following a face scissored the ears
                // apart instead of dragging them.
                earPitchDeg = (earPerk - 1f) * 36f +
                    (if (listening) voice * 8f else 0f) +
                    earLag * 14f +
                    earBreath * 1.4f - driveFrame.brace * 8f,
                earSplayDeg = 10f + earDroop * 0.35f + earBreath * 0.8f,
                // Follow-through: the ears trail the skull and overshoot when
                // it stops, on top of their own ambient twitch.
                // Genuine asymmetry only — the independent per-ear twitches.
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
                    // The eye renderer applies the shared blink once.
                    eyeOpen = shot.eyeOpen.coerceIn(0.05f, 1.6f),
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
                    // The clip still owns the ear pose; its own head motion
                    // just drags the ears behind it. Only the lag is additive —
                    // folding in restingPose.earPitchDeg here would re-apply
                    // the ambient perk the clip is deliberately overriding.
                    earPitchDeg = shot.earPitchDeg + shot.earLagDeg,
                    // Breath, weight shift, gaze pan and face-follow are additive.
                    offsetX = shot.offsetX + restingPose.offsetX,
                    offsetY = shot.offsetY + restingPose.offsetY,
                    headTiltDeg = shot.headTiltDeg + bodyTilt + ambient.tiltDeg,
                    scaleDelta = shot.scaleDelta + ambient.scale,
                    squash = shot.squash + ambient.squash,
                )
            } ?: restingPose
            // Anchor at screen center band; pose offsets carry the whole face.
            val center = flyPose?.let { Offset(it.x * w, it.y * h) } ?: Offset(
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
            val sceneScale = if (flyPose != null) habitatFaceScale(avatarScale) else avatarScale
            avatarHitTransform[0] = AvatarHitTransform(center, center, sceneScale)
            scale(sceneScale, sceneScale, center) {
            rotate(gPose.headTiltDeg, pivot = center) {
                scale(scaleX = squashX, scaleY = squashY, pivot = center) {
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
                    pearl = state.avatarStyle == AvatarStyle.PEARL,
                    featureColor = Color(state.avatarFeatureColor),
                )
                }
            }
            }
            fixedPrivacyMarks()
            return@Canvas
        }

        val classicCenter = Offset(cx, cy)
        val classicDestination = flyPose?.let { Offset(it.x * w, it.y * h) } ?: classicCenter
        val classicScale = if (flyPose != null) habitatFaceScale(avatarScale) else avatarScale
        avatarHitTransform[0] = AvatarHitTransform(classicCenter, classicDestination, classicScale)
        translate(classicDestination.x - classicCenter.x, classicDestination.y - classicCenter.y) {
        scale(classicScale, classicScale, classicCenter) {
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
        fixedPrivacyMarks()
    }
}

/** Same affine transform as the inner avatar draw; scenery and status marks never use it. */
internal data class AvatarHitTransform(val origin: Offset = Offset.Zero, val destination: Offset = Offset.Zero, val scale: Float = 1f) {
    fun inverse(point: Offset): Offset = origin + (point - destination) / scale.coerceAtLeast(.001f)
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
internal fun lumenZoneAt(offset: Offset, w: Int, h: Int, frame: LumenFrame = LumenFrame()): AvatarZone {
    val unit = lumenUnit(w.toFloat(), h.toFloat(), frame)
    val center = lumenCenter(w.toFloat(), h.toFloat(), frame)
    val raw = offset - center
    val stretch = lumenStretch(frame)
    val delta = Offset(raw.x * stretch, raw.y / stretch)
    val angle = -lumenRoll(frame) * Math.PI.toFloat() / 180f
    val point = Offset(delta.x * kotlin.math.cos(angle) - delta.y * kotlin.math.sin(angle),
        delta.x * kotlin.math.sin(angle) + delta.y * kotlin.math.cos(angle))
    val eyeShape = lumenEyeShape(frame.form)
    // Generous hit targets: Lumen eyes are small glass lenses, and camera-off
    // privacy makes them look even smaller. Missing an eye used to open tiles
    // (BACKGROUND) so face tracking looked "broken" when the user tapped near
    // an eye to turn the camera on.
    for (side in intArrayOf(-1, 1)) {
        val local = Offset((point.x - side * unit * .213f) / (1f + side * frame.headX * .1f),
            point.y - abs(frame.headX) * side * unit * .009f)
        val eyeAngle = -lumenEyeTilt(side, frame, eyeShape) * Math.PI.toFloat() / 180f
        val eyePoint = Offset(local.x * kotlin.math.cos(eyeAngle) - local.y * kotlin.math.sin(eyeAngle),
            local.x * kotlin.math.sin(eyeAngle) + local.y * kotlin.math.cos(eyeAngle))
        val x = eyePoint.x / (unit * .28f * eyeShape.width.coerceAtLeast(0.75f))
        val y = eyePoint.y / (unit * .32f * eyeShape.height.coerceAtLeast(0.75f))
        if (x * x + y * y <= 1f) return if (side < 0) AvatarZone.EYE_LEFT else AvatarZone.EYE_RIGHT
    }
    // Ears sit above the lenses — mic privacy toggle (classic parity).
    for (side in intArrayOf(-1, 1)) {
        val ear = Offset(side * unit * .30f, -unit * .34f)
        val dx = (point.x - ear.x) / (unit * .20f)
        val dy = (point.y - ear.y) / (unit * .22f)
        if (dx * dx + dy * dy <= 1f) return if (side < 0) AvatarZone.EAR_LEFT else AvatarZone.EAR_RIGHT
    }
    // Mouth / forehead so mute + settings are reachable without opening tiles.
    if (abs(point.x) < unit * .14f && abs(point.y - unit * .22f) < unit * .10f) {
        return AvatarZone.MOUTH
    }
    if (abs(point.x) < unit * .16f && abs(point.y + unit * .28f) < unit * .12f) {
        return AvatarZone.FOREHEAD
    }
    // The helper's wire/beard and hat belong to the avatar too. Using the
    // same inverse root keeps a toy behind them from stealing its pet gesture.
    val helper = (frame.form.clippy + frame.form.wizard).coerceIn(0f, 1f)
    if (helper > .05f && abs(point.x) < unit * .48f && point.y in (unit * .14f)..(unit * .51f))
        return AvatarZone.FOREHEAD
    if (frame.form.wizard > .05f && abs(point.x) < unit * .40f && point.y in (-unit * .58f)..(-unit * .25f))
        return AvatarZone.FOREHEAD
    return AvatarZone.BACKGROUND
}

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
