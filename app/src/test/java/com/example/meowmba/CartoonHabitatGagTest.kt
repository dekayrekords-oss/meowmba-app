package com.example.meowmba

import androidx.compose.ui.geometry.Offset
import com.example.meowmba.ui.companion.*
import com.example.meowmba.core.background.BackgroundAnimation
import com.example.meowmba.core.background.BackgroundSelection
import org.junit.Assert.*
import org.junit.Test

class CartoonHabitatGagTest {
    private val width = 800f
    private val height = 360f
    private val at = cartoonGagLayout(width, height)!!.center

    @Test fun allFourSpaceHabitatsPersistAndExposeTheirOwnNamedToy() {
        val rooms = listOf(
            BackgroundAnimation.CARTOON_PORTAL_ARCADE to CartoonGagKind.PORTAL,
            BackgroundAnimation.CARTOON_MOON_GARDEN to CartoonGagKind.MOONFLOWER,
            BackgroundAnimation.CARTOON_ALIEN_AQUARIUM to CartoonGagKind.PERISCOPE,
            BackgroundAnimation.CARTOON_METEOR_GARAGE to CartoonGagKind.ROCKET,
        )
        rooms.forEach { (room, toy) ->
            assertTrue(room.isPetEnvironment && room.isCartoonEnvironment)
            assertEquals(room, BackgroundSelection.parse("ANIMATION", room.id, "47").animation)
            assertEquals(toy, CartoonGagKind.forBackground(room.id))
            assertTrue(toy.description.contains("tap", ignoreCase = true))
        }
        assertEquals(4, rooms.map { it.second.description }.toSet().size)
        assertNull(CartoonGagKind.forBackground("missing-room"))
    }

    @Test fun everyNewMachineReplaysFromOneClockAndRetainsForegroundOwnership() {
        for (kind in listOf(CartoonGagKind.PORTAL, CartoonGagKind.MOONFLOWER, CartoonGagKind.PERISCOPE, CartoonGagKind.ROCKET)) {
            val first = CartoonHabitatGag(kind)
            val replay = CartoonHabitatGag(kind)
            assertFalse(tap(first, active = false))
            assertFalse(tap(first, avatar = true))
            assertFalse(tap(first, prop = true))
            assertEquals(CartoonGagPose(), first.pose(.4f))
            assertTrue(tap(first, clock = 7f)); assertTrue(tap(replay, clock = 7f))
            for (i in 0..200) {
                val time = 7f + i / 100f
                assertEquals("Deterministic $kind", first.pose(time), replay.pose(time))
            }
            val paused = first.pose(7.4f)
            assertTrue(paused.lift > 0f)
            repeat(120) { assertEquals(paused, first.pose(7.4f)) }
            assertEquals(CartoonGagPose(), first.pose(7.4f, reducedMotion = true))
            assertEquals(CartoonGagPose(), first.pose(9f))
        }
    }

    private fun tap(gag: CartoonHabitatGag, clock: Float = 0f, point: Offset = at,
        active: Boolean = true, avatar: Boolean = false, prop: Boolean = false) =
        gag.tap(point, width, height, clock, active, avatar, prop)

    @Test fun pausedAndForegroundOwnedTapsNeverStartBackgroundPlay() {
        val gag = CartoonHabitatGag(CartoonGagKind.JELLY)
        assertFalse(tap(gag, active = false))
        assertFalse(tap(gag, avatar = true))
        assertFalse(tap(gag, prop = true))
        assertFalse(tap(gag, point = Offset(width / 2f, height / 2f)))
        assertEquals(CartoonGagPose(), gag.pose(.4f))
        assertTrue(tap(gag))
        assertTrue(gag.pose(.4f).lift > .1f)
    }

    @Test fun equalSceneTimeFreezesAndReducedMotionKeepsStillArtwork() {
        val gag = CartoonHabitatGag(CartoonGagKind.SOCK)
        assertTrue(tap(gag))
        val frozen = gag.pose(.5f)
        repeat(120) { assertEquals(frozen, gag.pose(.5f)) }
        assertEquals(CartoonGagPose(), gag.pose(.5f, reducedMotion = true))
        assertEquals(frozen, gag.pose(.5f))
        assertEquals(CartoonGagPose(), gag.pose(CartoonHabitatGag.DURATION))
    }

    @Test fun repeatedTapsReplaceOneBoundedResponseWithoutAnEventQueue() {
        val gag = CartoonHabitatGag(CartoonGagKind.PANCAKE)
        repeat(1000) { assertTrue(tap(gag, clock = it / 100f)) }
        assertEquals(.72f, gag.pose(9.99f).squash, .0001f)
        for (i in 0..1800) {
            val pose = gag.pose(9.99f + i / 1000f)
            assertTrue(pose.lift in 0f..2.1f)
            assertTrue(pose.squash in .70f..1.14f)
            assertTrue(pose.lean in -13f..13f)
            assertTrue(pose.burst in 0f..1f)
        }
        assertEquals(CartoonGagPose(), gag.pose(12f))
    }

    @Test fun invalidGeometryAndTimesCannotTriggerPlayOrPoisonThePose() {
        val gag = CartoonHabitatGag(CartoonGagKind.JELLY)
        assertNull(cartoonGagLayout(0f, 480f))
        assertNull(cartoonGagLayout(Float.NaN, 480f))
        assertFalse(tap(gag, clock = Float.NaN))
        assertFalse(tap(gag, point = Offset(Float.POSITIVE_INFINITY, at.y)))
        assertTrue(tap(gag, clock = 1f))
        assertEquals(CartoonGagPose(), gag.pose(Float.POSITIVE_INFINITY))
        assertEquals(CartoonGagPose(), gag.pose(.5f))
        assertTrue(gag.pose(1.5f).lift > 0f)
    }

    @Test fun toyAndItsEntireTapTargetFitPortraitLandscapeFoldAndSmallWindows() {
        for ((w, h) in listOf(360f to 800f, 800f to 360f, 900f to 280f, 600f to 600f, 160f to 120f)) {
            val layout = cartoonGagLayout(w, h)!!
            assertTrue(layout.center.x - layout.unit * 1.22f >= 0f)
            assertTrue(layout.center.x + layout.unit * 1.22f < w * .4f)
            assertTrue(layout.center.y - layout.unit * 3.4f > 0f)
            assertTrue(layout.center.y + layout.unit * 1.6f < h * .78f)
            assertTrue(layout.contains(layout.center))
            assertFalse(layout.contains(Offset(w * .5f, h * .45f)))
        }
    }

    @Test fun gpuCameraProjectsItsAimToCenterAndProtectsTheFlyingAvatarAtEveryScale() {
        for ((w, h) in listOf(360f to 800f, 800f to 360f, 600f to 600f)) {
            val aim = cartoonProjectHabitatPoint(0f, 1.5f, 0f, w, h)!!
            assertEquals(w * .5f, aim.x, .0001f)
            assertEquals(h * .5f, aim.y, .0001f)
            for (scale in listOf(.5f, 2f, 3f)) {
                val frame = Fly3DFrame(x = -.8f, y = 2.5f, z = .3f, avatarScale = scale, flight = 1f)
                val root = cartoonProjectHabitatPoint(frame.x, frame.y, frame.z, w, h)!!
                assertTrue(cartoonFlyOwnsPoint(root, w, h, frame))
            }
        }
        assertNull(cartoonProjectHabitatPoint(0f, 1f, Float.NaN, width, height))
        assertNull(cartoonProjectHabitatPoint(0f, 1f, 0f, 0f, height))
    }
}
