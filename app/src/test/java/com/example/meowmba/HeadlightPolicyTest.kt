package com.example.meowmba

import com.example.meowmba.core.robot.HeadlightPolicy
import com.example.meowmba.core.robot.HeadlightPolicy.Mode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadlightPolicyTest {

    @Test
    fun `auto stays off in a normal room`() {
        assertFalse(HeadlightPolicy.shouldBeOn(90, Mode.AUTO, currentlyOn = false))
    }

    @Test
    fun `auto turns on only when the camera is dark`() {
        assertTrue(HeadlightPolicy.shouldBeOn(20, Mode.AUTO, currentlyOn = false))
        assertFalse(HeadlightPolicy.shouldBeOn(40, Mode.AUTO, currentlyOn = false))
        assertTrue(HeadlightPolicy.shouldBeOn(40, Mode.AUTO, currentlyOn = true))
        assertFalse(HeadlightPolicy.shouldBeOn(70, Mode.AUTO, currentlyOn = true))
    }

    @Test
    fun `user off stays off even in the dark`() {
        assertFalse(HeadlightPolicy.shouldBeOn(10, Mode.FORCE_OFF, currentlyOn = false))
    }

    @Test
    fun `user on stays on even in a bright room`() {
        assertTrue(HeadlightPolicy.shouldBeOn(200, Mode.FORCE_ON, currentlyOn = true))
    }
}
