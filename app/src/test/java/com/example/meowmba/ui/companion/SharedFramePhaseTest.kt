package com.example.meowmba.ui.companion

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The shared phase may only change WHICH vsync a background clock draws on,
 * never how often the avatar or the background draws. These tests pin both.
 */
class SharedFramePhaseTest {
    private val vsync120 = 8_333_333L
    private val vsync60 = 16_666_667L

    private data class Mode(val active: Boolean, val fps: Int, val thermal: Int)

    private fun modes(random: Random, count: Int): List<Mode> = List(count) {
        Mode(
            active = random.nextInt(4) == 0,
            fps = listOf(15, 30, 45, 60).random(random),
            thermal = listOf(
                PowerManager.THERMAL_STATUS_NONE, PowerManager.THERMAL_STATUS_NONE,
                PowerManager.THERMAL_STATUS_LIGHT, PowerManager.THERMAL_STATUS_MODERATE, PowerManager.THERMAL_STATUS_SEVERE,
            ).random(random),
        )
    }

    @Test fun `the avatar leader admits exactly the frames it admitted before`() {
        for (seed in 0 until 20) {
            val random = Random(seed)
            val private = AvatarFrameCadence()
            val leader = AvatarFrameCadence(SharedFramePhase(), leader = true)
            val period = if (seed % 2 == 0) vsync120 else vsync60
            // Change mode every ~0.5 s, with a little vsync jitter, for 20 s.
            val schedule = modes(random, 40)
            var t = 1_000_000_000L
            for (i in 0 until (20_000_000_000L / period).toInt()) {
                t += period + random.nextLong(-200_000L, 200_000L)
                val m = schedule[(i * period / 500_000_000L).toInt().coerceAtMost(schedule.lastIndex)]
                assertEquals(
                    "seed $seed vsync $i",
                    private.admit(t, m.active, m.fps, m.thermal),
                    leader.admit(t, m.active, m.fps, m.thermal),
                )
            }
        }
    }

    /** Frame indices each clock drew on, polling every vsync in a random callback order. */
    private fun run(
        vsyncs: Int,
        period: Long,
        followerStartsAt: Int,
        fps: Int,
        shared: Boolean,
        seed: Int = 1,
        leaderActive: (Int) -> Boolean = { false },
        leaderRunning: (Int) -> Boolean = { true },
    ): Pair<List<Int>, List<Int>> {
        val phase = if (shared) SharedFramePhase() else null
        val leader = AvatarFrameCadence(phase, leader = true)
        val follower = AvatarFrameCadence(phase)
        val random = Random(seed)
        val leaderFrames = mutableListOf<Int>()
        val followerFrames = mutableListOf<Int>()
        for (i in 0 until vsyncs) {
            val t = 1_000_000_000L + i * period
            val pollLeader = { if (leaderRunning(i) && leader.admit(t, leaderActive(i), fps)) leaderFrames += i }
            val pollFollower = { if (i >= followerStartsAt && follower.admit(t, false, fps)) followerFrames += i }
            if (random.nextBoolean()) { pollLeader(); pollFollower() } else { pollFollower(); pollLeader() }
        }
        return leaderFrames to followerFrames
    }

    @Test fun `a background clock at the avatar's rate draws on the avatar's vsyncs`() {
        // Avatar FPS target 45: both idle clocks admit every sixth 120 Hz vsync.
        // Started three vsyncs apart they used to interleave into 40 FPS of redraws.
        val (leaderPrivate, followerPrivate) = run(1200, vsync120, followerStartsAt = 3, fps = 45, shared = false)
        val privateUnion = (leaderPrivate + followerPrivate).toSet()
        assertEquals(leaderPrivate.size * 2, privateUnion.size, "baseline: every frame is a separate redraw")

        val (leader, follower) = run(1200, vsync120, followerStartsAt = 3, fps = 45, shared = true)
        assertEquals(leaderPrivate, leader)
        val joined = follower.drop(1)
        assertTrue(joined.all { it in leader }, "after joining, the background draws only with the avatar")
        val union = (leader + follower).toSet()
        assertTrue(union.size <= leader.size + 1, "redraws ~halved: ${union.size} vs ${privateUnion.size}")
        // Same frame rate as its private clock (one frame of slack for the join).
        assertTrue(kotlin.math.abs(follower.size - followerPrivate.size) <= 1)
        joined.zipWithNext { a, b -> assertEquals(6, b - a) }
    }

    @Test fun `a background clock never draws faster than the active rate while joining`() {
        for (start in 0 until 12) for (seed in 0 until 4) {
            val (_, follower) = run(600, vsync120, followerStartsAt = start, fps = 30, shared = true, seed = seed)
            val activeInterval = AvatarFrameCadence.intervalNs(true, 30, PowerManager.THERMAL_STATUS_NONE)
            follower.zipWithNext { a, b ->
                assertTrue("start $start seed $seed: ${b - a} vsyncs", (b - a) * vsync120 >= activeInterval)
            }
        }
    }

    @Test fun `a background clock keeps its own cadence while the avatar performs or stops`() {
        // Avatar performs (different interval) for vsyncs 300..599, then pauses from 900.
        val active = { i: Int -> i in 300 until 600 }
        val running = { i: Int -> i < 900 }
        val (_, shared) = run(1500, vsync120, 5, 30, true, leaderActive = active, leaderRunning = running)
        val (_, private) = run(1500, vsync120, 5, 30, false, leaderActive = active, leaderRunning = running)
        // Identical frame counts in the performing window and after the avatar stopped.
        fun count(frames: List<Int>, range: IntRange) = frames.count { it in range }
        assertTrue(kotlin.math.abs(count(shared, 320 until 580) - count(private, 320 until 580)) <= 1)
        assertTrue(kotlin.math.abs(count(shared, 920 until 1500) - count(private, 920 until 1500)) <= 1)
        // After the avatar stops, the background never goes quiet for longer than 1.5 intervals.
        shared.filter { it >= 900 }.zipWithNext { a, b -> assertEquals(9, b - a) }
    }

    /** Poll every vsync, or sleep after each admitted frame for idleNanosAfterAdmit(). */
    private fun sleepingFrames(
        cadence: AvatarFrameCadence,
        period: Long,
        vsyncs: Int,
        sleep: Boolean,
        mode: (Int) -> Mode,
    ): List<Int> {
        val admitted = mutableListOf<Int>()
        var wakeAt = Long.MIN_VALUE
        for (i in 0 until vsyncs) {
            val t = 1_000_000_000L + i * period
            if (sleep && t < wakeAt) continue
            val m = mode(i)
            if (cadence.admit(t, m.active, m.fps, m.thermal)) {
                admitted += i
                // The loop sleeps from the admitted frame; a wake between vsyncs
                // registers for the next vsync, like withFrameNanos after delay().
                if (sleep) wakeAt = t + cadence.idleNanosAfterAdmit() + 1
            }
        }
        return admitted
    }

    @Test fun `sleeping between frames admits exactly the vsyncs polling admits`() {
        val thermals = listOf(
            PowerManager.THERMAL_STATUS_NONE, PowerManager.THERMAL_STATUS_LIGHT, PowerManager.THERMAL_STATUS_MODERATE, PowerManager.THERMAL_STATUS_SEVERE,
        )
        for (period in listOf(vsync120, vsync60, 11_111_111L)) for (active in listOf(true, false))
            for (fps in listOf(15, 24, 30, 45, 60)) for (thermal in thermals) {
                val m = Mode(active, fps, thermal)
                val vsyncs = (10_000_000_000L / period).toInt()
                val polled = sleepingFrames(AvatarFrameCadence(), period, vsyncs, sleep = false) { m }
                val slept = sleepingFrames(AvatarFrameCadence(), period, vsyncs, sleep = true) { m }
                assertEquals("period $period $m", polled, slept)
            }
    }

    @Test fun `a sleeping background clock still draws on the avatar's vsyncs`() {
        val phase = SharedFramePhase()
        val leader = AvatarFrameCadence(phase, leader = true)
        val follower = AvatarFrameCadence(phase)
        val leaderFrames = mutableListOf<Int>()
        val followerFrames = mutableListOf<Int>()
        var leaderWake = Long.MIN_VALUE
        var followerWake = Long.MIN_VALUE
        val random = Random(7)
        for (i in 0 until 2400) {
            val t = 1_000_000_000L + i * vsync120
            val pollLeader = {
                if (t >= leaderWake && leader.admit(t, false, 45)) {
                    leaderFrames += i
                    leaderWake = t + leader.idleNanosAfterAdmit() + 1
                }
            }
            val pollFollower = {
                if (i >= 4 && t >= followerWake && follower.admit(t, false, 45)) {
                    followerFrames += i
                    followerWake = t + follower.idleNanosAfterAdmit() + 1
                }
            }
            if (random.nextBoolean()) { pollLeader(); pollFollower() } else { pollFollower(); pollLeader() }
        }
        assertTrue(followerFrames.size > 390, "background kept its rate: ${followerFrames.size}")
        assertTrue(followerFrames.all { it in leaderFrames }, "background drew only with the avatar")
    }

    @Test fun `sleep is skipped when there is no room before the next frame`() {
        val c = AvatarFrameCadence()
        // 60 FPS target at 120 Hz: the next frame is two vsyncs away.
        for (i in 0 until 4) c.admit(i * vsync120, fpsTarget = 60)
        assertEquals(0L, c.idleNanosAfterAdmit())
        val idle = AvatarFrameCadence()
        for (i in 0 until 20) idle.admit(i * vsync120, active = false)
        assertTrue(idle.idleNanosAfterAdmit() in 40_000_000L..51_000_000L)
    }

    private fun assertEquals(expected: Int, actual: Int, message: String) = assertEquals(message, expected.toLong(), actual.toLong())
    private fun assertTrue(condition: Boolean, message: String) = assertTrue(message, condition)
}
