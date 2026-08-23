package com.example.meowmba

import com.example.meowmba.core.audio.VoiceHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceHealthTest {

    private var now = 1_000L
    private fun health() = VoiceHealth(now = { now }, wedgeMs = 60_000L)

    @Test
    fun `healthy platform wins when both exist`() {
        val h = health()
        assertEquals(
            VoiceHealth.Engine.PLATFORM,
            h.pick(platformAvailable = true, sherpaAvailable = true),
        )
    }

    @Test
    fun `four contentions flip to Sherpa`() {
        val h = health()
        assertFalse(h.noteContention())
        assertFalse(h.noteContention())
        assertFalse(h.noteContention())
        assertTrue(h.noteContention())
        assertEquals(
            VoiceHealth.Engine.SHERPA,
            h.pick(platformAvailable = true, sherpaAvailable = true),
        )
    }

    @Test
    fun `a silent session flips immediately`() {
        val h = health()
        h.noteSilentSession()
        assertEquals(
            VoiceHealth.Engine.SHERPA,
            h.pick(platformAvailable = true, sherpaAvailable = true),
        )
    }

    @Test
    fun `success restores the accurate recognizer`() {
        val h = health()
        h.noteSilentSession()
        h.noteSuccess()
        assertEquals(
            VoiceHealth.Engine.PLATFORM,
            h.pick(platformAvailable = true, sherpaAvailable = true),
        )
    }

    @Test
    fun `wedge expires and AiAi is tried again`() {
        val h = health()
        h.noteSilentSession()
        now += 60_001L
        assertEquals(
            VoiceHealth.Engine.PLATFORM,
            h.pick(platformAvailable = true, sherpaAvailable = true),
        )
    }

    @Test
    fun `no engines means null, never a fake pick`() {
        val h = health()
        assertNull(h.pick(platformAvailable = false, sherpaAvailable = false))
    }

    @Test
    fun `Sherpa-only still hears when platform is gone`() {
        val h = health()
        assertEquals(
            VoiceHealth.Engine.SHERPA,
            h.pick(platformAvailable = false, sherpaAvailable = true),
        )
    }

    @Test
    fun `wedged with no Sherpa keeps trying the platform`() {
        val h = health()
        h.noteSilentSession()
        assertEquals(
            VoiceHealth.Engine.PLATFORM,
            h.pick(platformAvailable = true, sherpaAvailable = false),
        )
    }
}
