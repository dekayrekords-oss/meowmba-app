package com.example.meowmba

import com.example.meowmba.core.robot.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class BodyNeckProfileTest {
    @Test fun `minimum jerk curve is monotonic bounded and ends exactly at target`() {
        val points = (0..1000).map { BodyNeckProfile.sample(-0.35f, 0.35f, it / 1000f) }
        assertEquals(-0.35f, points.first(), 0f)
        assertEquals(0.35f, points.last(), 0f)
        assertTrue(points.all { it in -0.35f..0.35f })
        assertTrue(points.zipWithNext().all { (a, b) -> b + 0.000002f >= a })
        assertEquals(0f, BodyNeckProfile.ease(-1f), 0f)
        assertEquals(1f, BodyNeckProfile.ease(2f), 0f)
    }

    @Test fun `initial and terminal velocity and acceleration approach zero`() {
        val h = 0.001f
        // Numerical endpoint derivatives; not measurements of the physical motor.
        fun f(t: Float) = BodyNeckProfile.ease(t)
        assertTrue(abs((f(h) - f(0f)) / h) < 0.001f)
        assertTrue(abs((f(1f) - f(1f - h)) / h) < 0.002f)
        assertTrue(abs((f(2 * h) - 2 * f(h) + f(0f)) / (h * h)) < 0.1f)
        // The float polynomial loses precision very near one; symmetry gives
        // the equivalent terminal derivative without cancellation noise.
        assertEquals(1f - f(0.01f), f(0.99f), 0.000002f)
    }

    @Test fun `explicit duration grows with travel and retains existing wire cadence`() {
        val small = BodyNeckProfile.durationMs(0f, 0.05f)
        val large = BodyNeckProfile.durationMs(-0.35f, 0.35f)
        assertTrue(small >= 750L)
        assertTrue(large > small && large <= 2000L)
        assertEquals(0L, large % LooiBleBodyOutput.NECK_SPACING_MS)
        val positions = (0..(large / 250).toInt()).map {
            BodyNeckProfile.sample(-0.35f, 0.35f, it * 250f / large)
        }
        assertTrue(positions.zipWithNext().all { (a, b) -> abs(b - a) <= 0.7f * 0.25f + 0.00001f })
    }

    @Test fun `custom neck keyframes ease on their timeline without changing other actuators`() {
        val other = listOf(BodyCue(100, BodyVerb.Light(true)), BodyCue(700, BodyVerb.Turn(25, 3, -1)),
            BodyCue(1000, BodyVerb.Light(false)), BodyCue(1000, BodyVerb.Brake))
        val expanded = BodyNeckProfile.expand(listOf(BodyCue(0, BodyVerb.Neck(0f)),
            BodyCue(1000, BodyVerb.Neck(0.3f))) + other)
        assertEquals(other, expanded.filterNot { it.verb is BodyVerb.Neck })
        val neck = expanded.filter { it.verb is BodyVerb.Neck }
        assertEquals(listOf(0, 250, 500, 750, 1000), neck.map { it.atMs })
        assertTrue(neck.all { (it.verb as BodyVerb.Neck).profiled })
        assertEquals(0.15f, (neck[2].verb as BodyVerb.Neck).pitch, 0.00001f)
        assertEquals(BodyVerb.Brake, expanded.last().verb)
    }

    @Test fun `fast neck beats are attenuated and never extend the clip stop`() {
        val expanded = BodyNeckProfile.expand(listOf(BodyCue(0, BodyVerb.Neck(0f)),
            BodyCue(100, BodyVerb.Neck(1f)), BodyCue(100, BodyVerb.Brake)))
        val target = (expanded.first { it.atMs == 100 && it.verb is BodyVerb.Neck }.verb as BodyVerb.Neck).pitch
        assertTrue(target > 0f && target < 0.04f)
        assertEquals(100, expanded.maxOf { it.atMs })
        assertEquals(BodyVerb.Brake, expanded.last().verb)
    }

    @Test fun `profile deduplicates competing joint targets and rejects invalid points`() {
        val expanded = BodyNeckProfile.expand(listOf(BodyCue(0, BodyVerb.Neck(0.3f)),
            BodyCue(0, BodyVerb.Neck(-0.2f)), BodyCue(100, BodyVerb.Neck(Float.NaN)),
            BodyCue(150, BodyVerb.Neck(Float.POSITIVE_INFINITY)), BodyCue(-1, BodyVerb.Neck(0f)),
            BodyCue(Int.MAX_VALUE, BodyVerb.Neck(0f)), BodyCue(500, BodyVerb.Brake)))
        val neck = expanded.filter { it.verb is BodyVerb.Neck }
        assertEquals(listOf(BodyCue(0, BodyVerb.Neck(-0.2f, profiled = true))), neck)
    }

    @Test fun `a brake is a profile barrier and cannot be followed by invented intermediate motion`() {
        val expanded = BodyNeckProfile.expand(listOf(BodyCue(0, BodyVerb.Neck(0f)),
            BodyCue(300, BodyVerb.Brake), BodyCue(1000, BodyVerb.Neck(0.3f)), BodyCue(1200, BodyVerb.Brake)))
        assertFalse(expanded.any { it.atMs in 1..999 && it.verb is BodyVerb.Neck })
    }

    private class Fixture(now: () -> Long) {
        val writes = mutableListOf<Pair<Long, Float>>()
        var brakes = 0
        var connected = true
        private val ble = LooiBleBodyOutput(null, { connected }, { writes += now() to it }, now)
        val output = object : BodyOutput by ble {
            override fun brake() { brakes++; ble.brake() }
        }
        val gate = BodySafety(now).also { it.attach(output) { connected } }
    }

    @Test fun `profile samples bypass only camera sustain not spacing bounds or finite checks`() {
        var now = 1000L
        val f = Fixture { now }
        val lease = f.gate.beginPlayback()!!
        assertTrue(lease.submit(BodyVerb.Neck(0f, profiled = true)))
        now += 100
        lease.submit(BodyVerb.Neck(0.1f, profiled = true))
        assertEquals(1, f.writes.size)
        now += 150
        lease.submit(BodyVerb.Neck(0.1f, profiled = true))
        now += 250
        lease.submit(BodyVerb.Neck(0.2f, profiled = true))
        now += 250
        lease.submit(BodyVerb.Neck(8f, profiled = true))
        assertFalse(lease.submit(BodyVerb.Neck(Float.NaN, profiled = true)))
        assertEquals(listOf(0f, 0.1f, 0.2f, 0.35f), f.writes.map { it.second })
        assertTrue(f.writes.zipWithNext().all { (a, b) -> b.first - a.first >= 250 })
    }

    @Test fun `camera follow cannot impersonate an authored neck profile`() {
        val f = Fixture { 1000L }
        assertFalse(f.gate.follow(BodyVerb.Neck(0.2f, profiled = true)))
        assertTrue(f.writes.isEmpty())
        assertTrue(f.gate.follow(BodyVerb.Neck(0.2f)))
        assertEquals(1, f.writes.size)
    }

    @Test fun `unknown starting position never invents a center or old connection pose`() = runTest {
        val f = Fixture { testScheduler.currentTime }
        val lease = f.gate.beginExplicitMotion()!!
        assertNull(lease.lastCommandedNeckPitch)
        launch { try { assertTrue(lease.holdNeckPose(0.2f)) } finally { lease.finish() } }
        advanceUntilIdle()
        assertEquals(listOf(0.2f), f.writes.map { it.second })
        assertEquals(1, f.brakes)
        assertNull(lease.lastCommandedNeckPitch)
    }

    @Test fun `scheduler sends intermediate samples through the one live safety lease`() = runTest {
        val f = Fixture { testScheduler.currentTime }
        BodyScheduler(this, f.gate).play(listOf(BodyCue(0, BodyVerb.Neck(0f)),
            BodyCue(1000, BodyVerb.Neck(0.3f)), BodyCue(1250, BodyVerb.Brake)))
        advanceUntilIdle()
        assertEquals(listOf(0L, 250L, 500L, 750L, 1000L), f.writes.map { it.first })
        assertEquals(0.3f, f.writes.last().second, 0.00001f)
        assertEquals(1, f.brakes)
        assertFalse(f.gate.translationAllowed)
    }

    @Test fun `stop cancels interpolated custom frames immediately and cannot replay them`() = runTest {
        val f = Fixture { testScheduler.currentTime }
        val scheduler = BodyScheduler(this, f.gate)
        scheduler.play(listOf(BodyCue(0, BodyVerb.Neck(0f)), BodyCue(2000, BodyVerb.Neck(0.35f)),
            BodyCue(2200, BodyVerb.Brake)))
        advanceTimeBy(600)
        runCurrent()
        val count = f.writes.size
        scheduler.stop()
        assertTrue(f.brakes > 0)
        advanceUntilIdle()
        assertEquals(count, f.writes.size)
    }

    @Test fun `pause touch cliff and unknown interrupt profiles without reviving them`() = runTest {
        val hazards: List<(BodySafety) -> Unit> = listOf({ it.setPaused(true) }, { it.touch() },
            { it.onSensor(SensorEvent.Cliff(byteArrayOf(1))) }, { it.onSensor(SensorEvent.Unknown(byteArrayOf(2))) })
        hazards.forEach { interrupt ->
            val f = Fixture { testScheduler.currentTime }
            f.output.neck(0f)
            val lease = f.gate.beginExplicitMotion()!!
            var complete = true
            launch { try { complete = lease.holdNeckPose(0.3f) } finally { lease.finish() } }
            advanceTimeBy(300)
            runCurrent()
            interrupt(f.gate)
            val stoppedCount = f.writes.size
            assertEquals(1, f.brakes)
            advanceUntilIdle()
            assertFalse(complete)
            assertEquals(stoppedCount, f.writes.size)
            assertFalse(lease.isValid)
        }
    }

    @Test fun `reconnection fences scheduled profile frames and old cleanup cannot brake new owner`() = runTest {
        val first = Fixture { testScheduler.currentTime }
        val scheduler = BodyScheduler(this, first.gate)
        scheduler.play(listOf(BodyCue(0, BodyVerb.Neck(0f)), BodyCue(2000, BodyVerb.Neck(0.3f)),
            BodyCue(2200, BodyVerb.Brake)))
        advanceTimeBy(300)
        runCurrent()
        val next = Fixture { testScheduler.currentTime }
        first.gate.attach(next.output)
        val owner = first.gate.beginExplicitMotion()!!
        owner.submit(BodyVerb.Neck(-0.1f, profiled = true))
        advanceUntilIdle()
        assertEquals(listOf(-0.1f), next.writes.map { it.second })
        assertEquals(0, next.brakes)
        assertTrue(owner.isValid)
        owner.finish()
    }

    @Test fun `cancelling an explicit eased look brakes during the curve not after it`() = runTest {
        val f = Fixture { testScheduler.currentTime }
        f.output.neck(0f)
        val lease = f.gate.beginExplicitMotion()!!
        val job = launch { lease.holdNeckPose(0.3f) }
        advanceTimeBy(300)
        runCurrent()
        assertTrue(f.writes.last().second in 0f..0.3f)
        val count = f.writes.size
        job.cancel()
        runCurrent()
        assertEquals(1, f.brakes)
        advanceUntilIdle()
        assertEquals(count, f.writes.size)
        assertFalse(lease.isValid)
    }
}
