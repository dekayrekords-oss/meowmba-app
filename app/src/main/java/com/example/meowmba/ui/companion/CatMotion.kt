package com.example.meowmba.ui.companion

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The compositional motion system, ported from the Godot prototype.
 *
 * The idea it exists to serve: Loona and Looi do not ship a thousand
 * hand-authored animations, they combine a handful of independent channels and
 * let the count fall out of the product. Four channels here — eye, ear, jaw and
 * body — multiply out to far more expressions than anyone would author by hand,
 * and every one of them moves, because a body motion is *selected* rather than
 * hand-rolled per action.
 *
 * This deliberately does NOT port the prototype's 60 baked presets. Measuring
 * them found the approach had hit a ceiling: across four review passes the named
 * collisions dispersed but the tail got worse (0, then 2, then 6 near-identical
 * pairs out of 1,770). Porting the presets would import that problem. The
 * channels are the way out of it.
 *
 * Everything here is pure and frame-rate independent so it can be unit tested;
 * nothing in this file touches Compose.
 */

// ── easing helpers ──────────────────────────────────────────────────────────

/** Smoothstep with zero derivative at both ends. */
fun smoothStep01(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Ease in and out, so an action never pops on or off mid-pose. */
fun bell(t: Float): Float =
    smoothStep01(kotlin.math.min(t / 0.18f, 1f)) *
        smoothStep01(kotlin.math.min((1f - t) / 0.22f, 1f))

/** Positive lobes only — a rhythmic beat rather than an oscillation. */
fun pulse(t: Float, n: Float): Float = max(0f, sin(t * PI.toFloat() * n))

/** One-shot spike that peaks at [at] and decays. For hits and snaps. */
fun spike(t: Float, at: Float, fall: Float = 2f): Float {
    if (t < at) return smoothStep01(t / max(0.001f, at))
    return (1f - (t - at) / max(0.001f, 1f - at)).coerceIn(0f, 1f).pow(fall)
}

private fun degRad(deg: Float): Float = (deg * PI / 180.0).toFloat()

// ── channels ────────────────────────────────────────────────────────────────

enum class EyeL { NEUTRAL, WIDE, SAUCER, NARROW, SLIT, CLOSED, SQUINT, SOFT, HARD, SLEEPY, WOUNDED, ALERT }
enum class EarL { NEUTRAL, FORWARD, PERK, BACK, FLAT, SPLAY, DROOP, SWIVEL, ONE_BACK, ALERT }
enum class JawL { SHUT, PARTED, WIDE, CHATTER, PULSE, SNARL, SNEER, LICK, PURSE }

enum class Body {
    DRIFT, BOB, SWAY, NOD, LEAN_IN, PULL_BACK, SINK, RISE,
    SHAKE_H, SHAKE_V, BUZZ, WIGGLE, ORBIT, COIL, RECOIL,
    SNAP_FWD, STAGGER, TIP, FLOAT_UP, LURCH,
}

/**
 * One channel's contribution. A null field means "this channel has no opinion",
 * which is what lets three channels merge without fighting over the same value.
 */
data class ChannelPose(
    val open: Float? = null,
    val eh: Float? = null,
    val ew: Float? = null,
    val pupil: Float? = null,
    val lower: Float? = null,
    val etilt: Float? = null,
    val ear: Float? = null,
    val splay: Float? = null,
    val jaw: Float? = null,
    val mw: Float? = null,
    val curl: Float? = null,
    /** Per-ear asymmetry in degrees: +applied to one ear, −to the other. */
    val earSplit: Float? = null,
)

/** Later channels win on conflict, matching the prototype's merge order. */
fun ChannelPose.mergedWith(other: ChannelPose) = ChannelPose(
    open = other.open ?: open,
    eh = other.eh ?: eh,
    ew = other.ew ?: ew,
    pupil = other.pupil ?: pupil,
    lower = other.lower ?: lower,
    etilt = other.etilt ?: etilt,
    ear = other.ear ?: ear,
    splay = other.splay ?: splay,
    jaw = other.jaw ?: jaw,
    mw = other.mw ?: mw,
    curl = other.curl ?: curl,
    earSplit = other.earSplit ?: earSplit,
)

fun eyeLayer(k: EyeL): ChannelPose = when (k) {
    EyeL.WIDE -> ChannelPose(open = 1.15f, eh = 1.18f, pupil = 0.70f)
    EyeL.SAUCER -> ChannelPose(open = 1.30f, eh = 1.40f, ew = 1.08f, pupil = 1.0f)
    EyeL.NARROW -> ChannelPose(open = 0.70f, eh = 0.72f, ew = 1.10f, lower = 0.35f)
    EyeL.SLIT -> ChannelPose(open = 1.05f, eh = 0.80f, ew = 1.16f, pupil = 0.05f)
    EyeL.CLOSED -> ChannelPose(open = 0.06f, lower = 0.30f)
    EyeL.SQUINT -> ChannelPose(open = 0.34f, eh = 0.60f, ew = 1.18f, lower = 0.52f, pupil = 0.10f)
    EyeL.SOFT -> ChannelPose(open = 0.55f, eh = 0.70f, lower = 0.55f, pupil = 0.60f)
    EyeL.HARD -> ChannelPose(open = 0.95f, eh = 0.86f, etilt = 16.0f, pupil = 0.08f)
    EyeL.SLEEPY -> ChannelPose(open = 0.26f, eh = 0.34f, lower = 0.30f, pupil = 0.20f)
    EyeL.WOUNDED -> ChannelPose(open = 0.70f, eh = 0.92f, etilt = -15.0f, pupil = 0.90f)
    EyeL.ALERT -> ChannelPose(open = 1.12f, eh = 1.22f, pupil = 0.80f)
    EyeL.NEUTRAL -> ChannelPose(open = 1.0f, eh = 1.0f, pupil = 0.45f)
}

fun earLayer(k: EarL, t: Float): ChannelPose = when (k) {
    EarL.FORWARD -> ChannelPose(ear = 24.0f, splay = 6.0f)
    EarL.PERK -> ChannelPose(ear = 34.0f, splay = 2.0f)
    EarL.BACK -> ChannelPose(ear = -22.0f, splay = 22.0f)
    EarL.FLAT -> ChannelPose(ear = -38.0f, splay = 32.0f)
    EarL.SPLAY -> ChannelPose(ear = 2.0f, splay = 28.0f)
    EarL.DROOP -> ChannelPose(ear = -28.0f, splay = 26.0f)
    EarL.ALERT -> ChannelPose(ear = 30.0f, splay = 4.0f)
    // Opposing sweep — the only ear pose that needs the split channel.
    EarL.SWIVEL -> ChannelPose(ear = 16.0f, splay = 8.0f, earSplit = sin(t * PI.toFloat() * 3f) * 34f)
    EarL.ONE_BACK -> ChannelPose(ear = -6.0f, splay = 22.0f, earSplit = -44f * spike(t, 0.25f, 2.2f))
    EarL.NEUTRAL -> ChannelPose(ear = 0.0f, splay = 12.0f)
}

fun jawLayer(k: JawL, t: Float): ChannelPose = when (k) {
    JawL.PARTED -> ChannelPose(jaw = 0.26f, mw = 1.0f)
    JawL.WIDE -> ChannelPose(jaw = 0.92f, mw = 1.14f)
    JawL.PURSE -> ChannelPose(jaw = 0.62f, mw = 0.66f)
    JawL.CHATTER -> ChannelPose(jaw = 0.12f + pulse(t, 24f) * 0.20f, mw = 0.88f, curl = 0.5f)
    JawL.PULSE -> ChannelPose(jaw = 0.14f + pulse(t, 9f) * 0.28f, mw = 0.80f)
    JawL.SNARL -> ChannelPose(jaw = 0.80f, mw = 1.30f, curl = 1.0f)
    JawL.SNEER -> ChannelPose(jaw = 0.16f, mw = 0.94f, curl = 0.30f)
    JawL.LICK -> ChannelPose(jaw = 0.22f + pulse(t, 3f) * 0.30f, mw = 0.92f)
    JawL.SHUT -> ChannelPose(jaw = 0.0f, mw = 1.0f)
}

// ── body channel ────────────────────────────────────────────────────────────

/** Whole-head displacement accumulated by [bodyMotion]. Units are fractions of R. */
class Motion {
    var offX = 0f
    var offY = 0f
    var tiltDeg = 0f
    var scale = 0f

    fun reset() {
        offX = 0f; offY = 0f; tiltDeg = 0f; scale = 0f
    }
}

/**
 * Adds one body motion's contribution for normalised time [t] within the clip.
 * [actT] is absolute elapsed seconds, used by the motions whose frequency should
 * not stretch with clip length. [R] is the face radius in pixels.
 */
fun bodyMotion(kind: Body, t: Float, actT: Float, R: Float, amp: Float, m: Motion) {
    val e = bell(t)
    val pi = PI.toFloat()
    when (kind) {
        // Deliberately not "still": a slow wander so a held pose still breathes.
        // This is what stops the quiet expressions reading as frozen.
        Body.DRIFT -> {
            m.offX += sin(actT * 0.9f) * R * 0.030f * amp * e
            m.offY += sin(actT * 0.7f + 1.1f) * R * 0.022f * amp * e
            m.tiltDeg += sin(actT * 0.6f) * 2.2f * amp * e
        }
        Body.BOB -> {
            val b = sin(t * pi * 4f)
            m.offY += b * R * 0.085f * amp * e
            m.scale += 0.030f * b * amp * e
        }
        Body.SWAY -> {
            val w = sin(t * pi * 2f)
            m.offX += w * R * 0.13f * amp * e
            m.tiltDeg += w * 9f * amp * e
        }
        Body.NOD -> {
            val n = sin(t * pi * 3f)
            m.offY += n * R * 0.075f * amp * e
            m.tiltDeg += n * 6f * amp * e
        }
        Body.LEAN_IN -> {
            val l = sin(t * pi)
            m.scale += 0.13f * l * amp
            m.offY += R * 0.10f * l * amp
        }
        Body.PULL_BACK -> {
            val p = sin(t * pi)
            m.scale -= 0.10f * p * amp
            m.offY -= R * 0.13f * p * amp
        }
        Body.SINK -> {
            val s = smoothStep01(t)
            m.offY += R * 0.16f * s * amp
            m.scale -= 0.045f * s * amp
        }
        Body.RISE -> {
            val r = smoothStep01(t)
            m.offY -= R * 0.13f * r * amp
            m.scale += 0.045f * r * amp
        }
        Body.SHAKE_H -> {
            val k = sin(t * pi * 9f) * (1f - t)
            m.offX += k * R * 0.17f * amp
            m.tiltDeg += k * 15f * amp
        }
        Body.SHAKE_V -> {
            m.offY += sin(t * pi * 9f) * (1f - t) * R * 0.12f * amp
        }
        // Fast and small. Reads as vibration rather than displacement.
        Body.BUZZ -> {
            m.offX += sin(actT * 38f) * R * 0.016f * amp * e
            m.offY += cos(actT * 44f) * R * 0.011f * amp * e
        }
        // Builds through the action — the pre-pounce shimmy.
        Body.WIGGLE -> {
            val ramp = smoothStep01(t / 0.75f)
            val o = sin(actT * 14f)
            m.offX += o * R * 0.15f * ramp * amp
            m.offY += abs(o) * R * 0.035f * ramp * amp
            m.tiltDeg += o * 11f * ramp * amp
        }
        Body.ORBIT -> {
            m.offX += sin(t * pi * 2f) * R * 0.10f * amp * e
            m.offY += cos(t * pi * 2f) * 0.55f * R * 0.10f * amp * e
        }
        // Compress, hold, release. The hold is what makes the release land.
        Body.COIL -> {
            val c = when {
                t < 0.45f -> -smoothStep01(t / 0.45f)
                t < 0.60f -> -1f
                else -> -1f + (0.55f - -1f) * smoothStep01((t - 0.60f) / 0.40f)
            }
            m.scale += 0.11f * c * amp
            m.offY += R * 0.10f * c * amp
        }
        Body.RECOIL -> {
            val d = (1f - t).pow(1.9f)
            m.offY -= R * 0.17f * d * amp
            m.scale -= 0.075f * d * amp
            m.tiltDeg += sin(actT * 26f) * 3.5f * d * amp
        }
        Body.SNAP_FWD -> {
            val f = spike(t, 0.30f, 2.2f)
            m.scale += 0.16f * f * amp
            m.offY += R * 0.13f * f * amp
        }
        // Irregular jerks rather than a clean oscillation.
        Body.STAGGER -> {
            val j = pulse(t, 5f).pow(4f)
            m.offX += sin(actT * 9f) * R * 0.10f * j * amp
            m.offY += R * 0.05f * j * amp
            m.tiltDeg += sin(actT * 11f) * 9f * j * amp
        }
        // Rotate over and hold there — the head-cock family.
        Body.TIP -> {
            val tp = sin(t * pi)
            m.tiltDeg += 17f * tp * amp
            m.offX += R * 0.055f * tp * amp
        }
        Body.FLOAT_UP -> {
            val u = sin(t * pi)
            m.offY -= R * 0.09f * u * amp
            m.tiltDeg += -7f * u * amp
            m.scale += 0.05f * u * amp
        }
        // Asymmetric: wind back slowly, throw forward fast.
        Body.LURCH -> {
            val lu = if (t < 0.55f) {
                -0.45f * smoothStep01(t / 0.55f)
            } else {
                (1f - (t - 0.55f) / 0.45f).pow(2f)
            }
            m.offY += R * 0.14f * lu * amp
            m.tiltDeg += -9f * lu * amp
            m.scale += 0.08f * max(0f, lu) * amp
        }
    }
}

// ── springs ─────────────────────────────────────────────────────────────────

/**
 * One underdamped spring. Lerping toward a target arrives and stops; a spring
 * overshoots and settles, which is the whole difference between a value
 * changing and a thing moving.
 */
class Spring(
    private val stiffness: Float = 185f,
    private val bounce: Float = 0.42f,
    initial: Float = 0f,
) {
    var value: Float = initial
        private set
    private var velocity = 0f

    fun snapTo(v: Float) {
        value = v
        velocity = 0f
    }

    fun update(target: Float, dtSeconds: Float): Float {
        // A long frame (app resumed, debugger paused, first frame after a
        // config change) integrates to a huge velocity and the face explodes
        // off screen. Capping the step is cheaper than a stiff solver and the
        // visible result is identical at any sane frame rate.
        val dt = dtSeconds.coerceIn(0f, 1f / 30f)
        if (dt <= 0f) return value
        val damping = 2f * sqrt(stiffness) * bounce
        velocity += (target - value) * stiffness * dt - velocity * damping * dt
        value += velocity * dt
        if (!value.isFinite() || !velocity.isFinite()) snapTo(target)
        return value
    }
}

/**
 * The live motion state: springs per channel plus the composition clock.
 *
 * Spring constants are the prototype's. Ears and eye shape are the loosest,
 * because they are what an audience reads first and so carry the most
 * follow-through; the tail and body bob are looser still because they are
 * heavy and trail everything.
 */
class CatMotionEngine {
    private val open = Spring(185f, 0.42f, initial = 1f)
    private val pupil = Spring(120f, 0.70f, initial = 0.45f)
    private val ear = Spring(150f, 0.32f)
    private val splay = Spring(150f, 0.34f, initial = 12f)
    private val earSplit = Spring(150f, 0.34f)
    private val eyeW = Spring(200f, 0.38f, initial = 1f)
    private val eyeH = Spring(200f, 0.36f, initial = 1f)
    private val eyeTilt = Spring(170f, 0.40f)
    private val lowerLid = Spring(190f, 0.38f)
    private val jaw = Spring(170f, 0.40f)
    private val mouthW = Spring(170f, 0.40f, initial = 1f)
    private val curl = Spring(170f, 0.40f)

    private val motion = Motion()

    private var expression: ComposedExpression? = null
    private var elapsed = 0f

    /** Head-follow, deliberately NOT the saccade target — see [headGaze]. */
    private var headGazeX = 0f
    private var headGazeY = 0f

    val isPlaying: Boolean get() = expression != null

    fun play(e: ComposedExpression) {
        expression = e
        elapsed = 0f
    }

    fun stop() {
        expression = null
    }

    /**
     * A saccade moves the eyes, not the skull. Feeding the raw gaze target into
     * the head projection shears the whole face every time the eyes flick, which
     * is the single worst artefact the prototype had. Only the sustained part of
     * the gaze reaches the head, and it arrives slower than the eyes do.
     */
    fun headGaze(targetX: Float, targetY: Float, dtSeconds: Float): Pair<Float, Float> {
        val k = (dtSeconds * 3f).coerceIn(0f, 1f)
        headGazeX += (targetX - headGazeX) * k
        headGazeY += (targetY - headGazeY) * k
        return headGazeX to headGazeY
    }

    /**
     * Advances one frame and returns the pose to draw, or null when nothing is
     * playing and the caller should use its own resting pose.
     */
    fun update(dtSeconds: Float, faceRadius: Float): MotionPose? {
        val e = expression ?: return null
        elapsed += dtSeconds
        if (elapsed >= e.durationSeconds) {
            expression = null
            return null
        }

        val t = (elapsed / e.durationSeconds).coerceIn(0f, 1f)
        val target = eyeLayer(e.eye)
            .mergedWith(earLayer(e.ear, t))
            .mergedWith(jawLayer(e.jaw, t))

        motion.reset()
        bodyMotion(e.body, t, elapsed, faceRadius, e.bodyAmp, motion)

        val neutralEye = eyeLayer(EyeL.NEUTRAL)
        val neutralEar = earLayer(EarL.NEUTRAL, t)
        val neutralJaw = jawLayer(JawL.SHUT, t)

        return MotionPose(
            eyeOpen = open.update(target.open ?: neutralEye.open!!, dtSeconds),
            pupilSize = pupil.update(target.pupil ?: neutralEye.pupil!!, dtSeconds),
            eyeWidthScale = eyeW.update(target.ew ?: 1f, dtSeconds),
            eyeHeightScale = eyeH.update(target.eh ?: 1f, dtSeconds),
            eyeTiltDeg = eyeTilt.update(target.etilt ?: 0f, dtSeconds),
            lowerLid = lowerLid.update(target.lower ?: 0f, dtSeconds),
            earPitchDeg = ear.update(target.ear ?: neutralEar.ear!!, dtSeconds),
            earSplayDeg = splay.update(target.splay ?: neutralEar.splay!!, dtSeconds),
            earSplitDeg = earSplit.update(target.earSplit ?: 0f, dtSeconds),
            jawOpen = jaw.update(target.jaw ?: neutralJaw.jaw!!, dtSeconds),
            mouthWidth = mouthW.update(target.mw ?: neutralJaw.mw!!, dtSeconds),
            lipCurl = curl.update(target.curl ?: 0f, dtSeconds),
            offsetX = motion.offX,
            offsetY = motion.offY,
            headTiltDeg = motion.tiltDeg,
            scaleDelta = motion.scale,
        )
    }
    /** Neutral rest frame used when nothing is playing. */
    fun restPose(): MotionPose = MotionPose(
        eyeOpen = 1f,
        pupilSize = 0.45f,
        eyeWidthScale = 1f,
        eyeHeightScale = 1f,
        eyeTiltDeg = 0f,
        lowerLid = 0.12f,
        earPitchDeg = 0f,
        earSplayDeg = 12f,
        earSplitDeg = 0f,
        jawOpen = 0f,
        mouthWidth = 1f,
        lipCurl = 0f,
        offsetX = 0f,
        offsetY = 0f,
        headTiltDeg = 0f,
        scaleDelta = 0f,
    )

    /**
     * Always returns a pose. If no one-shot expression is active, plays/refreshes
     * [ambient] (mood idle). Used by the Godot face path so motion never freezes.
     */
    fun tick(dtSeconds: Float, faceRadius: Float, ambient: ComposedExpression?): MotionPose {
        if (expression == null && ambient != null) {
            play(ambient)
        }
        val frame = update(dtSeconds, faceRadius)
        if (frame != null) return frame
        if (ambient != null) {
            play(ambient)
            return update(dtSeconds, faceRadius) ?: restPose()
        }
        return restPose()
    }

}

/** An expression assembled from independent channels rather than a preset. */
data class ComposedExpression(
    val eye: EyeL = EyeL.NEUTRAL,
    val ear: EarL = EarL.NEUTRAL,
    val jaw: JawL = JawL.SHUT,
    val body: Body = Body.DRIFT,
    val durationSeconds: Float = 1.6f,
    val bodyAmp: Float = 1f,
)

/** One frame of composed motion, in the renderer's own units. */
data class MotionPose(
    val eyeOpen: Float,
    val pupilSize: Float,
    val eyeWidthScale: Float,
    val eyeHeightScale: Float,
    val eyeTiltDeg: Float,
    val lowerLid: Float,
    val earPitchDeg: Float,
    val earSplayDeg: Float,
    val earSplitDeg: Float,
    val jawOpen: Float,
    val mouthWidth: Float,
    val lipCurl: Float,
    val offsetX: Float,
    val offsetY: Float,
    val headTiltDeg: Float,
    val scaleDelta: Float,
)
