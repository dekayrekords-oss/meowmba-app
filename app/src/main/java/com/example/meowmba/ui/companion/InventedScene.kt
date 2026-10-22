package com.example.meowmba.ui.companion

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Native props only: model output is data, never executable drawing code or URLs. */
enum class InventedPropKind { ROD, FISH, BOBBER, WATER, BALL, STAR, HEART }
data class InventedPropPose(val atMs: Int, val x: Float, val y: Float, val rotation: Float = 0f,
    val scale: Float = 1f, val opacity: Float = 1f, val targetX: Float = .3f, val targetY: Float = .3f)
data class InventedProp(val kind: InventedPropKind, val frames: List<InventedPropPose>, val focus: Boolean = false) {
    fun sample(timeMs: Float): InventedPropPose {
        val time = timeMs.takeIf { it.isFinite() } ?: 0f
        val right = frames.indexOfFirst { it.atMs >= time }
        if (right <= 0) return if (right == 0) frames.first() else frames.last()
        val a = frames[right - 1]; val b = frames[right]
        val f = ((time - a.atMs) / (b.atMs - a.atMs)).coerceIn(0f, 1f)
        val t = f * f * (3f - 2f * f)
        fun mix(x: Float, y: Float) = x + (y - x) * t
        return InventedPropPose(time.roundToInt(), mix(a.x,b.x),mix(a.y,b.y),mix(a.rotation,b.rotation),
            mix(a.scale,b.scale),mix(a.opacity,b.opacity),mix(a.targetX,b.targetX),mix(a.targetY,b.targetY))
    }
}

object InventedScenes {
    const val MAX_PROPS = 6
    fun parse(root: JSONObject, durationMs: Int): List<InventedProp> {
        if (!root.has("props")) return emptyList()
        val props = root.optJSONArray("props") ?: throw CustomAnimations.SpecException("props must be an array")
        if (props.length() > MAX_PROPS) throw CustomAnimations.SpecException("at most 6 props")
        return (0 until props.length()).map { i ->
            val p = props.optJSONObject(i) ?: throw CustomAnimations.SpecException("prop $i must be an object")
            val kind = InventedPropKind.entries.firstOrNull { it.name.equals(p.optString("kind"), true) }
                ?: throw CustomAnimations.SpecException("unsupported prop kind; use ${InventedPropKind.entries.joinToString()}")
            val raw = p.optJSONArray("frames") ?: throw CustomAnimations.SpecException("prop $i needs frames")
            if (raw.length() !in 2..12) throw CustomAnimations.SpecException("prop $i needs 2-12 frames")
            val frames = (0 until raw.length()).map { j ->
                val f = raw.optJSONObject(j) ?: throw CustomAnimations.SpecException("prop frame must be an object")
                fun number(key: String, low: Float, high: Float, default: Float? = null): Float {
                    val v = if (!f.has(key) && default != null) default else f.optDouble(key, Double.NaN).toFloat()
                    if (!v.isFinite() || v !in low..high) throw CustomAnimations.SpecException("prop $i $key must be $low to $high")
                    return v
                }
                val at = number("at", 0f, durationMs.toFloat()).roundToInt()
                InventedPropPose(at, number("x", -.42f,.42f),number("y", -.32f,.38f),
                    number("rotation",-75f,75f,0f),number("scale",.3f,1.2f,1f),number("opacity",0f,1f,1f),
                    number("target_x",-.42f,.42f,.3f),number("target_y",-.32f,.38f,.3f))
            }
            if (frames.first().atMs != 0 || frames.last().atMs != durationMs || frames.zipWithNext().any { (a,b)-> a.atMs >= b.atMs })
                throw CustomAnimations.SpecException("prop frames must increase from 0 to the clip duration")
            InventedProp(kind,frames,p.optBoolean("focus",false))
        }
    }
    fun toJson(props: List<InventedProp>): JSONArray = JSONArray().also { array -> props.forEach { p ->
        val frames = JSONArray()
        p.frames.forEach { f -> frames.put(JSONObject().put("at",f.atMs).put("x",f.x.toDouble()).put("y",f.y.toDouble())
            .put("rotation",f.rotation.toDouble()).put("scale",f.scale.toDouble()).put("opacity",f.opacity.toDouble())
            .put("target_x",f.targetX.toDouble()).put("target_y",f.targetY.toDouble())) }
        array.put(JSONObject().put("kind",p.kind.name).put("focus",p.focus).put("frames",frames))
    } }
}

internal fun lumenInventedSceneInput(input: LumenInput, spec: CustomAnimationSpec?, timeMs: Float): LumenInput {
    val focus = spec?.props?.firstOrNull { it.focus } ?: return input
    val p = focus.sample(timeMs)
    return input.copy(sceneGazeX = (p.x * 2f).coerceIn(-1f,1f), sceneGazeY = (p.y * 2f).coerceIn(-1f,1f),
        sceneGazeWeight = p.opacity * .85f)
}
