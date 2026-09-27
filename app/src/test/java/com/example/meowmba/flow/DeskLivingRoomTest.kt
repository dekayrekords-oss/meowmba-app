package com.example.meowmba.flow

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.meowmba.ui.desk.*
import com.example.meowmba.ui.flow.DeskRoomActions
import com.example.meowmba.ui.flow.DeskWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * Owner request 2026-09-26: the cat tower goes behind the couch (with more levels) to make room
 * for a wider stereo; a wider TV; one moon (office window only); a picture the user chooses; and,
 * in landscape only, a wider bookshelf, a much bigger TV and a longer bed.
 */
class DeskLayoutTest {
    private fun DeskFrame.right() = left + width

    @Test fun everyObjectAndSpotFitsItsLayout() {
        for (layout in DeskLayout.entries) {
            DeskObject.entries.forEach { o ->
                val f = o.frame(layout)
                assertTrue("$o in $layout", f.left >= layout.left && f.right() <= layout.right)
            }
            val random = Random(926)
            repeat(300) {
                val room = DeskRoom.entries[it % 3]
                val act = DeskCatBehavior.apartmentIdle(random, DeskCatPose.REST, room, true, true, true, true, .5f, layout)
                assertTrue("$act in $layout", act.spot.x >= layout.left && act.spot.x + 110f <= layout.right)
            }
            DeskObject.entries.forEach { o ->
                val spot = DeskCatBehavior.atObject(o, layout).spot
                assertTrue("$o spot in $layout", spot.x >= layout.left && spot.x + 110f <= layout.right)
            }
        }
    }

    @Test fun livingRoomFurnitureDoesNotOverlap() {
        for (layout in DeskLayout.entries) {
            val shelf = DeskObject.BOOKSHELF.frame(layout)
            val couch = DeskObject.COUCH.frame(layout)
            val stereo = DeskObject.STEREO.frame(layout)
            val tv = DeskObject.BIG_TV.frame(layout)
            val tower = DeskObject.TOWER_LIVING.frame(layout)
            assertTrue(shelf.right() <= couch.left + 6f)
            assertTrue(couch.right() <= stereo.left + 6f)
            // The tower stands behind the couch, and the TV hangs over the stereo.
            assertTrue(tower.left >= couch.left && tower.right() <= couch.right())
            assertTrue(tv.top + tv.height < stereo.top)
            assertTrue(tv.left >= tower.right() - 1f)
            assertEquals(DeskRoom.LIVING, DeskObject.PICTURE.room)
        }
    }

    @Test fun stereoIsWideEnoughToReadAndLandscapeGrowsTheOuterRooms() {
        val std = DeskLayout.STANDARD; val land = DeskLayout.LANDSCAPE
        // The receiver's display used to be 74 wide; the stereo 142 including its speakers.
        assertTrue(std.living.displayW >= 118f)
        assertTrue(std.living.stereoW >= 170f)
        assertTrue(land.living.displayW > std.living.displayW)
        // Landscape only: wider bookshelf, much bigger TV, longer bed; the office doesn't move.
        assertTrue(DeskObject.BOOKSHELF.frame(land).width >= DeskObject.BOOKSHELF.frame(std).width * 1.8f)
        val tvArea = { l: DeskLayout -> DeskObject.BIG_TV.frame(l).let { it.width * it.height } }
        assertTrue(tvArea(land) >= tvArea(std) * 2f)
        assertTrue(DeskObject.BED.frame(land).width >= DeskObject.BED.frame(std).width + 100f)
        listOf(DeskObject.BUILD, DeskObject.WINDOW, DeskObject.ARCADE, DeskObject.BIG_WINDOW).forEach { assertEquals(it.frame(std), it.frame(land)) }
        assertEquals(DeskRoom.APT_LEFT, std.left); assertEquals(DeskRoom.APT_RIGHT, std.right)
    }

    @Test fun catClimbsTheTallTowerByItsMiddlePlatform() {
        for (layout in DeskLayout.entries) {
            val s = DeskCatBehavior.spots(layout)
            assertTrue(s.towerStep.y > s.livingTower.y && s.towerStep.y < DeskCatBehavior.FLOOR)
            assertEquals(s.livingTower, DeskCatBehavior.atObject(DeskObject.PICTURE, layout).spot)
        }
    }
}

private val room = RoomState(
    music = RoomMedia("Midnight City (Remastered 2021)", "M83 · Hurry Up, We're Dreaming", playing = true, app = "com.spotify.music"),
    video = RoomMedia("How to tune a guitar in 5 minutes, the easy way", "Fender", playing = true, app = "com.google.android.youtube"),
    audiobook = RoomMedia("Project Hail Mary", "Andy Weir", playing = false, app = "com.audible.application"),
)
private val desk = DeskState(
    clockHour = 22, clockMinute = 10, night = true, weatherLine = "64° clear",
    projectTitle = "Band plays Europe", tallyDone = 0, tallyTotal = 0, projectStatus = "Book the Berlin show",
    pinnedNote = emptyList(), monitorLines = listOf(MonitorLine("> band-site", true)),
    sticky = listOf("3:30", "tester", "call"), trayCount = 1, jarNote = null, lampOn = false, room = room,
)

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.settle(frames: Int = 60) {
    waitForIdle(); repeat(frames) { mainClock.advanceTimeByFrame(); waitForIdle() }
}

private fun shoot(rule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, out androidx.activity.ComponentActivity>, name: String) {
    val view = rule.activity.window.decorView
    val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(android.graphics.Canvas(bmp))
    File("build/flow-shots").apply { mkdirs() }.let { File(it, "$name.png") }.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

/** A stand-in photo: diagonal colour bands, so cropping and placement are visible. */
private fun photo() = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
    val c = android.graphics.Canvas(this)
    listOf(0xFF1F4FBF, 0xFFE0B66A, 0xFFB24A62, 0xFF2F7A3E).forEachIndexed { i, col ->
        c.drawRect(i * 100f, 0f, i * 100f + 100f, 300f, android.graphics.Paint().apply { color = col.toInt() })
    }
    c.drawCircle(200f, 150f, 60f, android.graphics.Paint().apply { color = 0xFFF4EEDB.toInt() })
}.asImageBitmap()

/** A phone turned sideways: the landscape apartment. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w915dp-h412dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeskLandscapeTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun landscapeShowsTheBigRoomsAndThePictureOpensThePhotoPicker() {
        var chosen = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            DeskWorkspace(desk, emptyList(), thermalStatus = 0, picture = photo(), roomActions = DeskRoomActions(choosePicture = { chosen++ }))
        }
        rule.settle(37)
        shoot(rule, "apartment-landscape-night")
        listOf("desk_picture", "desk_big_tv", "desk_stereo", "desk_bookshelf", "desk_bed").forEach { rule.onNodeWithTag(it).assertIsDisplayed() }
        rule.onNodeWithTag("desk_picture").performClick(); rule.settle(25)
        rule.onNodeWithTag("desk_reset_picture").assertIsDisplayed()
        rule.onNodeWithTag("desk_choose_picture").performClick(); rule.settle(25)
        assertEquals(1, chosen)
    }
}

/** The unfolded screen keeps the standard rooms; the painting hangs until a photo is chosen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w840dp-h760dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeskUnfoldedNightTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun unfoldedNightHasOneMoonAndThePainting() {
        rule.mainClock.autoAdvance = false
        rule.setContent { DeskWorkspace(desk, emptyList(), thermalStatus = 0) }
        rule.settle(37)
        shoot(rule, "apartment-open-night")
        rule.onNodeWithTag("desk_picture").performClick(); rule.settle(25)
        rule.onNodeWithTag("desk_choose_picture").assertIsDisplayed()
        rule.onNodeWithTag("desk_reset_picture").assertDoesNotExist()
    }
}

/** Folded: the living room on its own, to check the stereo and TV are readable at phone size. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeskCoverLivingRoomTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun coverLivingRoom() {
        rule.mainClock.autoAdvance = false
        rule.setContent { DeskHabitat(desk.copy(night = false), wanderEveryMs = 0, thermalStatus = 0, picture = photo()) }
        rule.settle(31)
        rule.onNodeWithTag("desk_room_left").performClick(); rule.settle(520)
        shoot(rule, "apartment-cover-living")
        rule.onNodeWithTag("desk_room_right").performClick(); rule.settle(420)
        rule.onNodeWithTag("desk_room_right").performClick(); rule.settle(420)
        shoot(rule, "apartment-cover-bedroom-day")
    }
}
