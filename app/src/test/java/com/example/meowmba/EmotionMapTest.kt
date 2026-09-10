package com.example.meowmba

import com.example.meowmba.core.robot.BodyCue
import com.example.meowmba.core.robot.BodyNeckProfile
import com.example.meowmba.core.robot.BodyProfile
import com.example.meowmba.core.robot.BodyVerb
import com.example.meowmba.core.robot.EmotionMap
import com.example.meowmba.core.robot.RobotManager
import com.example.meowmba.ui.companion.Body
import com.example.meowmba.ui.companion.ComposedExpression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Behavior and safety of simulated intent; these do not measure robot motion.
 * The score owns its axes and timing. An emotion name cannot invent a light,
 * a yaw gesture, or permission to translate on a desk. */
class EmotionMapTest {
    private fun expr(
        body: Body = Body.DRIFT,
        durationSeconds: Float = 1.6f,
        bodyAmp: Float = 1f,
    ) = ComposedExpression(body = body, durationSeconds = durationSeconds, bodyAmp = bodyAmp)

    private fun cues(body: Body, profile: BodyProfile = BodyProfile.DESK,
        seconds: Float = 4f, amp: Float = 1f) =
        EmotionMap.cues(expr(body, seconds, amp), profile)

    private fun neck(cues: List<BodyCue>) = cues.filter { it.verb is BodyVerb.Neck }
    private fun pitch(cue: BodyCue) = (cue.verb as BodyVerb.Neck).pitch

    @Test
    fun `bob follows authored rise and fall without inventing mood lighting`() {
        val timeline = cues(Body.BOB)
        val pitches = neck(timeline).map(::pitch)
        assertTrue("bob must contain its rising phase", pitches.any { it < -.025f })
        assertTrue("bob must contain its descending phase", pitches.any { it > .025f })
        assertFalse("the composed score has no light channel", timeline.any { it.verb is BodyVerb.Light })
        assertFalse("a vertical bob has no yaw", timeline.any { it.verb is BodyVerb.Turn })
    }

    @Test
    fun `sink descends through the score and eases back before its exact stop`() {
        val timeline = BodyNeckProfile.expand(cues(Body.SINK))
        val points = neck(timeline)
        val peak = points.maxBy(::pitch)
        assertTrue("sink should visibly lower its pitch target", pitch(peak) > .10f)
        assertTrue("recovery must follow the descending peak", peak.atMs < points.last().atMs)
        assertTrue("recovery reduces displacement", abs(pitch(points.last())) < abs(pitch(peak)))
        assertFalse(timeline.any { it.verb is BodyVerb.Light })
        assertEquals(BodyCue(4000, BodyVerb.Brake), timeline.last())
    }

    @Test
    fun `lean-in projects its vertical axis without inventing a sideways turn`() {
        val timeline = cues(Body.LEAN_IN)
        assertTrue(neck(timeline).any { pitch(it) > .05f })
        assertFalse("lean-in score has no horizontal displacement or roll", timeline.any { it.verb is BodyVerb.Turn })
        assertFalse(timeline.any { it.verb is BodyVerb.Drive || it.verb is BodyVerb.Light })
    }

    @Test
    fun `wiggle retains its authored alternating turn intent`() {
        val turns = cues(Body.WIGGLE).map { it.verb }.filterIsInstance<BodyVerb.Turn>()
        assertTrue("wiggle should retain its horizontal score", turns.isNotEmpty())
        assertTrue(turns.any { it.sign == -1 })
        assertTrue(turns.any { it.sign == 1 })
        assertTrue(turns.all { it.speedPercent == 25 && it.ticks == 3 })
    }

    @Test
    fun `every expression ends braked at its own deadline`() {
        for (body in Body.entries) for (profile in BodyProfile.entries) {
            val timeline = cues(body, profile, seconds = 1.6f)
            assertEquals("$body/$profile", BodyCue(1600, BodyVerb.Brake), timeline.last())
            assertTrue("$body/$profile cannot restart after stop", timeline.all { it.atMs in 0..1600 })
        }
    }

    @Test
    fun `release scores recover within bounds without inventing a late center command`() {
        for (body in Body.entries) {
            val timeline = BodyNeckProfile.expand(cues(body))
            val points = neck(timeline)
            assertTrue("$body should have sampled pitch targets", points.isNotEmpty())
            assertTrue("$body remains within the joint envelope", points.all {
                pitch(it).isFinite() && abs(pitch(it)) <= RobotManager.NECK_RANGE
            })
            assertTrue("$body does not add motion at or after brake", points.all { it.atMs < 4000 })
            assertTrue("$body retains the transport interval", points.zipWithNext().all { (a, b) ->
                b.atMs - a.atMs >= BodyNeckProfile.SAMPLE_MS
            })
            if (body == Body.SINK || body == Body.RISE) {
                // These scores have an authored release ramp. COIL and LURCH
                // have a later visual settle tail, so universal pre-stop center
                // would be a false claim; their exact brake still applies.
                val peak = points.maxOf { abs(pitch(it)) }
                assertTrue("$body must recover before its stop", abs(pitch(points.last())) < peak * .8f)
            }
            assertEquals(BodyCue(4000, BodyVerb.Brake), timeline.last())
        }
    }

    @Test
    fun `desk profile never drives across every body character`() {
        for (body in Body.entries) for (amp in listOf(0f, 1f, 2f)) {
            val timeline = cues(body, BodyProfile.DESK, amp = amp)
            assertFalse("$body at $amp produced desk translation", timeline.any { it.verb is BodyVerb.Drive })
        }
    }

    @Test
    fun `floor bounce uses bounded existing pulses with their brakes`() {
        val timeline = cues(Body.BOB, BodyProfile.FLOOR)
        val drives = timeline.filter { it.verb is BodyVerb.Drive }
        assertTrue("the rising bounce should produce a floor pulse", drives.isNotEmpty())
        for (cue in drives) {
            val speed = (cue.verb as BodyVerb.Drive).speed
            assertTrue(speed.isFinite() && speed > 0f && speed <= .5f)
            assertTrue("pulse at ${cue.atMs} must stop", timeline.any {
                it.atMs == minOf(cue.atMs + 200, 4000) && it.verb == BodyVerb.Brake
            })
        }
    }

    @Test
    fun `zero body amplitude produces only the scheduled brake`() {
        assertEquals(listOf(BodyCue(1600, BodyVerb.Brake)),
            cues(Body.BOB, BodyProfile.FLOOR, seconds = 1.6f, amp = 0f))
    }

    @Test
    fun `vertical shake remains pitch motion without invented yaw or lights`() {
        val timeline = cues(Body.SHAKE_V)
        val pitches = neck(timeline).map(::pitch)
        assertTrue(pitches.any { it < -.025f })
        assertTrue(pitches.any { it > .025f })
        assertFalse(timeline.any { it.verb is BodyVerb.Turn })
        assertFalse(timeline.any { it.verb is BodyVerb.Light || it.verb is BodyVerb.Drive })
    }
}
