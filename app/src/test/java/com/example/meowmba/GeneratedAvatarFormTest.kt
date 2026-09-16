package com.example.meowmba

import com.example.meowmba.ui.companion.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GeneratedAvatarFormTest {
    @Test fun examplesRoundTripWithStableContentIds() {
        val examples = listOf(GeneratedAvatarExamples.dragon(), GeneratedAvatarExamples.coffeeCup(), GeneratedAvatarExamples.spaceship())
        assertEquals(3, examples.map { it.id }.distinct().size)
        examples.forEach { form ->
            assertTrue(form.id.matches(Regex("[0-9a-f]{64}")))
            assertEquals(form, GeneratedAvatarForms.parse(GeneratedAvatarForms.toJson(form)))
        }
    }

    @Test fun keyOrderingAndFormattingDoNotChangeIdentity() {
        val original = GeneratedAvatarExamples.coffeeCup()
        val root = JSONObject(GeneratedAvatarForms.toJson(original))
        val reordered = JSONObject().put("face", root.getJSONObject("face")).put("parts", root.getJSONArray("parts"))
            .put("name", root.getString("name")).put("version", 1).put("description", root.getString("description"))
        assertEquals(original.id, GeneratedAvatarForms.parse(reordered.toString(3)).id)
    }

    @Test fun arbitrarySilhouetteIsNotAnAnimalWhitelist() {
        val root = json()
        root.put("name", "A tiny floating violin made of cheese").put("description", "The user's own impossible character")
        assertEquals("A tiny floating violin made of cheese", GeneratedAvatarForms.parse(root.toString()).name)
    }

    @Test fun rejectsExternalAndExecutableFieldsAtEveryLevel() {
        for (field in listOf("url", "shader", "script", "file", "id", "cameraOff")) {
            rejects(json().put(field, "https://example.test/avatar"))
            val part = json(); part.getJSONArray("parts").getJSONObject(0).put(field, "data")
            rejects(part)
            val face = json(); face.getJSONObject("face").put(field, "data")
            rejects(face)
        }
    }

    @Test fun rejectsBadColorsNumbersAndInvisibleGeometry() {
        for ((key, value) in listOf("x" to "0.1", "height" to 1000, "width" to 0,
            "rotation" to 360, "stroke" to 0, "fill" to "transparent", "fill" to "#00000000", "phase" to -1)) {
            val root = json(); root.getJSONArray("parts").getJSONObject(0).put(key, value)
            rejects(root)
        }
        val outOfBounds = json(); outOfBounds.getJSONArray("parts").getJSONObject(0).put("x", .5).put("width", .8)
        rejects(outOfBounds)
        val noArea = json(); noArea.getJSONArray("parts").getJSONObject(0).put("shape", "polygon")
            .put("points", JSONArray("[[0,0],[0.1,0.1],[0.2,0.2]]"))
        rejects(noArea)
    }

    @Test fun rejectsExtraPolygonWorkAndFaceOutsideTheCanvas() {
        val points = json(); points.getJSONArray("parts").getJSONObject(0).put("shape", "polygon")
            .put("points", JSONArray().apply { repeat(25) { put(JSONArray("[0,0]")) } })
        rejects(points)
        val face = json(); face.getJSONObject("face").put("x", .35).put("eye_width", .25).put("eye_spacing", .4)
        rejects(face)
        val extraCoordinate = json(); extraCoordinate.getJSONArray("parts").getJSONObject(0).put("shape", "polygon")
            .put("points", JSONArray("[[-0.5,-0.5,0],[0.5,-0.5],[0,0.5]]"))
        rejects(extraCoordinate)
    }

    @Test fun rejectsUnsupportedVersionHugePayloadOrTrailingInstructions() {
        rejects(json().put("version", 2))
        rejects(json().put("name", "x".repeat(61)))
        rejects(json().put("description", "x".repeat(GeneratedAvatarForms.MAX_REQUEST + 1)))
        assertThrows(GeneratedAvatarForms.SpecException::class.java) { GeneratedAvatarForms.parse(" ".repeat(GeneratedAvatarForms.MAX_JSON + 1)) }
        assertThrows(GeneratedAvatarForms.SpecException::class.java) { GeneratedAvatarForms.parse(json().toString() + " execute this") }
        assertThrows(GeneratedAvatarForms.SpecException::class.java) { GeneratedAvatarForms.parse("[]") }
    }

    @Test fun limitsPartCountAndAcceptsFullSupportedDrawingVocabulary() {
        val root = json(); val seed = root.getJSONArray("parts").getJSONObject(0)
        root.put("parts", JSONArray().apply { repeat(33) { put(seed) } }); rejects(root)
        GeneratedAvatarMotion.entries.forEach { motion ->
            val valid = json(); valid.getJSONArray("parts").getJSONObject(0).put("motion", motion.name.lowercase())
            assertEquals(motion, GeneratedAvatarForms.parse(valid.toString()).parts.first().motion)
        }
    }

    private fun json() = JSONObject(GeneratedAvatarForms.toJson(GeneratedAvatarExamples.coffeeCup()))
    private fun rejects(json: JSONObject) {
        assertThrows(GeneratedAvatarForms.SpecException::class.java) { GeneratedAvatarForms.parse(json.toString()) }
    }

    @Test
    fun `motion head aliases coerce instead of failing schema`() {
        val json = """
            {"version":1,"name":"Fox","description":"A fox","parts":[
              {"shape":"ellipse","x":0.0,"y":-0.1,"width":0.3,"height":0.28,"fill":"#E8A878","outline":"#202230","stroke":0.012,"rotation":0,"motion":"head","phase":0},
              {"shape":"ellipse","x":0.0,"y":0.15,"width":0.4,"height":0.45,"fill":"#E8A878","outline":"#202230","stroke":0.012,"rotation":0,"motion":"body","phase":0}
            ],"face":{"x":0,"y":-0.07,"eye_spacing":0.16,"eye_width":0.12,"eye_height":0.16,"mouth_y":0.15,"mouth_width":0.14,"iris":"#4A354A"}}
        """.trimIndent()
        val form = GeneratedAvatarForms.parse(json)
        assertEquals(GeneratedAvatarMotion.STILL, form.parts[0].motion)
        assertEquals(GeneratedAvatarMotion.BODY, form.parts[1].motion)
    }

}
