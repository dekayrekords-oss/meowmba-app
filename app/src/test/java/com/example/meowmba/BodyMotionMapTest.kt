package com.example.meowmba

import com.example.meowmba.core.robot.BodyCue
import com.example.meowmba.core.robot.BodyMotionMap
import com.example.meowmba.core.robot.BodyProfile
import com.example.meowmba.core.robot.BodyVerb
import com.example.meowmba.ui.companion.AnimFrame
import com.example.meowmba.ui.companion.CustomAnimationSpec
import com.example.meowmba.ui.companion.CustomAnimations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The face-to-body mapper is pure, so every rule it encodes is pinned here.
 * The load-bearing test is `desk profile never drives` — the safety property
 * the whole design rests on.
 */
class BodyMotionMapTest {

    private fun spec(vararg frames: AnimFrame) =
        CustomAnimationSpec("test", frames.toList())

    @Test
    fun `gaze up becomes neck pitch, inverted`() {
        // gaze 1.0 = looking up; neck pitch -1 = up.
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, pupilGazeUp = 1.0f), AnimFrame(atMs = 500)),
            BodyProfile.DESK,
        )
        val neck = cues.first { it.verb is BodyVerb.Neck }.verb as BodyVerb.Neck
        assertEquals(-1.0f, neck.pitch, 0.01f)
    }

    @Test
    fun `centered gaze is centered neck`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, pupilGazeUp = 0.5f), AnimFrame(atMs = 500)),
            BodyProfile.DESK,
        )
        val neck = cues.first { it.verb is BodyVerb.Neck }.verb as BodyVerb.Neck
        assertEquals(0.0f, neck.pitch, 0.01f)
    }

    @Test
    fun `head tilt becomes a turn pulse carrying its sign`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, headTiltDeg = -20f), AnimFrame(atMs = 500)),
            BodyProfile.DESK,
        )
        val turn = cues.first { it.verb is BodyVerb.Turn }.verb as BodyVerb.Turn
        assertEquals(-1, turn.sign)
        assertTrue("speed must be positive", turn.speedPercent > 0)
        assertTrue("ticks must be positive", turn.ticks > 0)
    }

    @Test
    fun `small head tilt is below the deadzone and emits no turn`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, headTiltDeg = 2f), AnimFrame(atMs = 500)),
            BodyProfile.DESK,
        )
        assertFalse(cues.any { it.verb is BodyVerb.Turn })
    }

    @Test
    fun `body bounce is a neck bob on the desk`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, bodyBounce = 0.8f), AnimFrame(atMs = 500)),
            BodyProfile.DESK,
        )
        assertTrue(cues.any { it.verb is BodyVerb.Neck })
        assertFalse(cues.any { it.verb is BodyVerb.Drive })
    }

    @Test
    fun `body bounce is a drive pulse on the floor`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, bodyBounce = 0.8f), AnimFrame(atMs = 500)),
            BodyProfile.FLOOR,
        )
        val drive = cues.first { it.verb is BodyVerb.Drive }.verb as BodyVerb.Drive
        assertTrue("drive is forward only", drive.speed > 0f && drive.speed <= 1f)
    }

    @Test
    fun `downward bounce never drives, because there is no reverse`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, bodyBounce = -0.9f), AnimFrame(atMs = 500)),
            BodyProfile.FLOOR,
        )
        assertFalse(cues.any { it.verb is BodyVerb.Drive })
    }

    @Test
    fun `tail wag becomes a turn pulse`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, tailWag = 0.9f), AnimFrame(atMs = 500)),
            BodyProfile.DESK,
        )
        assertTrue(cues.any { it.verb is BodyVerb.Turn })
    }

    @Test
    fun `bright glow turns the headlight on`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, glowColorHex = 0xFFD29922L), AnimFrame(atMs = 500)),
            BodyProfile.DESK,
        )
        assertTrue(cues.any { it.verb == BodyVerb.Light(true) })
    }

    @Test
    fun `sparkle turns the headlight on`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, sparkle = 0.8f), AnimFrame(atMs = 500)),
            BodyProfile.DESK,
        )
        assertTrue(cues.any { it.verb == BodyVerb.Light(true) })
    }

    @Test
    fun `every clip ends braked and dark`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, sparkle = 0.9f), AnimFrame(atMs = 800)),
            BodyProfile.FLOOR,
        )
        assertEquals(BodyVerb.Brake, cues.last().verb)
        assertEquals(800, cues.last().atMs)
        assertTrue(cues.any { it.verb == BodyVerb.Light(false) })
    }

    @Test
    fun `bounce recovery never outlives the clip final brake`() {
        BodyProfile.entries.forEach { profile ->
            val cues = BodyMotionMap.cues(spec(AnimFrame(atMs = 0),
                AnimFrame(atMs = 500, bodyBounce = 0.8f)), profile)
            assertTrue(cues.all { it.atMs <= 500 })
            assertEquals(BodyCue(500, BodyVerb.Brake), cues.last())
        }
    }

    @Test
    fun `cues are sorted by time`() {
        val cues = BodyMotionMap.cues(
            spec(
                AnimFrame(atMs = 0, pupilGazeUp = 0.2f),
                AnimFrame(atMs = 300, bodyBounce = 0.7f),
                AnimFrame(atMs = 900, tailWag = -0.8f),
                AnimFrame(atMs = 1200),
            ),
            BodyProfile.FLOOR,
        )
        assertEquals(cues.map { it.atMs }.sorted(), cues.map { it.atMs })
    }

    @Test
    fun `ear channels are deliberately unmapped`() {
        val cues = BodyMotionMap.cues(
            spec(
                AnimFrame(atMs = 0, earPerk = 1.6f, earDroopDeg = 35f),
                AnimFrame(atMs = 500),
            ),
            BodyProfile.FLOOR,
        )
        // Only the unconditional end-of-clip Brake survives.
        assertEquals(listOf<BodyVerb>(BodyVerb.Brake), cues.map { it.verb })
    }

    // ── The safety property ───────────────────────────────────────────────

    @Test
    fun `desk profile never drives, across every built-in clip`() {
        CustomAnimations.BUILT_INS.forEach { (name, builtIn) ->
            val cues = BodyMotionMap.cues(builtIn, BodyProfile.DESK)
            assertFalse(
                "built-in \"$name\" produced a Drive cue in DESK profile",
                cues.any { it.verb is BodyVerb.Drive },
            )
        }
    }

    @Test
    fun `desk profile never drives, across a fuzz corpus`() {
        val rng = kotlin.random.Random(20260811)
        repeat(2_000) { iteration ->
            val frames = (0..rng.nextInt(1, 16)).map { i ->
                AnimFrame(
                    atMs = i * rng.nextInt(1, 600),
                    eyeOpen = rng.nextFloat() * 1.6f,
                    pupilGazeUp = rng.nextFloat(),
                    headTiltDeg = rng.nextFloat() * 50f - 25f,
                    earPerk = 0.3f + rng.nextFloat() * 1.3f,
                    earDroopDeg = rng.nextFloat() * 40f,
                    bodyBounce = rng.nextFloat() * 2f - 1f,
                    tailWag = rng.nextFloat() * 2f - 1f,
                    sparkle = rng.nextFloat(),
                )
            }
            val cues = BodyMotionMap.cues(
                CustomAnimationSpec("fuzz-$iteration", frames),
                BodyProfile.DESK,
            )
            assertFalse(
                "fuzz iteration $iteration produced a Drive cue in DESK profile",
                cues.any { it.verb is BodyVerb.Drive },
            )
        }
    }
}
