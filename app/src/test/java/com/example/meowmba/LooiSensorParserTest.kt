package com.example.meowmba

import com.example.meowmba.core.robot.LooiSensorParser
import com.example.meowmba.core.robot.SensorEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fed9 layout has never been captured from this robot, so the parser
 * classifies everything as Unknown on purpose. These tests pin that honesty
 * in place: an Unknown result is a *designed* outcome, and the capture log is
 * the thing that will eventually let the layout be decoded.
 */
class LooiSensorParserTest {

    @Test
    fun `an undecoded frame is Unknown, not an error`() {
        val event = LooiSensorParser.parse(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        assertTrue("expected Unknown, got $event", event is SensorEvent.Unknown)
    }

    @Test
    fun `an empty frame is Unknown and does not crash`() {
        assertTrue(LooiSensorParser.parse(ByteArray(0)) is SensorEvent.Unknown)
    }

    @Test
    fun `Unknown carries the raw bytes for later decoding`() {
        val raw = byteArrayOf(0x7F, 0x00, 0x2A.toByte())
        val event = LooiSensorParser.parse(raw) as SensorEvent.Unknown
        assertEquals(listOf<Byte>(0x7F, 0x00, 0x2A), event.raw.toList())
    }

    @Test
    fun `capture log records frames with the motion in flight`() {
        val log = LooiSensorParser.CaptureLog(capacity = 4)
        log.record(byteArrayOf(0x0A, 0x0B), motionInFlight = "Turn", atMs = 1000L)
        assertEquals(1, log.entries.size)
        val line = log.entries.single()
        assertTrue("expected hex bytes in \"$line\"", line.contains("0a0b"))
        assertTrue("expected motion label in \"$line\"", line.contains("Turn"))
        assertTrue("expected timestamp in \"$line\"", line.contains("1000"))
    }

    @Test
    fun `capture log records frames with no motion in flight`() {
        val log = LooiSensorParser.CaptureLog(capacity = 4)
        log.record(byteArrayOf(0x01), motionInFlight = null, atMs = 5L)
        assertTrue(log.entries.single().contains("idle"))
    }

    @Test
    fun `capture log is bounded and keeps the newest frames`() {
        val log = LooiSensorParser.CaptureLog(capacity = 3)
        repeat(10) { i -> log.record(byteArrayOf(i.toByte()), null, i.toLong()) }
        assertEquals(3, log.entries.size)
        assertTrue("oldest should have been evicted", log.entries.none { it.contains("t=0 ") })
        assertTrue("newest must be present", log.entries.any { it.contains("t=9 ") })
    }

    @Test
    fun `dump joins entries one per line`() {
        val log = LooiSensorParser.CaptureLog(capacity = 4)
        log.record(byteArrayOf(0x01), null, 1L)
        log.record(byteArrayOf(0x02), null, 2L)
        assertEquals(2, log.dump().lines().size)
    }
}