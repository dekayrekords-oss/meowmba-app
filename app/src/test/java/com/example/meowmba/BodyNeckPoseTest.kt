package com.example.meowmba

import com.example.meowmba.core.robot.BodyOutput
import com.example.meowmba.core.robot.BodySafety
import com.example.meowmba.core.robot.BodyVerb
import com.example.meowmba.core.robot.LooiBleBodyOutput
import com.example.meowmba.core.robot.holdNeckPose
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BodyNeckPoseTest {
    private class Fixture(nowMs: () -> Long) {
        val writes = mutableListOf<Float>()
        var brakes = 0
        private val ble = LooiBleBodyOutput(null, { true }, { writes += it }, nowMs)
        val output = object : BodyOutput by ble {
            override fun brake() {
                brakes++
                ble.brake()
            }
        }
        val gate = BodySafety(nowMs).also { it.attach(output) }
    }

    @Test fun `explicit look eases from the last command and holds ownership through settling`() = runTest {
        val f = Fixture { testScheduler.currentTime }
        f.output.neck(0f)
        val session = f.gate.beginExplicitMotion()!!
        var reachedHold = false
        launch {
            try { reachedHold = session.holdNeckPose(-0.3f) }
            finally { session.finish() }
        }
        runCurrent()
        assertEquals(listOf(0f), f.writes)
        advanceTimeBy(250)
        runCurrent()
        assertEquals(2, f.writes.size)
        assertTrue(f.writes.last() < 0f && f.writes.last() > -0.3f)
        assertEquals(0, f.brakes)
        assertFalse(f.gate.follow(BodyVerb.Neck(0.2f)))
        f.gate.neck(0.2f)
        advanceUntilIdle()
        assertTrue(reachedHold)
        assertEquals(-0.3f, f.writes.last(), 0.0001f)
        assertTrue(f.writes.zipWithNext().all { (a, b) -> b <= a })
        assertEquals(1, f.brakes)
    }

    @Test fun `each nod target and final center reach the pitch writer`() = runTest {
        val f = Fixture { testScheduler.currentTime }
        f.output.neck(0f)
        val session = f.gate.beginExplicitMotion()!!
        val targets = listOf(-0.15f, 0.35f, -0.15f, 0.35f, 0f)
        launch {
            try { targets.forEach { assertTrue(session.holdNeckPose(it)) } }
            finally { session.finish() }
        }
        advanceUntilIdle()
        var at = 0
        targets.forEach { target ->
            at = f.writes.indexOfFirstFrom(at + 1) { kotlin.math.abs(it - target) < 0.0001f }
            assertTrue("each nod endpoint must reach the writer in order: $target", at >= 0)
        }
        assertTrue("intermediate targets should replace hard jumps", f.writes.size > targets.size + 1)
        assertEquals(1, f.brakes)
    }

    @Test fun `cancelling held neck brakes immediately and prevents the pending retry`() = runTest {
        val f = Fixture { testScheduler.currentTime }
        f.output.neck(0f)
        val session = f.gate.beginExplicitMotion()!!
        val job = launch { session.holdNeckPose(0.3f) }
        runCurrent()
        advanceTimeBy(100)
        job.cancel()
        runCurrent()
        assertEquals(1, f.brakes)
        assertFalse(session.isValid)
        advanceUntilIdle()
        assertEquals(listOf(0f), f.writes)
        assertEquals(1, f.brakes)
    }

    @Test fun `a touch interrupts a held target before its retry`() = runTest {
        val f = Fixture { testScheduler.currentTime }
        f.output.neck(0f)
        val session = f.gate.beginExplicitMotion()!!
        var completed = true
        launch {
            try { completed = session.holdNeckPose(0.3f) }
            finally { session.finish() }
        }
        runCurrent()
        f.gate.touch()
        advanceUntilIdle()
        assertFalse(completed)
        assertEquals(listOf(0f), f.writes)
        assertEquals(1, f.brakes)
    }

    private fun <T> List<T>.indexOfFirstFrom(start: Int, predicate: (T) -> Boolean): Int =
        (start until size).firstOrNull { predicate(this[it]) } ?: -1
}
