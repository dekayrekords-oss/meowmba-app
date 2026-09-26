package com.example.meowmba.flow

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.meowmba.core.computer.DevStudioLog
import com.example.meowmba.core.media.MediaKind
import com.example.meowmba.core.media.RoomMediaWatcher
import com.example.meowmba.ui.companion.AppTheme
import com.example.meowmba.ui.companion.DriveAvatarPolicy
import com.example.meowmba.ui.desk.*
import com.example.meowmba.ui.desk.DeskPainter.drawCat
import com.example.meowmba.ui.flow.DeskRoomActions
import com.example.meowmba.ui.flow.DeskWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/** Owner request 2026-09-25: the Desk becomes Meow's whole place. */
class DeskApartmentLogicTest {
    @Test fun roomsAreLivingOfficeBedroomLeftToRight() {
        assertEquals(DeskRoom.LIVING, DeskRoom.of(-300f))
        assertEquals(DeskRoom.OFFICE, DeskRoom.of(180f))
        assertEquals(DeskRoom.BEDROOM, DeskRoom.of(700f))
        assertEquals(DeskRoom.BEDROOM, DeskObject.BED.room)
        assertEquals(DeskRoom.LIVING, DeskObject.STEREO.room)
        assertEquals(DeskRoom.OFFICE, DeskObject.BUILD.room)
        assertTrue(DeskObject.entries.all { it.left >= DeskRoom.APT_LEFT && it.left + it.width <= DeskRoom.APT_RIGHT })
    }

    @Test fun apartmentWanderingVisitsEveryRoomAndStaysOnSurfaces() {
        val random = Random(925)
        var room = DeskRoom.OFFICE
        val seen = mutableSetOf<DeskRoom>()
        repeat(300) {
            val act = DeskCatBehavior.apartmentIdle(random, DeskCatPose.REST, room, hasMail = true, hasGoal = true,
                musicPlaying = true, videoPlaying = true, roomChance = .55f)
            room = DeskRoom.of(act.spot.x); seen += room
            assertTrue(act.spot.x >= DeskRoom.APT_LEFT && act.spot.x + 110f <= DeskRoom.APT_RIGHT)
            assertTrue("paws above the floor", act.spot.y + 81f <= 741f)
        }
        assertEquals(DeskRoom.entries.toSet(), seen)
    }

    @Test fun everyPoseHasAFaceAndDescription() {
        DeskCatPose.entries.forEach { assertTrue(DeskCatBehavior.description(it, 0f).isNotBlank()); DeskCatBehavior.defaultFace(it) }
        assertEquals(DeskCatFace.ASLEEP, DeskCatBehavior.defaultFace(DeskCatPose.SLEEP))
    }

    @Test fun studioLogFollowsProjectsFromTheTerminal() {
        var s = DevStudioLog.State()
        s = DevStudioLog.fold(s, "Meow Development Studio\nProject: /root/MeowProjects/band-site\nReview/rec", 1_000)
        s = DevStudioLog.fold(s, "overy data: x\nCheckpoint saved: 12 source files.\n", 2_000)
        assertEquals("band-site", s.projects.single().name)
        assertEquals("Checkpoint saved", s.projects.single().lastEvent)
        // A split line is completed by the next chunk; ANSI colour codes are ignored.
        s = DevStudioLog.fold(s, "\u001B[32mExit co", 3_000)
        s = DevStudioLog.fold(s, "de: 0\u001B[0m\r\n", 4_000)
        assertEquals("Build succeeded", s.projects.single().lastEvent)
        assertEquals(1, s.projects.single().builds)
        s = DevStudioLog.fold(s, "Project: /root/MeowProjects/meow-app\nBuilt APK: app-debug.apk\n", 5_000)
        assertEquals(listOf("meow-app", "band-site"), s.projects.map { it.name })
        assertEquals("APK built", s.projects.first().lastEvent)
        assertEquals("3 min ago", DevStudioLog.ago(0, 3 * 60_000L))
    }

    @Test fun mediaIsSortedIntoStereoTvAndBookshelf() {
        assertEquals(MediaKind.VIDEO, RoomMediaWatcher.kindOf("com.google.android.youtube"))
        assertEquals(MediaKind.AUDIOBOOK, RoomMediaWatcher.kindOf("com.audible.application"))
        assertEquals(MediaKind.MUSIC, RoomMediaWatcher.kindOf("com.spotify.music"))
        // Audiobooks show the book, not the chapter.
        assertEquals("Dune", RoomMediaWatcher.nowPlaying("com.audible.application", "Chapter 3", "Frank Herbert", "Dune", true)?.title)
        assertNull(RoomMediaWatcher.nowPlaying("com.spotify.music", null, "Artist", null, true))
    }

    @Test fun drivingShowsTheAvatarAndGivesHomeBack() {
        var (show, back) = DriveAvatarPolicy.onDriving(true, AppTheme.DESK, null)
        assertEquals(AppTheme.COMPANION, show); assertEquals(AppTheme.DESK, back)
        DriveAvatarPolicy.onDriving(false, AppTheme.COMPANION, back).let { show = it.first; back = it.second }
        assertEquals(AppTheme.DESK, show); assertNull(back)
        // Already on the avatar or Professional: nothing to switch.
        assertEquals(AppTheme.PROFESSIONAL to null, DriveAvatarPolicy.onDriving(true, AppTheme.PROFESSIONAL, null))
        // The user picked something else mid-drive: keep it.
        assertEquals(AppTheme.PROFESSIONAL to null, DriveAvatarPolicy.onDriving(false, AppTheme.PROFESSIONAL, AppTheme.FLOW))
    }
}

private val demoRoom = RoomState(
    music = RoomMedia("Midnight City", "M83", playing = true, app = "com.spotify.music"),
    video = RoomMedia("How to tune a guitar in 5 minutes", "Fender", playing = true, app = "com.google.android.youtube"),
    audiobook = RoomMedia("Project Hail Mary", "Andy Weir", playing = false, app = "com.audible.application"),
    studio = listOf(StudioLine("band-site", "Build succeeded · 3 min ago"), StudioLine("meow-app", "Checkpoint saved · 2 h ago")),
)
private val demoDesk = DeskState(
    clockHour = 9, clockMinute = 40, night = false, weatherLine = "82° cloudy",
    projectTitle = "Band plays Europe", tallyDone = 0, tallyTotal = 0, projectStatus = "Book the Berlin show",
    pinnedNote = listOf("Studio", "2 projects"),
    monitorLines = listOf(MonitorLine("> band-site", true), MonitorLine("  Build succeeded"), MonitorLine("> meow-app", true), MonitorLine("  Checkpoint saved")),
    sticky = listOf("3:30", "tester", "call"), trayCount = 2, jarNote = "\$412", lampOn = true, room = demoRoom,
)

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.settle(frames: Int = 60) {
    waitForIdle(); repeat(frames) { mainClock.advanceTimeByFrame(); waitForIdle() }
}

private fun shoot(rule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, out androidx.activity.ComponentActivity>, name: String): Bitmap {
    val view = rule.activity.window.decorView
    val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(android.graphics.Canvas(bmp))
    File("build/flow-shots").apply { mkdirs() }.let { File(it, "$name.png") }.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return bmp
}

/** The unfolded inner screen: all three rooms at once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w840dp-h760dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeskApartmentOpenTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun unfoldedShowsAllThreeRoomsAndTheirPanels() {
        var studio = 0; var arcadeAsked = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            DeskWorkspace(demoDesk, emptyList(), thermalStatus = 0, roomActions = DeskRoomActions(
                openStudio = { studio++ }, openArcade = { false }, getArcade = { arcadeAsked++ }, manual = { "# The Meow manual\n\n## Meow's place\n\nThree **rooms**." }))
        }
        rule.settle(37)
        shoot(rule, "apartment-open")
        listOf("desk_stereo", "desk_big_tv", "desk_bookshelf", "desk_build", "desk_bed", "desk_arcade", "desk_litter", "desk_food")
            .forEach { rule.onNodeWithTag(it).assertIsDisplayed() }
        rule.onNodeWithTag("desk_room_right").assertDoesNotExist() // no room buttons when everything is visible

        rule.onNodeWithTag("desk_build").performClick(); rule.settle(25)
        rule.onNodeWithTag("desk_studio_band-site").assertIsDisplayed()
        rule.onNodeWithTag("desk_open_studio").performClick(); rule.settle(37)
        assertEquals(1, studio)

        rule.onNodeWithTag("desk_stereo").performClick(); rule.settle(25)
        rule.onNodeWithTag("desk_now_playing").assertIsDisplayed()
        rule.onNodeWithTag("desk_detail_close").performClick(); rule.settle(37)

        rule.onNodeWithTag("desk_bookshelf").performClick(); rule.settle(25)
        rule.onNodeWithTag("desk_manual").performScrollTo().performClick(); rule.settle(20)
        rule.onNodeWithTag("desk_manual_text").assertExists()
        rule.onNodeWithTag("desk_detail_close").performClick(); rule.settle(37)

        // MAME4droid isn't installed in the test: the arcade explains, and only opens Play on a tap.
        rule.onNodeWithTag("desk_arcade").performClick(); rule.settle(25)
        rule.onNodeWithTag("desk_get_arcade").assertIsDisplayed()
        assertEquals(0, arcadeAsked)
    }
}

/** The folded cover screen: one room at a time, and the camera follows the cat. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeskApartmentCoverTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun roomButtonsPanAndTheCatComesAlong() {
        rule.mainClock.autoAdvance = false
        rule.setContent { DeskHabitat(demoDesk, wanderEveryMs = 0, thermalStatus = 0) }
        rule.settle(31)
        shoot(rule, "apartment-cover-office")
        // Pet Meow on the desk: a startled blink, then a happy face and a reply.
        rule.onNodeWithTag("desk_cat_pet").performClick()
        rule.settle(40)
        rule.onNodeWithTag("desk_bubble").assertIsDisplayed()
        rule.onNode(androidx.compose.ui.test.hasTestTag("desk_bubble") and androidx.compose.ui.test.SemanticsMatcher("pet line") { n ->
            DeskCatBehavior.petLines.any { l -> runCatching { n.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription] }.getOrNull()?.any { d -> d.endsWith(l) } == true } }).assertExists()
        shoot(rule, "apartment-cover-office-petted")
        rule.onNodeWithTag("desk_room_right").performClick()
        rule.settle(420)
        shoot(rule, "apartment-cover-bedroom")
        rule.onNodeWithTag("desk_bed").assertIsDisplayed()
        // Two steps left reach the living room.
        rule.onNodeWithTag("desk_room_left").performClick(); rule.settle(420)
        rule.onNodeWithTag("desk_room_left").performClick(); rule.settle(520)
        rule.onNodeWithTag("desk_stereo").assertIsDisplayed()
        shoot(rule, "apartment-cover-living")
    }
}

/** Every expression and every new pose draws something different. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w700dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeskCatExpressionsTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun facesAndPosesSheet() {
        val faces = DeskCatFace.entries
        val poses = listOf(DeskCatPose.SLEEP, DeskCatPose.EAT, DeskCatPose.DIG, DeskCatPose.PERCH, DeskCatPose.WATCH,
            DeskCatPose.GAME, DeskCatPose.BOB, DeskCatPose.READ, DeskCatPose.SCRATCH)
        var only by mutableStateOf<Int?>(null)
        rule.setContent {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(androidx.compose.ui.graphics.Color(0xFF1D343C))
                val cell = size.width / 5f
                val k = cell / 130f
                (faces.map { it to DeskCatPose.REST } + poses.map { DeskCatBehavior.defaultFace(it) to it }).forEachIndexed { i, (face, pose) ->
                    if (only != null && only != i) return@forEachIndexed
                    withTransform({ translate((i % 5) * cell, (i / 5) * cell); scale(k, k, Offset.Zero) }) {
                        drawCat(10f, 20f, false, 0, 0f, 0f, 0f, false, pose, 1.2f, face)
                    }
                }
            }
        }
        rule.waitForIdle()
        shoot(rule, "desk-cat-expressions")
        // Each face, drawn alone in its cell, differs from the neutral face drawn alone in its cell.
        val neutral = faces.indexOf(DeskCatFace.NEUTRAL)
        fun cellShot(i: Int): Bitmap { rule.runOnIdle { only = i }; rule.waitForIdle()
            val v = rule.activity.window.decorView
            return Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) } }
        val w = rule.activity.window.decorView.width / 5
        fun crop(b: Bitmap, i: Int) = Bitmap.createBitmap(b, (i % 5) * w, (i / 5) * w, w, w)
        val base = crop(cellShot(neutral), neutral)
        faces.forEachIndexed { i, f ->
            if (i == neutral) return@forEachIndexed
            val shot = crop(cellShot(i), i)
            assertFalse("$f must look different from NEUTRAL", sameInk(shot, base))
        }
    }

    private fun sameInk(a: Bitmap, b: Bitmap): Boolean {
        var diff = 0
        for (y in 0 until minOf(a.height, b.height) step 2) for (x in 0 until minOf(a.width, b.width) step 2) if (a.getPixel(x, y) != b.getPixel(x, y)) diff++
        return diff < 20
    }
}
