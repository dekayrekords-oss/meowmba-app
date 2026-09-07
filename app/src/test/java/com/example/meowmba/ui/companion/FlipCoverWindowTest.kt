package com.example.meowmba.ui.companion

import org.junit.Assert.*
import org.junit.Test

class FlipCoverWindowTest {
    @Test fun coverToggleSupportsCompactBuiltInScreensOnly() {
        assertTrue(useCoverAvatarLayout(true, 1, true, 360, 400))
        assertFalse(useCoverAvatarLayout(false, 1, true, 360, 400))
        assertFalse(useCoverAvatarLayout(true, 0, true, 360, 400))
        assertFalse(useCoverAvatarLayout(true, 1, false, 360, 400))
        assertFalse(useCoverAvatarLayout(true, 1, true, 360, 800))
        assertFalse(useCoverAvatarLayout(true, 1, true, 0, 400))
    }
    @Test fun coverDetectionDoesNotCaptureFoldOrMainScreen() {
        assertTrue(isFlipCoverWindow("samsung", "SM-F741U", 1, 360, 360))
        assertFalse(isFlipCoverWindow("samsung", "SM-F966U1", 1, 360, 700))
        assertFalse(isFlipCoverWindow("samsung", "SM-F741U", 0, 360, 360))
        assertFalse(isFlipCoverWindow("samsung", "SM-F741U", 2, 360, 360))
        assertFalse(isFlipCoverWindow("other", "SM-F741U", 1, 360, 360))
    }
}
