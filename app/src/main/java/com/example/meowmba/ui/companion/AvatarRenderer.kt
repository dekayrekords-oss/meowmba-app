package com.example.meowmba.ui.companion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
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

@Composable
fun AvatarRenderer(
    state: CompanionState,
    onZoneClick: (AvatarZone) -> Unit,
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    paused: Boolean = false,
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
    val pose = customAnim?.let { clip ->
        CustomAnimations.sample(clip, animT)?.let { with(CustomAnimations) { basePose.overlay(it) } }
    } ?: basePose

    // ── Expression blending: every pose field eases toward its target ──────
    // When a custom animation is playing, CustomAnimations.sample() already
    // interpolates between keyframes (smoothstep). Adding a 350ms expression
    // blend ON TOP causes the animated value to never reach its keyframe target
    // before the next one arrives — the motion is cancelled out. Use a near-
    // instant spec during playback so the sample values are honored directly.
    val blendMs = if (customAnim != null) 16 else CatAnimationRegistry.spec("system.expression_blend").durationMs
    val blendSpec = tween<Float>(blendMs)
    val eyeOpen by animateFloatAsState(pose.eyeOpen, blendSpec, label = "eyeOpen")
    val pupilSize by animateFloatAsState(pose.pupilSize, blendSpec, label = "pupil")
    val gazeUp by animateFloatAsState(pose.pupilGazeUp, blendSpec, label = "gazeUp")
    val headTilt by animateFloatAsState(pose.headTiltDeg, blendSpec, label = "tilt")
    val earPerk by animateFloatAsState(pose.earPerk, blendSpec, label = "earPerk")
    val earDroop by animateFloatAsState(pose.earDroopDeg, blendSpec, label = "earDroop")
    val faceAlpha by animateFloatAsState(if (pose.dimmed) 0.55f else 1f, blendSpec, label = "dim")

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

    // Gentle at-rest tail sway so the tail exists between clips instead of
    // only appearing mid-wag. Clips override it (customAnim != null zeroes it).
    val tailSway by rememberInfiniteTransition(label = "tailSway").animateFloat(
        initialValue = if (paused) 0f else -0.14f,
        targetValue = if (paused) 0f else 0.14f,
        animationSpec = infiniteRepeatable(
            tween(3600), RepeatMode.Reverse
        ),
        label = "tailSway"
    )

    fun registryInfinite(name: String) = CatAnimationRegistry.spec(name)
    // Paused = frozen endpoints (from == to), so ambient loops stop moving
    // instead of being torn out of the composition.
    fun inf(name: String, target: Boolean): Float {
        val s = CatAnimationRegistry.spec(name)
        return if (paused) (s.from + s.to) / 2f else if (target) s.to else s.from
    }
    val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
        initialValue = inf("idle.breathe", false),
        targetValue = inf("idle.breathe", true),
        animationSpec = infiniteRepeatable(
            tween(registryInfinite("idle.breathe").durationMs), RepeatMode.Reverse
        ),
        label = "breathe"
    )
    val driftX by rememberInfiniteTransition(label = "driftX").animateFloat(
        initialValue = inf("idle.gaze_drift_x", false),
        targetValue = inf("idle.gaze_drift_x", true),
        animationSpec = infiniteRepeatable(
            tween(registryInfinite("idle.gaze_drift_x").durationMs, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "driftX"
    )
    val driftY by rememberInfiniteTransition(label = "driftY").animateFloat(
        initialValue = inf("idle.gaze_drift_y", false),
        targetValue = inf("idle.gaze_drift_y", true),
        animationSpec = infiniteRepeatable(
            tween(registryInfinite("idle.gaze_drift_y").durationMs, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "driftY"
    )
    val talk by rememberInfiniteTransition(label = "talk").animateFloat(
        initialValue = inf("speech.talk_cycle", false),
        targetValue = inf("speech.talk_cycle", true),
        animationSpec = infiniteRepeatable(
            tween(registryInfinite("speech.talk_cycle").durationMs), RepeatMode.Reverse
        ),
        label = "talk"
    )
    val whiskerSway by rememberInfiniteTransition(label = "whiskers").animateFloat(
        initialValue = inf("idle.whisker_sway", false),
        targetValue = inf("idle.whisker_sway", true),
        animationSpec = infiniteRepeatable(
            tween(registryInfinite("idle.whisker_sway").durationMs, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "whiskers"
    )
    val mouthWobble by rememberInfiniteTransition(label = "mouth").animateFloat(
        initialValue = inf("idle.mouth_wobble", false),
        targetValue = inf("idle.mouth_wobble", true),
        animationSpec = infiniteRepeatable(
            tween(registryInfinite("idle.mouth_wobble").durationMs), RepeatMode.Reverse
        ),
        label = "mouth"
    )
    val haloPulse by rememberInfiniteTransition(label = "halo").animateFloat(
        initialValue = inf("system.halo_pulse", false),
        targetValue = inf("system.halo_pulse", true),
        animationSpec = infiniteRepeatable(
            tween(registryInfinite("system.halo_pulse").durationMs), RepeatMode.Reverse
        ),
        label = "halo"
    )

    // ── Gaze: pupils follow the user's touch, then wander off ──────────────
    var touchGaze by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(touchGaze) {
        if (touchGaze != null) {
            delay(3200)
            touchGaze = null
        }
    }
    val gazeTargetX = touchGaze?.x ?: driftX
    val gazeTargetY = touchGaze?.y ?: driftY
    val gazeX by animateFloatAsState(gazeTargetX, tween(420), label = "gazeX")
    val gazeY by animateFloatAsState(gazeTargetY, tween(420), label = "gazeY")

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
        val w = size.width
        val h = size.height
        val s = min(w, h)

        // Face center: gaze shifts the whole face slightly (with a tiny
        // constant drift so an always-on desk display never burns in).
        // body_bounce shifts the whole face up/down during animations. Clamped
        // exactly like its siblings (tailWag/tongue/sparkle) so no out-of-range or
        // non-finite value can ever reach the canvas transforms.
        val bounceY = effectivePose.bodyBounce.coerceIn(-1f, 1f) * s * 0.06f
        val cx = w / 2f + gazeX * s * 0.03f + driftX * s * 0.004f
        val cy = h * 0.47f + gazeY * s * 0.02f - bounceY

        val eyeDX = s * 0.26f
        val eyeW = s * 0.19f
        val baseEyeH = s * 0.24f
        val eyeH = (baseEyeH * eyeOpen * blink.value * breathe).coerceAtLeast(s * 0.012f)
        val eyeCy = cy - s * 0.06f
        val stroke = s * 0.014f

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
            val tailAngle = -40f + wag * 35f  // -75° (left) to -5° (right)
            val tailColor = EYE_COLOR.copy(alpha = 0.7f * faceAlpha)
            rotate(tailAngle, pivot = Offset(tailBaseX, tailBaseY)) {
                val tail = Path().apply {
                    moveTo(tailBaseX, tailBaseY)
                    quadraticBezierTo(
                        tailBaseX + tailLen * 0.4f, tailBaseY - tailLen * 0.8f,
                        tailBaseX + tailLen * 0.1f, tailBaseY - tailLen
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
        drawEye(-1f, -headTilt, cx, eyeCy, s, eyeDX, eyeW, baseEyeH, eyeH,
            blink.value, effectivePose, pupilSize, gazeX, gazeY, gazeUp, eyeColor)
        drawEye(1f, headTilt, cx, eyeCy, s, eyeDX, eyeW, baseEyeH, eyeH,
            blink.value, effectivePose, pupilSize, gazeX, gazeY, gazeUp, eyeColor)

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
