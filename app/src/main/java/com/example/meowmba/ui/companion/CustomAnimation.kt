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
    private const val MAX_FRAMES = 16
    private const val MAX_DURATION_MS = 10_000

    /**
     * Shorter than this and nobody sees the performance. Measured on device:
     * a weak model invented a 60ms clip — the tool honestly reported "Playing
     * (60ms)" and the user reported "it does not make an animation". Both were
     * right. Sub-threshold clips are stretched (ratios preserved) and the
     * stretch is reported, exactly like every other clamp.
     */
    private const val MIN_VISIBLE_MS = 500
    private val NAME_PATTERN = Regex("[A-Za-z0-9 _\\-]{1,40}")

    /** Mouths a clip may use. MUTED is system-owned (the mute X is a fact, not a look). */
    private val ALLOWED_MOUTHS = setOf(MouthMode.IDLE, MouthMode.HAPPY, MouthMode.SPEAKING)

    /**
     * Signature clips shipped with the app, so "do a trick" performs instantly with
     * no model call — and so a fresh install has real animations before the companion
     * invents its first one. Looked up by name before the on-disk store.
     */
    val BUILT_INS: Map<String, CustomAnimationSpec> = listOf(
        CustomAnimationSpec(
            "ear wiggle",
            listOf(
                AnimFrame(atMs = 0, earPerk = 1.0f),
                AnimFrame(atMs = 140, earPerk = 1.5f),
                AnimFrame(atMs = 280, earPerk = 0.7f),
                AnimFrame(atMs = 420, earPerk = 1.4f),
                AnimFrame(atMs = 600, earPerk = 1.0f),
            ),
        ),
        CustomAnimationSpec(
            "happy bop",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, happyEyes = false, mouth = MouthMode.IDLE),
                AnimFrame(
                    atMs = 200, headTiltDeg = -12f, happyEyes = true,
                    mouth = MouthMode.HAPPY, glowColorHex = 0xFF3FB950,
                ),
                AnimFrame(atMs = 450, headTiltDeg = 12f),
                AnimFrame(atMs = 700, headTiltDeg = -8f, whiskerFlutter = true),
                AnimFrame(atMs = 900, headTiltDeg = 0f, happyEyes = false, mouth = MouthMode.IDLE),
            ),
        ),
        CustomAnimationSpec(
            "curious tilt",
            listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, pupilSize = 0.28f),
                AnimFrame(atMs = 350, headTiltDeg = 16f, pupilSize = 0.62f, earPerk = 1.2f),
                AnimFrame(atMs = 700, headTiltDeg = 16f, pupilSize = 0.62f, earPerk = 1.2f),
                AnimFrame(atMs = 1000, headTiltDeg = 0f, pupilSize = 0.28f, earPerk = 1.0f),
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
