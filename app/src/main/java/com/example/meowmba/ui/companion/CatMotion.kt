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

/**
 * Attack/release envelope for a clip's FACE channels, in absolute seconds.
 *
 * [bell] does this for the body but works in normalised clip time, which makes
 * a 0.9s clip ease for 0.16s and a 3.4s clip ease for 0.6s — the short one
 * feels mushy and the long one lazy. Fixing the ramp in real seconds (capped so
 * a very short clip still gets a full arc) gives every performance the same
 * crispness regardless of length.
 */
fun envelope(
    elapsedSeconds: Float,
    durationSeconds: Float,
    anticipate: Float = 0f,
): Float {
    if (durationSeconds <= 0f) return 0f
    // Anticipation: a signed pre-move. The envelope multiplies each channel's
    // deviation FROM NEUTRAL, so returning a negative value here swings the
    // channel to the far side of rest before it travels to the pose — ears go
    // forward before they flatten, lids drop a hair before the eyes go wide.
    // Without this the envelope is capped at [0,1] and no amount of spring
    // tuning can produce a wind-up: springs only ring AFTER the pose (settle),
    // which is a different thing entirely.
    val ant = if (anticipate > 0f) kotlin.math.min(0.09f, durationSeconds * 0.12f) else 0f
    if (ant > 0f && elapsedSeconds < ant) {
        // One negative half-lobe: 0 -> -anticipate -> 0 across `ant` seconds.
        val u = elapsedSeconds / ant
        return -anticipate * sin(u * PI.toFloat())
    }
    val attack = kotlin.math.min(0.16f, durationSeconds * 0.22f)
    val release = kotlin.math.min(0.34f, durationSeconds * 0.32f)
    // Shifted by `ant` so the lobe hands off at exactly 0 — no discontinuity.
    val rising = smoothStep01((elapsedSeconds - ant) / attack)
    val falling = smoothStep01((durationSeconds - elapsedSeconds) / release)
    return (rising * falling).coerceIn(0f, 1f)
}

/**
 * [envelope] with a per-channel start delay, so channels do not all arrive on
 * the same frame.
 *
 * Overlapping action is a hierarchy of arrival times: the eyes lead (they are
 * the intent), the jaw follows, the ears land last (lightest, most inertial).
 * A 35-70ms stagger is below conscious notice and is most of what separates a
 * rig having a pose applied from a character performing one.
 */
fun envelopeAt(
    elapsedSeconds: Float,
    durationSeconds: Float,
    delaySeconds: Float,
    anticipate: Float = 0f,
): Float {
    if (delaySeconds <= 0f) return envelope(elapsedSeconds, durationSeconds, anticipate)
    // A delay that would eat the whole clip is not worth staggering.
    if (durationSeconds <= delaySeconds * 2f) {
        return envelope(elapsedSeconds, durationSeconds, anticipate)
    }
    return envelope(elapsedSeconds - delaySeconds, durationSeconds - delaySeconds, anticipate)
}

/** Positive lobes only — a rhythmic beat rather than an oscillation. */
fun pulse(t: Float, n: Float): Float = max(0f, sin(t * PI.toFloat() * n))

/** One-shot spike that peaks at [at] and decays. For hits and snaps. */
fun spike(t: Float, at: Float, fall: Float = 2f): Float {
    if (t < at) return smoothStep01(t / max(0.001f, at))
    return (1f - (t - at) / max(0.001f, 1f - at)).coerceIn(0f, 1f).pow(fall)
}

private fun degRad(deg: Float): Float = (deg * PI / 180.0).toFloat()

/**
 * Fades a sustained motion out over the tail of its clip.
 *
 * SINK, RISE and WIGGLE all reach their maximum displacement at t=1 and then
 * the clip simply ends, so the head teleports back to rest in one frame — a
 * visible pop at the end of every one of them. Easing the last fifth to zero
 * means the pose arrives home instead of being cut.
 */
fun releaseRamp(t: Float, from: Float = 0.80f): Float =
    1f - smoothStep01(((t - from) / (1f - from)).coerceIn(0f, 1f))

// ── channels ────────────────────────────────────────────────────────────────

/**
 * SAUCER is deliberately absent: a 1.30 open / 1.40 tall / full-round pupil
 * pose read as bug-eyed rather than surprised, and it was removed on user
 * report. Surprise and alarm are carried by WIDE and ALERT, whose proportions
 * stay inside the eye shape the face is drawn around.
 */
enum class EyeL { NEUTRAL, WIDE, NARROW, SLIT, CLOSED, SQUINT, SOFT, HARD, SLEEPY, WOUNDED, ALERT }
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
    // An arc, not a hold: a mouth that stays wide open for the clip's whole
    // duration reads as a scream. Peaks at 38% and closes by the end.
    JawL.WIDE -> spike(t, 0.38f, 1.7f).let { s ->
        ChannelPose(jaw = 0.10f + 0.82f * s, mw = 1f + 0.14f * s)
    }
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

    /**
     * Uniform size change — the head moving toward or away from the viewer.
     * Perspective, not deformation: LEAN_IN gets bigger, it does not stretch.
     */
    var scale = 0f

    /**
     * Deformation, kept separate from [scale] because they mean opposite
     * things. Positive stretches the head tall and narrow, negative squashes it
     * flat and wide, always conserving silhouette area (see [squashStretch]).
     * Only the motions with real acceleration set it — a bounce squashes at
     * the bottom and stretches at the top; a lean-in does neither.
     */
    var squash = 0f

    fun reset() {
        offX = 0f; offY = 0f; tiltDeg = 0f; scale = 0f; squash = 0f
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
            // Stretch at the top of the bounce, squash at the bottom. offY is
            // screen-space (+Y down), so the sign follows the travel.
            m.squash += 0.075f * -b * amp * e
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
            val s = smoothStep01(t) * releaseRamp(t)
            m.offY += R * 0.16f * s * amp
            m.scale -= 0.045f * s * amp
        }
        Body.RISE -> {
            val r = smoothStep01(t) * releaseRamp(t)
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
            val ramp = smoothStep01(t / 0.75f) * releaseRamp(t, 0.70f)
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
            // The coil is the anticipation; flattening into it is what sells
            // the spring being loaded.
            m.squash += 0.14f * c * amp
        }
        Body.RECOIL -> {
            // A real recoil is preceded by the thing that caused it. Pure
            // (1-t)^n starts at full displacement on frame one — the head is
            // already thrown back before anything hit it. Two frames of
            // forward lean first give it something to recoil FROM.
            val d = if (t < 0.06f) -0.35f * smoothStep01(t / 0.06f)
            else (1f - t).pow(1.9f)
            m.offY -= R * 0.17f * d * amp
            m.scale -= 0.075f * d * amp
            m.tiltDeg += sin(actT * 26f) * 3.5f * d * amp
            m.squash += 0.10f * d * amp
        }
        Body.SNAP_FWD -> {
            // Wind back, strike, overshoot past rest, ring home.
            //
            // spike() is strictly non-negative: attack then decay, no wind-up
            // and no rebound, which is why a bonk read as a lunge rather than
            // a hit. The wind-back is what makes the strike read as fast — the
            // strike phase here covers a 1.28 swing in 0.12 of the clip, so it
            // is genuinely quicker than the old 1.0 over 0.30 despite the
            // added anticipation.
            val f = when {
                t < 0.22f -> -0.28f * smoothStep01(t / 0.22f)
                t < 0.34f -> -0.28f + 1.28f * smoothStep01((t - 0.22f) / 0.12f)
                else -> {
                    val u = (t - 0.34f) / 0.66f
                    (1f - u).coerceAtLeast(0f).pow(1.6f) * cos(u * PI.toFloat() * 1.5f)
                }
            }
            m.scale += 0.16f * f * amp
            m.offY += R * 0.13f * f * amp
            // Squash follows travel: stretched on the wind-back, flattened on
            // the hit, stretched again through the rebound.
            m.squash -= 0.13f * f * amp
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
            // Stretch on the throw forward, nothing on the slow wind-back.
            m.squash += 0.11f * max(0f, lu) * amp
        }
    }
}

// ── ambient life ────────────────────────────────────────────────────────────

/**
 * Breath curve over one cycle, in 0..1: a slow rise (inhale), a faster fall
 * (exhale). A symmetric wave is the tell that something is generated — a real
 * chest does not spend equal time going up and down.
 */
internal fun breathSkew(phase: Float): Float {
    val p = ((phase % 1f) + 1f) % 1f
    return if (p < 0.46f) smoothStep01(p / 0.46f) else 1f - smoothStep01((p - 0.46f) / 0.54f)
}

/**
 * The layer that never stops: breath, weight shift, micro-drift.
 *
 * Unlike [bodyMotion] this is NOT gated by [bell] and has no clip boundary, so
 * it cannot pulse to zero at a loop seam — it is what the face does when it is
 * doing nothing, and it keeps running underneath every performance. A cat that
 * holds a pose is still breathing; freezing the ambient layer while a clip
 * plays is what made held expressions read as a still image.
 *
 * Every component is a sine at a deliberately non-harmonic period, so the sum
 * has no visible repeat — the eye picks up a repeating idle within seconds.
 * [energy] scales the whole layer: ~0.4 asleep, 1.0 awake, >1 alert.
 */
fun ambientLife(actT: Float, R: Float, energy: Float, m: Motion) {
    val e = energy.coerceIn(0f, 1.6f)
    // Breath: the primary motion, and the one that carries "alive". Centered
    // to ±1 so the head oscillates around its rest position rather than
    // floating permanently above it.
    val breath = (breathSkew(actT / 3.7f) - 0.5f) * 2f
    m.offY -= breath * R * 0.028f * e
    // A chest expanding is a deformation, not a change of distance — the head
    // lengthens fractionally on the inhale rather than growing overall.
    m.squash += breath * 0.020f * e
    // Weight shift: a slow lateral lean with the tilt trailing it. Two
    // non-harmonic sines so the drift never returns to the same place.
    val shift = sin(actT * 0.41f) * 0.62f + sin(actT * 0.23f + 2.2f) * 0.38f
    m.offX += shift * R * 0.026f * e
    m.tiltDeg += shift * 1.8f * e
    // Micro-drift: below conscious notice, above the threshold of "frozen".
    m.offY += sin(actT * 1.31f + 0.7f) * R * 0.0065f * e
    m.tiltDeg += sin(actT * 0.87f + 1.4f) * 0.55f * e
}

// ── the classical principles ────────────────────────────────────────────────

/**
 * Volume-preserving squash and stretch.
 *
 * [amount] > 0 stretches vertically, < 0 squashes. The horizontal scale moves
 * the opposite way so the silhouette keeps its area: a head that only scales
 * uniformly reads as "moving toward the camera", while one that widens as it
 * flattens reads as *made of something*. This is the first principle in the
 * Disney list and the one whose absence makes procedural faces look rigid.
 *
 * [preserve] dials it: 1 = full area conservation, 0 = pure vertical scale.
 * Faces want ~0.7 — full conservation on a head this wide looks rubbery.
 */
fun squashStretch(amount: Float, preserve: Float = 0.7f): Pair<Float, Float> {
    val a = amount.coerceIn(-0.45f, 0.45f)
    val sy = 1f + a
    val sx = 1f + (1f / sy - 1f) * preserve.coerceIn(0f, 1f)
    return sx to sy
}

/**
 * How far along a shallow arc a horizontal move should rise.
 *
 * Straight-line translation is the other great tell of computed motion: living
 * things move their mass along arcs because everything pivots about a joint.
 * Returns a positive rise (in fractions of R) that peaks at the extremes of
 * travel, so panning the head left or right lifts it slightly rather than
 * sliding it flat across the screen.
 */
fun arcRise(travel: Float, depth: Float = 0.06f): Float {
    val x = travel.coerceIn(-1f, 1f)
    return (1f - sqrt((1f - x * x).coerceAtLeast(0f))) * depth
}

/**
 * Inertial lag for a light appendage attached to a moving mass.
 *
 * Ears do not arrive with the skull: the head starts, the ears stay behind,
 * then whip past the pose and settle. That trailing overshoot ("follow through
 * and overlapping action") is most of what separates a character from a
 * diagram, and it is free once head velocity is known — the ear's target is
 * simply the negative of that velocity.
 */
class Trailing(stiffness: Float = 90f, bounce: Float = 0.30f) {
    private val spring = Spring(stiffness, bounce)
    private var last = 0f

    /**
     * [position] is what the appendage is attached to. Returns the trailing
     * offset, in the same units per second of travel scaled by [gain].
     */
    fun update(position: Float, dtSeconds: Float, gain: Float = 1f): Float {
        val dt = dtSeconds.coerceIn(1e-4f, 1f / 30f)
        val velocity = (position - last) / dt
        last = position
        // Clamped: a teleport (config change, first frame) must not fling the
        // ears off the head.
        val target = (-velocity * gain).coerceIn(-1f, 1f)
        return spring.update(target, dt)
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

    // The head. Deliberately the loosest springs in the engine (k=70 -> ~1.3Hz,
    // zeta 0.30 -> ~37% overshoot) so it reads as the heaviest thing on screen
    // and settles last. Body fields used to be copied raw out of the frame
    // accumulator, which meant the head could not overshoot, could not settle,
    // and hard-cut to zero the instant a clip ended.
    private val bodyX = Spring(70f, 0.30f)
    private val bodyY = Spring(70f, 0.30f)
    private val bodyTiltS = Spring(80f, 0.32f)
    private val bodyScaleS = Spring(95f, 0.36f)
    private val bodySquashS = Spring(95f, 0.30f)

    // Ear follow-through, driven by the head's own motion. Light appendages lag
    // the heavy mass on the SAME axis and as a PAIR — the faster the head, the
    // bigger the lag.
    private val trailX = Trailing(88f, 0.28f)
    private val trailY = Trailing(110f, 0.26f)

    private val motion = Motion()

    /**
     * Seconds spent decaying to rest after a clip ends; <0 means not settling.
     *
     * Without this, a clip ending is a teleport: Body.COIL's release branch
     * evaluates to +0.55 at t->1, so a pounce's last drawn frame is still
     * displaced and the next frame is flat zero. Follow-through means the
     * motion decays home, it does not cut.
     */
    private var settleT = -1f

    private companion object {
        /** Length of the post-clip decay tail. */
        const val SETTLE_SECONDS = 0.45f
    }

    private var expression: ComposedExpression? = null
    private var elapsed = 0f

    /** Head-follow, deliberately NOT the saccade target — see [headGaze]. */
    private var headGazeX = 0f
    private var headGazeY = 0f

    val isPlaying: Boolean get() = expression != null

    /**
     * Start a clip.
     *
     * The springs are NOT reset. That is deliberate and it is the difference
     * between a cut and a performance: a spring carrying the previous pose's
     * position and velocity eases into the new one, which is how a real face
     * transitions. Snapping every channel to neutral first would put a
     * one-frame jump at the start of every clip.
     *
     * What DOES reset is the clock, and [update] now envelopes the channels
     * toward neutral at both ends, so a clip no longer inherits a stale target
     * from a clip that was interrupted mid-pose.
     */
    fun play(e: ComposedExpression) {
        expression = e
        elapsed = 0f
        // An interrupting clip cancels the settle rather than fighting it.
        settleT = -1f
    }

    fun stop() {
        expression = null
        settleT = -1f
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
     * The decay tail after a clip ends: every target is rest, and the springs
     * carry their own velocity home. Returns null once settled, restoring the
     * old "caller uses its own resting pose" contract.
     */
    private fun settleFrame(dtSeconds: Float): MotionPose? {
        if (settleT < 0f) return null
        settleT += dtSeconds
        if (settleT > SETTLE_SECONDS) {
            settleT = -1f
            return null
        }
        return MotionPose(
            eyeOpen = open.update(1f, dtSeconds),
            pupilSize = pupil.update(0.45f, dtSeconds),
            eyeWidthScale = eyeW.update(1f, dtSeconds),
            eyeHeightScale = eyeH.update(1f, dtSeconds),
            eyeTiltDeg = eyeTilt.update(0f, dtSeconds),
            lowerLid = lowerLid.update(0.10f, dtSeconds),
            earPitchDeg = ear.update(0f, dtSeconds),
            earSplayDeg = splay.update(12f, dtSeconds),
            earSplitDeg = earSplit.update(0f, dtSeconds),
            jawOpen = jaw.update(0f, dtSeconds),
            mouthWidth = mouthW.update(1f, dtSeconds),
            lipCurl = curl.update(0f, dtSeconds),
            offsetX = bodyX.update(0f, dtSeconds),
            offsetY = bodyY.update(0f, dtSeconds),
            headTiltDeg = bodyTiltS.update(0f, dtSeconds),
            scaleDelta = bodyScaleS.update(0f, dtSeconds),
            squash = bodySquashS.update(0f, dtSeconds),
        )
    }

    /**
     * Advances one frame and returns the pose to draw, or null when nothing is
     * playing and the caller should use its own resting pose.
     */
    fun update(dtSeconds: Float, faceRadius: Float): MotionPose? {
        val e = expression ?: return settleFrame(dtSeconds)
        elapsed += dtSeconds
        if (elapsed >= e.durationSeconds) {
            expression = null
            settleT = 0f
            return settleFrame(dtSeconds)
        }

        val t = (elapsed / e.durationSeconds).coerceIn(0f, 1f)
        val raw = eyeLayer(e.eye)
            .mergedWith(earLayer(e.ear, t))
            .mergedWith(jawLayer(e.jaw, t))

        motion.reset()
        bodyMotion(e.body, t, elapsed, faceRadius, e.bodyAmp, motion)

        // Ear inertia is measured in face-radius units so the lag is the same
        // gesture on a phone and a tablet.
        val rInv = 1f / faceRadius.coerceAtLeast(1f)
        val lagY = trailY.update(motion.offY * rInv, dtSeconds, gain = 0.55f)
        val lagX = trailX.update(motion.offX * rInv, dtSeconds, gain = 0.45f)

        val neutralEye = eyeLayer(EyeL.NEUTRAL)
        val neutralEar = earLayer(EarL.NEUTRAL, t)
        val neutralJaw = jawLayer(JawL.SHUT, t)

        // Ease the expression in and back out.
        //
        // The channels used to be step functions: full target value on frame
        // one, full target value on the last frame, then the clip ends and
        // every spring is suddenly chasing neutral again. The body had `bell()`
        // for exactly this and the face had nothing, so performances popped on,
        // held rigid, and popped off — the opposite of the slow-in/slow-out
        // that makes motion read as deliberate. The envelope is in ABSOLUTE
        // seconds so a 0.9s clip and a 3.4s clip get the same crisp attack
        // rather than the short one easing for a third of its length.
        //
        // Three envelopes, not one. A single scalar released all twelve fields
        // from the starting gate on the same frame; the springs then settled at
        // different rates, but simultaneous departure is the read that says
        // "pose applied" rather than "action performed". Eyes lead, jaw follows
        // 35ms later, ears land last at 70ms — and each gets its own
        // anticipation depth, because a lid and an ear do not wind up equally.
        //
        // A blink is exempt: pre-widening before the lids shut looks like a
        // flinch, not an intention.
        val antEye = if (e.eye == EyeL.CLOSED) 0f else 0.10f
        val envEye = envelopeAt(elapsed, e.durationSeconds, 0f, anticipate = antEye)
        val envJaw = envelopeAt(elapsed, e.durationSeconds, 0.035f, anticipate = 0.12f)
        val envEar = envelopeAt(elapsed, e.durationSeconds, 0.070f, anticipate = 0.18f)
        fun ease(target: Float?, neutral: Float, env: Float): Float? =
            target?.let { neutral + (it - neutral) * env }
        val target = ChannelPose(
            open = ease(raw.open, neutralEye.open!!, envEye),
            eh = ease(raw.eh, 1f, envEye),
            ew = ease(raw.ew, 1f, envEye),
            pupil = ease(raw.pupil, neutralEye.pupil!!, envEye),
            lower = ease(raw.lower, 0f, envEye),
            etilt = ease(raw.etilt, 0f, envEye),
            ear = ease(raw.ear, neutralEar.ear!!, envEar),
            splay = ease(raw.splay, neutralEar.splay!!, envEar),
            jaw = ease(raw.jaw, 0f, envJaw),
            mw = ease(raw.mw, 1f, envJaw),
            curl = ease(raw.curl, 0f, envJaw),
            earSplit = ease(raw.earSplit, 0f, envEar),
        )

        return MotionPose(
            eyeOpen = open.update(target.open ?: neutralEye.open, dtSeconds),
            pupilSize = pupil.update(target.pupil ?: neutralEye.pupil, dtSeconds),
            eyeWidthScale = eyeW.update(target.ew ?: 1f, dtSeconds),
            eyeHeightScale = eyeH.update(target.eh ?: 1f, dtSeconds),
            eyeTiltDeg = eyeTilt.update(target.etilt ?: 0f, dtSeconds),
            lowerLid = lowerLid.update(target.lower ?: 0f, dtSeconds),
            earPitchDeg = ear.update(target.ear ?: neutralEar.ear, dtSeconds),
            earSplayDeg = splay.update(target.splay ?: neutralEar.splay, dtSeconds),
            earSplitDeg = earSplit.update(target.earSplit ?: 0f, dtSeconds),
            jawOpen = jaw.update(target.jaw ?: neutralJaw.jaw!!, dtSeconds),
            mouthWidth = mouthW.update(target.mw ?: neutralJaw.mw!!, dtSeconds),
            lipCurl = curl.update(target.curl ?: 0f, dtSeconds),
            // Ears lag the skull. Negative on Y because offY is screen-space
            // +down: the head drops, the ears hang behind it going up.
            earLagDeg = -lagY * 20f,
            earYawLagDeg = lagX * 16f,
            // Through springs, not raw: this is what gives the head overshoot
            // on a snap and a real settle when it stops.
            offsetX = bodyX.update(motion.offX, dtSeconds),
            offsetY = bodyY.update(motion.offY, dtSeconds),
            headTiltDeg = bodyTiltS.update(motion.tiltDeg, dtSeconds),
            scaleDelta = bodyScaleS.update(motion.scale, dtSeconds),
            squash = bodySquashS.update(motion.squash, dtSeconds),
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
    /** Uniform size — perspective (nearer / further). */
    val scaleDelta: Float,
    /** Area-conserving deformation — see [Motion.squash]. */
    val squash: Float = 0f,
    /**
     * Symmetric ear pitch lag: the ears trailing the skull's vertical motion.
     *
     * Applied to BOTH ears equally, unlike [earSplitDeg] which is deliberately
     * antisymmetric (one ear up, the other down) and reads as a single
     * deliberate flick. Feeding inertia into the split channel makes the ears
     * scissor, which is the opposite of follow-through.
     */
    val earLagDeg: Float = 0f,
    /** Symmetric ear yaw lag from the skull's horizontal motion. */
    val earYawLagDeg: Float = 0f,
)
