package com.example.meowmba

import com.example.meowmba.core.personality.Mood
import com.example.meowmba.core.personality.MoodEngine
import com.example.meowmba.core.personality.MoodEvent
import com.example.meowmba.core.robot.FaceMotorMap
import com.example.meowmba.core.robot.SimulatedMotorController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase G: mood engine state machine + motor mapping. */
class PhaseGTest {

    // ── G1: mood transitions ──────────────────────────────────────────────

    @Test
    fun `events drive mood changes`() {
        val e = MoodEngine()
        assertEquals(Mood.EXCITED, e.onEvent(MoodEvent.WakeGreeting, 1000))
        assertEquals(Mood.PROUD, e.onEvent(MoodEvent.Praise, 2000))
        assertEquals(Mood.STARTLED, e.onEvent(MoodEvent.Shake, 3000))
        assertEquals(Mood.CURIOUS, e.onEvent(MoodEvent.UserSpoke, 4000))
        assertEquals(Mood.CONTENT, e.onEvent(MoodEvent.Settled, 5000))
    }

    @Test
    fun `long silence makes the companion sleepy`() {
        val e = MoodEngine(idleSleepyAfterMs = 30 * 60_000L)
        e.onEvent(MoodEvent.UserSpoke, 1000)
        assertEquals(Mood.SLEEPY, e.tick(1000 + 31 * 60_000L))
    }

    @Test
    fun `startle fades after it settles`() {
        val e = MoodEngine(startledSettleMs = 20_000L)
        e.onEvent(MoodEvent.Shake, 0)
        assertEquals(Mood.STARTLED, e.tick(10_000L))
        assertEquals(Mood.CONTENT, e.tick(21_000L))
    }

    @Test
    fun `chatter is rate-limited to once per cooldown`() {
        val e = MoodEngine(chatterCooldownMs = 60 * 60_000L)
        assertTrue(e.mayChatter(0))
        e.markChattered(0)
        assertFalse(e.mayChatter(30 * 60_000L))
        assertTrue(e.mayChatter(61 * 60_000L))
    }

    @Test
    fun `mood biases idle animation choice`() {
        val e = MoodEngine()
        e.onEvent(MoodEvent.WakeGreeting, 0)
        assertTrue(e.idleAnimationWeight("happy bop") > e.idleAnimationWeight("slow blink"))
        e.onEvent(MoodEvent.Settled, 1)
        e.tick(0) // still content
        assertEquals(1f, e.idleAnimationWeight("ear wiggle", Mood.CONTENT))
    }

    @Test
    fun `sleepy voice drops pitch, excited lifts it`() {
        val e = MoodEngine()
        assertTrue(e.ttsPitch(Mood.EXCITED) > 1f)
        assertTrue(e.ttsPitch(Mood.SLEEPY) < 1f)
        assertEquals(1f, e.ttsPitch(Mood.CONTENT))
    }

    @Test
    fun `chatter remixed from seed is not a four-line loop`() {
        val e = MoodEngine()
        val morning = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 8) }.timeInMillis
        val line = e.pickChatter(morning, 0)
        assertTrue(line.isNotBlank())
        assertNotEquals(line, e.pickChatter(morning, 1))
        val unique = (0 until 40).map { e.pickChatter(morning, it * 17) }.toSet()
        assertTrue("expected lots of unique lines, got ${unique.size}", unique.size >= 20)
    }

    // ── G2: motor mapping ─────────────────────────────────────────────────

    @Test
    fun `followFace looks at the subject on both axes and recenters`() {
        val center = FaceMotorMap.followFace(0f, 0f)
        assertEquals(90f, center[FaceMotorMap.SERVO_HEAD_PAN]!!, 0.01f)
        assertEquals(90f, center[FaceMotorMap.SERVO_HEAD_TILT]!!, 0.01f)

        val right = FaceMotorMap.followFace(0.8f, 0f)
        assertTrue(right[FaceMotorMap.SERVO_HEAD_PAN]!! > 90f)
        val left = FaceMotorMap.followFace(-0.8f, 0f)
        assertTrue(left[FaceMotorMap.SERVO_HEAD_PAN]!! < 90f)

        val down = FaceMotorMap.followFace(0f, 0.8f)
        assertTrue(
            "face lower in frame must nod down",
            down[FaceMotorMap.SERVO_HEAD_TILT]!! > 90f,
        )
        val up = FaceMotorMap.followFace(0f, -0.8f)
        assertTrue(up[FaceMotorMap.SERVO_HEAD_TILT]!! < 90f)
        // Selfie framing lives at y≈-0.5. High gain here made the neck bob
        // with box jitter — a small nod is enough, a large one is chatter.
        val selfie = FaceMotorMap.followFace(0f, -0.5f)[FaceMotorMap.SERVO_HEAD_TILT]!!
        assertTrue("selfie must nod up, got $selfie", selfie < 80f)
        assertTrue("selfie must not slam the neck, got $selfie", selfie >= 60f)
        val near = FaceMotorMap.followFace(0f, -0.10f)[FaceMotorMap.SERVO_HEAD_TILT]!!
        assertEquals("small Y jitter must not nod", 90f, near, 0.01f)

        val gone = FaceMotorMap.followFace(null, null)
        assertEquals(90f, gone[FaceMotorMap.SERVO_HEAD_PAN]!!, 0.01f)
        assertEquals(90f, gone[FaceMotorMap.SERVO_HEAD_TILT]!!, 0.01f)
    }

    @Test
    fun `balance demo counter-rotates the head and clamps`() {
        val level = FaceMotorMap.balanceTilt(0f)[FaceMotorMap.SERVO_HEAD_TILT]!!
        assertEquals(90f, level)
        val leaning = FaceMotorMap.balanceTilt(8f)[FaceMotorMap.SERVO_HEAD_TILT]!!
        assertTrue(leaning < level) // counter-rotation
        val extreme = FaceMotorMap.balanceTilt(80f)[FaceMotorMap.SERVO_HEAD_TILT]!!
        assertEquals(75f, extreme) // clamped to -15°
    }

    @Test
    fun `simulated controller records and clamps commands`() {
        val log = mutableListOf<String>()
        val mc = SimulatedMotorController(log::add)
        mc.setServo(0, 250f)
        mc.setExpression("happy")
        assertTrue(log[0].contains("180.0")) // clamped
        assertTrue(log[1].contains("happy"))
        assertEquals(2, mc.history.size)
    }
}
