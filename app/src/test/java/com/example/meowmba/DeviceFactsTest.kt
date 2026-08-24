package com.example.meowmba

import com.example.meowmba.core.agent.tools.BatteryReading
import com.example.meowmba.core.memory.PromptBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFactsTest {

    @Test
    fun `facts forbid inventing a battery percent or a LOOI when none is live`() {
        val snap = BatteryReading.Snapshot(percent = 73, charging = false, saver = false)
        val facts = PromptBuilder.deviceFacts(
            batteryLine = BatteryReading.factLine(snap),
            robotLive = false,
            robotStatus = "simulated (no body)",
        )
        assertTrue(facts.contains("73%"))
        assertTrue(facts.contains("not charging"))
        assertTrue(facts.contains("No robot body is connected"))
        assertTrue(facts.contains("Never say you are sitting on"))
        assertFalse(facts.contains("IS connected"))
    }

    @Test
    fun `unknown battery tells the model not to invent a number`() {
        val line = BatteryReading.factLine(
            BatteryReading.Snapshot(percent = null, charging = false, saver = false),
        )
        assertTrue(line.contains("do not invent"))
    }

    @Test
    fun `a live body is stated as connected`() {
        val facts = PromptBuilder.deviceFacts(
            batteryLine = "Phone battery is 40% (charging).",
            robotLive = true,
            robotStatus = "LOOI body (BLE) @ AA:BB",
        )
        assertTrue(facts.contains("IS connected"))
        assertTrue(facts.contains("LOOI body (BLE)"))
    }
}
