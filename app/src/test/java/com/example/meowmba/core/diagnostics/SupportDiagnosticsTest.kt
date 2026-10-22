package com.example.meowmba.core.diagnostics

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SupportDiagnosticsTest {
    private fun snapshot(exits: List<SupportDiagnostics.Exit> = emptyList()) = SupportDiagnostics.Snapshot(
        "1.3.94", 150, 36, null, null, null, false, false, false, false, exits, false)

    @Test fun `report exposes only allowlisted metadata and unknown health stays unknown`() {
        val report = Json.parseToJsonElement(SupportDiagnostics.render(snapshot())).jsonObject
        assertEquals(setOf("schema", "scope", "version", "versionCode", "androidApi", "modes",
            "exitHistoryAvailable", "recentProcessExits"), report.keys)
        assertFalse(report["exitHistoryAvailable"]!!.jsonPrimitive.boolean)
        val modes = report["modes"]!!.jsonObject
        assertEquals(setOf("microphone", "camera", "wakeWord", "backgroundListening"), modes.keys)
        assertTrue(modes.values.all { !it.jsonPrimitive.boolean })
    }

    @Test fun `exit history is bounded newest first and excludes freeform descriptions`() {
        val report = Json.parseToJsonElement(SupportDiagnostics.render(snapshot((1L..9L).map {
            SupportDiagnostics.Exit(it, 4, 0, 100)
        }))).jsonObject
        val exits = report["recentProcessExits"]!!.jsonArray
        assertEquals(listOf(9L, 8L, 7L, 6L, 5L), exits.map { it.jsonObject["timestamp"]!!.jsonPrimitive.long })
        exits.forEach { assertEquals(setOf("timestamp", "reasonCode", "status", "importance"), it.jsonObject.keys) }
    }
}
