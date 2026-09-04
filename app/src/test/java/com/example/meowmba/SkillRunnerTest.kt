package com.example.meowmba

import com.example.meowmba.core.plugins.ServiceIntegrations
import com.example.meowmba.core.plugins.SkillParsing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the SkillRunner library's pure pieces: duration/reminder parsing is
 * Android-free and stays correct, and every live service integration knows
 * where its key comes from and produces at least one tool.
 */
class SkillRunnerTest {

    @Test
    fun `parseDurationSeconds handles units`() {
        assertEquals(5 * 60, SkillParsing.parseDurationSeconds("5 minutes"))
        assertEquals(90, SkillParsing.parseDurationSeconds("90 seconds"))
        assertEquals(1 * 3600, SkillParsing.parseDurationSeconds("1 hour"))
        assertEquals(2 * 3600, SkillParsing.parseDurationSeconds("2 hours"))
        assertEquals(45, SkillParsing.parseDurationSeconds("45 secs"))
        assertEquals(-1, SkillParsing.parseDurationSeconds("sometime soon"))
    }

    @Test
    fun `parseReminder handles in-until and clock times`() {
        val (inSecs, note) = SkillParsing.parseReminder("remind me in 10 minutes to stretch")
        assertEquals(10 * 60, inSecs)
        assertEquals("to stretch", note)

        val (clockSecs, _) = SkillParsing.parseReminder("remind me at 3:30pm to call the vet")
        assertTrue("clock time should be a positive offset", clockSecs > 0)
    }

    @Test
    fun `formatDuration reads naturally`() {
        assertEquals("5 minutes", SkillParsing.formatDuration(300))
        assertEquals("45 seconds", SkillParsing.formatDuration(45))
        assertEquals("1 hour", SkillParsing.formatDuration(3600))
        assertEquals("1 hour 30 minutes", SkillParsing.formatDuration(5400))
    }

    @Test
    fun `live service integrations are wired`() {
        assertTrue("should have Spotify + GitHub", ServiceIntegrations.all.size >= 2)
        ServiceIntegrations.all.forEach { svc ->
            assertTrue("storageKey blank: ${svc.name}", svc.storageKey.isNotBlank())
            assertTrue("keyLink invalid: ${svc.name}", svc.keyLink.startsWith("https://"))
            val tools = svc.toolFactory { "test-token" }
            assertTrue("${svc.name} must produce a real tool", tools.isNotEmpty())
        }
    }
}
