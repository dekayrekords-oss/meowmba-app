package com.example.meowmba

import com.example.meowmba.core.robot.BodyOutput
import com.example.meowmba.core.robot.BodyVerb
import com.example.meowmba.core.robot.LooiBleBodyOutput
import com.example.meowmba.core.robot.SimulatedBodyOutput
import com.example.meowmba.core.robot.perform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The BLE body cannot be held in a unit test, so what is pinned here is the
 * honest stand-in, the verb dispatch every implementation shares, and the
 * neck coalescing that keeps a steady face from hammering the live link.
 */
class BodyOutputTest {

    @Test
    fun `simulated output records every verb`() {
        val out = SimulatedBodyOutput(log = {})
        out.perform(BodyVerb.Neck(-0.5f))
        out.perform(BodyVerb.Turn(25, 3, -1))
        out.perform(BodyVerb.Drive(0.4f))
        out.perform(BodyVerb.Light(true))
        out.perform(BodyVerb.Brake)

        assertEquals(5, out.history.size)
        assertTrue(out.history[0].contains("neck"))
        assertTrue(out.history[1].contains("turn"))
        assertTrue(out.history[2].contains("drive"))
        assertTrue(out.history[3].contains("light"))
        assertTrue(out.history[4].contains("brake"))
    }

    @Test
    fun `simulated output clamps neck pitch to the legal range`() {
        val out = SimulatedBodyOutput(log = {})
        out.neck(5f)
        out.neck(-5f)
        assertTrue(out.history[0].contains("1.0"))
        assertTrue(out.history[1].contains("-1.0"))
    }

    @Test
    fun `simulated output clamps drive to forward only`() {
        val out = SimulatedBodyOutput(log = {})
        out.drive(-0.8f)
        assertTrue("reverse must clamp to zero", out.history.single().contains("0.0"))
    }

    @Test
    fun `history is bounded so a long session cannot grow without limit`() {
        val out = SimulatedBodyOutput(log = {})
        repeat(500) { out.brake() }
        assertTrue("history should be capped", out.history.size <= 200)
    }

    @Test
    fun `a failing output never throws through perform`() {
        val exploding = object : BodyOutput {
            override fun neck(pitch: Float) = error("link down")
            override fun turn(speedPercent: Int, ticks: Int, sign: Int) = error("link down")
            override fun drive(speed: Float) = error("link down")
            override fun light(on: Boolean) = error("link down")
            override fun brake() = error("link down")
        }
        // perform swallows: the face must never die because the body did.
        exploding.perform(BodyVerb.Neck(0f))
        exploding.perform(BodyVerb.Brake)
    }

    /**
     * The neck rules, driven by a hand-rolled clock: a pose must HOLD before it
     * is believed, and two writes must be [NECK_SPACING_MS] apart. Without both,
     * tilting the phone moves the camera, which moves the measured pose, which
     * tilts it back — the nod loop this replaced.
     */
    @Test
    fun `ble neck coalesces moves under the deadband so a steady face stays quiet`() {
        val writes = mutableListOf<Float>()
        var now = 1_000L
        val out = LooiBleBodyOutput(null, { true }, { writes += it }, { now })

        out.neck(0.1f)            // first pose: no history to distrust -> writes
        out.neck(0.12f)           // +0.02 < deadband -> skipped
        out.neck(0.15f)           // new target, not yet held -> skipped
        now += 200L
        out.neck(0.15f)           // held, but only 200ms since the last write
        now += 100L
        out.neck(0.15f)           // 300ms since the write -> goes through
        now += 300L
        out.neck(0.11f)           // new target -> must hold first
        now += 200L
        out.neck(0.11f)           // -0.04 == deadband -> writes (not strictly less)
        out.neck(0.105f)          // -0.005 -> skipped

        assertEquals(listOf(0.1f, 0.15f, 0.11f), writes)
    }

    @Test
    fun `ble neck ignores a pose that does not hold so the nod loop cannot feed itself`() {
        val writes = mutableListOf<Float>()
        var now = 1_000L
        val out = LooiBleBodyOutput(null, { true }, { writes += it }, { now })

        out.neck(0.5f)            // first pose -> writes
        // A target that flits by for one camera frame is never a pose.
        now += 16L
        out.neck(-0.5f)
        now += 16L
        out.neck(0.5f)
        assertEquals(listOf(0.5f), writes)

        // Hold it and it goes through.
        now += 400L
        out.neck(-0.5f)           // new target -> must hold first
        now += 200L
        out.neck(-0.5f)
        assertEquals(listOf(0.5f, -0.5f), writes)
    }

    @Test
    fun `ble neck clamps pitch before writing`() {
        val writes = mutableListOf<Float>()
        var now = 1_000L
        val out = LooiBleBodyOutput(null, { true }, { writes += it }, { now })
        out.neck(5f)
        now += 400L
        out.neck(-5f)
        now += 200L
        out.neck(-5f)
        assertEquals(listOf(1f, -1f), writes)
    }

    @Test
    fun `ble neck disconnected writes nothing and reconnection unblocks the next write`() {
        var linkUp = false
        val writes = mutableListOf<Float>()
        var now = 1_000L
        val out = LooiBleBodyOutput(null, { linkUp }, { writes += it }, { now })

        out.neck(0.3f)            // link down: no write, and lastNeckPitch stays stale
        linkUp = true
        out.neck(0.3f)            // first live write must not be swallowed by the stale value
        now += 400L
        out.neck(0.31f)           // then coalescing applies again

        assertEquals(listOf(0.3f), writes)
    }
}