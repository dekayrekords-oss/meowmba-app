package com.example.meowmba

import com.example.meowmba.ui.companion.AnimFrame
import com.example.meowmba.ui.companion.AvatarPose
import com.example.meowmba.ui.companion.CustomAnimationSpec
import com.example.meowmba.ui.companion.CustomAnimationStore
import com.example.meowmba.ui.companion.CustomAnimations
import com.example.meowmba.ui.companion.MouthMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The companion inventing its own animations is only safe because the pipeline
 * constrains what a model can propose: these pin the parsing, the clamps, the
 * per-field sparse sampling, and the persistence round-trip.
 */
class CustomAnimationTest {

    private fun parseFails(json: String): String = try {
        CustomAnimations.parse(json)
        throw AssertionError("should have been rejected: $json")
    } catch (e: CustomAnimations.SpecException) {
        e.message!!
    }

    @Test
    fun `a full spec parses with every field intact`() {
        val (spec, notes) = CustomAnimations.parse(
            """
            {"name": "happy wiggle", "frames": [
              {"at": 0, "ear_perk": 1.5, "happy_eyes": true, "mouth": "HAPPY", "glow": "#3FB950"},
              {"at": 600, "ear_perk": 0.6, "head_tilt": -12},
              {"at": 1200, "ear_perk": 1.0, "eye_open": 0.9, "pupil_size": 0.5, "gaze_up": 0.4, "ear_droop": 10}
            ]}
            """.trimIndent()
        )
        assertEquals("happy wiggle", spec.name)
        assertEquals(3, spec.frames.size)
        assertEquals(1200, spec.durationMs)
        assertEquals(1.5f, spec.frames[0].earPerk!!, 0.001f)
        assertEquals(true, spec.frames[0].happyEyes)
        assertEquals(MouthMode.HAPPY, spec.frames[0].mouth)
        assertEquals(0xFF3FB950, spec.frames[0].glowColorHex)
        assertEquals(-12f, spec.frames[1].headTiltDeg!!, 0.001f)
        assertEquals(0.4f, spec.frames[2].pupilGazeUp!!, 0.001f)
        assertTrue(notes.isEmpty())
    }

    @Test
    fun `camelCase keys are accepted because models emit both`() {
        val (spec, _) = CustomAnimations.parse(
            """{"name":"blink twice","frames":[{"at":0,"eyeOpen":0.1},{"at":200,"eyeOpen":1.2,"headTiltDeg":5}]}"""
        )
        assertEquals(0.1f, spec.frames[0].eyeOpen!!, 0.001f)
        assertEquals(5f, spec.frames[1].headTiltDeg!!, 0.001f)
    }

    @Test
    fun `broken specs are rejected with the reason, never guessed`() {
        assertTrue(parseFails("""{"frames":[{"at":0}]}""").contains("name"))
        assertTrue(parseFails("""{"name":"x","frames":[]}""").contains("empty"))
        assertTrue(parseFails("""{"name":"no frames here"}""").contains("frames"))
        assertTrue(parseFails("""{"name":"bad/name","frames":[{"at":0},{"at":10}]}""").contains("bad name"))
        assertTrue(parseFails("""{"name":"x","frames":[{"at":0,"mouth":"MUTED"}]}""").contains("system-owned"))
        assertTrue(parseFails("""{"name":"x","frames":[{"at":0,"mouth":"SCREAMING"}]}""").contains("unknown mouth"))
        assertTrue(parseFails("""{"name":"x","frames":[{"ear_perk":1.5}]}""").contains("no \"at\""))
        assertTrue(parseFails("""{"name":"x","frames":[{"at":0}]}""").contains("never plays"))
        assertTrue(parseFails("not json at all").contains("not JSON"))
    }

    @Test
    fun `more than 16 frames is rejected outright`() {
        val frames = (0..16).joinToString(",") { "{\"at\":${it * 100}}" }
        assertTrue(parseFails("""{"name":"x","frames":[$frames]}""").contains("limit"))
    }

    @Test
    fun `out-of-range numbers are clamped and every clamp is reported`() {
        val (spec, notes) = CustomAnimations.parse(
            """{"name":"wild","frames":[{"at":0,"eye_open":99,"head_tilt":-90},{"at":99000,"ear_perk":0.01}]}"""
        )
        assertEquals(1.6f, spec.frames[0].eyeOpen!!, 0.001f)
        assertEquals(-25f, spec.frames[0].headTiltDeg!!, 0.001f)
        assertEquals(0.3f, spec.frames[1].earPerk!!, 0.001f)
        assertEquals(10_000, spec.frames[1].atMs)
        assertEquals(4, notes.size)
    }

    @Test
    fun `frames sort by time so model order does not matter`() {
        val (spec, _) = CustomAnimations.parse(
            """{"name":"x","frames":[{"at":2000,"eye_open":1.0},{"at":1200,"eye_open":0.2}]}"""
        )
        assertEquals(1200, spec.frames[0].atMs)
        assertEquals(2000, spec.frames[1].atMs)
    }

    @Test
    fun `a clip too short to see is stretched and the stretch is reported`() {
        // Measured on device: a weak model invented a 60ms clip; the tool said
        // "Playing (60ms)" and the user said "it does not make an animation".
        val (spec, notes) = CustomAnimations.parse(
            """{"name":"tiny","frames":[{"at":0,"ear_perk":1.5},{"at":60,"ear_perk":1.0}]}"""
        )
        assertEquals(1200, spec.durationMs)
        assertEquals(0, spec.frames[0].atMs)
        assertEquals(1200, spec.frames[1].atMs)
        assertTrue(notes.any { it.contains("stretched") })
    }

    @Test
    fun `all-zero keyframes are rejected because the clip never plays`() {
        assertTrue(parseFails("""{"name":"x","frames":[{"at":0},{"at":0}]}""").contains("never plays"))
    }

    @Test
    fun `sampling interpolates per field with smoothstep easing`() {
        val spec = CustomAnimationSpec(
            "fade",
            listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f),
                AnimFrame(atMs = 1000, eyeOpen = 0.0f),
            ),
        )
        // u=0.5 is the one point where smoothstep is exactly linear.
        assertEquals(0.5f, CustomAnimations.sample(spec, 500)!!.eyeOpen!!, 0.001f)
        // And it eases: a quarter of the way in, less than a quarter has changed.
        val quarter = CustomAnimations.sample(spec, 250)!!.eyeOpen!!
        assertTrue(quarter > 0.75f)
    }

    @Test
    fun `sampling holds edge values and ends after the duration`() {
        val spec = CustomAnimationSpec(
            "edge",
            listOf(
                AnimFrame(atMs = 200, earPerk = 1.4f),
                AnimFrame(atMs = 600, earPerk = 0.8f),
            ),
        )
        assertEquals(1.4f, CustomAnimations.sample(spec, 0)!!.earPerk!!, 0.001f)
        assertEquals(0.8f, CustomAnimations.sample(spec, 600)!!.earPerk!!, 0.001f)
        assertNull(CustomAnimations.sample(spec, 601))
    }

    @Test
    fun `sparse fields track their own keyframes independently`() {
        val spec = CustomAnimationSpec(
            "sparse",
            listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.2f),
                AnimFrame(atMs = 500, earPerk = 1.5f),
                AnimFrame(atMs = 1000, eyeOpen = 0.8f),
            ),
        )
        val mid = CustomAnimations.sample(spec, 750)!!
        // Eye is between its own 0ms and 1000ms frames…
        assertTrue(mid.eyeOpen!! < 1.2f && mid.eyeOpen!! > 0.8f)
        // …while the ear's single keyframe holds everywhere.
        assertEquals(1.5f, mid.earPerk!!, 0.001f)
        // A field no frame names stays null → the base pose keeps control.
        assertNull(mid.headTiltDeg)
    }

    @Test
    fun `discrete fields step at their keyframe instead of interpolating`() {
        val spec = CustomAnimationSpec(
            "step",
            listOf(
                AnimFrame(atMs = 0, happyEyes = false, mouth = MouthMode.IDLE),
                AnimFrame(atMs = 400, happyEyes = true, mouth = MouthMode.HAPPY),
            ),
        )
        assertEquals(false, CustomAnimations.sample(spec, 399)!!.happyEyes)
        assertEquals(true, CustomAnimations.sample(spec, 400)!!.happyEyes)
        assertEquals(MouthMode.HAPPY, CustomAnimations.sample(spec, 400)!!.mouth)
        // Before the first keyframe, the first keyed value shows (no surprise blank).
        assertEquals(MouthMode.IDLE, CustomAnimations.sample(spec, 0)!!.mouth)
    }

    @Test
    fun `overlay changes only the fields the frame names`() {
        val base = AvatarPose(eyeOpen = 1.1f, earPerk = 1.05f, mouth = MouthMode.SPEAKING)
        val frame = AnimFrame(atMs = 0, eyeOpen = 0.3f, happyEyes = true)
        val out = with(CustomAnimations) { base.overlay(frame) }
        assertEquals(0.3f, out.eyeOpen, 0.001f)
        assertTrue(out.happyEyes)
        assertEquals(1.05f, out.earPerk, 0.001f)
        assertEquals(MouthMode.SPEAKING, out.mouth)
    }

    @Test
    fun `privacy marks are not part of the animatable surface`() {
        // There is no micOffMark/cameraOffMark/dimmed on AnimFrame at all — a
        // clip physically cannot fake a privacy state. Pin the surface:
        val frame = AnimFrame(atMs = 0)
        val out = with(CustomAnimations) { AvatarPose(dimmed = true, micOffMark = true).overlay(frame) }
        assertTrue(out.dimmed)
        assertTrue(out.micOffMark)
        assertFalse(frame.let { it.eyeOpen != null || it.mouth != null })
    }

    @Test
    fun `store round-trips a clip by name`() {
        val dir = createTempDir("anims")
        try {
            val store = CustomAnimationStore(dir)
            val (spec, _) = CustomAnimations.parse(
                """{"name":"ear dance","frames":[{"at":0,"ear_perk":1.5},{"at":400,"ear_perk":0.7}]}"""
            )
            store.save(spec)
            assertEquals(listOf("ear_dance"), store.list())
            val loaded = store.load("ear dance")!!
            assertEquals(spec.name, loaded.name)
            assertEquals(spec.frames, loaded.frames)
            assertTrue(File(dir, "ear_dance.json").isFile)
            assertTrue(store.delete("ear dance"))
            assertTrue(store.list().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `serialization is round-trip stable`() {
        val json = """{"name":"loop","frames":[{"at":0,"eye_open":0.5,"happy_eyes":true},""" +
            """{"at":800,"eye_open":1.2,"mouth":"HAPPY","glow":4294923604}]}"""
        val (spec, _) = CustomAnimations.parse(json)
        val (again, _) = CustomAnimations.parse(CustomAnimations.toJson(spec))
        assertEquals(spec, again)
    }

    // ── The tool's replay path (the reported "makes a file instead" fix) ────

    @Test
    fun `the tool plays a built-in by name with no frames`() = kotlinx.coroutines.test.runTest {
        val played = mutableListOf<CustomAnimationSpec>()
        val tool = com.example.meowmba.core.agent.tools.AvatarAnimateTool(null) { played.add(it) }
        val result = tool.execute(mapOf("name" to "ear wiggle"))
        assertTrue(result is com.example.meowmba.core.agent.tools.ToolResult.Success)
        assertEquals("ear wiggle", played.single().name)
    }

    @Test
    fun `the tool replays a saved clip by name`() = kotlinx.coroutines.test.runTest {
        val dir = createTempDir("anims")
        try {
            val store = CustomAnimationStore(dir)
            val (spec, _) = CustomAnimations.parse(
                """{"name":"my spin","frames":[{"at":0,"head_tilt":-15},{"at":500,"head_tilt":15}]}"""
            )
            store.save(spec)
            val played = mutableListOf<CustomAnimationSpec>()
            val tool = com.example.meowmba.core.agent.tools.AvatarAnimateTool(store) { played.add(it) }
            val result = tool.execute(mapOf("name" to "my spin"))
            assertTrue(result is com.example.meowmba.core.agent.tools.ToolResult.Success)
            assertEquals("my spin", played.single().name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an unknown clip name fails honestly with what DOES exist`() = kotlinx.coroutines.test.runTest {
        val tool = com.example.meowmba.core.agent.tools.AvatarAnimateTool(null) { }
        val result = tool.execute(mapOf("name" to "backflip"))
        assertTrue(result is com.example.meowmba.core.agent.tools.ToolResult.Failure)
        val message = (result as com.example.meowmba.core.agent.tools.ToolResult.Failure).error
        assertTrue(message.contains("backflip"))
        assertTrue(message.contains("ear wiggle"))
    }
}
