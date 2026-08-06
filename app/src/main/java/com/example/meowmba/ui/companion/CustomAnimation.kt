package com.example.meowmba.ui.companion

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

/**
 * Custom (invented) avatar animations.
 *
 * The companion can invent its own face animations at runtime: a named list of
 * keyframes, each carrying SPARSE pose overrides. Only the fields a frame
 * names are animated; everything else keeps whatever the state machine says.
 * Frames interpolate (smoothstep) between the surrounding keyframes per field,
 * so a clip can wiggle just the ears while the eyes keep reacting normally.
 *
 * The trust boundary matches the rest of the agent system: the model PROPOSES,
 * this file CONSTRAINS. Out-of-range numbers are clamped (and the clamp is
 * reported), structurally broken specs are rejected with the reason, and the
 * privacy marks (mic/camera X's, dim) are not animatable at all — an invented
 * clip must never fake a privacy state.
 *
 * Pure Kotlin + org.json (available on-device AND on the JVM, see the
 * `org.json:json` test dependency), so the whole pipeline is unit-testable.
 */

/** Sparse per-frame pose overrides. Null = "this field is not animated". */
data class AnimFrame(
    val atMs: Int,
    val eyeOpen: Float? = null,
    val pupilSize: Float? = null,
    val pupilGazeUp: Float? = null,
    val headTiltDeg: Float? = null,
    val earPerk: Float? = null,
    val earDroopDeg: Float? = null,
    val happyEyes: Boolean? = null,
    val whiskerFlutter: Boolean? = null,
    val mouth: MouthMode? = null,
    val glowColorHex: Long? = null,
    /** Tongue extension (0 = hidden, 1 = fully out for licking). */
    val tongueOut: Float? = null,
    /** Vertical body bounce (-1 = squish down, 1 = pop up). */
    val bodyBounce: Float? = null,
    /** Tail wag angle (-1 = left, 1 = right, 0 = center). */
    val tailWag: Float? = null,
    /** Sparkle particle intensity (0 = none, 1 = full sparkle). */
    val sparkle: Float? = null,
)

/** A named invented clip: sorted frames, last frame's time is the duration. */
data class CustomAnimationSpec(
    val name: String,
    val frames: List<AnimFrame>,
) {
    val durationMs: Int get() = frames.last().atMs
}

object CustomAnimations {

    /** Why a spec was refused. Always a plain, actionable sentence. */
    class SpecException(message: String) : Exception(message)

    // Bounds: generous enough for expression, tight enough that a hallucinated
    // clip cannot turn the face inside out or run forever.
    private val EYE_OPEN_RANGE = 0f..1.6f
    private val PUPIL_RANGE = 0.05f..0.9f
    private val GAZE_RANGE = 0f..1f
    private val TILT_RANGE = -25f..25f
    private val PERK_RANGE = 0.3f..1.6f
    private val DROOP_RANGE = 0f..40f
    private val TONGUE_RANGE = 0f..1f
    private val BOUNCE_RANGE = -1f..1f
    private val WAG_RANGE = -1f..1f
    private val SPARKLE_RANGE = 0f..1f
    private const val MAX_FRAMES = 16
    private const val MAX_DURATION_MS = 10_000

    /**
     * Shorter than this and nobody sees the performance. Measured on device:
     * a weak model invented a 60ms clip — the tool honestly reported "Playing
     * (60ms)" and the user reported "it does not make an animation". Both were
     * right. 500ms proved too short too: a clip that starts while the chat
     * sheet slides away is over before the sheet is. Sub-threshold clips are
     * stretched (ratios preserved) and the stretch is reported, exactly like
     * every other clamp.
     */
    private const val MIN_VISIBLE_MS = 1200
    private val NAME_PATTERN = Regex("[A-Za-z0-9 _\\-]{1,40}")

    /** Mouths a clip may use. MUTED is system-owned (the mute X is a fact, not a look). */
    private val ALLOWED_MOUTHS = setOf(MouthMode.IDLE, MouthMode.HAPPY, MouthMode.SPEAKING)

    /**
     * Signature clips shipped with the app, so "do a trick" performs instantly with
     * no model call — and so a fresh install has real animations before the companion
     * invents its first one. Looked up by name before the on-disk store.
     */
    val BUILT_INS: Map<String, CustomAnimationSpec> = listOf(
        // ── Original three ─────────────────────────────────────────────────────
        CustomAnimationSpec(
            "ear wiggle",
            listOf(
                AnimFrame(atMs = 0, earPerk = 1.0f),
                AnimFrame(atMs = 300, earPerk = 1.5f),
                AnimFrame(atMs = 600, earPerk = 0.7f),
                AnimFrame(atMs = 900, earPerk = 1.4f),
                AnimFrame(atMs = 1200, earPerk = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "happy bop",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, happyEyes = false, mouth = MouthMode.IDLE),
                AnimFrame(
                    atMs = 250, headTiltDeg = -12f, happyEyes = true,
                    mouth = MouthMode.HAPPY, glowColorHex = 0xFF3FB950,
                ),
                AnimFrame(atMs = 550, headTiltDeg = 12f),
                AnimFrame(atMs = 800, headTiltDeg = -8f, whiskerFlutter = true),
                AnimFrame(atMs = 1200, headTiltDeg = 0f, happyEyes = false, mouth = MouthMode.IDLE),
            ),
        ),
        CustomAnimationSpec(
            "curious tilt",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, pupilSize = 0.28f),
                AnimFrame(atMs = 400, headTiltDeg = 16f, pupilSize = 0.62f, earPerk = 1.2f),
                AnimFrame(atMs = 800, headTiltDeg = 16f, pupilSize = 0.62f, earPerk = 1.2f),
                AnimFrame(atMs = 1200, headTiltDeg = 0f, pupilSize = 0.28f, earPerk = 1.0f),
            ),
        ),
        // ── New cat animations ──────────────────────────────────────────────────
        // slow blink: the classic cat trust signal — a long, deliberate close and reopen
        CustomAnimationSpec(
            "slow blink",
            listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f),
                AnimFrame(atMs = 700, eyeOpen = 0.06f),
                AnimFrame(atMs = 1300, eyeOpen = 0.06f),
                AnimFrame(atMs = 2000, eyeOpen = 1.0f),
            ),
        ),
        // paw lick: head dips toward a paw, eyes squint, tongue extends for two licks, whiskers quiver
        CustomAnimationSpec(
            "paw lick",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, eyeOpen = 1.0f, whiskerFlutter = false, tongueOut = 0f),
                AnimFrame(atMs = 350, headTiltDeg = 8f, eyeOpen = 0.55f, tongueOut = 0f),
                AnimFrame(atMs = 550, headTiltDeg = 10f, eyeOpen = 0.4f, whiskerFlutter = true, tongueOut = 0.9f),
                AnimFrame(atMs = 750, headTiltDeg = 10f, eyeOpen = 0.4f, whiskerFlutter = true, tongueOut = 0.3f),
                AnimFrame(atMs = 950, headTiltDeg = 10f, eyeOpen = 0.4f, whiskerFlutter = true, tongueOut = 0.9f),
                AnimFrame(atMs = 1150, headTiltDeg = 10f, eyeOpen = 0.4f, whiskerFlutter = true, tongueOut = 0.3f),
                AnimFrame(atMs = 1400, headTiltDeg = 4f, eyeOpen = 0.75f, whiskerFlutter = false, tongueOut = 0f),
                AnimFrame(atMs = 1800, headTiltDeg = 0f, eyeOpen = 1.0f, tongueOut = 0f),
            ),
        ),
        // ear flatten: defensive or annoyed — ears fold back and droop low
        CustomAnimationSpec(
            "ear flatten",
            listOf(
                AnimFrame(atMs = 0, earPerk = 1.0f, earDroopDeg = 0f),
                AnimFrame(atMs = 500, earPerk = 0.6f, earDroopDeg = 14f),
                AnimFrame(atMs = 1000, earPerk = 0.35f, earDroopDeg = 28f),
                AnimFrame(atMs = 1500, earPerk = 0.35f, earDroopDeg = 28f),
                AnimFrame(atMs = 2000, earPerk = 1.0f, earDroopDeg = 0f),
            ),
        ),
        // nose twitch: micro-oscillations like sniffing something interesting
        CustomAnimationSpec(
            "nose twitch",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, earPerk = 1.0f, pupilSize = 0.28f),
                AnimFrame(atMs = 180, headTiltDeg = 2f, earPerk = 1.12f, pupilSize = 0.33f),
                AnimFrame(atMs = 360, headTiltDeg = -1f, earPerk = 0.95f, pupilSize = 0.28f),
                AnimFrame(atMs = 540, headTiltDeg = 3f, earPerk = 1.18f, pupilSize = 0.36f),
                AnimFrame(atMs = 720, headTiltDeg = -2f, earPerk = 0.9f, pupilSize = 0.28f),
                AnimFrame(atMs = 900, headTiltDeg = 1f, earPerk = 1.08f, pupilSize = 0.31f),
                AnimFrame(atMs = 1200, headTiltDeg = 0f, earPerk = 1.0f, pupilSize = 0.28f),
            ),
        ),
        // chirp: pupils dilate, ears perk, mouth chatters in rapid bursts
        CustomAnimationSpec(
            "chirp",
            listOf(
                AnimFrame(atMs = 0, pupilSize = 0.28f, earPerk = 1.0f, mouth = MouthMode.IDLE),
                AnimFrame(atMs = 180, pupilSize = 0.65f, earPerk = 1.4f, mouth = MouthMode.SPEAKING),
                AnimFrame(atMs = 320, mouth = MouthMode.IDLE),
                AnimFrame(atMs = 460, mouth = MouthMode.SPEAKING),
                AnimFrame(atMs = 600, mouth = MouthMode.IDLE),
                AnimFrame(atMs = 740, mouth = MouthMode.SPEAKING, pupilSize = 0.72f),
                AnimFrame(atMs = 880, mouth = MouthMode.IDLE),
                AnimFrame(atMs = 1100, pupilSize = 0.45f, earPerk = 1.2f),
                AnimFrame(atMs = 1500, pupilSize = 0.28f, earPerk = 1.0f, mouth = MouthMode.IDLE),
            ),
        ),
        // yawn: jaw drops wide, eyes close to slits, ears droop, body relaxes back open
        CustomAnimationSpec(
            "yawn",
            listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, mouth = MouthMode.IDLE, earDroopDeg = 0f),
                AnimFrame(atMs = 450, eyeOpen = 0.6f, earDroopDeg = 8f),
                AnimFrame(atMs = 850, eyeOpen = 0.2f, mouth = MouthMode.SPEAKING, earDroopDeg = 20f),
                AnimFrame(atMs = 1250, eyeOpen = 0.06f, mouth = MouthMode.SPEAKING, earDroopDeg = 28f),
                AnimFrame(atMs = 1600, eyeOpen = 0.35f, mouth = MouthMode.IDLE, earDroopDeg = 14f),
                AnimFrame(atMs = 2000, eyeOpen = 1.0f, earDroopDeg = 0f),
            ),
        ),
        // hunting focus: pupils dilate to maximum, ears radar-lock, amber glow of concentration
        CustomAnimationSpec(
            "hunting focus",
            listOf(
                AnimFrame(atMs = 0, pupilSize = 0.28f, earPerk = 1.0f, headTiltDeg = 0f),
                AnimFrame(atMs = 380, pupilSize = 0.58f, earPerk = 1.3f, headTiltDeg = 5f),
                AnimFrame(
                    atMs = 800, pupilSize = 0.85f, earPerk = 1.55f,
                    headTiltDeg = 5f, glowColorHex = 0xFFD29922,
                ),
                AnimFrame(atMs = 1200, pupilSize = 0.9f, earPerk = 1.6f, glowColorHex = 0xFFD29922),
                AnimFrame(atMs = 1600, pupilSize = 0.28f, earPerk = 1.0f, headTiltDeg = 0f),
            ),
        ),
        // zoomies: wide-eyed chaos — rapid head swings with bursts of happy eyes
        CustomAnimationSpec(
            "zoomies",
            listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, happyEyes = false, headTiltDeg = 0f),
                AnimFrame(atMs = 150, eyeOpen = 1.6f, happyEyes = true, headTiltDeg = -15f),
                AnimFrame(atMs = 330, headTiltDeg = 20f),
                AnimFrame(atMs = 500, headTiltDeg = -22f, happyEyes = false),
                AnimFrame(atMs = 670, headTiltDeg = 22f, happyEyes = true),
                AnimFrame(atMs = 840, headTiltDeg = -18f),
                AnimFrame(atMs = 1010, headTiltDeg = 14f, happyEyes = false),
                AnimFrame(atMs = 1300, headTiltDeg = 0f, eyeOpen = 1.0f),
                AnimFrame(atMs = 1600, headTiltDeg = 0f),
            ),
        ),
        // stretch: the full-body morning stretch — slow close, peak open mouth, bright reopen
        CustomAnimationSpec(
            "stretch",
            listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, mouth = MouthMode.IDLE, earDroopDeg = 0f, headTiltDeg = 0f),
                AnimFrame(atMs = 450, eyeOpen = 0.5f, earDroopDeg = 10f),
                AnimFrame(atMs = 850, eyeOpen = 0.15f, mouth = MouthMode.HAPPY, earDroopDeg = 22f, headTiltDeg = 8f),
                AnimFrame(atMs = 1250, eyeOpen = 0.1f, mouth = MouthMode.HAPPY, earDroopDeg = 25f),
                AnimFrame(atMs = 1700, eyeOpen = 0.5f, mouth = MouthMode.IDLE, earDroopDeg = 10f, headTiltDeg = 4f),
                AnimFrame(atMs = 2200, eyeOpen = 1.0f, earDroopDeg = 0f, headTiltDeg = 0f),
            ),
        ),
        // headshake: emphatic "nope" — dampened oscillation that settles back to center
        CustomAnimationSpec(
            "headshake",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f),
                AnimFrame(atMs = 160, headTiltDeg = -20f),
                AnimFrame(atMs = 360, headTiltDeg = 20f),
                AnimFrame(atMs = 560, headTiltDeg = -14f),
                AnimFrame(atMs = 760, headTiltDeg = 12f),
                AnimFrame(atMs = 960, headTiltDeg = -6f),
                AnimFrame(atMs = 1200, headTiltDeg = 0f),
            ),
        ),
        // trill: chirpy vibrato — ears pulse, whiskers buzz, warm green glow of happiness
        CustomAnimationSpec(
            "trill",
            listOf(
                AnimFrame(atMs = 0, earPerk = 1.0f, happyEyes = false, whiskerFlutter = false),
                AnimFrame(
                    atMs = 180, earPerk = 1.5f, happyEyes = true,
                    whiskerFlutter = true, glowColorHex = 0xFF3FB950,
                ),
                AnimFrame(atMs = 380, earPerk = 0.8f, whiskerFlutter = false),
                AnimFrame(atMs = 580, earPerk = 1.5f, whiskerFlutter = true),
                AnimFrame(atMs = 780, earPerk = 0.8f, whiskerFlutter = false),
                AnimFrame(atMs = 980, earPerk = 1.5f, whiskerFlutter = true),
                AnimFrame(atMs = 1180, earPerk = 0.8f, whiskerFlutter = false),
                AnimFrame(atMs = 1450, earPerk = 1.2f, whiskerFlutter = true),
                AnimFrame(atMs = 1700, earPerk = 1.0f, happyEyes = false, whiskerFlutter = false),
            ),
        ),
        // Whole-tail wag: big sweeping wags with happy eyes. The user-visible
        // "tail" animation — previously no built-in ever moved the tail.
        CustomAnimationSpec(
            "tail wag",
            listOf(
                AnimFrame(atMs = 0, tailWag = 0f, happyEyes = false, earPerk = 1.0f),
                AnimFrame(atMs = 200, tailWag = 0.9f, happyEyes = true, earPerk = 1.15f),
                AnimFrame(atMs = 400, tailWag = -0.9f, earPerk = 1.1f),
                AnimFrame(atMs = 600, tailWag = 0.9f, happyEyes = true, earPerk = 1.15f),
                AnimFrame(atMs = 800, tailWag = -0.9f, earPerk = 1.1f),
                AnimFrame(atMs = 1000, tailWag = 0.5f, happyEyes = true, earPerk = 1.05f),
                AnimFrame(atMs = 1600, tailWag = 0f, happyEyes = false, earPerk = 1.0f),
            ),
        ),
        // ── Twenty new tricks ──────────────────────────────────────────────
        CustomAnimationSpec(
            "slow blink of trust",
            listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f),
                AnimFrame(atMs = 800, eyeOpen = 0.06f),
                AnimFrame(atMs = 1500, eyeOpen = 0.06f),
                AnimFrame(atMs = 2400, eyeOpen = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "zoomies burst",
            listOf(
                AnimFrame(atMs = 0, bodyBounce = 0f, tailWag = 0f, eyeOpen = 1.2f),
                AnimFrame(atMs = 200, bodyBounce = 0.9f, tailWag = 0.9f, happyEyes = true),
                AnimFrame(atMs = 400, bodyBounce = -0.3f, tailWag = -0.9f),
                AnimFrame(atMs = 600, bodyBounce = 0.9f, tailWag = 0.9f),
                AnimFrame(atMs = 800, bodyBounce = -0.3f, tailWag = -0.9f),
                AnimFrame(atMs = 1100, bodyBounce = 0.6f, tailWag = 0.5f),
                AnimFrame(atMs = 1600, bodyBounce = 0f, tailWag = 0f, happyEyes = false),
            ),
        ),
        CustomAnimationSpec(
            "belly flop",
            listOf(
                AnimFrame(atMs = 0, bodyBounce = 0f),
                AnimFrame(atMs = 300, bodyBounce = 0.7f),
                AnimFrame(atMs = 500, bodyBounce = -0.9f, eyeOpen = 0.5f),
                AnimFrame(atMs = 1200, bodyBounce = -0.9f, eyeOpen = 0.5f),
                AnimFrame(atMs = 1800, bodyBounce = 0f, eyeOpen = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "head scratch tilt",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f),
                AnimFrame(atMs = 500, headTiltDeg = 22f, happyEyes = true, whiskerFlutter = true),
                AnimFrame(atMs = 1400, headTiltDeg = 22f, happyEyes = true, whiskerFlutter = true),
                AnimFrame(atMs = 2200, headTiltDeg = 0f, happyEyes = false, whiskerFlutter = false),
            ),
        ),
        CustomAnimationSpec(
            "bird stare",
            listOf(
                AnimFrame(atMs = 0, pupilGazeUp = 0f, pupilSize = 0.28f, earPerk = 1.0f),
                AnimFrame(atMs = 400, pupilGazeUp = 1f, pupilSize = 0.7f, earPerk = 1.6f),
                AnimFrame(atMs = 1200, pupilGazeUp = 1f, pupilSize = 0.7f, earPerk = 1.6f),
                AnimFrame(atMs = 1800, pupilGazeUp = 0f, pupilSize = 0.28f, earPerk = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "loaf tuck",
            listOf(
                AnimFrame(atMs = 0, bodyBounce = 0f, earDroopDeg = 0f, eyeOpen = 1.0f),
                AnimFrame(atMs = 700, bodyBounce = -0.7f, earDroopDeg = 18f, eyeOpen = 0.4f),
                AnimFrame(atMs = 1600, bodyBounce = -0.7f, earDroopDeg = 18f, eyeOpen = 0.4f),
                AnimFrame(atMs = 2400, bodyBounce = 0f, earDroopDeg = 0f, eyeOpen = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "nose boop",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, sparkle = 0f),
                AnimFrame(atMs = 250, headTiltDeg = 10f, sparkle = 1f, happyEyes = true),
                AnimFrame(atMs = 600, headTiltDeg = 0f, sparkle = 0.3f),
                AnimFrame(atMs = 1000, headTiltDeg = 0f, sparkle = 0f, happyEyes = false),
            ),
        ),
        CustomAnimationSpec(
            "morning stretch",
            listOf(
                AnimFrame(atMs = 0, bodyBounce = -0.6f, earPerk = 0.8f),
                AnimFrame(atMs = 600, bodyBounce = -0.6f, earPerk = 0.8f, eyeOpen = 0.3f),
                AnimFrame(atMs = 1100, bodyBounce = 0.8f, earPerk = 1.4f, eyeOpen = 1.2f),
                AnimFrame(atMs = 1800, bodyBounce = 0f, earPerk = 1.0f, eyeOpen = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "surprise dilate",
            listOf(
                AnimFrame(atMs = 0, pupilSize = 0.28f, bodyBounce = 0f),
                AnimFrame(atMs = 250, pupilSize = 0.9f, bodyBounce = 0.4f, eyeOpen = 1.5f),
                AnimFrame(atMs = 900, pupilSize = 0.9f),
                AnimFrame(atMs = 1400, pupilSize = 0.28f, bodyBounce = 0f, eyeOpen = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "purr loaf",
            listOf(
                AnimFrame(atMs = 0, whiskerFlutter = false, happyEyes = false, sparkle = 0f),
                AnimFrame(atMs = 500, whiskerFlutter = true, happyEyes = true, sparkle = 0.5f),
                AnimFrame(atMs = 1800, whiskerFlutter = true, happyEyes = true, sparkle = 0.6f),
                AnimFrame(atMs = 3000, whiskerFlutter = false, happyEyes = false, sparkle = 0f),
            ),
        ),
        CustomAnimationSpec(
            "sassy tail flick",
            listOf(
                AnimFrame(atMs = 0, tailWag = 0f),
                AnimFrame(atMs = 200, tailWag = -0.9f),
                AnimFrame(atMs = 400, tailWag = 0.3f),
                AnimFrame(atMs = 600, tailWag = -0.2f),
                AnimFrame(atMs = 900, tailWag = 0f),
            ),
        ),
        CustomAnimationSpec(
            "double take",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, eyeOpen = 1.0f),
                AnimFrame(atMs = 300, headTiltDeg = 18f, eyeOpen = 1.4f),
                AnimFrame(atMs = 600, headTiltDeg = 0f, eyeOpen = 0.2f),
                AnimFrame(atMs = 900, headTiltDeg = -18f, eyeOpen = 1.4f),
                AnimFrame(atMs = 1600, headTiltDeg = 0f, eyeOpen = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "sleepy collapse",
            listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, earDroopDeg = 0f, bodyBounce = 0f),
                AnimFrame(atMs = 800, eyeOpen = 0.3f, earDroopDeg = 16f, bodyBounce = -0.3f),
                AnimFrame(atMs = 1600, eyeOpen = 0.1f, earDroopDeg = 28f, bodyBounce = -0.7f),
                AnimFrame(atMs = 2200, eyeOpen = 0.3f, earDroopDeg = 20f, bodyBounce = -0.5f),
                AnimFrame(atMs = 2600, eyeOpen = 1.0f, earDroopDeg = 0f, bodyBounce = 0f),
            ),
        ),
        CustomAnimationSpec(
            "alert perch",
            listOf(
                AnimFrame(atMs = 0, earPerk = 1.0f, pupilSize = 0.28f),
                AnimFrame(atMs = 400, earPerk = 1.6f, pupilSize = 0.7f, bodyBounce = 0.4f, glowColorHex = 0xFFD29922),
                AnimFrame(atMs = 1200, earPerk = 1.6f, pupilSize = 0.7f, bodyBounce = 0.4f),
                AnimFrame(atMs = 1800, earPerk = 1.0f, pupilSize = 0.28f, bodyBounce = 0f, glowColorHex = 0xFF58A6FF),
            ),
        ),
        CustomAnimationSpec(
            "blep",
            listOf(
                AnimFrame(atMs = 0, tongueOut = 0f),
                AnimFrame(atMs = 300, tongueOut = 0.5f, happyEyes = true),
                AnimFrame(atMs = 800, tongueOut = 0.5f),
                AnimFrame(atMs = 1200, tongueOut = 0f, happyEyes = false),
            ),
        ),
        CustomAnimationSpec(
            "wiggle pounce",
            listOf(
                AnimFrame(atMs = 0, bodyBounce = 0f, tailWag = 0f),
                AnimFrame(atMs = 200, bodyBounce = -0.5f, tailWag = 0.7f),
                AnimFrame(atMs = 400, bodyBounce = -0.5f, tailWag = -0.7f),
                AnimFrame(atMs = 600, bodyBounce = -0.5f, tailWag = 0.7f),
                AnimFrame(atMs = 800, bodyBounce = 1.0f, tailWag = 0f, eyeOpen = 1.4f),
                AnimFrame(atMs = 1200, bodyBounce = 0f, eyeOpen = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "star gaze",
            listOf(
                AnimFrame(atMs = 0, pupilGazeUp = 0f, pupilSize = 0.28f, sparkle = 0f),
                AnimFrame(atMs = 700, pupilGazeUp = 1f, pupilSize = 0.6f, sparkle = 0.5f, earPerk = 1.3f),
                AnimFrame(atMs = 1800, pupilGazeUp = 1f, pupilSize = 0.6f, sparkle = 0.5f),
                AnimFrame(atMs = 2600, pupilGazeUp = 0f, pupilSize = 0.28f, sparkle = 0f, earPerk = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "grumpy squint",
            listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, earPerk = 1.0f, earDroopDeg = 0f),
                AnimFrame(atMs = 400, eyeOpen = 0.35f, earPerk = 0.7f, earDroopDeg = 8f),
                AnimFrame(atMs = 1200, eyeOpen = 0.35f, earPerk = 0.7f, earDroopDeg = 8f),
                AnimFrame(atMs = 1800, eyeOpen = 1.0f, earPerk = 1.0f, earDroopDeg = 0f),
            ),
        ),
        CustomAnimationSpec(
            "confetti pop",
            listOf(
                AnimFrame(atMs = 0, sparkle = 0f, bodyBounce = 0f, happyEyes = false, mouth = MouthMode.IDLE),
                AnimFrame(atMs = 250, sparkle = 1f, bodyBounce = 0.8f, happyEyes = true, mouth = MouthMode.HAPPY),
                AnimFrame(atMs = 700, sparkle = 0.7f, bodyBounce = -0.2f),
                AnimFrame(atMs = 1000, sparkle = 1f, bodyBounce = 0.6f),
                AnimFrame(atMs = 1400, sparkle = 0f, bodyBounce = 0f, happyEyes = false, mouth = MouthMode.IDLE),
            ),
        ),
        CustomAnimationSpec(
            "ear radar sweep",
            listOf(
                AnimFrame(atMs = 0, earPerk = 1.0f),
                AnimFrame(atMs = 300, earPerk = 1.6f),
                AnimFrame(atMs = 600, earPerk = 0.5f),
                AnimFrame(atMs = 900, earPerk = 1.6f),
                AnimFrame(atMs = 1200, earPerk = 0.5f),
                AnimFrame(atMs = 1600, earPerk = 1.0f),
            ),
        ),
        // ── 30 Additional Avatar Animations ──────────────────────────────────────
        CustomAnimationSpec(
            "airplane ears",
            listOf(
                AnimFrame(atMs = 0, earPerk = 1.0f, earDroopDeg = 0f, eyeOpen = 1.0f),
                AnimFrame(atMs = 400, earPerk = 0.4f, earDroopDeg = 35f, eyeOpen = 0.75f),
                AnimFrame(atMs = 1200, earPerk = 0.4f, earDroopDeg = 35f, eyeOpen = 0.75f),
                AnimFrame(atMs = 1800, earPerk = 1.0f, earDroopDeg = 0f, eyeOpen = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "laser pointer chase",
            listOf(
                AnimFrame(atMs = 0, pupilSize = 0.28f, headTiltDeg = 0f, tailWag = 0f),
                AnimFrame(atMs = 250, pupilSize = 0.88f, headTiltDeg = -14f, tailWag = 0.9f),
                AnimFrame(atMs = 500, pupilSize = 0.88f, headTiltDeg = 16f, tailWag = -0.9f),
                AnimFrame(atMs = 750, pupilSize = 0.88f, headTiltDeg = -10f, tailWag = 0.9f),
                AnimFrame(atMs = 1000, pupilSize = 0.88f, headTiltDeg = 12f, tailWag = -0.9f),
                AnimFrame(atMs = 1500, pupilSize = 0.28f, headTiltDeg = 0f, tailWag = 0f),
            ),
        ),
        CustomAnimationSpec(
            "dramatic gasp",
            listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, mouth = MouthMode.IDLE, glowColorHex = 0xFF58A6FF),
                AnimFrame(
                    atMs = 300, eyeOpen = 1.5f, mouth = MouthMode.SPEAKING,
                    headTiltDeg = -8f, bodyBounce = 0.4f, glowColorHex = 0xFFA855F7,
                ),
                AnimFrame(atMs = 1000, eyeOpen = 1.4f, mouth = MouthMode.SPEAKING, glowColorHex = 0xFFA855F7),
                AnimFrame(atMs = 1600, eyeOpen = 1.0f, mouth = MouthMode.IDLE, headTiltDeg = 0f, bodyBounce = 0f, glowColorHex = 0xFF58A6FF),
            ),
        ),
        CustomAnimationSpec(
            "catnap doze",
            listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, earDroopDeg = 0f, bodyBounce = 0f),
                AnimFrame(atMs = 600, eyeOpen = 0.4f, earDroopDeg = 12f, bodyBounce = -0.3f),
                AnimFrame(atMs = 1200, eyeOpen = 0.05f, earDroopDeg = 24f, bodyBounce = -0.6f),
                AnimFrame(atMs = 2200, eyeOpen = 0.05f, earDroopDeg = 24f, bodyBounce = -0.6f),
                AnimFrame(atMs = 3000, eyeOpen = 1.0f, earDroopDeg = 0f, bodyBounce = 0f),
            ),
        ),
        CustomAnimationSpec(
            "playful pounce prep",
            listOf(
                AnimFrame(atMs = 0, bodyBounce = 0f, tailWag = 0f, pupilSize = 0.28f),
                AnimFrame(atMs = 350, bodyBounce = -0.8f, tailWag = -0.7f, pupilSize = 0.75f, earPerk = 1.4f),
                AnimFrame(atMs = 700, bodyBounce = -0.8f, tailWag = 0.7f, pupilSize = 0.75f),
                AnimFrame(atMs = 1000, bodyBounce = 0.9f, tailWag = 0f, eyeOpen = 1.4f, happyEyes = true),
                AnimFrame(atMs = 1500, bodyBounce = 0f, eyeOpen = 1.0f, happyEyes = false),
            ),
        ),
        CustomAnimationSpec(
            "sneaky stealth crawl",
            listOf(
                AnimFrame(atMs = 0, bodyBounce = 0f, eyeOpen = 1.0f, earPerk = 1.0f),
                AnimFrame(atMs = 500, bodyBounce = -0.6f, eyeOpen = 0.6f, earPerk = 0.6f),
                AnimFrame(atMs = 1100, bodyBounce = -0.5f, eyeOpen = 0.65f, earPerk = 0.7f),
                AnimFrame(atMs = 1700, bodyBounce = -0.6f, eyeOpen = 0.6f, earPerk = 0.6f),
                AnimFrame(atMs = 2300, bodyBounce = 0f, eyeOpen = 1.0f, earPerk = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "ear pop",
            listOf(
                AnimFrame(atMs = 0, earPerk = 1.0f, eyeOpen = 1.0f),
                AnimFrame(atMs = 250, earPerk = 1.5f, eyeOpen = 1.2f),
                AnimFrame(atMs = 600, earPerk = 0.7f, eyeOpen = 1.0f),
                AnimFrame(atMs = 950, earPerk = 1.5f, eyeOpen = 1.2f),
                AnimFrame(atMs = 1400, earPerk = 1.0f, eyeOpen = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "tail spin",
            listOf(
                AnimFrame(atMs = 0, tailWag = 0f, happyEyes = false),
                AnimFrame(atMs = 250, tailWag = -1.0f, happyEyes = true),
                AnimFrame(atMs = 500, tailWag = 1.0f),
                AnimFrame(atMs = 750, tailWag = -1.0f),
                AnimFrame(atMs = 1000, tailWag = 1.0f),
                AnimFrame(atMs = 1400, tailWag = 0f, happyEyes = false),
            ),
        ),
        CustomAnimationSpec(
            "curious head spin",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, pupilSize = 0.28f),
                AnimFrame(atMs = 400, headTiltDeg = -18f, pupilSize = 0.55f),
                AnimFrame(atMs = 800, headTiltDeg = 18f, pupilSize = 0.55f),
                AnimFrame(atMs = 1200, headTiltDeg = -12f, pupilSize = 0.4f),
                AnimFrame(atMs = 1600, headTiltDeg = 0f, pupilSize = 0.28f),
            ),
        ),
        CustomAnimationSpec(
            "snack smell",
            listOf(
                AnimFrame(atMs = 0, earPerk = 1.0f, pupilSize = 0.28f, tongueOut = 0f),
                AnimFrame(atMs = 300, earPerk = 1.35f, pupilSize = 0.6f, tongueOut = 0.4f, mouth = MouthMode.HAPPY),
                AnimFrame(atMs = 800, earPerk = 1.35f, pupilSize = 0.6f, tongueOut = 0.8f),
                AnimFrame(atMs = 1400, earPerk = 1.0f, pupilSize = 0.28f, tongueOut = 0f, mouth = MouthMode.IDLE),
            ),
        ),
        CustomAnimationSpec(
            "mischief grin",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, happyEyes = false, tongueOut = 0f),
                AnimFrame(
                    atMs = 350, headTiltDeg = 14f, happyEyes = true,
                    tongueOut = 0.6f, glowColorHex = 0xFFEC4899,
                ),
                AnimFrame(atMs = 1100, headTiltDeg = 14f, happyEyes = true, tongueOut = 0.6f),
                AnimFrame(atMs = 1600, headTiltDeg = 0f, happyEyes = false, tongueOut = 0f, glowColorHex = 0xFF58A6FF),
            ),
        ),
        CustomAnimationSpec(
            "hiss fluff",
            listOf(
                AnimFrame(atMs = 0, earPerk = 1.0f, earDroopDeg = 0f, eyeOpen = 1.0f),
                AnimFrame(
                    atMs = 250, earPerk = 0.3f, earDroopDeg = 32f,
                    eyeOpen = 0.4f, glowColorHex = 0xFFF85149,
                ),
                AnimFrame(atMs = 1000, earPerk = 0.3f, earDroopDeg = 32f, eyeOpen = 0.4f, glowColorHex = 0xFFF85149),
                AnimFrame(atMs = 1600, earPerk = 1.0f, earDroopDeg = 0f, eyeOpen = 1.0f, glowColorHex = 0xFF58A6FF),
            ),
        ),
        CustomAnimationSpec(
            "gazing at moon",
            listOf(
                AnimFrame(atMs = 0, pupilGazeUp = 0f, pupilSize = 0.28f, sparkle = 0f),
                AnimFrame(
                    atMs = 600, pupilGazeUp = 0.95f, pupilSize = 0.65f,
                    sparkle = 0.8f, glowColorHex = 0xFF38BDF8,
                ),
                AnimFrame(atMs = 1800, pupilGazeUp = 0.95f, pupilSize = 0.65f, sparkle = 0.8f),
                AnimFrame(atMs = 2500, pupilGazeUp = 0f, pupilSize = 0.28f, sparkle = 0f, glowColorHex = 0xFF58A6FF),
            ),
        ),
        CustomAnimationSpec(
            "fast blink flutter",
            listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, earPerk = 1.0f),
                AnimFrame(atMs = 150, eyeOpen = 0.1f, earPerk = 1.15f),
                AnimFrame(atMs = 300, eyeOpen = 1.0f),
                AnimFrame(atMs = 450, eyeOpen = 0.1f, earPerk = 1.15f),
                AnimFrame(atMs = 600, eyeOpen = 1.0f),
                AnimFrame(atMs = 750, eyeOpen = 0.1f),
                AnimFrame(atMs = 1200, eyeOpen = 1.0f, earPerk = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "cozy loaf purr",
            listOf(
                AnimFrame(atMs = 0, bodyBounce = 0f, earDroopDeg = 0f, whiskerFlutter = false, sparkle = 0f),
                AnimFrame(
                    atMs = 500, bodyBounce = -0.7f, earDroopDeg = 16f,
                    whiskerFlutter = true, sparkle = 0.6f, happyEyes = true,
                ),
                AnimFrame(atMs = 1800, bodyBounce = -0.7f, earDroopDeg = 16f, whiskerFlutter = true, sparkle = 0.6f),
                AnimFrame(atMs = 2500, bodyBounce = 0f, earDroopDeg = 0f, whiskerFlutter = false, sparkle = 0f, happyEyes = false),
            ),
        ),
        CustomAnimationSpec(
            "confused tilt left",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, eyeOpen = 1.0f, earDroopDeg = 0f),
                AnimFrame(atMs = 450, headTiltDeg = -20f, eyeOpen = 0.7f, earDroopDeg = 12f),
                AnimFrame(atMs = 1300, headTiltDeg = -20f, eyeOpen = 0.7f, earDroopDeg = 12f),
                AnimFrame(atMs = 1800, headTiltDeg = 0f, eyeOpen = 1.0f, earDroopDeg = 0f),
            ),
        ),
        CustomAnimationSpec(
            "victory dance",
            listOf(
                AnimFrame(atMs = 0, bodyBounce = 0f, tailWag = 0f, sparkle = 0f, happyEyes = false),
                AnimFrame(
                    atMs = 250, bodyBounce = 0.8f, tailWag = 0.9f,
                    sparkle = 1.0f, happyEyes = true, glowColorHex = 0xFF3FB950,
                ),
                AnimFrame(atMs = 500, bodyBounce = -0.4f, tailWag = -0.9f),
                AnimFrame(atMs = 750, bodyBounce = 0.8f, tailWag = 0.9f),
                AnimFrame(atMs = 1000, bodyBounce = -0.4f, tailWag = -0.9f),
                AnimFrame(atMs = 1500, bodyBounce = 0f, tailWag = 0f, sparkle = 0f, happyEyes = false, glowColorHex = 0xFF58A6FF),
            ),
        ),
        CustomAnimationSpec(
            "side eye glance",
            listOf(
                AnimFrame(atMs = 0, pupilSize = 0.28f, earPerk = 1.0f),
                AnimFrame(atMs = 300, pupilSize = 0.45f, earPerk = 1.25f, headTiltDeg = 4f),
                AnimFrame(atMs = 1100, pupilSize = 0.45f, earPerk = 1.25f, headTiltDeg = 4f),
                AnimFrame(atMs = 1500, pupilSize = 0.28f, earPerk = 1.0f, headTiltDeg = 0f),
            ),
        ),
        CustomAnimationSpec(
            "paws up stretch",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, bodyBounce = 0f, mouth = MouthMode.IDLE),
                AnimFrame(
                    atMs = 400, headTiltDeg = -12f, bodyBounce = 0.95f,
                    mouth = MouthMode.HAPPY, happyEyes = true, sparkle = 0.7f,
                ),
                AnimFrame(atMs = 1100, headTiltDeg = -12f, bodyBounce = 0.95f, happyEyes = true),
                AnimFrame(atMs = 1700, headTiltDeg = 0f, bodyBounce = 0f, mouth = MouthMode.IDLE, happyEyes = false, sparkle = 0f),
            ),
        ),
        CustomAnimationSpec(
            "dizzy spin",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, pupilSize = 0.28f),
                AnimFrame(atMs = 250, headTiltDeg = -18f, pupilSize = 0.6f, earDroopDeg = 10f),
                AnimFrame(atMs = 500, headTiltDeg = 18f, pupilSize = 0.2f, earDroopDeg = 20f),
                AnimFrame(atMs = 750, headTiltDeg = -18f, pupilSize = 0.6f, earDroopDeg = 10f),
                AnimFrame(atMs = 1000, headTiltDeg = 18f, pupilSize = 0.2f, earDroopDeg = 20f),
                AnimFrame(atMs = 1500, headTiltDeg = 0f, pupilSize = 0.28f, earDroopDeg = 0f),
            ),
        ),
        CustomAnimationSpec(
            "wall-e tilt",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, pupilSize = 0.28f),
                AnimFrame(
                    atMs = 500, headTiltDeg = 16f, pupilSize = 0.52f,
                    glowColorHex = 0xFFD29922, earPerk = 1.3f,
                ),
                AnimFrame(atMs = 1300, headTiltDeg = 16f, pupilSize = 0.52f, glowColorHex = 0xFFD29922),
                AnimFrame(atMs = 1800, headTiltDeg = 0f, pupilSize = 0.28f, glowColorHex = 0xFF58A6FF, earPerk = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "chatterbox",
            listOf(
                AnimFrame(atMs = 0, mouth = MouthMode.IDLE, earPerk = 1.0f, whiskerFlutter = false),
                AnimFrame(atMs = 150, mouth = MouthMode.SPEAKING, earPerk = 1.3f, whiskerFlutter = true),
                AnimFrame(atMs = 300, mouth = MouthMode.IDLE),
                AnimFrame(atMs = 450, mouth = MouthMode.SPEAKING, earPerk = 1.3f),
                AnimFrame(atMs = 600, mouth = MouthMode.IDLE),
                AnimFrame(atMs = 750, mouth = MouthMode.SPEAKING, earPerk = 1.3f),
                AnimFrame(atMs = 900, mouth = MouthMode.IDLE),
                AnimFrame(atMs = 1300, earPerk = 1.0f, whiskerFlutter = false),
            ),
        ),
        CustomAnimationSpec(
            "sleepy twitch",
            listOf(
                AnimFrame(atMs = 0, eyeOpen = 0.1f, earDroopDeg = 25f, whiskerFlutter = false),
                AnimFrame(atMs = 400, eyeOpen = 0.1f, earDroopDeg = 25f, whiskerFlutter = true, earPerk = 0.7f),
                AnimFrame(atMs = 800, eyeOpen = 0.1f, earDroopDeg = 25f, whiskerFlutter = false),
                AnimFrame(atMs = 1200, eyeOpen = 0.1f, earDroopDeg = 25f, whiskerFlutter = true, earPerk = 0.7f),
                AnimFrame(atMs = 1800, eyeOpen = 0.1f, earDroopDeg = 25f, whiskerFlutter = false),
            ),
        ),
        CustomAnimationSpec(
            "spooky cat",
            listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, pupilSize = 0.28f, bodyBounce = 0f),
                AnimFrame(
                    atMs = 300, eyeOpen = 1.5f, pupilSize = 0.85f,
                    bodyBounce = 0.6f, glowColorHex = 0xFFA855F7, sparkle = 0.9f,
                ),
                AnimFrame(atMs = 1200, eyeOpen = 1.5f, pupilSize = 0.85f, bodyBounce = 0.6f),
                AnimFrame(atMs = 1800, eyeOpen = 1.0f, pupilSize = 0.28f, bodyBounce = 0f, glowColorHex = 0xFF58A6FF, sparkle = 0f),
            ),
        ),
        CustomAnimationSpec(
            "happy nuzzle",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, happyEyes = false, sparkle = 0f),
                AnimFrame(
                    atMs = 350, headTiltDeg = 14f, happyEyes = true,
                    sparkle = 0.8f, glowColorHex = 0xFF3FB950,
                ),
                AnimFrame(atMs = 700, headTiltDeg = -14f, happyEyes = true, sparkle = 0.8f),
                AnimFrame(atMs = 1050, headTiltDeg = 14f, happyEyes = true, sparkle = 0.8f),
                AnimFrame(atMs = 1500, headTiltDeg = 0f, happyEyes = false, sparkle = 0f, glowColorHex = 0xFF58A6FF),
            ),
        ),
        CustomAnimationSpec(
            "ear flop surprise",
            listOf(
                AnimFrame(atMs = 0, earDroopDeg = 0f, earPerk = 1.0f, eyeOpen = 1.0f),
                AnimFrame(atMs = 300, earDroopDeg = 30f, earPerk = 0.4f, eyeOpen = 0.5f),
                AnimFrame(atMs = 700, earDroopDeg = 0f, earPerk = 1.55f, eyeOpen = 1.4f),
                AnimFrame(atMs = 1400, earPerk = 1.0f, eyeOpen = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "tail slap",
            listOf(
                AnimFrame(atMs = 0, tailWag = 0f, earPerk = 1.0f),
                AnimFrame(atMs = 150, tailWag = -0.95f, earPerk = 1.3f),
                AnimFrame(atMs = 350, tailWag = 0.95f, earPerk = 1.3f),
                AnimFrame(atMs = 600, tailWag = -0.5f),
                AnimFrame(atMs = 900, tailWag = 0f, earPerk = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "starry dazzle",
            listOf(
                AnimFrame(atMs = 0, sparkle = 0f, pupilGazeUp = 0f, happyEyes = false),
                AnimFrame(
                    atMs = 500, sparkle = 1.0f, pupilGazeUp = 0.8f,
                    happyEyes = true, glowColorHex = 0xFFEAB308,
                ),
                AnimFrame(atMs = 1500, sparkle = 1.0f, pupilGazeUp = 0.8f, happyEyes = true),
                AnimFrame(atMs = 2200, sparkle = 0f, pupilGazeUp = 0f, happyEyes = false, glowColorHex = 0xFF58A6FF),
            ),
        ),
        CustomAnimationSpec(
            "head tilts left and right",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, earPerk = 1.0f),
                AnimFrame(atMs = 300, headTiltDeg = -16f, earPerk = 1.2f),
                AnimFrame(atMs = 600, headTiltDeg = 16f, earPerk = 1.2f),
                AnimFrame(atMs = 900, headTiltDeg = -16f, earPerk = 1.2f),
                AnimFrame(atMs = 1200, headTiltDeg = 16f, earPerk = 1.2f),
                AnimFrame(atMs = 1600, headTiltDeg = 0f, earPerk = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "happy purr vibration",
            listOf(
                AnimFrame(atMs = 0, bodyBounce = 0f, whiskerFlutter = false, happyEyes = false),
                AnimFrame(
                    atMs = 300, bodyBounce = 0.2f, whiskerFlutter = true,
                    happyEyes = true, glowColorHex = 0xFF3FB950,
                ),
                AnimFrame(atMs = 600, bodyBounce = -0.1f, whiskerFlutter = true),
                AnimFrame(atMs = 900, bodyBounce = 0.2f, whiskerFlutter = true),
                AnimFrame(atMs = 1200, bodyBounce = -0.1f, whiskerFlutter = true),
                AnimFrame(atMs = 1700, bodyBounce = 0f, whiskerFlutter = false, happyEyes = false, glowColorHex = 0xFF58A6FF),
            ),
        ),
        CustomAnimationSpec(
            "ear radar sweep",
            listOf(
                AnimFrame(atMs = 0, earPerk = 1.0f),
                AnimFrame(atMs = 300, earPerk = 1.6f),
                AnimFrame(atMs = 600, earPerk = 0.5f),
                AnimFrame(atMs = 900, earPerk = 1.6f),
                AnimFrame(atMs = 1200, earPerk = 0.5f),
                AnimFrame(atMs = 1600, earPerk = 1.0f),
            ),
        ),
    ).associateBy { it.name }

    /** A built-in clip by name, or null. Store clips are the caller's fallback. */
    fun builtIn(name: String): CustomAnimationSpec? = BUILT_INS[name.trim().lowercase()]

    /**
     * Parses a spec from JSON: `{"name": "wiggle", "frames": [{"at": 0, "ear_perk": 1.5}, …]}`.
     * Numeric keys accept snake_case or camelCase ("eye_open"/"eyeOpen") — models emit both.
     *
     * Returns the validated spec plus human-readable notes for every clamp applied.
     * Throws [SpecException] on anything structurally wrong.
     */
    fun parse(json: String): Pair<CustomAnimationSpec, List<String>> {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw SpecException("not JSON: ${e.message?.take(80)}")
        }
        val name = root.optString("name").trim()
        if (!NAME_PATTERN.matches(name)) {
            throw SpecException(
                "bad name \"$name\" — use 1-40 letters, numbers, spaces, '-' or '_'"
            )
        }
        val rawFrames = root.optJSONArray("frames")
            ?: throw SpecException("missing \"frames\" array")
        if (rawFrames.length() == 0) throw SpecException("frames is empty — at least one keyframe")
        if (rawFrames.length() > MAX_FRAMES) {
            throw SpecException("${rawFrames.length()} frames — the limit is $MAX_FRAMES")
        }

        val notes = mutableListOf<String>()
        val frames = (0 until rawFrames.length()).map { i ->
            val f = rawFrames.optJSONObject(i)
                ?: throw SpecException("frame $i is not an object")
            parseFrame(i, f, notes)
        }.sortedBy { it.atMs }

        if (frames.last().atMs == 0) {
            throw SpecException("every keyframe is at 0ms — the clip never plays; add a later keyframe")
        }
        val stretched = if (frames.last().atMs < MIN_VISIBLE_MS) {
            val factor = MIN_VISIBLE_MS.toFloat() / frames.last().atMs
            notes.add("clip was ${frames.last().atMs}ms — stretched to ${MIN_VISIBLE_MS}ms so it can be seen")
            frames.map { f -> f.copy(atMs = (f.atMs * factor).roundToInt()) }
        } else {
            frames
        }
        return CustomAnimationSpec(name, stretched) to notes
    }

    private fun parseFrame(index: Int, f: JSONObject, notes: MutableList<String>): AnimFrame {
        val at = clampInt(f.optLong("at", -1), 0, MAX_DURATION_MS, notes, "frame $index 'at'")
        if (at < 0) throw SpecException("frame $index has no \"at\" (milliseconds)")
        return AnimFrame(
            atMs = at,
            eyeOpen = num(f, "eye_open", "eyeOpen")?.let {
                clampF(it, EYE_OPEN_RANGE, notes, "eye_open")
            },
            pupilSize = num(f, "pupil_size", "pupilSize")?.let {
                clampF(it, PUPIL_RANGE, notes, "pupil_size")
            },
            pupilGazeUp = num(f, "gaze_up", "pupilGazeUp")?.let {
                clampF(it, GAZE_RANGE, notes, "gaze_up")
            },
            headTiltDeg = num(f, "head_tilt", "headTiltDeg")?.let {
                clampF(it, TILT_RANGE, notes, "head_tilt")
            },
            earPerk = num(f, "ear_perk", "earPerk")?.let {
                clampF(it, PERK_RANGE, notes, "ear_perk")
            },
            earDroopDeg = num(f, "ear_droop", "earDroopDeg")?.let {
                clampF(it, DROOP_RANGE, notes, "ear_droop")
            },
            happyEyes = bool(f, "happy_eyes", "happyEyes"),
            whiskerFlutter = bool(f, "whisker_flutter", "whiskerFlutter"),
            mouth = mouth(f),
            glowColorHex = glow(f),
            tongueOut = num(f, "tongue_out", "tongueOut")?.let {
                clampF(it, TONGUE_RANGE, notes, "tongue_out")
            },
            bodyBounce = num(f, "body_bounce", "bodyBounce")?.let {
                clampF(it, BOUNCE_RANGE, notes, "body_bounce")
            },
            tailWag = num(f, "tail_wag", "tailWag")?.let {
                clampF(it, WAG_RANGE, notes, "tail_wag")
            },
            sparkle = num(f, "sparkle")?.let {
                clampF(it, SPARKLE_RANGE, notes, "sparkle")
            },
        )
    }

    private fun num(f: JSONObject, vararg keys: String): Float? =
        keys.firstOrNull { f.has(it) }?.let { f.getDouble(it).toFloat() }

    private fun bool(f: JSONObject, vararg keys: String): Boolean? =
        keys.firstOrNull { f.has(it) }?.let { f.getBoolean(it) }

    private fun mouth(f: JSONObject): MouthMode? {
        val raw = f.optString("mouth", null) ?: return null
        val mode = runCatching { MouthMode.valueOf(raw.uppercase()) }.getOrNull()
            ?: throw SpecException("unknown mouth \"$raw\" — use IDLE, HAPPY or SPEAKING")
        if (mode !in ALLOWED_MOUTHS) {
            throw SpecException("mouth $mode is system-owned — the mute mark is not animatable")
        }
        return mode
    }

    private fun glow(f: JSONObject): Long? {
        if (!f.has("glow")) return null
        return when (val v = f.get("glow")) {
            is Number -> v.toLong()
            is String -> {
                val hex = v.removePrefix("#")
                when (hex.length) {
                    6 -> ("FF" + hex).toLong(16)
                    8 -> hex.toLong(16)
                    else -> throw SpecException("glow \"$v\" — use #RRGGBB or #AARRGGBB")
                }
            }
            else -> throw SpecException("glow must be a color string or ARGB number")
        }
    }

    private fun clampF(v: Float, range: ClosedFloatingPointRange<Float>, notes: MutableList<String>, field: String): Float {
        val c = v.coerceIn(range.start, range.endInclusive)
        if (c != v) notes.add("$field $v clamped to $c")
        return c
    }

    private fun clampInt(v: Long, min: Int, max: Int, notes: MutableList<String>, field: String): Int {
        if (v < 0) return -1
        val c = v.coerceIn(min.toLong(), max.toLong()).toInt()
        if (c.toLong() != v) notes.add("$field ${v}ms clamped to ${c}ms")
        return c
    }

    /** Serializes to the same JSON shape [parse] accepts (round-trip stable). */
    fun toJson(spec: CustomAnimationSpec): String {
        val frames = JSONArray()
        spec.frames.forEach { f ->
            val o = JSONObject().put("at", f.atMs)
            f.eyeOpen?.let { o.put("eye_open", it.toDouble()) }
            f.pupilSize?.let { o.put("pupil_size", it.toDouble()) }
            f.pupilGazeUp?.let { o.put("gaze_up", it.toDouble()) }
            f.headTiltDeg?.let { o.put("head_tilt", it.toDouble()) }
            f.earPerk?.let { o.put("ear_perk", it.toDouble()) }
            f.earDroopDeg?.let { o.put("ear_droop", it.toDouble()) }
            f.happyEyes?.let { o.put("happy_eyes", it) }
            f.whiskerFlutter?.let { o.put("whisker_flutter", it) }
            f.mouth?.let { o.put("mouth", it.name) }
            f.glowColorHex?.let { o.put("glow", it) }
            f.tongueOut?.let { o.put("tongue_out", it.toDouble()) }
            f.bodyBounce?.let { o.put("body_bounce", it.toDouble()) }
            f.tailWag?.let { o.put("tail_wag", it.toDouble()) }
            f.sparkle?.let { o.put("sparkle", it.toDouble()) }
            frames.put(o)
        }
        return JSONObject().put("name", spec.name).put("frames", frames).toString()
    }

    /**
     * Samples a clip at [tMs]. Returns null when the clip is over (t > duration),
     * which is how the renderer knows to stop applying it. Each field tracks its
     * own surrounding keyframes independently — that is what makes frames sparse.
     * Numbers ease (smoothstep); booleans, mouth and color step at their keyframe.
     */
    fun sample(spec: CustomAnimationSpec, tMs: Long): AnimFrame? {
        if (tMs < 0 || tMs > spec.durationMs) return null
        val t = tMs.toFloat()
        return AnimFrame(
            atMs = tMs.toInt(),
            eyeOpen = lerp(spec.frames, t) { it.eyeOpen },
            pupilSize = lerp(spec.frames, t) { it.pupilSize },
            pupilGazeUp = lerp(spec.frames, t) { it.pupilGazeUp },
            headTiltDeg = lerp(spec.frames, t) { it.headTiltDeg },
            earPerk = lerp(spec.frames, t) { it.earPerk },
            earDroopDeg = lerp(spec.frames, t) { it.earDroopDeg },
            happyEyes = step(spec.frames, t) { it.happyEyes },
            whiskerFlutter = step(spec.frames, t) { it.whiskerFlutter },
            mouth = step(spec.frames, t) { it.mouth },
            glowColorHex = step(spec.frames, t) { it.glowColorHex },
            tongueOut = lerp(spec.frames, t) { it.tongueOut },
            bodyBounce = lerp(spec.frames, t) { it.bodyBounce },
            tailWag = lerp(spec.frames, t) { it.tailWag },
            sparkle = lerp(spec.frames, t) { it.sparkle },
        )
    }

    /** Interpolates one nullable numeric field across the frames that set it. */
    private fun lerp(frames: List<AnimFrame>, t: Float, pick: (AnimFrame) -> Float?): Float? {
        val keyed = frames.mapNotNull { f -> pick(f)?.let { f.atMs to it } }
        if (keyed.isEmpty()) return null
        val after = keyed.firstOrNull { it.first >= t } ?: return keyed.last().second
        val before = keyed.lastOrNull { it.first <= t } ?: return after.second
        if (after.first == before.first) return before.second
        val u = (t - before.first) / (after.first - before.first)
        val eased = u * u * (3f - 2f * u) // smoothstep: gentle in and out
        return before.second + (after.second - before.second) * eased
    }

    /** Steps one nullable discrete field: the most recent keyframe's value wins. */
    private fun <T> step(frames: List<AnimFrame>, t: Float, pick: (AnimFrame) -> T?): T? {
        val keyed = frames.mapNotNull { f -> pick(f)?.let { f.atMs to it } }
        if (keyed.isEmpty()) return null
        return (keyed.lastOrNull { it.first <= t } ?: keyed.first()).second
    }

    /** Applies a sampled frame over a base pose; null fields keep the base value. */
    fun AvatarPose.overlay(frame: AnimFrame): AvatarPose = copy(
        eyeOpen = frame.eyeOpen ?: eyeOpen,
        pupilSize = frame.pupilSize ?: pupilSize,
        pupilGazeUp = frame.pupilGazeUp ?: pupilGazeUp,
        headTiltDeg = frame.headTiltDeg ?: headTiltDeg,
        earPerk = frame.earPerk ?: earPerk,
        earDroopDeg = frame.earDroopDeg ?: earDroopDeg,
        happyEyes = frame.happyEyes ?: happyEyes,
        whiskerFlutter = frame.whiskerFlutter ?: whiskerFlutter,
        mouth = frame.mouth ?: mouth,
        glowColorHex = frame.glowColorHex ?: glowColorHex,
        tongueOut = frame.tongueOut ?: tongueOut,
        bodyBounce = frame.bodyBounce ?: bodyBounce,
        tailWag = frame.tailWag ?: tailWag,
        sparkle = frame.sparkle ?: sparkle,
    )
}

/**
 * Persists invented clips as JSON files, one per animation, so something the
 * companion invented today still exists after a restart. Plain files (not Room)
 * — these are user-visible artifacts, like the workspace drafts.
 */
class CustomAnimationStore(private val dir: File) {

    fun save(spec: CustomAnimationSpec) {
        dir.mkdirs()
        File(dir, fileName(spec.name)).writeText(CustomAnimations.toJson(spec))
    }

    fun load(name: String): CustomAnimationSpec? {
        val file = File(dir, fileName(name))
        if (!file.isFile) return null
        return runCatching { CustomAnimations.parse(file.readText()).first }.getOrNull()
    }

    fun list(): List<String> =
        dir.listFiles()?.filter { it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()

    fun delete(name: String): Boolean = File(dir, fileName(name)).delete()

    private fun fileName(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9_\\-]"), "_") + ".json"
}
