package com.example.meowmba

import com.example.meowmba.ui.companion.fitGodotFaceCenterY
import com.example.meowmba.ui.companion.fitGodotFaceRadius
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The head must fit on screen — ears included — in every orientation the app
 * runs in, with room left for the stretch the animation applies.
 *
 * Landscape on the Fold 7 cover display clipped the ear tips because the height
 * budget (1.70 R) was smaller than the face's real extent (1.85 R).
 */
class FaceFitTest {

    /** Face geometry, mirrored from AvatarRenderer's private constants. */
    private val above = 1.10f
    private val below = 0.75f
    private val stretch = 1.12f

    private fun assertFits(w: Float, h: Float, label: String) {
        val r = fitGodotFaceRadius(w, h)
        val cy = fitGodotFaceCenterY(w, h)
        val topMargin = h * 0.07f
        val landscape = w > h * 1.15f
        val bottomMargin = if (landscape) h * 0.12f else h * 0.15f

        val earTip = cy - above * r * stretch
        val chin = cy + below * r * stretch
        assertTrue(
            "$label: ear tips clip the top ($earTip < $topMargin)",
            earTip >= topMargin - 0.5f,
        )
        assertTrue(
            "$label: chin clips the bottom ($chin > ${h - bottomMargin})",
            chin <= h - bottomMargin + 0.5f,
        )
        assertTrue("$label: head has no size", r > 0f)
    }

    @Test
    fun `fold 7 cover display portrait fits`() {
        assertFits(1080f, 2520f, "cover portrait")
    }

    @Test
    fun `fold 7 cover display landscape fits`() {
        // The regression: this orientation cut the ears off at rest.
        assertFits(2520f, 1080f, "cover landscape")
    }

    @Test
    fun `fold 7 inner display fits both ways`() {
        assertFits(2176f, 1812f, "inner portrait")
        assertFits(1812f, 2176f, "inner landscape")
    }

    @Test
    fun `a range of real phone and tablet sizes all fit`() {
        val sizes = listOf(
            1080f to 1920f, 1440f to 3120f, 1170f to 2532f,
            2560f to 1600f, 800f to 1280f, 1920f to 1080f,
        )
        for ((w, h) in sizes) {
            assertFits(w, h, "${w.toInt()}x${h.toInt()}")
            assertFits(h, w, "${h.toInt()}x${w.toInt()}")
        }
    }

    @Test
    fun `portrait keeps the head near the middle of the band`() {
        // The landscape fix must not shove the portrait face off-center.
        val h = 2520f
        val cy = fitGodotFaceCenterY(1080f, h)
        val bandMid = (h * 0.07f + (h - h * 0.15f)) * 0.5f
        assertEquals("portrait center should be unchanged", bandMid, cy, 1f)
    }

    @Test
    fun `head never exceeds its share of the shorter axis`() {
        assertTrue(fitGodotFaceRadius(1080f, 2520f) <= 1080f * 0.46f + 0.5f)
        assertTrue(fitGodotFaceRadius(2520f, 1080f) <= 1080f * 0.54f + 0.5f)
    }

    @Test
    fun `degenerate canvases produce a finite centered head`() {
        // A 1px or zero-size canvas happens for one frame during layout.
        for ((w, h) in listOf(1f to 1f, 0f to 0f, 100f to 10f)) {
            val r = fitGodotFaceRadius(w, h)
            val cy = fitGodotFaceCenterY(w, h)
            assertTrue("radius must be finite for ${w}x$h", r.isFinite() && r > 0f)
            assertTrue("center must be finite for ${w}x$h", cy.isFinite())
        }
    }
}
