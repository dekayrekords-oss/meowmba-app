package com.example.meowmba.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The window arithmetic in [LoopTelemetry].
 *
 * These tests exist because the first version reported only process-lifetime
 * averages, and that is not a diagnostic — it is an alibi. A loop that ran hard
 * for two minutes and then stopped kept printing a decaying "4.0% core, 3.9 Hz"
 * for the next twenty, and read exactly like a loop that was still running.
 * Two separate profiling sessions were spent chasing an ASR loop that was
 * already dead. `win` must mean "since the last drain" and nothing else.
 */
class LoopTelemetryTest {

    @Before
    fun clear() = LoopTelemetry.reset()

    private fun stat(feature: String): LoopTelemetry.Stat =
        LoopTelemetry.drain().single { it.feature == feature }

    @Test
    fun `drain reports only what happened since the previous drain`() {
        repeat(5) { LoopTelemetry.hit("loop") }
        val first = stat("loop")
        assertEquals("first drain is the whole lifetime so far", 5L, first.recentIters)

        repeat(3) { LoopTelemetry.hit("loop") }
        val second = stat("loop")
        assertEquals("window must not re-count earlier iterations", 3L, second.recentIters)
        assertEquals("lifetime keeps counting", 8L, second.iterations)
    }

    @Test
    fun `a loop that stopped is not live and costs nothing in the window`() {
        repeat(10) { LoopTelemetry.hit("asr.decode") }
        LoopTelemetry.addBusyNanos("asr.decode", 40_000_000L) // 40 ms
        val running = stat("asr.decode")
        assertTrue("running loop must be live", running.isLive)
        assertEquals(40L, running.recentBusyMs)

        // Nothing happens for the next window: the loop stopped.
        val stopped = stat("asr.decode")
        assertFalse("a stopped loop must not be reported as live", stopped.isLive)
        assertEquals(0L, stopped.recentIters)
        assertEquals(0L, stopped.recentBusyMs)
        assertEquals(
            "a stopped loop costs 0% of a core right now, whatever it used to cost",
            0.0, stopped.recentCorePct, 0.0
        )
        assertEquals("lifetime is preserved for the historical view", 10L, stopped.iterations)
        assertEquals("lifetime busy time is preserved", 40L, stopped.busyMs)
    }

    @Test
    fun `the window decays to nothing instead of trailing a stale average`() {
        // A long, expensive run followed by silence is the exact shape that
        // looked like live load before: lifetime says "expensive", the window
        // must say "zero".
        LoopTelemetry.addBusyNanos("warmup", 5_000_000_000L) // 5 s of CPU
        repeat(1000) { LoopTelemetry.hit("warmup") }

        val during = stat("warmup")
        assertTrue(during.isLive)

        repeat(12) { stat("warmup") } // twelve quiet windows

        val after = stat("warmup")
        assertFalse(after.isLive)
        assertEquals(0.0, after.recentCorePct, 0.0)
        assertEquals(0.0, after.recentHz, 0.0)
        assertTrue("lifetime must still show the real cost", after.busyMs >= 5_000L)
    }

    @Test
    fun `measure charges busy time and counts exactly one iteration`() {
        val value = LoopTelemetry.measure("work") { 42 }
        assertEquals(42, value)
        val s = stat("work")
        assertEquals(1L, s.recentIters)
        assertTrue("measure must also count, not just time", s.iterations == 1L)
    }

    @Test
    fun `ranking puts the loop that is hot now above the one that was hot before`() {
        // "cold" was very expensive historically; "hot" is cheap but is the one
        // actually burning CPU in this window. Historical cost must not win.
        LoopTelemetry.addBusyNanos("cold", 5_000_000_000L)
        repeat(500) { LoopTelemetry.hit("cold") }
        LoopTelemetry.addBusyNanos("hot", 1_000_000L)
        repeat(5) { LoopTelemetry.hit("hot") }
        LoopTelemetry.drain() // close the window that built up the history

        LoopTelemetry.addBusyNanos("hot", 2_000_000L)
        repeat(2) { LoopTelemetry.hit("hot") }

        val ranked = LoopTelemetry.drain()
        assertEquals("hot", ranked.first().feature)
        assertEquals("cold", ranked.last().feature)
        assertEquals(0L, ranked.last().recentBusyMs)
    }
}
