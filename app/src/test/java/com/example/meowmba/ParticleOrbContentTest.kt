package com.example.meowmba

import com.example.meowmba.ui.companion.ParticleOrbContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Unit tests for the Jarvis particle orb seeds + caption (pure logic in
 * [ParticleOrbContent], ported from jarvis_mode.js on the MEOW server).
 */
class ParticleOrbContentTest {

    @Test
    fun `seeds count matches request`() {
        assertEquals(650, ParticleOrbContent.seedParticles(650).size)
        assertEquals(10, ParticleOrbContent.seedParticles(10).size)
    }

    @Test
    fun `seed dirs are unit length on the sphere`() {
        for (p in ParticleOrbContent.seedParticles(650)) {
            val len = sqrt(p.dx * p.dx + p.dy * p.dy + p.dz * p.dz)
            assertTrue("unit dir, was $len", len > 0.999f && len < 1.001f)
        }
    }

    @Test
    fun `shells span seven layers with fractions in range`() {
        val specs = ParticleOrbContent.seedParticles(650)
        assertEquals((1..7).toSet(), specs.map { it.shell }.toSet())
        for (p in specs) {
            assertTrue(p.radiusFrac in 0.35f..1.0f)
            assertTrue("hue ${p.hue} in cyan→violet band", p.hue in 160f..290f)
        }
    }

    @Test
    fun `rotation preserves unit length`() {
        for (p in ParticleOrbContent.seedParticles(100)) {
            val (x, y, z) = ParticleOrbContent.rotateDir(
                p.dx, p.dy, p.dz, p.spinY, p.spinX, p.spinZ, 12.34f + p.phase,
            )
            val len = sqrt(x * x + y * y + z * z)
            assertTrue("rotation is rigid, was $len", len > 0.999f && len < 1.001f)
        }
    }

    @Test
    fun `caption mirrors state honestly`() {
        assertEquals("650 PARAMETERS", ParticleOrbContent.captionFor(650, thinking = false, offline = false))
        assertEquals("◈ INFERENCE", ParticleOrbContent.captionFor(650, thinking = true, offline = false))
        assertEquals("OFFLINE", ParticleOrbContent.captionFor(650, thinking = false, offline = true))
        assertEquals("OFFLINE", ParticleOrbContent.captionFor(650, thinking = true, offline = true))
    }

    @Test
    fun `param names are stable and non-blank`() {
        assertEquals(ParticleOrbContent.paramName(0), ParticleOrbContent.paramName(0))
        assertTrue(ParticleOrbContent.paramName(3).isNotBlank())
        assertTrue(ParticleOrbContent.paramName(900).isNotBlank())
    }
}
