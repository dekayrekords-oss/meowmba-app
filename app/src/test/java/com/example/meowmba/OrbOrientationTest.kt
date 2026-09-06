package com.example.meowmba

import com.example.meowmba.ui.companion.orbViewRotation
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class OrbOrientationTest {
    private val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    @Test fun `initial pose is neutral even with phone rotated`() {
        val pose = floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        assertArrayEquals(identity, orbViewRotation(pose, pose), 0.0001f)
    }

    @Test fun `phone yaw produces inverse camera rotation including depth`() {
        val yaw = floatArrayOf(0f, 0f, 1f, 0f, 1f, 0f, -1f, 0f, 0f)
        val inverse = floatArrayOf(0f, 0f, -1f, 0f, 1f, 0f, 1f, 0f, 0f)
        assertArrayEquals(inverse, orbViewRotation(yaw, identity), 0.0001f)
    }

    @Test fun `full turn returns to original view`() {
        assertArrayEquals(identity, orbViewRotation(identity, identity), 0.0001f)
    }
}
