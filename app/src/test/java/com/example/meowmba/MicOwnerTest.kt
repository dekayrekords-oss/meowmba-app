package com.example.meowmba

import com.example.meowmba.core.audio.MicOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Only one wake loop may hold the mic. Two did, and the platform answered with a
 * steady stream of ERROR_RECOGNIZER_BUSY (30 in 45s on device) — which is what made the
 * wake phrase unreliable.
 */
class MicOwnerTest {

    @Before
    fun reset() = MicOwner.resetForTest()

    @Test
    fun `first claimant wins`() {
        assertTrue(MicOwner.acquire(MicOwner.IN_APP))
        assertEquals(MicOwner.IN_APP, MicOwner.current)
    }

    @Test
    fun `second claimant is denied`() {
        assertTrue(MicOwner.acquire(MicOwner.IN_APP))
        assertFalse(MicOwner.acquire(MicOwner.SERVICE))
        assertEquals(MicOwner.IN_APP, MicOwner.current)
    }

    @Test
    fun `re-acquiring by the same owner is allowed`() {
        // startWakeStandby() runs on every return to idle; it must not lock itself out.
        assertTrue(MicOwner.acquire(MicOwner.IN_APP))
        assertTrue(MicOwner.acquire(MicOwner.IN_APP))
    }

    @Test
    fun `release hands over cleanly`() {
        MicOwner.acquire(MicOwner.IN_APP)
        MicOwner.release(MicOwner.IN_APP)
        assertNull(MicOwner.current)
        assertTrue(MicOwner.acquire(MicOwner.SERVICE))
    }

    @Test
    fun `a non-owner cannot release`() {
        // A late release from the other side must not steal the mic out from under the
        // current owner.
        MicOwner.acquire(MicOwner.IN_APP)
        MicOwner.release(MicOwner.SERVICE)
        assertEquals(MicOwner.IN_APP, MicOwner.current)
    }
}
