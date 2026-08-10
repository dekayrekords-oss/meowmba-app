package com.example.meowmba

import com.example.meowmba.core.robot.LooiBleLink
import com.example.meowmba.core.robot.RobotManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Find base (scan)" picker.
 *
 * Reported: the scan returned a long list of MAC addresses and the LOOI base
 * appeared to be missing. It was not missing — a LOOI advertises its name in
 * the scan response rather than the advertisement, so the picker (which read
 * only the advertisement) fell back to showing its raw address, indistinguishable
 * from every other radio in range.
 */
class BaseDiscoveryTest {

    private val manager = RobotManager()
    private val looiMac = LooiBleLink.KNOWN_LOOI_MAC

    @Test
    fun `a named LOOI base sorts to the top`() {
        val rows = manager.rankBases(
            mapOf(
                "AA:BB:CC:00:00:01" to "Galaxy Buds",
                "AA:BB:CC:00:00:02" to "Living Room TV",
                "EC:64:C9:11:22:33" to "looi_a444da",
                "AA:BB:CC:00:00:03" to "Someone's Watch",
            )
        )
        assertEquals("looi_a444da", rows.first().first)
    }

    @Test
    fun `the known LOOI address wins even with no name at all`() {
        // The exact reported case: the base is seen, but nameless.
        val rows = manager.rankBases(
            mapOf(
                "AA:BB:CC:00:00:01" to "Galaxy Buds",
                looiMac to null,
                "AA:BB:CC:00:00:02" to null,
            )
        )
        assertEquals(looiMac, rows.first().second)
        assertTrue(
            "the row must say what it is, not just show a MAC",
            rows.first().first.contains("LOOI", ignoreCase = true),
        )
    }

    @Test
    fun `nameless radios are capped so they cannot bury the base`() {
        // 40 anonymous beacons is what "a long list of MAC addresses" was.
        val noise = (1..40).associate { "AA:BB:CC:00:%02d:FF".format(it) to null }
        val rows = manager.rankBases(noise + mapOf("EC:64:C9:11:22:33" to "looi_a444da"))
        assertEquals("looi_a444da", rows.first().first)
        assertTrue("unnamed rows must be capped, got ${rows.size}", rows.size <= 9)
    }

    @Test
    fun `every named device is kept, however many there are`() {
        val many = (1..30).associate { "AA:BB:CC:00:%02d:FF".format(it) to "Device $it" }
        assertEquals(30, manager.rankBases(many).size)
    }

    @Test
    fun `named devices always outrank anonymous ones`() {
        val rows = manager.rankBases(
            mapOf(
                "AA:BB:CC:00:00:01" to null,
                "AA:BB:CC:00:00:02" to "Kitchen Speaker",
                "AA:BB:CC:00:00:03" to null,
            )
        )
        assertEquals("Kitchen Speaker", rows.first().first)
        assertTrue(rows.drop(1).all { it.first.startsWith("unnamed") })
    }

    @Test
    fun `robot-ish names float above ordinary ones`() {
        val rows = manager.rankBases(
            mapOf(
                "AA:BB:CC:00:00:01" to "Airpods",
                "AA:BB:CC:00:00:02" to "meow base",
                "AA:BB:CC:00:00:03" to "Zebra Printer",
            )
        )
        assertEquals("meow base", rows.first().first)
    }

    @Test
    fun `blank names are treated as no name, not as a name`() {
        val rows = manager.rankBases(
            mapOf("AA:BB:CC:00:00:01" to "   ", "AA:BB:CC:00:00:02" to "Real Name")
        )
        assertEquals("Real Name", rows.first().first)
    }

    @Test
    fun `an empty scan returns an empty list rather than failing`() {
        assertTrue(manager.rankBases(emptyMap()).isEmpty())
    }

    @Test
    fun `every row carries the address needed to connect`() {
        val rows = manager.rankBases(
            mapOf("AA:BB:CC:00:00:01" to "Thing", "AA:BB:CC:00:00:02" to null)
        )
        assertTrue(rows.all { it.second.isNotBlank() })
        assertTrue(rows.all { it.first.isNotBlank() })
    }
}
