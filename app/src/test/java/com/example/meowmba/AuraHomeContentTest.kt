package com.example.meowmba

import androidx.compose.ui.geometry.Offset
import com.example.meowmba.ui.companion.AuraHomeContent
import com.example.meowmba.ui.companion.AvatarZone
import com.example.meowmba.ui.companion.InteractionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Aura Home replacement homescreen (pure logic in
 * [AuraHomeContent] — no Compose, no Android framework).
 */
class AuraHomeContentTest {

    @Test
    fun `greeting follows time of day`() {
        assertEquals("Good morning", AuraHomeContent.greetingFor(8))
        assertEquals("Good afternoon", AuraHomeContent.greetingFor(13))
        assertEquals("Good evening", AuraHomeContent.greetingFor(20))
        assertEquals("Up late", AuraHomeContent.greetingFor(2))
    }

    @Test
    fun `greeting clamps out-of-range hours instead of crashing`() {
        assertEquals("Up late", AuraHomeContent.greetingFor(-5))
        assertEquals("Up late", AuraHomeContent.greetingFor(99))
        assertEquals("Good evening", AuraHomeContent.greetingFor(21))
    }

    @Test
    fun `status titles mirror the real pipeline state`() {
        assertEquals("Ready", AuraHomeContent.statusTitle(InteractionState.IDLE, false))
        assertEquals("Listening", AuraHomeContent.statusTitle(InteractionState.LISTENING, false))
        assertEquals("Thinking", AuraHomeContent.statusTitle(InteractionState.THINKING, false))
        assertEquals("Speaking", AuraHomeContent.statusTitle(InteractionState.SPEAKING, false))
        assertEquals("Offline", AuraHomeContent.statusTitle(InteractionState.OFFLINE, false))
    }

    @Test
    fun `mute wins over pipeline state`() {
        assertEquals("Muted", AuraHomeContent.statusTitle(InteractionState.SPEAKING, true))
        assertEquals("Muted", AuraHomeContent.statusTitle(InteractionState.IDLE, true))
    }

    @Test
    fun `status colors are distinct per state`() {
        val colors = InteractionState.entries.map {
            AuraHomeContent.statusDotArgb(it, false)
        }.toSet()
        // MUTED shares gray with isMuted=true path; every pipeline state differs.
        assertTrue("each state has its own color", colors.size == InteractionState.entries.size)
    }

    @Test
    fun `subtitle names model and privacy truthfully`() {
        val plain = AuraHomeContent.subtitleFor("Qwen 1.5B", true, false, false, null)
        assertEquals("Qwen 1.5B", plain)

        val private = AuraHomeContent.subtitleFor("Qwen 1.5B", false, false, true, null)
        assertTrue(private.contains("Mic off"))
        assertTrue(private.contains("Speech muted"))

        val task = AuraHomeContent.subtitleFor("Qwen 1.5B", true, false, false, "Shelve: saving note")
        assertTrue(task.contains("Shelve"))
    }

    @Test
    fun `orb core tap mutes, ring tap opens menu`() {
        val center = Offset(500f, 500f)
        val ring = 200f
        assertEquals(
            AvatarZone.MOUTH,
            AuraHomeContent.auraZoneAt(Offset(500f, 500f), center, ring),
        )
        assertEquals(
            AvatarZone.BACKGROUND,
            AuraHomeContent.auraZoneAt(Offset(500f, 650f), center, ring),
        )
        assertEquals(
            AvatarZone.BACKGROUND,
            AuraHomeContent.auraZoneAt(Offset(50f, 50f), center, ring),
        )
    }

    @Test
    fun `degenerate radius never crashes`() {
        val zone = AuraHomeContent.auraZoneAt(Offset(0f, 0f), Offset(0f, 0f), 0f)
        assertEquals(AvatarZone.BACKGROUND, zone)
    }

    @Test
    fun `long-press opens settings`() {
        assertEquals(AvatarZone.FOREHEAD, AuraHomeContent.auraLongPressZone())
    }
}
