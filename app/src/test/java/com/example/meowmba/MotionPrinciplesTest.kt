package com.example.meowmba

import com.example.meowmba.ui.companion.Motion
import com.example.meowmba.ui.companion.Trailing
import com.example.meowmba.ui.companion.ambientLife
import com.example.meowmba.ui.companion.arcRise
import com.example.meowmba.ui.companion.breathSkew
import com.example.meowmba.ui.companion.squashStretch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The classical animation principles, as pinned behaviour.
 *
 * Each test names the principle it protects, because the failure mode when one
 * of these regresses is not a crash — it is a face that goes subtly dead, which
 * nothing else in the suite would notice.
 */
class MotionPrinciplesTest {

    // ── squash & stretch ────────────────────────────────────────────────────

    @Test
    fun `stretching vertically narrows horizontally`() {
        val (sx, sy) = squashStretch(0.2f)
        assertTrue("vertical must stretch", sy > 1f)
        assertTrue("horizontal must narrow to compensate", sx < 1f)
    }

    @Test
    fun `squashing vertically widens horizontally`() {
        val (sx, sy) = squashStretch(-0.2f)
        assertTrue("vertical must squash", sy < 1f)
        assertTrue("horizontal must widen to compensate", sx > 1f)
    }

    @Test
    fun `neutral amount is an identity transform`() {
        val (sx, sy) = squashStretch(0f)
        assertEquals(1f, sx, 1e-5f)
        assertEquals(1f, sy, 1e-5f)
    }

    @Test
    fun `full preservation conserves area`() {
        val (sx, sy) = squashStretch(0.3f, preserve = 1f)
        assertEquals("area must be conserved", 1f, sx * sy, 1e-4f)
    }

    @Test
    fun `zero preservation is a pure vertical scale`() {
        val (sx, sy) = squashStretch(0.3f, preserve = 0f)
        assertEquals(1f, sx, 1e-5f)
        assertEquals(1.3f, sy, 1e-5f)
    }

    @Test
    fun `extreme input is clamped so the head cannot invert`() {
        for (a in listOf(-100f, -1f, 1f, 100f)) {
            val (sx, sy) = squashStretch(a)
            assertTrue("scaleX $sx must stay positive", sx > 0f)
            assertTrue("scaleY $sy must stay positive", sy > 0f)
        }
    }

    // ── arcs ────────────────────────────────────────────────────────────────

    @Test
    fun `travel through center has no rise`() {
        assertEquals(0f, arcRise(0f), 1e-5f)
    }

    @Test
    fun `rise grows toward the extremes of travel and is symmetric`() {
        assertTrue(arcRise(0.9f) > arcRise(0.5f))
        assertTrue(arcRise(0.5f) > arcRise(0.1f))
        assertEquals("arc must be symmetric", arcRise(-0.7f), arcRise(0.7f), 1e-6f)
    }

    @Test
    fun `rise never exceeds its depth`() {
        for (i in -20..20) {
            val r = arcRise(i / 10f, depth = 0.06f)
            assertTrue("rise $r out of range", r >= 0f && r <= 0.06f + 1e-6f)
        }
    }

    // ── follow through ──────────────────────────────────────────────────────

    @Test
    fun `ears lag behind a moving head and settle when it stops`() {
        val trail = Trailing()
        val dt = 1f / 60f
        var pos = 0f
        var duringMove = 0f
        // Head sweeps right for 200ms.
        repeat(12) {
            pos += 0.05f
            duringMove = trail.update(pos, dt, gain = 0.3f)
        }
        assertTrue("ears must trail a rightward sweep", duringMove < -0.01f)
        // Head stops; the trail overshoots back through zero and settles.
        var settled = duringMove
        repeat(240) { settled = trail.update(pos, dt, gain = 0.3f) }
        assertTrue("trail must settle when the head stops", abs(settled) < 0.02f)
    }

    @Test
    fun `a still head produces no ear lag`() {
        val trail = Trailing()
        var v = 0f
        repeat(60) { v = trail.update(0.4f, 1f / 60f) }
        assertTrue("no motion means no follow-through, got $v", abs(v) < 0.02f)
    }

    @Test
    fun `a teleport cannot fling the ears off the head`() {
        val trail = Trailing()
        // A config change or first frame can jump the head across the screen.
        val v = trail.update(1000f, 1f / 60f, gain = 5f)
        assertTrue("lag must stay bounded, got $v", abs(v) <= 1f)
    }

    // ── ambient life ────────────────────────────────────────────────────────

    @Test
    fun `breath is asymmetric - inhale and exhale take different times`() {
        // A symmetric wave is the tell of generated motion. The curve must
        // reach its peak somewhere other than the halfway point.
        var peakPhase = 0f
        var peak = -1f
        for (i in 0..1000) {
            val p = i / 1000f
            val v = breathSkew(p)
            if (v > peak) { peak = v; peakPhase = p }
        }
        assertTrue("peak at $peakPhase is too close to symmetric", abs(peakPhase - 0.5f) > 0.02f)
    }

    @Test
    fun `breath stays in range and is continuous across the loop seam`() {
        for (i in 0..1000) {
            val v = breathSkew(i / 1000f)
            assertTrue("breath $v out of range", v in 0f..1f)
        }
        // No jump at the wrap point, or the face hitches once per cycle.
        assertEquals(breathSkew(0.999f), breathSkew(1.001f), 0.02f)
        assertEquals("phase must wrap", breathSkew(0.25f), breathSkew(1.25f), 1e-5f)
    }

    @Test
    fun `the face is never still`() {
        // The whole point of the ambient layer: sample it across a long window
        // and there must be no frame where everything is at rest.
        var moved = 0
        var prev: Triple<Float, Float, Float>? = null
        for (i in 0..600) {
            val t = i * 0.1f
            val m = Motion().also { ambientLife(t, R = 400f, energy = 1f, m = it) }
            val cur = Triple(m.offX, m.offY, m.tiltDeg)
            if (prev != null && cur != prev) moved++
            prev = cur
        }
        assertEquals("every sampled frame must differ from the last", 600, moved)
    }

    @Test
    fun `ambient motion stays subtle relative to head size`() {
        val r = 400f
        for (i in 0..2000) {
            val m = Motion().also { ambientLife(i * 0.05f, R = r, energy = 1.6f, m = it) }
            assertTrue("offX ${m.offX} too large", abs(m.offX) < r * 0.12f)
            assertTrue("offY ${m.offY} too large", abs(m.offY) < r * 0.12f)
            assertTrue("tilt ${m.tiltDeg} too large", abs(m.tiltDeg) < 6f)
        }
    }

    @Test
    fun `energy scales the whole layer and zero energy rests`() {
        val low = Motion().also { ambientLife(3.3f, 400f, energy = 0.4f, m = it) }
        val high = Motion().also { ambientLife(3.3f, 400f, energy = 1.2f, m = it) }
        assertTrue("higher energy must move more", abs(high.offY) > abs(low.offY))
        val none = Motion().also { ambientLife(3.3f, 400f, energy = 0f, m = it) }
        assertEquals(0f, none.offX, 1e-6f)
        assertEquals(0f, none.offY, 1e-6f)
        assertEquals(0f, none.tiltDeg, 1e-6f)
    }

    @Test
    fun `ambient cycle does not visibly repeat`() {
        // Non-harmonic periods: the sum must not return to the same state on
        // any short interval, or the idle reads as a loop.
        val a = Motion().also { ambientLife(5f, 400f, 1f, it) }
        for (period in listOf(1f, 2f, 3f, 4f, 5f, 6f, 8f, 10f)) {
            val b = Motion().also { ambientLife(5f + period, 400f, 1f, it) }
            val same = abs(a.offX - b.offX) < 0.5f &&
                abs(a.offY - b.offY) < 0.5f &&
                abs(a.tiltDeg - b.tiltDeg) < 0.05f
            assertTrue("ambient layer repeats after ${period}s", !same)
        }
    }

    @Test
    fun `ambient values are always finite`() {
        for (i in 0..5000) {
            val m = Motion().also { ambientLife(i * 0.37f, 400f, 1f, it) }
            assertTrue(m.offX.isFinite() && m.offY.isFinite())
            assertTrue(m.tiltDeg.isFinite() && m.scale.isFinite())
        }
    }

    @Test
    fun `perspective and deformation stay on separate channels`() {
        // LEAN_IN means "nearer the viewer" — uniform growth. Deforming for it
        // would read as the head stretching taller, not approaching.
        val lean = Motion().also {
            com.example.meowmba.ui.companion.bodyMotion(
                com.example.meowmba.ui.companion.Body.LEAN_IN, 0.5f, 0.5f, 400f, 1f, it,
            )
        }
        assertTrue("lean-in must scale up", lean.scale > 0.05f)
        assertEquals("lean-in must not deform", 0f, lean.squash, 1e-6f)

        // An impact is the opposite: it deforms.
        val hit = Motion().also {
            com.example.meowmba.ui.companion.bodyMotion(
                com.example.meowmba.ui.companion.Body.SNAP_FWD, 0.30f, 0.30f, 400f, 1f, it,
            )
        }
        assertTrue("an impact must squash the head flat", hit.squash < -0.05f)
    }

    @Test
    fun `a bounce stretches at the top and squashes at the bottom`() {
        // BOB's offY is screen-space, +Y down. At the top of the travel the
        // head must be stretched; at the bottom, squashed.
        val samples = (0..100).map { i ->
            val t = i / 100f
            Motion().also {
                com.example.meowmba.ui.companion.bodyMotion(
                    com.example.meowmba.ui.companion.Body.BOB, t, t, 400f, 1f, it,
                )
            }
        }
        val highest = samples.minByOrNull { it.offY }!!
        val lowest = samples.maxByOrNull { it.offY }!!
        assertTrue("top of the bounce must stretch", highest.squash > 0f)
        assertTrue("bottom of the bounce must squash", lowest.squash < 0f)
    }

    @Test
    fun `breath deforms rather than resizing the head`() {
        // Breath is a chest expanding, not the head moving closer.
        var sawSquash = false
        for (i in 0..400) {
            val m = Motion().also { ambientLife(i * 0.05f, 400f, 1f, it) }
            assertEquals("breath must not change apparent distance", 0f, m.scale, 1e-6f)
            if (abs(m.squash) > 0.005f) sawSquash = true
        }
        assertTrue("breath must deform the silhouette", sawSquash)
    }

    @Test
    fun `breath and weight shift are independent motions`() {
        // If they ran at the same period the head would bob and lean in
        // lockstep, which reads as one rigid oscillation instead of two
        // overlapping ones.
        val samples = (0..400).map { i ->
            Motion().also { ambientLife(i * 0.05f, 400f, 1f, it) }
        }
        val yPeak = samples.indices.maxByOrNull { abs(samples[it].offY) }!!
        val xPeak = samples.indices.maxByOrNull { abs(samples[it].offX) }!!
        assertNotEquals("breath and sway must not peak together", yPeak, xPeak)
    }

    // ── clip envelope ───────────────────────────────────────────────────────

    @Test
    fun `a clip eases in from neutral and back out to it`() {
        val dur = 2f
        assertEquals("starts at neutral", 0f, com.example.meowmba.ui.companion.envelope(0f, dur), 1e-4f)
        assertEquals("ends at neutral", 0f, com.example.meowmba.ui.companion.envelope(dur, dur), 1e-4f)
        assertTrue("holds full value in the middle",
            com.example.meowmba.ui.companion.envelope(dur / 2f, dur) > 0.99f)
    }

    @Test
    fun `short and long clips get the same crisp attack`() {
        // The envelope is in absolute seconds, so a 0.9s clip does not spend a
        // third of its life ramping while a 3.4s clip snaps.
        val shortAttack = (0..100).map { it / 100f * 0.9f }
            .first { com.example.meowmba.ui.companion.envelope(it, 0.9f) > 0.95f }
        val longAttack = (0..100).map { it / 100f * 3.4f }
            .first { com.example.meowmba.ui.companion.envelope(it, 3.4f) > 0.95f }
        assertTrue("attacks should be within 0.1s of each other", abs(shortAttack - longAttack) < 0.1f)
    }

    @Test
    fun `the envelope never leaves the unit range`() {
        for (dur in listOf(0.5f, 1f, 2.6f, 10f)) {
            for (i in 0..200) {
                val v = com.example.meowmba.ui.companion.envelope(i / 200f * dur, dur)
                assertTrue("envelope $v out of range", v in 0f..1f)
            }
        }
        assertEquals(0f, com.example.meowmba.ui.companion.envelope(1f, 0f), 1e-6f)
    }

    // ── clip endings ────────────────────────────────────────────────────────

    @Test
    fun `sustained body motions return home instead of being cut`() {
        // SINK, RISE and WIGGLE used to hold maximum displacement at t=1, so
        // the head teleported back to rest the frame the clip ended.
        for (body in listOf(
            com.example.meowmba.ui.companion.Body.SINK,
            com.example.meowmba.ui.companion.Body.RISE,
            com.example.meowmba.ui.companion.Body.WIGGLE,
        )) {
            val end = Motion().also {
                com.example.meowmba.ui.companion.bodyMotion(body, 1f, 1f, 400f, 1f, it)
            }
            assertTrue("$body must land near rest, got offY=${end.offY}", abs(end.offY) < 8f)
            assertTrue("$body must land near rest, got offX=${end.offX}", abs(end.offX) < 8f)
        }
    }

    @Test
    fun `a held gape becomes an arc`() {
        // "yawn" held 0.92 open for its whole 2.6s, which reads as a scream.
        val open = com.example.meowmba.ui.companion.jawLayer(
            com.example.meowmba.ui.companion.JawL.WIDE, 0.38f,
        ).jaw!!
        val closed = com.example.meowmba.ui.companion.jawLayer(
            com.example.meowmba.ui.companion.JawL.WIDE, 1f,
        ).jaw!!
        assertTrue("must open wide at the peak", open > 0.8f)
        assertTrue("must close by the end, got $closed", closed < 0.25f)
    }
}
