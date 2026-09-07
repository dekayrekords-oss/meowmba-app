package com.example.meowmba.ui.companion

import org.junit.Assert.*
import org.junit.Test

class FlipCoverWindowTest {
    @Test fun coverDetectionDoesNotCaptureFoldOrMainScreen() {
        assertTrue(isFlipCoverWindow("samsung", "SM-F741U", 1, 360, 360))
        assertFalse(isFlipCoverWindow("samsung", "SM-F966U1", 1, 360, 700))
        assertFalse(isFlipCoverWindow("samsung", "SM-F741U", 0, 360, 360))
        assertFalse(isFlipCoverWindow("other", "SM-F741U", 1, 360, 360))
    }
}
