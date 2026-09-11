package com.example.meowmba.ui.companion

import org.junit.Assert.*
import org.junit.Test

class AvatarFrameCadenceTest {
    @Test fun boundsHighRefreshDisplaysAndRecoversAfterPause() {
        val cadence = AvatarFrameCadence()
        // 120 Hz samples over 1s → ~60 admits at 16 ms active interval.
        assertEquals(60, (0L until 1_000_000_000L step 8_333_334L).count(cadence::admit))
        assertTrue(cadence.admit(5_000_000_000L))
        assertFalse(cadence.admit(5_008_333_334L))
    }

    @Test fun `the first frame always admits so nothing waits a full interval to appear`() {
        assertTrue(AvatarFrameCadence().admit(1_000_000_000L))
        assertTrue(AvatarFrameCadence().admit(1_000_000_000L, false))
    }

    @Test fun `active cadence holds roughly 60 fps`() {
        val c = AvatarFrameCadence()
        assertTrue(c.admit(0L))
        assertFalse(c.admit(8_000_000L))   // a 120 Hz frame is too soon
        assertFalse(c.admit(15_000_000L))
        assertTrue(c.admit(17_000_000L))
    }

    @Test fun `idle cadence sits roughly at 30 fps`() {
        val c = AvatarFrameCadence()
        assertTrue(c.admit(0L, false))
        assertFalse(c.admit(16_000_000L, false))
        assertFalse(c.admit(32_000_000L, false))
        assertTrue(c.admit(34_000_000L, false))
    }

    @Test fun `a 60 Hz display is throttled to about 30 fps while idle`() {
        val c = AvatarFrameCadence()
        var admitted = 0
        for (i in 0 until 60) {              // one second of 60 Hz frames
            if (c.admit(i * 16_666_667L, false)) admitted++
        }
        assertEquals(30, admitted)
    }

    @Test fun `a 120 Hz display admits about 60 fps while active`() {
        val c = AvatarFrameCadence()
        var admitted = 0
        for (i in 0 until 120) {
            if (c.admit(i * 8_333_334L)) admitted++
        }
        assertEquals(60, admitted)
    }

    @Test fun `starting a clip admits again instead of waiting out the idle interval`() {
        val c = AvatarFrameCadence()
        assertTrue(c.admit(0L, false))
        // 20 ms later: still inside the idle window, but a performance began.
        assertTrue(c.admit(20_000_000L))
    }

    @Test fun `going idle never admits sooner than the active rate did`() {
        val c = AvatarFrameCadence()
        assertTrue(c.admit(0L))
        assertFalse(c.admit(17_000_000L, false))
    }
}
