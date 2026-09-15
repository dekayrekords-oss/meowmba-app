package com.example.meowmba.ui.companion

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** How the companion might use a dispensed prop. */
enum class GeneratedPropCategory {
    HELD, WEARABLE, SCENE, TOY;

    companion object {
        fun fromKey(raw: String?): GeneratedPropCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: HELD
    }
}

/**
 * A bounded vector prop the companion can hold or place — same shape language
 * as generated avatars, without a live face. Invented through the Vending Machine.
 */
data class GeneratedPropForm(
    val id: String,
    val name: String,
    val description: String,
    val category: GeneratedPropCategory,
    val useHint: String,
    val parts: List<GeneratedAvatarPart>,
    val anchorX: Float = 0f,
    val anchorY: Float = 0.22f,
    val version: Int = 1,
)

object GeneratedPropForms {
    const val MAX_JSON = 24_576
    const val MAX_PARTS = 16
    const val MAX_REQUEST = 720
    const val MAX_SAVED = 32
    class SpecException(message: String) : IllegalArgumentException(message)

    fun parse(raw: String): GeneratedPropForm {
        if (raw.length > MAX_JSON) fail("Prop drawing is too large (maximum $MAX_JSON characters)")
        val root = try {
            val tokenizer = JSONTokener(raw)
            val value = tokenizer.nextValue() as? JSONObject ?: fail("Prop drawing must be a JSON object")
            if (tokenizer.nextClean() != '\u0000') fail("Prop drawing contains trailing content")
            value
        } catch (e: SpecException) { throw e }
        catch (_: Exception) { fail("Prop drawing is not valid JSON") }
        keys(root, setOf("version", "name", "description", "category", "use_hint", "parts", "anchor"), "prop")
        if (number(root, "version", 1f, 1f) != 1f) fail("Unsupported prop version")
        val name = string(root, "name", 60)
        val description = string(root, "description", MAX_REQUEST, allowEmpty = true)
        val catRaw = root.opt("category") as? String ?: fail("Prop needs a category string")
        val category = GeneratedPropCategory.entries.firstOrNull { it.name.equals(catRaw, ignoreCase = true) }
            ?: fail("Prop category must be held, wearable, scene or toy")
        val useHint = string(root, "use_hint", 240, allowEmpty = true)
        val array = root.optJSONArray("parts") ?: fail("Prop needs a parts array")
        if (array.length() !in 1..MAX_PARTS) fail("Prop needs 1 to $MAX_PARTS parts")
        val parts = (0 until array.length()).map { index -> parsePart(array.optJSONObject(index), index) }
        val anchor = root.optJSONObject("anchor") ?: fail("Prop needs an anchor")
        keys(anchor, setOf("x", "y"), "anchor")
        val form = GeneratedPropForm(
            "", name, description, category, useHint, parts,
            number(anchor, "x", -.5f, .5f, 0f),
            number(anchor, "y", -.5f, .5f, .22f),
        )
        val id = MessageDigest.getInstance("SHA-256").digest(toJson(form).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return form.copy(id = id)
    }

    fun toJson(form: GeneratedPropForm): String = JSONObject().apply {
        put("version", 1)
        put("name", form.name)
        put("description", form.description)
        put("category", form.category.name.lowercase(Locale.ROOT))
        put("use_hint", form.useHint)
        put("parts", JSONArray().apply {
            form.parts.forEach { part ->
                put(JSONObject().apply {
                    put("shape", part.shape.name.lowercase(Locale.ROOT))
                    put("x", part.x); put("y", part.y)
                    put("width", part.width); put("height", part.height)
                    put("fill", colorHex(part.fill))
                    put("outline", colorHex(part.outline))
                    put("stroke", part.stroke)
                    put("rotation", part.rotation)
                    put("motion", part.motion.name.lowercase(Locale.ROOT))
                    put("phase", part.phase)
                    if (part.points.isNotEmpty()) {
                        put("points", JSONArray().apply {
                            part.points.forEach { p -> put(JSONArray().put(p.x.toDouble()).put(p.y.toDouble())) }
                        })
                    }
                })
            }
        })
        put("anchor", JSONObject().put("x", form.anchorX).put("y", form.anchorY))
    }.toString()

    private fun parsePart(p: JSONObject?, index: Int): GeneratedAvatarPart {
        val part = p ?: fail("Part $index must be an object")
        keys(part, setOf("shape", "x", "y", "width", "height", "fill", "outline", "stroke", "rotation", "motion", "phase", "points"), "part $index")
        val shape = GeneratedAvatarShape.entries.firstOrNull { it.name.lowercase(Locale.ROOT) == part.opt("shape") }
            ?: fail("Part $index shape must be ellipse, round_rect or polygon")
        val motion = if (!part.has("motion")) GeneratedAvatarMotion.STILL else
            GeneratedAvatarMotion.entries.firstOrNull { it.name.lowercase(Locale.ROOT) == part.opt("motion") }
                ?: fail("Part $index has an unsupported motion")
        val x = number(part, "x", -.5f, .5f); val y = number(part, "y", -.5f, .5f)
        val width = number(part, "width", .01f, 1f); val height = number(part, "height", .01f, 1f)
        val rotation = number(part, "rotation", -180f, 180f, 0f)
        val stroke = number(part, "stroke", .002f, .025f, .012f)
        val angle = rotation * Math.PI.toFloat() / 180f
        val extentX = (abs(cos(angle)) * width + abs(sin(angle)) * height) * .5f + stroke * .5f
        val extentY = (abs(sin(angle)) * width + abs(cos(angle)) * height) * .5f + stroke * .5f
        if (abs(x) + extentX > .57f || abs(y) + extentY > .57f)
            fail("Part $index extends beyond the drawing boundary; move or shrink it within -0.57 to 0.57")
        val points = if (shape == GeneratedAvatarShape.POLYGON) {
            val values = part.optJSONArray("points") ?: fail("Polygon $index needs points")
            if (values.length() !in 3..16) fail("Polygon $index needs 3 to 16 points")
            (0 until values.length()).map { j ->
                val point = values.optJSONArray(j) ?: fail("Polygon point must be an [x,y] pair")
                if (point.length() != 2) fail("Polygon point must have exactly two coordinates")
                fun coordinate(i: Int): Float {
                    val n = (point.opt(i) as? Number)?.toFloat() ?: fail("Polygon coordinates must be numbers")
                    if (!n.isFinite() || n !in -.5f.. .5f) fail("Polygon local coordinates must be -0.5 to 0.5")
                    return n
                }
                GeneratedAvatarPoint(coordinate(0), coordinate(1))
            }
        } else {
            if (part.has("points")) fail("Only polygon parts may contain points")
            emptyList()
        }
        return GeneratedAvatarPart(
            shape, x, y, width, height, color(part, "fill"), color(part, "outline", 0xFF182032),
            stroke, rotation, motion, number(part, "phase", 0f, 6.284f, 0f), points,
        )
    }

    private fun keys(obj: JSONObject, allowed: Set<String>, label: String) {
        obj.keys().asSequence().forEach { key ->
            if (key !in allowed) fail("$label has unsupported field \"$key\"")
        }
    }

    private fun string(obj: JSONObject, key: String, max: Int, allowEmpty: Boolean = false): String {
        val value = obj.opt(key) as? String ?: fail("$key must be a string")
        if (!allowEmpty && value.isBlank()) fail("$key cannot be blank")
        if (value.length > max) fail("$key is too long")
        if (value.any { it.isISOControl() && it != '\n' && it != '\t' }) fail("$key contains control characters")
        return value.trim()
    }

    private fun number(obj: JSONObject, key: String, min: Float, max: Float, default: Float? = null): Float {
        if (!obj.has(key)) {
            if (default != null) return default
            fail("$key is required")
        }
        val n = (obj.opt(key) as? Number)?.toFloat() ?: fail("$key must be a number")
        if (!n.isFinite() || n !in min..max) fail("$key must be $min to $max")
        return n
    }

    private fun color(obj: JSONObject, key: String, default: Long? = null): Long {
        if (!obj.has(key)) {
            if (default != null) return default
            fail("$key is required")
        }
        val raw = obj.opt(key) as? String ?: fail("$key must be a hex color string")
        val match = Regex("^#([0-9A-Fa-f]{6})$").matchEntire(raw)
            ?: fail("$key must be opaque #RRGGBB")
        return 0xFF000000 or match.groupValues[1].toLong(16)
    }

    private fun colorHex(value: Long): String = "#%06X".format(value and 0xFFFFFF)

    private fun fail(message: String): Nothing = throw SpecException(message)
}
