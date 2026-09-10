package com.example.meowmba.ui.companion

import org.junit.Assert.*
import org.junit.Test

class AvatarFrameCadenceTest {
    @Test fun boundsHighRefreshDisplaysAndRecoversAfterPause() {
        val cadence = AvatarFrameCadence()
        assertEquals(30, (0L until 1_000_000_000L step 8_333_334L).count(cadence::admit))
        assertTrue(cadence.admit(5_000_000_000L))
        assertFalse(cadence.admit(5_008_333_334L))
    }

    @Test fun `the first frame always admits so nothing waits a full interval to appear`() {
        assertTrue(AvatarFrameCadence().admit(1_000_000_000L))
        assertTrue(AvatarFrameCadence().admit(1_000_000_000L, false))
    }

    @Test fun `active cadence still holds roughly 31 fps`() {
        val c = AvatarFrameCadence()
        assertTrue(c.admit(0L))
        assertFalse(c.admit(16_000_000L))   // a 60 Hz frame is too soon
        assertFalse(c.admit(31_000_000L))
        assertTrue(c.admit(33_000_000L))
    }

    @Test fun `idle cadence drops to roughly 15 fps`() {
        val c = AvatarFrameCadence()
        assertTrue(c.admit(0L, false))
        assertFalse(c.admit(33_000_000L, false))   // the old rate no longer passes
        assertFalse(c.admit(65_000_000L, false))
        assertTrue(c.admit(67_000_000L, false))
    }

    @Test fun `a 60 Hz display is throttled to about 15 fps while idle`() {
        val c = AvatarFrameCadence()
        var admitted = 0
        for (i in 0 until 60) {              // one second of 60 Hz frames
            if (c.admit(i * 16_666_667L, false)) admitted++
        }
        assertEquals(15, admitted)
    }

    @Test fun `starting a clip admits again instead of waiting out the idle interval`() {
        val c = AvatarFrameCadence()
        assertTrue(c.admit(0L, false))
        // 40 ms later: still inside the idle window, but a performance began.
        assertTrue(c.admit(40_000_000L))
    }

    @Test fun `going idle never admits sooner than the active rate did`() {
        val c = AvatarFrameCadence()
        assertTrue(c.admit(0L))
        assertFalse(c.admit(33_000_000L, false))
    }
}
