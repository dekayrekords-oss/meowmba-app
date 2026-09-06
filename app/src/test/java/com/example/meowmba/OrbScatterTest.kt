package com.example.meowmba

import com.example.meowmba.ui.companion.orbScatterAmount
import org.junit.Assert.*
import org.junit.Test

class OrbScatterTest {
    @Test fun `scatter expands then recovers continuously`() {
        assertEquals(0f, orbScatterAmount(0f), 0f)
        assertEquals(1f, orbScatterAmount(0.22f), 0.0001f)
        assertTrue(orbScatterAmount(0.1f) in 0f..1f)
        assertTrue(orbScatterAmount(1f) > orbScatterAmount(2f))
        assertEquals(0f, orbScatterAmount(3f), 0f)
        assertEquals(0f, orbScatterAmount(Float.NaN), 0f)
    }
}
