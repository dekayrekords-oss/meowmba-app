package com.example.meowmba

import com.example.meowmba.ui.companion.illustratedDecodeSize
import com.example.meowmba.ui.companion.illustratedSourceTableAnchor
import com.example.meowmba.ui.companion.illustratedSourceFocusX
import com.example.meowmba.core.background.BackgroundAnimation
import com.example.meowmba.ui.companion.illustratedRoomPlacement
import com.example.meowmba.ui.companion.PET_ENVIRONMENT_TABLE_Y
import org.junit.Assert.*
import org.junit.Test

class IllustratedEnvironmentPlacementTest {
    @Test fun authoredContactPlaneStaysAlignedWithoutStretchingAcrossFoldSizes() {
        for ((w, h) in listOf(320f to 740f, 540f to 960f, 960f to 540f, 768f to 768f, 160f to 96f)) {
            val p = illustratedRoomPlacement(1024, 1536, w, h, .689f)
            assertEquals("contact remains at .78", h * PET_ENVIRONMENT_TABLE_Y, p.y + p.height * .689f, .0002f)
            assertEquals("image geometry stays proportional", 1024f / 1536f, p.width / p.height, .00001f)
            assertTrue("left/top edges cover", p.x <= .001f && p.y <= .001f)
            assertTrue("right/bottom edges cover", p.x + p.width >= w - .001f && p.y + p.height >= h - .001f)
        }
    }

    @Test fun decodingCapsMemoryWithoutUpscaling() {
        assertEquals(1024 to 1536, illustratedDecodeSize(1024, 1536, 1536))
        assertEquals(1024 to 1536, illustratedDecodeSize(4096, 6144, 1536))
        assertEquals(1536 to 1024, illustratedDecodeSize(6144, 4096, 1536))
        assertEquals(213 to 320, illustratedDecodeSize(1024, 1536, 320))
        assertEquals(32 to 48, illustratedDecodeSize(32, 48, 320))
        val huge = illustratedDecodeSize(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
        assertTrue(huge.first <= 1536 && huge.second <= 1536)
        assertEquals(1 to 1, illustratedDecodeSize(0, -1, 320))
    }

    @Test fun finalBeachAndBedroomUseTheirMeasuredAssetEdges() {
        assertEquals(982f / 1536f, illustratedSourceTableAnchor(BackgroundAnimation.BEACH), 0f)
        assertEquals(887f / 1536f, illustratedSourceTableAnchor(BackgroundAnimation.BEDROOM), 0f)
        assertEquals(612f / 941f, illustratedSourceTableAnchor(BackgroundAnimation.CARTOON_NOOK), 0f)
        assertEquals(604f / 941f, illustratedSourceTableAnchor(BackgroundAnimation.CARTOON_GALLEY), 0f)
    }

    @Test fun landscapeArtKeepsTheInteractivePlaneVisibleOnFoldAspect() {
        for (room in listOf(BackgroundAnimation.CARTOON_NOOK, BackgroundAnimation.CARTOON_GALLEY)) {
            val anchor = illustratedSourceTableAnchor(room)
            val p = illustratedRoomPlacement(1672, 941, 2208f, 1768f, anchor)
            assertEquals(1768f * PET_ENVIRONMENT_TABLE_Y, p.y + p.height * anchor, .001f)
            assertTrue(p.width >= 2208f && p.height >= 1768f)
        }
    }

    @Test fun everyMeasuredRoomRetainsContactAndCoverageAtExtremeAspectRatios() {
        val viewports = listOf(280f to 1200f, 1200f to 280f, 1768f to 2208f, 2208f to 1768f)
        BackgroundAnimation.entries.filter { it.isPetEnvironment }.forEach { room ->
            val anchor = illustratedSourceTableAnchor(room)
            val (sourceWidth, sourceHeight) = when (room) {
                BackgroundAnimation.CARTOON_NOOK, BackgroundAnimation.CARTOON_GALLEY -> 1672 to 941
                BackgroundAnimation.CARTOON_SNACK_LAB, BackgroundAnimation.CARTOON_LAUNDROMAT,
                BackgroundAnimation.CARTOON_DINER -> 1536 to 1024
                BackgroundAnimation.CARTOON_PORTAL_ARCADE, BackgroundAnimation.CARTOON_MOON_GARDEN,
                BackgroundAnimation.CARTOON_ALIEN_AQUARIUM, BackgroundAnimation.CARTOON_METEOR_GARAGE -> 1536 to 1536
                else -> 1024 to 1536
            }
            for ((width, height) in viewports) {
                val p = illustratedRoomPlacement(sourceWidth, sourceHeight, width, height, anchor, illustratedSourceFocusX(room))
                assertEquals("measured table edge: ${room.id}", height * PET_ENVIRONMENT_TABLE_Y, p.y + p.height * anchor, .001f)
                assertEquals(sourceWidth.toFloat() / sourceHeight, p.width / p.height, .00001f)
                assertTrue("full viewport coverage: ${room.id}", p.x <= .001f && p.y <= .001f &&
                    p.x + p.width >= width - .001f && p.y + p.height >= height - .001f)
            }
        }
    }

    @Test fun portraitFramingKeepsNewBackgroundJokesOnScreen() {
        for ((room, subjectX) in listOf(BackgroundAnimation.CARTOON_SNACK_LAB to 320f,
            BackgroundAnimation.CARTOON_DINER to 250f, BackgroundAnimation.CARTOON_LAUNDROMAT to 768f)) {
            val p = illustratedRoomPlacement(1536, 1024, 360f, 800f,
                illustratedSourceTableAnchor(room), illustratedSourceFocusX(room))
            val x = p.x + subjectX / 1536f * p.width
            assertTrue("Joke remains inside the portrait viewport: ${room.id}", x in 40f..320f)
        }
    }

    @Test fun invalidViewportNeverProducesNonfiniteGeometry() {
        val p = illustratedRoomPlacement(0, -1, Float.NaN, Float.POSITIVE_INFINITY)
        assertTrue(listOf(p.x, p.y, p.width, p.height).all { it.isFinite() })
        assertTrue(p.width > 0 && p.height > 0)
    }
}
