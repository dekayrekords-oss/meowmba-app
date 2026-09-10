package com.example.meowmba

import com.example.meowmba.core.robot.BodyCue
import com.example.meowmba.core.robot.BodyOutput
import com.example.meowmba.core.robot.BodyScheduler
import com.example.meowmba.core.robot.BodySafety
import com.example.meowmba.core.robot.BodyVerb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BodySchedulerTest {

    private class Recorder : BodyOutput {
        val calls = mutableListOf<String>()
        var failEverything = false
        private fun record(s: String) {
            if (failEverything) error("link down")
            calls.add(s)
        }
        override fun neck(pitch: Float) = record("neck:$pitch")
        override fun turn(speedPercent: Int, ticks: Int, sign: Int) = record("turn:$sign")
        override fun drive(speed: Float) = record("drive:$speed")
        override fun light(on: Boolean) = record("light:$on")
        override fun brake() = record("brake")
    }

    @Test
    fun `cues fire at their scheduled times`() = runTest {
        val out = Recorder()
        val scheduler = BodyScheduler(TestScope(testScheduler + kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)), out)

        scheduler.play(
            listOf(
                BodyCue(0, BodyVerb.Neck(-0.5f)),
                BodyCue(500, BodyVerb.Light(true)),
                BodyCue(1000, BodyVerb.Brake),
            )
        )

        runCurrent()
        assertEquals(listOf("neck:-0.5"), out.calls)

        advanceTimeBy(600)
        runCurrent()
        assertEquals(listOf("neck:-0.5", "light:true"), out.calls)

        advanceUntilIdle()
        assertEquals(listOf("neck:-0.5", "light:true", "brake"), out.calls)

        testScheduler.advanceUntilIdle()
        this.coroutineContext.cancelChildren()
    }

    @Test
    fun `writes closer than the minimum interval are coalesced`() = runTest {
        val out = Recorder()
        val scheduler = BodyScheduler(TestScope(testScheduler + kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)), out, minWriteIntervalMs = 60L)

        // Three neck writes 10ms apart: only the first and the settled last survive.
        scheduler.play(
            listOf(
                BodyCue(0, BodyVerb.Neck(0.1f)),
                BodyCue(10, BodyVerb.Neck(0.2f)),
                BodyCue(20, BodyVerb.Neck(0.3f)),
                BodyCue(500, BodyVerb.Brake),
            )
        )
        advanceUntilIdle()

        val neckWrites = out.calls.count { it.startsWith("neck") }
        assertTrue("expected coalescing, saw $neckWrites neck writes", neckWrites < 3)
        assertTrue("the clip must still end braked", out.calls.last() == "brake")
    }

    @Test
    fun `two motion verbs never overlap`() = runTest {
        val out = Recorder()
        val scheduler = BodyScheduler(TestScope(testScheduler + kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)), out)

        // Drive and Turn scheduled at the same instant: one must be dropped.
        scheduler.play(
            listOf(
                BodyCue(0, BodyVerb.Drive(0.5f)),
                BodyCue(0, BodyVerb.Turn(25, 3, 1)),
                BodyCue(2000, BodyVerb.Brake),
            )
        )
        advanceUntilIdle()

        val motion = out.calls.filter { it.startsWith("drive") || it.startsWith("turn") }
        assertEquals("only one motion verb may be in flight", 1, motion.size)
    }

    @Test
    fun `state writes may interleave with motion`() = runTest {
        val out = Recorder()
        val scheduler = BodyScheduler(TestScope(testScheduler + kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)), out)

        scheduler.play(
            listOf(
                BodyCue(0, BodyVerb.Turn(25, 3, 1)),
                BodyCue(0, BodyVerb.Light(true)),
                BodyCue(1000, BodyVerb.Brake),
            )
        )
        advanceUntilIdle()

        assertTrue("light is a state write and must survive", out.calls.contains("light:true"))
        assertTrue(out.calls.contains("turn:1"))
    }

    @Test
    fun `stop brakes immediately and cancels the rest`() = runTest {
        val out = Recorder()
        val scheduler = BodyScheduler(TestScope(testScheduler + kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)), out)

        scheduler.play(
            listOf(
                BodyCue(0, BodyVerb.Neck(0.2f)),
                BodyCue(5000, BodyVerb.Light(true)),
                BodyCue(9000, BodyVerb.Brake),
            )
        )
        runCurrent()

        scheduler.stop()
        runCurrent()
        assertEquals("brake", out.calls.last())

        advanceUntilIdle()
        assertTrue("cancelled cues must not fire", !out.calls.contains("light:true"))
    }

    @Test
    fun `a new clip supersedes the previous one`() = runTest {
        val out = Recorder()
        val scheduler = BodyScheduler(TestScope(testScheduler + kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)), out)

        scheduler.play(listOf(BodyCue(0, BodyVerb.Neck(0.9f)), BodyCue(8000, BodyVerb.Light(true))))
        runCurrent()
        scheduler.play(listOf(BodyCue(0, BodyVerb.Neck(-0.9f)), BodyCue(100, BodyVerb.Brake)))
        advanceUntilIdle()

        assertTrue("the superseded clip's late cue must not fire", !out.calls.contains("light:true"))
        assertTrue(out.calls.contains("neck:-0.9"))
    }

    @Test
    fun `a failing output never breaks the schedule`() = runTest {
        val out = Recorder()
        out.failEverything = true
        val scheduler = BodyScheduler(TestScope(testScheduler + kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)), out)

        // Must complete without throwing — the face depends on this.
        scheduler.play(listOf(BodyCue(0, BodyVerb.Neck(0f)), BodyCue(200, BodyVerb.Brake)))
        advanceUntilIdle()
    }

    @Test
    fun `motion windows are reported for the confinement model`() = runTest {
        val starts = mutableListOf<Long>()
        val ends = mutableListOf<Long>()
        val scheduler = BodyScheduler(
            TestScope(testScheduler + kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)),
            Recorder(),
            onMotionWindowStart = { starts.add(it) },
            onMotionWindowEnd = { ends.add(it) },
        )

        scheduler.play(
            listOf(
                BodyCue(0, BodyVerb.Drive(0.4f)),
                BodyCue(1000, BodyVerb.Brake),
            )
        )
        advanceUntilIdle()

        assertEquals(1, starts.size)
        assertEquals(1, ends.size)
    }

    @Test
    fun `brake is never swallowed by the write budget`() = runTest {
        val out = Recorder()
        val scheduler = BodyScheduler(TestScope(testScheduler + kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)), out, minWriteIntervalMs = 60L)

        // BodyMotionMap emits Light(false) and Brake at the same instant. If
        // the budget applied to Brake, the base would keep moving after the
        // clip ended — the one failure this subsystem must not have.
        scheduler.play(
            listOf(
                BodyCue(1000, BodyVerb.Light(false)),
                BodyCue(1000, BodyVerb.Brake),
            )
        )
        advanceUntilIdle()

        assertTrue("brake must survive a same-instant neighbour", out.calls.contains("brake"))
    }

    @Test
    fun `an empty cue list is harmless`() = runTest {
        val out = Recorder()
        BodyScheduler(TestScope(testScheduler + kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)), out).play(emptyList())
        advanceUntilIdle()
        assertTrue(out.calls.isEmpty())
    }

    @Test fun `superseding a moving clip brakes before the next clip starts`() = runTest {
        val out = Recorder()
        val scheduler = BodyScheduler(this, out)
        scheduler.play(listOf(BodyCue(0, BodyVerb.Drive(0.5f)), BodyCue(5000, BodyVerb.Brake)))
        runCurrent()
        scheduler.play(listOf(BodyCue(0, BodyVerb.Neck(0.1f)), BodyCue(50, BodyVerb.Brake)))
        advanceUntilIdle()
        assertEquals(listOf("drive:0.5", "brake", "neck:0.1", "brake"), out.calls)
    }

    @Test fun `scope cancellation brakes and closes the motion window`() = runTest {
        val out = Recorder()
        var ends = 0
        val child = TestScope(testScheduler)
        val scheduler = BodyScheduler(child, out, onMotionWindowEnd = { ends++ })
        scheduler.play(listOf(BodyCue(0, BodyVerb.Drive(0.5f)), BodyCue(5000, BodyVerb.Brake)))
        runCurrent()
        child.coroutineContext.cancelChildren()
        runCurrent()
        assertEquals(listOf("drive:0.5", "brake"), out.calls)
        assertEquals(1, ends)
    }

    @Test fun `preconnect and queued old connection clips never reach a newly attached body`() = runTest {
        val gate = BodySafety { testScheduler.currentTime }
        val scheduler = BodyScheduler(this, gate)
        val cues = listOf(BodyCue(100, BodyVerb.Neck(0.2f)), BodyCue(500, BodyVerb.Brake))
        scheduler.play(cues)
        val first = Recorder()
        gate.attach(first)
        advanceUntilIdle()
        assertTrue(first.calls.isEmpty())
        scheduler.play(cues)
        val second = Recorder()
        gate.attach(second)
        advanceUntilIdle()
        assertTrue(second.calls.isEmpty())
        scheduler.play(cues)
        advanceUntilIdle()
        assertEquals(listOf("neck:0.2", "brake"), second.calls)
    }

    @Test fun `rejected desk drive does not create clean motion evidence`() = runTest {
        val gate = BodySafety { testScheduler.currentTime }
        gate.attach(Recorder())
        var starts = 0
        val scheduler = BodyScheduler(this, gate, onMotionWindowStart = { starts++ })
        scheduler.play(listOf(BodyCue(0, BodyVerb.Drive(0.5f)), BodyCue(2000, BodyVerb.Brake)))
        advanceUntilIdle()
        assertEquals(0, starts)
    }

    @Test fun `level drive stays exclusive until an actual brake cue`() = runTest {
        val out = Recorder()
        val scheduler = BodyScheduler(this, out)
        scheduler.play(listOf(
            BodyCue(0, BodyVerb.Drive(0.5f)),
            BodyCue(500, BodyVerb.Turn(25, 3, 1)),
            BodyCue(600, BodyVerb.Brake),
            BodyCue(700, BodyVerb.Turn(25, 3, -1)),
            BodyCue(800, BodyVerb.Brake),
        ))
        advanceUntilIdle()
        assertEquals(listOf("drive:0.5", "brake", "turn:-1", "brake"), out.calls)
    }
}
