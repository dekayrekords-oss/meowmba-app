package com.example.meowmba.core.ring

import org.junit.Assert.*
import org.junit.Test

class RingSensorsTest {
    @Test fun validatesFramesTypesAndErrorFlags() {
        assertEquals("74 bpm", ringSensorReply(RingSensor.HEART_RATE, ringControlFrame(0x69, 1, 0, 74))?.value)
        assertEquals("36.5 °C", ringSensorReply(RingSensor.TEMPERATURE, ringControlFrame(0x6a, 11, 165, 0), true)?.value)
        assertEquals("258 steps", ringSensorReply(RingSensor.STEPS, ringControlFrame(0x48, 0, 1, 2))?.value)
        assertEquals("0%", ringSensorReply(RingSensor.BATTERY, ringControlFrame(3, 0))?.value)
        assertNull(ringSensorReply(RingSensor.BATTERY, ringControlFrame(3, 101)))
        assertNull(ringSensorReply(RingSensor.OXYGEN, ringControlFrame(0x69, 3, 0, 101))?.value)
        assertNull(ringSensorReply(RingSensor.HEART_RATE, ringControlFrame(0x69, 3, 0, 98)))
        assertNull(ringSensorReply(RingSensor.HEART_RATE, byteArrayOf(0x69)))
        val broken = ringControlFrame(0x69, 1, 0, 74).also { it[15] = 0 }
        assertNull(ringSensorReply(RingSensor.HEART_RATE, broken))
        assertEquals("Not worn / poor contact", ringSensorReply(RingSensor.HEART_RATE, ringControlFrame(0x69, 1, 1, 0))?.error)
        assertEquals("Ring rejected request", ringSensorReply(RingSensor.BATTERY, ringControlFrame(0x83))?.error)
    }
    private class Harness {
        val writes = mutableListOf<ByteArray>()
        var timer: (() -> Unit)? = null
        var active: RingSensor? = null
        var readings: Map<RingSensor, RingReading> = emptyMap()
        var accepts = true
        val session = RingSensorSession(send = { writes.add(it); accepts },
            schedule = { _, task -> timer = task; { if (timer === task) timer = null } },
            publish = { sensor, values -> active = sensor; readings = values }, now = { 1234 })
    }
    @Test fun serializesWritesAndCollectsFinalReadingOnTimeout() {
        val h = Harness(); h.session.start(RingSensor.HRV)
        h.session.start(RingSensor.BATTERY); assertEquals(1, h.writes.size)
        h.session.onWrite(true)
        h.session.receive(ringControlFrame(0x69, 10, 0, 0))
        assertEquals(RingSensor.HRV, h.active)
        h.timer!!.invoke()
        assertEquals(0x6a.toByte(), h.writes.last()[0])
        h.session.receive(ringControlFrame(0x6a, 10, 43, 0))
        assertEquals(RingSensor.HRV, h.active) // response must not overtake write acknowledgement
        h.session.onWrite(true)
        assertNull(h.active); assertEquals("43 ms", h.readings.getValue(RingSensor.HRV).value)
        assertEquals(1234L, h.readings.getValue(RingSensor.HRV).receivedAt)
    }
    @Test fun cancelsAfterOutstandingWriteAndIgnoresStaleReplies() {
        val h = Harness(); h.session.start(RingSensor.HEART_RATE); h.session.stop()
        assertEquals(1, h.writes.size)
        h.session.onWrite(true); assertEquals(2, h.writes.size)
        h.session.onWrite(true); h.session.receive(ringControlFrame(0x6a, 1, 74, 0))
        assertNull(h.active)
        assertEquals("Cancelled", h.readings.getValue(RingSensor.HEART_RATE).status)
        h.session.start(RingSensor.BATTERY); h.session.onWrite(true)
        h.session.receive(ringControlFrame(0x69, 1, 0, 75))
        assertEquals(RingSensor.BATTERY, h.active)
        h.session.disconnected(); assertNull(h.active); assertNull(h.timer)
        h.session.receive(ringControlFrame(3, 80))
        assertEquals("—", h.readings.getValue(RingSensor.BATTERY).value)
    }
    @Test fun reportsMissingResponsesAndFailedWritesWithoutInventingReadings() {
        val h = Harness(); h.session.start(RingSensor.BATTERY); h.session.onWrite(true); h.timer!!.invoke()
        assertNull(h.active); assertEquals("No response · unverified", h.readings.getValue(RingSensor.BATTERY).status)
        h.accepts = false; h.session.start(RingSensor.STEPS)
        assertNull(h.active); assertEquals("Could not send request", h.readings.getValue(RingSensor.STEPS).status)
        h.session.clear(); assertEquals("Not tested", h.readings.getValue(RingSensor.STEPS).status)
    }
}
