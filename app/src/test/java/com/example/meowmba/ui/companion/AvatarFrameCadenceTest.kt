package com.example.meowmba.ui.companion

import android.os.PowerManager
import org.junit.Assert.*
import org.junit.Test

class AvatarFrameCadenceTest {
    @Test fun boundsHighRefreshDisplaysAndRecoversAfterPause() {
        val cadence = AvatarFrameCadence()
        // 120 Hz samples over 1s → ~30 admits at default 30 FPS active.
        assertEquals(30, (0L until 1_000_000_000L step 8_333_334L).count { cadence.admit(it) })
        assertTrue(cadence.admit(5_000_000_000L))
        assertFalse(cadence.admit(5_008_333_334L))
    }

    @Test fun `the first frame always admits so nothing waits a full interval to appear`() {
        assertTrue(AvatarFrameCadence().admit(1_000_000_000L))
        assertTrue(AvatarFrameCadence().admit(1_000_000_000L, false))
    }

    @Test fun `active cadence holds roughly 30 fps by default`() {
        val c = AvatarFrameCadence()
        assertTrue(c.admit(0L))
        assertFalse(c.admit(16_000_000L))
        assertFalse(c.admit(32_000_000L))
        assertTrue(c.admit(34_000_000L))
    }

    @Test fun `idle cadence sits roughly at half the active target`() {
        val c = AvatarFrameCadence()
        assertTrue(c.admit(0L, false))
        assertFalse(c.admit(33_000_000L, false))
        assertFalse(c.admit(65_000_000L, false))
        assertTrue(c.admit(67_000_000L, false))
    }

    @Test fun `a 60 Hz display is throttled to about 15 fps while idle at default`() {
        val c = AvatarFrameCadence()
        var admitted = 0
        for (i in 0 until 60) {
            if (c.admit(i * 16_666_667L, false)) admitted++
        }
        assertEquals(15, admitted)
    }

    @Test fun `a 120 Hz display admits about 30 fps while active at default`() {
        val c = AvatarFrameCadence()
        var admitted = 0
        for (i in 0 until 120) {
            if (c.admit(i * 8_333_334L)) admitted++
        }
        assertEquals(30, admitted)
    }

    @Test fun `user 60 fps target admits about 60 on a 120 Hz display`() {
        val c = AvatarFrameCadence()
        var admitted = 0
        for (i in 0 until 120) {
            if (c.admit(i * 8_333_334L, fpsTarget = 60)) admitted++
        }
        assertEquals(60, admitted)
    }

    @Test fun `user 42 fps target spaces admits at one over forty-two seconds`() {
        val c = AvatarFrameCadence()
        val intervalNs = (1_000_000_000.0 / 42).toLong()
        assertTrue(c.admit(0L, fpsTarget = 42))
        assertFalse(c.admit(intervalNs - 1, fpsTarget = 42))
        assertTrue(c.admit(intervalNs, fpsTarget = 42))
        assertFalse(c.admit(intervalNs + intervalNs / 2, fpsTarget = 42))
        assertTrue(c.admit(intervalNs * 2, fpsTarget = 42))
    }

    @Test fun `LIGHT thermal no longer slows the avatar`() {
        val c = AvatarFrameCadence()
        var admitted = 0
        for (i in 0 until 120) {
            if (c.admit(i * 8_333_334L, fpsTarget = 60, thermalStatus = PowerManager.THERMAL_STATUS_LIGHT)) admitted++
        }
        assertEquals(60, admitted)
    }

    @Test fun `MODERATE thermal halves effective active fps`() {
        val c = AvatarFrameCadence()
        var admitted = 0
        for (i in 0 until 120) {
            if (c.admit(i * 8_333_334L, fpsTarget = 60, thermalStatus = PowerManager.THERMAL_STATUS_MODERATE)) admitted++
        }
        assertEquals(30, admitted)
    }

    @Test fun `SEVERE thermal keeps a sparse motion cadence`() {
        val c = AvatarFrameCadence()
        assertTrue(c.admit(0L, fpsTarget = 60, thermalStatus = PowerManager.THERMAL_STATUS_SEVERE))
        assertFalse(c.admit(60_000_000L, fpsTarget = 60, thermalStatus = PowerManager.THERMAL_STATUS_SEVERE))
        assertTrue(c.admit(67_000_000L, fpsTarget = 60, thermalStatus = PowerManager.THERMAL_STATUS_SEVERE))
    }

    @Test fun `CRITICAL thermal stops motion`() {
        val c = AvatarFrameCadence()
        assertFalse(c.admit(0L, fpsTarget = 60, thermalStatus = PowerManager.THERMAL_STATUS_CRITICAL))
        assertFalse(c.admit(1_000_000_000L, fpsTarget = 60, thermalStatus = PowerManager.THERMAL_STATUS_EMERGENCY))
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
        assertFalse(c.admit(34_000_000L, false))
    }
}
