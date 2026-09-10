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
}
