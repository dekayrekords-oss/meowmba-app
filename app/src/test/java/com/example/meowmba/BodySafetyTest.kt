package com.example.meowmba

import com.example.meowmba.core.robot.*
import org.junit.Assert.*
import org.junit.Test

class BodySafetyTest {
    private class Recorder(override val wheelControlVerified: Boolean = true) : BodyOutput {
        val calls = mutableListOf<BodyVerb>()
        override fun neck(pitch: Float) { calls += BodyVerb.Neck(pitch) }
        override fun turn(speedPercent: Int, ticks: Int, sign: Int) { calls += BodyVerb.Turn(speedPercent, ticks, sign) }
        override fun drive(speed: Float) { calls += BodyVerb.Drive(speed) }
        override fun light(on: Boolean) { calls += BodyVerb.Light(on) }
        override fun brake() { calls += BodyVerb.Brake }
    }

    @Test fun `unverified wheel protocol blocks every motion owner even with no sensor packets`() {
        val gate = BodySafety()
        val out = Recorder(wheelControlVerified = false)
        gate.attach(out)
        assertFalse(gate.rotationAllowed)
        gate.turn(20, 1, 1)
        assertFalse(gate.follow(BodyVerb.Turn(20, 1, -1)))
        assertFalse(gate.beginPlayback()!!.submit(BodyVerb.Turn(20, 1, 1)))
        val explicit = gate.beginExplicitMotion()!!
        assertFalse(explicit.submit(BodyVerb.Turn(100, 150, -1)))
        gate.resume()
        assertFalse(gate.beginExplicitMotion()!!.submit(BodyVerb.Turn(20, 1, 1)))
        assertTrue(out.calls.none { it is BodyVerb.Turn || it is BodyVerb.Drive })
    }

    @Test fun `stable output resolves the current body but old clip cannot follow reconnect`() {
        val gate = BodySafety()
        val stable: BodyOutput = gate
        assertNull(gate.beginPlayback())
        val first = Recorder()
        gate.attach(first)
        stable.neck(0.2f)
        val old = gate.beginPlayback()!!
        old.drive(0.3f)
        val second = Recorder()
        gate.attach(second)
        val fresh = gate.beginPlayback()!!
        fresh.neck(-0.2f)
        old.neck(0.3f)
        old.finish()
        assertEquals(listOf(BodyVerb.Neck(-0.2f)), second.calls)
        assertFalse(old.isValid)
        assertTrue(fresh.isValid)
        assertTrue(first.calls.contains(BodyVerb.Brake))
    }

    @Test fun `cliff and unknown stop every expressive writer until deliberate resume`() {
        listOf(SensorEvent.Cliff(byteArrayOf(1)), SensorEvent.Unknown(byteArrayOf(2))).forEach { event ->
            val gate = BodySafety()
            val out = Recorder()
            gate.attach(out)
            val clip = gate.beginPlayback()!!
            clip.turn(25, 3, 1)
            gate.onSensor(event)
            val stopped = out.calls.toList()
            clip.turn(25, 3, 1)
            gate.neck(0.2f)
            assertFalse(gate.follow(BodyVerb.Neck(0.2f)))
            assertNull(gate.beginPlayback())
            assertNull(gate.beginExplicitMotion())
            gate.onSensor(SensorEvent.Tof(1000))
            assertEquals(stopped, out.calls)
            assertFalse(clip.isValid)
            assertEquals(BodyVerb.Brake, out.calls.last())
            gate.resume()
            assertNotNull(gate.beginPlayback())
        }
    }

    @Test fun `stop remains latched through reconnect and pause changes`() {
        val gate = BodySafety()
        gate.attach(Recorder())
        gate.stop()
        gate.setPaused(true)
        gate.setPaused(false)
        gate.attach(Recorder())
        assertNull(gate.beginPlayback())
        assertNull(gate.beginExplicitMotion())
    }

    @Test fun `touch brakes immediately and expired pause cannot revive an old clip`() {
        var now = 1000L
        val gate = BodySafety { now }
        val out = Recorder()
        gate.attach(out)
        val clip = gate.beginPlayback()!!
        clip.turn(25, 3, 1)
        gate.touch(500)
        assertEquals(BodyVerb.Brake, out.calls.last())
        assertNull(gate.beginPlayback())
        assertFalse(gate.follow(BodyVerb.Neck(0.2f)))
        now += 501L
        clip.neck(0.2f)
        assertFalse(clip.isValid)
        assertEquals(BodyVerb.Brake, out.calls.last())
        assertNotNull(gate.beginPlayback())
    }

    @Test fun `lifecycle pause blocks mood following clips and explicit motion`() {
        val gate = BodySafety()
        val out = Recorder()
        gate.attach(out)
        val clip = gate.beginPlayback()!!
        clip.neck(0.2f)
        gate.setPaused(true)
        val calls = out.calls.toList()
        gate.neck(-0.2f)
        clip.neck(-0.2f)
        assertFalse(gate.follow(BodyVerb.Neck(0.2f)))
        assertNull(gate.beginPlayback())
        assertNull(gate.beginExplicitMotion())
        assertEquals(calls, out.calls)
        gate.setPaused(false)
        assertFalse(clip.isValid)
        assertNotNull(gate.beginPlayback())
    }

    @Test fun `explicit motion preempts clip and stale clip cleanup cannot brake it`() {
        val gate = BodySafety()
        val out = Recorder()
        gate.attach(out)
        val clip = gate.beginPlayback()!!
        clip.turn(25, 3, -1)
        val explicit = gate.beginExplicitMotion()!!
        explicit.turn(40, 3, 1)
        val before = out.calls.toList()
        assertNull(gate.beginPlayback())
        assertFalse(gate.follow(BodyVerb.Neck(0.2f)))
        gate.neck(0.3f)
        clip.finish()
        assertEquals(before, out.calls)
        explicit.finish()
        assertEquals(BodyVerb.Brake, out.calls.last())
    }

    @Test fun `clip brakes a follow pulse and blocks following and mood until finished`() {
        val gate = BodySafety { 1000L }
        val out = Recorder()
        gate.attach(out)
        assertTrue(gate.follow(BodyVerb.Turn(25, 3, 1)))
        gate.neck(0.3f)
        val clip = gate.beginPlayback()!!
        assertEquals(listOf(BodyVerb.Turn(25, 3, 1), BodyVerb.Brake), out.calls)
        assertFalse(gate.follow(BodyVerb.Brake))
        clip.finish()
        assertTrue(gate.follow(BodyVerb.Neck(0.1f)))
    }

    @Test fun `desk and requested floor both reject translation without a verified decoder`() {
        val gate = BodySafety()
        val out = Recorder()
        gate.attach(out)
        val desk = gate.beginPlayback()!!
        assertFalse(desk.submit(BodyVerb.Drive(0.5f)))
        desk.finish()
        assertTrue(out.calls.isEmpty())
        gate.setProfile(BodyProfile.FLOOR)
        val floor = gate.beginPlayback()!!
        assertFalse(floor.submit(BodyVerb.Drive(0.5f)))
        assertFalse(gate.translationAllowed)
        gate.setProfile(BodyProfile.DESK)
        floor.finish()
        assertTrue(out.calls.isEmpty())
    }

    @Test fun `all neck writers respect existing travel cap and reject nonfinite input`() {
        val gate = BodySafety()
        val out = Recorder()
        gate.attach(out)
        gate.neck(1f)
        gate.follow(BodyVerb.Neck(-1f))
        val clip = gate.beginPlayback()!!
        clip.neck(Float.NaN)
        clip.neck(5f)
        assertEquals(listOf(0.35f, -0.35f, 0.35f), out.calls.filterIsInstance<BodyVerb.Neck>().map { it.pitch })
    }

    @Test fun `silent motion windows cannot authorize floor translation`() {
        val confinement = ConfinementModel()
        val gate = BodySafety()
        val out = Recorder()
        gate.attach(out)
        repeat(4) { index ->
            confinement.onMotionWindowStart(index * 3000L)
            confinement.onMotionWindowEnd(index * 3000L + 2000L)
            gate.setProfile(confinement.profile)
        }
        assertEquals(BodyProfile.FLOOR, confinement.profile)
        assertFalse(gate.translationAllowed)
        assertFalse(gate.beginExplicitMotion()!!.submit(BodyVerb.Drive(0.5f)))
        assertTrue(out.calls.isEmpty())
    }

    @Test fun `repeated hazard frames send only the initial brake`() {
        val gate = BodySafety()
        val out = Recorder()
        gate.attach(out)
        gate.beginPlayback()!!.turn(25, 3, 1)
        repeat(20) { gate.onSensor(SensorEvent.Unknown(byteArrayOf(it.toByte()))) }
        gate.stop()
        assertEquals(1, out.calls.count { it is BodyVerb.Brake })
        assertNull(gate.beginPlayback())
        gate.resume()
        gate.beginPlayback()!!.turn(25, 3, 1)
        val before = out.calls.count { it is BodyVerb.Brake }
        gate.onSensor(SensorEvent.Unknown(byteArrayOf(1)))
        assertEquals(before + 1, out.calls.count { it is BodyVerb.Brake })
    }

    @Test fun `losing the face stops following once and never brakes a clip or explicit motion`() {
        val gate = BodySafety { 1000L }
        val out = Recorder()
        gate.attach(out)
        gate.follow(BodyVerb.Turn(25, 3, 1))
        gate.stopFollowing()
        gate.stopFollowing()
        assertEquals(listOf(BodyVerb.Turn(25, 3, 1), BodyVerb.Brake), out.calls)
        val clip = gate.beginPlayback()!!
        clip.turn(30, 3, -1)
        gate.stopFollowing()
        assertEquals(BodyVerb.Turn(30, 3, -1), out.calls.last())
        assertTrue(clip.isValid)
        val explicit = gate.beginExplicitMotion()!!
        explicit.turn(40, 3, 1)
        gate.stopFollowing()
        assertEquals(BodyVerb.Turn(40, 3, 1), out.calls.last())
        assertTrue(explicit.isValid)
    }
}
