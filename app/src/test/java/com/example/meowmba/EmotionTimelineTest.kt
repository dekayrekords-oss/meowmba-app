package com.example.meowmba

import com.example.meowmba.core.robot.*
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/** These assert simulated command intent only, never measured motor dynamics. */
@OptIn(ExperimentalCoroutinesApi::class)
class EmotionTimelineTest {
    private fun expr(body: Body, seconds: Float = 4f, amp: Float = 1f) =
        ComposedExpression(body = body, durationSeconds = seconds, bodyAmp = amp)
    private fun neck(cues: List<BodyCue>) = cues.filter { it.verb is BodyVerb.Neck }
    private fun pitch(cue: BodyCue) = (cue.verb as BodyVerb.Neck).pitch

    @Test fun `sampled neck follows the actual composed engine rather than enum-label poses`() {
        val expression = expr(Body.COIL)
        val engine = CatMotionEngine().also { it.play(expression) }
        val cues = neck(EmotionMap.cues(expression, BodyProfile.DESK)).associateBy { it.atMs }
        var compared = 0
        for (tick in 1 until 4 * EmotionMap.SIMULATION_HZ) {
            val motion = engine.update(1f / EmotionMap.SIMULATION_HZ, 200f)!!
            if (tick % 30 != 0) continue
            val atMs = tick * 1000 / EmotionMap.SIMULATION_HZ
            val expected = (motion.offsetY / 100f).coerceIn(-RobotManager.NECK_RANGE, RobotManager.NECK_RANGE)
            assertEquals("score pitch at $atMs", expected, pitch(cues.getValue(atMs)), .000001f)
            compared++
        }
        assertEquals(15, compared)
    }

    @Test fun `bobs retain repeated reversals while sink retains a sustained direction`() {
        val bob = neck(EmotionMap.cues(expr(Body.BOB), BodyProfile.DESK))
        val sink = neck(EmotionMap.cues(expr(Body.SINK), BodyProfile.DESK))
        fun signs(cues: List<BodyCue>) = cues.map(::pitch).filter { abs(it) > .025f }
            .map { if (it > 0f) 1 else -1 }
        val bobSigns = signs(bob)
        assertTrue(bobSigns.containsAll(listOf(-1, 1)))
        assertTrue(bobSigns.zipWithNext().count { (a, b) -> a != b } >= 3)
        assertTrue(signs(sink).all { it == 1 })
        assertNotEquals(bob.map(::pitch), sink.map(::pitch))
    }

    @Test fun `snap forward strikes before the coil releases its held anticipation`() {
        val snap = neck(EmotionMap.cues(expr(Body.SNAP_FWD), BodyProfile.DESK))
        val coil = neck(EmotionMap.cues(expr(Body.COIL), BodyProfile.DESK))
        val snapStrike = snap.first { pitch(it) > .05f }.atMs
        val coilRelease = coil.first { pitch(it) > .05f }.atMs
        assertTrue("snap=$snapStrike coil=$coilRelease", snapStrike + 1000 < coilRelease)
        val held = coil.filter { it.atMs in 1750..2250 }.map(::pitch)
        assertTrue(held.size >= 3)
        assertTrue(held.all { it < -.10f })
    }

    @Test fun `every score has exact duration bounded channels and original command cadence`() {
        for (body in Body.entries) for (profile in BodyProfile.entries) for (seconds in listOf(.45f, 1.6f, 3.333f, 10f)) {
            val expression = expr(body, seconds, 2f)
            val duration = (seconds * 1000f).roundToInt()
            val cues = EmotionMap.cues(expression, profile)
            assertEquals("$body/$seconds", BodyCue(duration, BodyVerb.Brake), cues.last())
            assertTrue(cues.all { it.atMs in 0..duration })
            assertTrue(cues.zipWithNext().all { (a, b) -> a.atMs <= b.atMs })
            assertTrue(neck(cues).all { it.atMs % BodyNeckProfile.SAMPLE_MS == 0L })
            assertTrue(neck(cues).zipWithNext().all { (a, b) -> b.atMs - a.atMs >= BodyNeckProfile.SAMPLE_MS })
            for (cue in cues) when (val verb = cue.verb) {
                is BodyVerb.Neck -> assertTrue(verb.pitch.isFinite() && abs(verb.pitch) <= RobotManager.NECK_RANGE)
                is BodyVerb.Drive -> {
                    assertEquals(BodyProfile.FLOOR, profile)
                    assertTrue(verb.speed.isFinite() && verb.speed in 0f.. .5f)
                }
                is BodyVerb.Turn -> {
                    assertEquals(25, verb.speedPercent)
                    assertEquals(3, verb.ticks)
                    assertTrue(verb.sign == -1 || verb.sign == 1)
                }
                is BodyVerb.Light -> fail("Composed expression has no authored light channel")
                BodyVerb.Brake -> Unit
            }
        }
    }

    @Test fun `neck profiling attenuates score changes without extending stop or changing other cues`() {
        for (body in listOf(Body.SHAKE_V, Body.COIL, Body.SNAP_FWD, Body.LURCH)) {
            val raw = EmotionMap.cues(expr(body, 1.6f, 2f), BodyProfile.DESK)
            val profiled = BodyNeckProfile.expand(raw)
            assertEquals(raw.filterNot { it.verb is BodyVerb.Neck }, profiled.filterNot { it.verb is BodyVerb.Neck })
            assertEquals(BodyCue(1600, BodyVerb.Brake), profiled.last())
            assertTrue(neck(profiled).all { (it.verb as BodyVerb.Neck).profiled })
            assertTrue(neck(profiled).all { abs(pitch(it)) <= RobotManager.NECK_RANGE })
            for ((a, b) in neck(profiled).zipWithNext()) {
                assertTrue(b.atMs - a.atMs >= BodyNeckProfile.SAMPLE_MS)
                assertTrue(abs(pitch(b) - pitch(a)) <= .7f * (b.atMs - a.atMs) / 1000f + .00001f)
            }
        }
    }

    @Test fun `facial channels do not invent headlight or unrepresentable actuator movement`() {
        val reference = EmotionMap.cues(expr(Body.SWAY), BodyProfile.DESK)
        for (eye in EyeL.entries) for (ear in EarL.entries) {
            val actual = EmotionMap.cues(expr(Body.SWAY).copy(eye = eye, ear = ear, jaw = JawL.CHATTER), BodyProfile.DESK)
            assertEquals(reference, actual)
        }
        assertFalse(reference.any { it.verb is BodyVerb.Light })
    }

    @Test fun `malformed and unbounded inputs fail closed instead of allocating long timelines`() {
        for (duration in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, 0f,
            Float.MIN_VALUE, Float.MAX_VALUE, 10.01f)) {
            assertEquals(listOf(BodyCue(0, BodyVerb.Brake)), EmotionMap.cues(expr(Body.BOB, duration), BodyProfile.FLOOR))
        }
        for (amp in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, Float.MAX_VALUE, 2.01f)) {
            assertEquals(listOf(BodyCue(0, BodyVerb.Brake)), EmotionMap.cues(expr(Body.BOB, amp = amp), BodyProfile.FLOOR))
        }
        assertEquals(listOf(BodyCue(125, BodyVerb.Brake)), EmotionMap.cues(expr(Body.BOB, .125f), BodyProfile.FLOOR))
        assertEquals(listOf(BodyCue(4000, BodyVerb.Brake)), EmotionMap.cues(expr(Body.WIGGLE, amp = 0f), BodyProfile.FLOOR))
    }

    @Test fun `simulated playback stops exactly at cutoff and cancellation cannot replay remaining intent`() = runTest {
        val writes = mutableListOf<Pair<Long, BodyVerb>>()
        val output = object : BodyOutput {
            override fun neck(pitch: Float) { writes += testScheduler.currentTime to BodyVerb.Neck(pitch) }
            override fun turn(speedPercent: Int, ticks: Int, sign: Int) { writes += testScheduler.currentTime to BodyVerb.Turn(speedPercent, ticks, sign) }
            override fun drive(speed: Float) { writes += testScheduler.currentTime to BodyVerb.Drive(speed) }
            override fun light(on: Boolean) { writes += testScheduler.currentTime to BodyVerb.Light(on) }
            override fun brake() { writes += testScheduler.currentTime to BodyVerb.Brake }
        }
        val scheduler = BodyScheduler(this, output)
        scheduler.play(EmotionMap.cues(expr(Body.BOB, 1.6f), BodyProfile.FLOOR))
        advanceUntilIdle()
        assertEquals(1600L to BodyVerb.Brake, writes.last())
        assertTrue(writes.all { it.first <= 1600L })
        scheduler.play(EmotionMap.cues(expr(Body.COIL), BodyProfile.DESK))
        advanceTimeBy(600)
        runCurrent()
        scheduler.stop()
        val count = writes.size
        assertEquals(testScheduler.currentTime to BodyVerb.Brake, writes.last())
        advanceUntilIdle()
        assertEquals(count, writes.size)
    }
}
