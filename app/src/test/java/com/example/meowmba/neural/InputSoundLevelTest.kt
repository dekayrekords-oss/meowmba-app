package com.example.meowmba.neural

import com.example.meowmba.core.audio.InputSoundLevel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class InputSoundLevelTest {
    @Before fun reset() { InputSoundLevel.clear() }
    @Test fun pcmMeasuresEnergyAndExpires() {
        InputSoundLevel.publishPcm(ShortArray(1600) { 32767 }, 1600, 1000)
        val loud = InputSoundLevel.snapshot(1000, false)
        assertEquals(1f, loud.activity, .001f)
        assertTrue(loud.loudness > .99f)
        assertTrue(InputSoundLevel.snapshot(1799, false).fresh)
        assertFalse(InputSoundLevel.snapshot(1800, false).fresh)
        assertEquals(0f, InputSoundLevel.snapshot(1800, false).activity, 0f)
    }
    @Test fun silenceIsFreshButNotActivity() {
        InputSoundLevel.publishPcm(ShortArray(20), 20, 0)
        val silent = InputSoundLevel.snapshot(0, false)
        assertTrue(silent.fresh)
        assertEquals(0f, silent.activity, 0f)
        assertEquals(0f, silent.loudness, 0f)
    }
    @Test fun ownSpeechIsErasedAndUpdatesRateLimited() {
        InputSoundLevel.publishSystemRms(10f, 100)
        InputSoundLevel.publishSystemRms(-2f, 150)
        assertEquals(1f, InputSoundLevel.snapshot(150, false).loudness, 0f)
        assertFalse(InputSoundLevel.snapshot(150, true).fresh)
        assertFalse(InputSoundLevel.snapshot(151, false).fresh)
        InputSoundLevel.publishSystemRms(-2f, 200)
        assertEquals(0f, InputSoundLevel.snapshot(200, false).loudness, 0f)
    }
    @Test fun nonfiniteInvalidAndBackwardSamplesCannotBecomeEvidence() {
        InputSoundLevel.publishSystemRms(Float.NaN, 0)
        InputSoundLevel.publishSystemRms(Float.POSITIVE_INFINITY, 0)
        InputSoundLevel.publishPcm(shortArrayOf(1), 2, 0)
        assertFalse(InputSoundLevel.snapshot(0, false).fresh)
        InputSoundLevel.publishSystemRms(10f, 1000)
        InputSoundLevel.publishSystemRms(-2f, 500)
        assertFalse(InputSoundLevel.snapshot(500, false).fresh)
        assertEquals(1f, InputSoundLevel.snapshot(1000, false).loudness, 0f)
        InputSoundLevel.clear("sherpa_pcm")
        assertTrue(InputSoundLevel.snapshot(1000, false).fresh)
        InputSoundLevel.clear("system_rms")
        assertFalse(InputSoundLevel.snapshot(1000, false).fresh)
    }
}
