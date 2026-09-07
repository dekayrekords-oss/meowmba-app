package com.example.meowmba.ui.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class SceneClockTest {
    @Test fun `shared clock preserves both original periods and keeps phase when frozen`() {
        val clock = SceneClock()
        clock.advance(11_000_000_000L)
        assertEquals(0.5f, clock.phase(22_000), 0.00001f)
        assertEquals(11f / 17f, clock.phase(17_000), 0.00001f)
        assertEquals(0.5f, clock.phase(22_000), 0.00001f)
        clock.advance(11_000_000_000L)
        assertEquals(0f, clock.phase(22_000), 0.00001f)
        assertEquals(5f / 17f, clock.phase(17_000), 0.00001f)
    }
}
