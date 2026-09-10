package com.example.meowmba.ui.companion

import org.junit.Assert.*
import org.junit.Test

class AvatarWorkPolicyTest {
    @Test fun hiddenScreenAndSevereHeatStopAnimation() {
        assertTrue(allowAvatarWork(true, true, 0))
        assertTrue(allowAvatarWork(true, true, 1))
        assertFalse(allowAvatarWork(false, true, 0))
        assertFalse(allowAvatarWork(true, false, 0))
        for (status in 2..6) assertFalse(allowAvatarWork(true, true, status))
    }
}
