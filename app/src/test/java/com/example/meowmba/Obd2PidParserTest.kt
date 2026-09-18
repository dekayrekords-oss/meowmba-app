package com.example.meowmba

import com.example.meowmba.core.car.obd2.Obd2AlertKind
import com.example.meowmba.core.car.obd2.Obd2AlertThresholds
import com.example.meowmba.core.car.obd2.Obd2Alerts
import com.example.meowmba.core.car.obd2.Obd2LumenCues
import com.example.meowmba.core.car.obd2.Obd2PidParser
import com.example.meowmba.core.car.obd2.Obd2Reading
import com.example.meowmba.ui.companion.DriveEmotionBoost
import com.example.meowmba.ui.companion.DriveReaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Obd2PidParserTest {
    @Test
    fun parsesCommonPids() {
        assertEquals(1726f, Obd2PidParser.parseRpm("41 0C 1A F8")!!, 0.1f)
        assertEquals(55f, Obd2PidParser.parseSpeedKph("41 0D 37")!!, 0.01f)
        assertEquals(90f, Obd2PidParser.parseCoolantC("41 05 82")!!, 0.01f)
        assertEquals(12.5f, Obd2PidParser.parseBatteryV("41 42 30 D4")!!, 0.05f)
        assertTrue(Obd2PidParser.parseMil("41 01 83 00 00 00")!!)
        assertFalse(Obd2PidParser.parseMil("41 01 03 00 00 00")!!)
    }

    @Test
    fun alertsFireOnThresholds() {
        val r = Obd2Reading(connected = true, coolantC = 110f, batteryV = 11.2f, rpm = 6000f, milOn = true)
        val alerts = Obd2Alerts.evaluate(r, Obd2AlertThresholds())
        assertTrue(alerts.any { it.kind == Obd2AlertKind.COOLANT_HIGH })
        assertTrue(alerts.any { it.kind == Obd2AlertKind.BATTERY_LOW })
        assertTrue(alerts.any { it.kind == Obd2AlertKind.RPM_HIGH })
        assertTrue(alerts.any { it.kind == Obd2AlertKind.MIL })
    }

    @Test
    fun lumenCuesScaleWithRpm() {
        assertEquals(0f, Obd2LumenCues.excitementFromRpm(800f), 0.01f)
        assertTrue(Obd2LumenCues.excitementFromRpm(4000f) > 0.4f)
    }

    @Test
    fun driveEmotionBoostLowersThresholds() {
        val mild = DriveEmotionBoost.driveReaction(50f, 0.30f, 0f, boosted = true)
        assertEquals(DriveReaction.LEAN_RIGHT, mild)
        val normal = DriveEmotionBoost.driveReaction(50f, 0.30f, 0f, boosted = false)
        assertEquals(DriveReaction.NONE, normal)
    }
}
