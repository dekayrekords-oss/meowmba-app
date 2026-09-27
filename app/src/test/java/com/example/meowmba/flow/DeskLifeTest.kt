package com.example.meowmba.flow

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.meowmba.ui.desk.*
import com.example.meowmba.ui.desk.DeskAmbientPainter.boxFront
import com.example.meowmba.ui.desk.DeskAmbientPainter.drawAmbient
import com.example.meowmba.ui.desk.DeskAmbientPainter.pen
import com.example.meowmba.ui.desk.DeskAmbientPainter.toyMouse
import com.example.meowmba.ui.desk.DeskAmbientPainter.yarn
import com.example.meowmba.ui.desk.DeskPainter.drawCat
import com.example.meowmba.ui.desk.DeskPainter.drawObjects
import com.example.meowmba.ui.desk.DeskPainter.drawRoom
import com.example.meowmba.ui.flow.DeskWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

/**
 * Owner request 2026-09-27: landscape fills the screen with the menu on top, a room-size slider,
 * a living room that moves (clouds and weather by the real time of day, blinking cursor, flapping
 * sticky note, lamp on at night, a game on the old TV, the rare plane and UFO), and a cat with
 * more personality (research, reading, games, mice, zoomies, yarn, and ten more habits).
 */
private fun desk(hour: Int, night: Boolean, weather: String?, lamp: Boolean = false) = DeskState(
    clockHour = hour, clockMinute = 0, night = night, weatherLine = weather,
    projectTitle = "Band plays Europe", tallyDone = 0, tallyTotal = 0, projectStatus = "Book the Berlin show",
    pinnedNote = emptyList(), monitorLines = listOf(MonitorLine("> band-site", true), MonitorLine("  Build succeeded")),
    sticky = listOf("3:30", "tester", "call"), trayCount = 1, jarNote = null, lampOn = lamp,
    room = RoomState(music = RoomMedia("Midnight City", "M83", true, "com.spotify.music")),
)

class DeskSkyTest {
    @Test fun weatherLineMapsToWeather() {
        assertEquals(DeskWeather.CLEAR, DeskSky.weather("64° clear"))
        assertEquals(DeskWeather.PARTLY, DeskSky.weather("70° partly cloudy"))
        assertEquals(DeskWeather.CLOUDY, DeskSky.weather("79° cloudy"))
        assertEquals(DeskWeather.RAIN, DeskSky.weather("58° light rain"))
        assertEquals(DeskWeather.STORM, DeskSky.weather("thunderstorm"))
        assertEquals(DeskWeather.SNOW, DeskSky.weather("30° snow"))
        assertEquals(DeskWeather.FOG, DeskSky.weather("fog"))
        assertEquals(DeskWeather.CLEAR, DeskSky.weather(null))
    }

    @Test fun skyFollowsTheTimeOfDay() {
        val morning = DeskSky.colors(desk(7, false, "clear"))
        val noon = DeskSky.colors(desk(12, false, "clear"))
        val sunset = DeskSky.colors(desk(19, false, "clear"))
        val night = DeskSky.colors(desk(23, true, "clear"))
        assertEquals(4, setOf(morning, noon, sunset, night).size)
        assertNotEquals(noon, DeskSky.colors(desk(12, false, "rain")))              // rain dulls the sky
        // The sun rises low, is high at noon and sets low; the moon crosses the night sky.
        assertTrue(DeskSky.bodyIn(desk(12, false, null)).y < DeskSky.bodyIn(desk(7, false, null)).y)
        assertTrue(DeskSky.bodyIn(desk(18, false, null)).x > DeskSky.bodyIn(desk(8, false, null)).x)
        assertNull(DeskSky.sunbeamX(desk(23, true, "clear")))
        assertNull(DeskSky.sunbeamX(desk(12, false, "rain")))
        assertTrue(DeskSky.sunbeamX(desk(9, false, "clear"))!! > DeskSky.sunbeamX(desk(16, false, "clear"))!!)
    }

    @Test fun landscapeFillsEdgeToEdgeWithTheBookshelfAtTheWall() {
        for (wing in listOf(0f, 60f, 93f, 140f, 180f)) {
            val l = DeskLayout.fitting(wing)
            assertEquals(l.left, DeskObject.BOOKSHELF.frame(l).left)
            assertTrue(DeskObject.BED.frame(l).let { it.left + it.width } >= l.right - 60f)
        }
        assertEquals(1596f, DeskLayout.fitting(500f).width)                 // capped at the full landscape rooms
    }
}

class DeskTricksTest {
    @Test fun everyHabitShowsUp() {
        val seen = mutableSetOf<DeskCatTrick>()
        val random = Random(927)
        var sunbeamNap = false
        repeat(3_000) { i ->
            val room = DeskRoom.entries[i % 3]
            val ctx = DeskIdleContext(night = i % 2 == 0, sunbeamX = if (i % 2 == 0) null else 600f)
            val act = DeskCatBehavior.apartmentIdle(random, DeskCatPose.REST, room, true, true, true, true, 0f, DeskLayout.STANDARD, ctx)
            seen += act.trick
            if (room == DeskRoom.BEDROOM && act.pose == DeskCatPose.SLEEP && act.spot.y == DeskCatBehavior.FLOOR) sunbeamNap = true
            assertFalse("no moth by day", act.trick == DeskCatTrick.MOTH && !ctx.night)
        }
        assertEquals(DeskCatTrick.entries.toSet(), seen)
        assertTrue("naps in the sunbeam", sunbeamNap)
    }

    @Test fun scaleSettingIsClamped() {
        assertEquals(100, DeskScale.of(null))
        assertEquals(60, DeskScale.of("10"))
        assertEquals(200, DeskScale.of("900"))
        assertEquals(140, DeskScale.of("140"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w840dp-h760dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeskAmbientRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun bitmap(): Bitmap {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
    }

    private fun save(b: Bitmap, name: String) =
        File("build/flow-shots").apply { mkdirs() }.let { File(it, "$name.png") }.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }

    @Test fun theRoomMovesOnItsOwn() {
        val ambient = DeskAmbient()
        var state by mutableStateOf(desk(10, false, "light rain"))
        var game by mutableStateOf(true)
        rule.setContent {
            val m = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / 1236f
                withTransform({ scale(k, k, Offset.Zero); translate(412f, -40f) }) {
                    drawRoom(); drawObjects(state, m); drawAmbient(state, ambient, m, DeskLayout.STANDARD, game)
                }
            }
        }
        // Morning rain: a UFO over the city, a bird on the sill, the cat "researching", the note lifting, the lamp swinging.
        rule.runOnIdle {
            ambient.t = 100f
            ambient.flyer = DeskFlyer(ufo = true, bedroom = true, start = 95f, leftToRight = true, lane = .2f)
            ambient.birdUntil = 200f; ambient.monitor = DeskMonitor.BROWSER; ambient.monitorSince = 97f
            ambient.flapAt = 99.2f; ambient.lampPushAt = 99.7f; ambient.toyPushAt = 99.8f
        }
        val a = bitmap(); save(a, "desk-ambient-rain-ufo")
        rule.runOnIdle { ambient.t = 100.6f }
        val b = bitmap()
        assertFalse("clouds, rain, UFO, note and lamp move with time", a.sameAs(b))
        // Night: a plane crosses the office window, the lamp is on with a moth, the TV attract screen.
        rule.runOnIdle {
            state = desk(22, true, "64° clear", lamp = true); game = false
            ambient.t = 300f; ambient.flyer = DeskFlyer(ufo = false, bedroom = false, start = 295f, leftToRight = false, lane = .3f)
            ambient.monitor = DeskMonitor.KEYS; ambient.monitorSince = 296f
        }
        save(bitmap(), "desk-ambient-night-plane")
        rule.runOnIdle { state = desk(18, false, "70° partly cloudy"); ambient.flyer = null; ambient.monitor = DeskMonitor.NORMAL }
        save(bitmap(), "desk-ambient-sunset")
    }

    @Test fun newPosesAndPropsRender() {
        var pose by mutableStateOf(DeskCatPose.BAT)
        var phase by mutableFloatStateOf(0f)
        rule.setContent {
            val m = rememberTextMeasurer()
            val s = remember { desk(12, false, "clear") }
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / 412f
                withTransform({ scale(k, k, Offset.Zero); translate(0f, -390f) }) {
                    drawRoom(); drawObjects(s, m)
                    pen(212f, 593f, 0f)
                    val spot = if (pose == DeskCatPose.HIDE) DeskCatBehavior.box else DeskCatSpot(60f, DeskCatBehavior.FLOOR)
                    drawCat(spot.x, spot.y, false, 0, 0f, 0f, 0f, pose == DeskCatPose.HIDE, pose, phase)
                    if (pose == DeskCatPose.HIDE) boxFront()
                    yarn(230f, 190f, phase * 40f); toyMouse(40f, false)
                }
            }
        }
        for (p in listOf(DeskCatPose.BAT, DeskCatPose.KNEAD, DeskCatPose.ROLL, DeskCatPose.HIDE)) {
            rule.runOnIdle { pose = p; phase = 0f }
            val first = bitmap()
            rule.runOnIdle { phase = (Math.PI / 2).toFloat() }
            val next = bitmap()
            assertFalse("$p moves", first.sameAs(next))
            save(next, "desk-cat-${p.name.lowercase()}")
        }
    }
}

/** The folded cover screen turned sideways: menu on top, rooms from edge to edge. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w701dp-h443dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeskLandscapeFillTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun roomsFillTheScreenUnderATopMenu() {
        rule.mainClock.autoAdvance = false
        rule.setContent { DeskWorkspace(desk(9, false, "64° clear"), emptyList(), thermalStatus = 0, onAsk = {}) }
        rule.waitForIdle(); repeat(30) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
        val view = rule.activity.window.decorView
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
            .let { b -> File("build/flow-shots").apply { mkdirs() }.let { File(it, "desk-landscape-fill.png") }.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        rule.onNodeWithTag("desk_nav_top").assertIsDisplayed()
        val root = rule.onNodeWithTag("desk_habitat").getBoundsInRoot()
        val shelf = rule.onNodeWithTag("desk_bookshelf").getBoundsInRoot()
        val bed = rule.onNodeWithTag("desk_bed").getBoundsInRoot()
        assertTrue("bookshelf touches the left edge: ${shelf.left}", shelf.left - root.left <= 2.dp)
        assertTrue("bed reaches the right edge: ${bed.right} of ${root.right}", root.right - bed.right <= 4.dp)
    }
}

/** Zoomed in with the room-size slider: bigger rooms, room buttons, the view follows the cat. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w840dp-h760dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeskScaleTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun biggerRoomsPanWithRoomButtons() {
        var scale by mutableIntStateOf(100)
        rule.mainClock.autoAdvance = false
        rule.setContent { DeskWorkspace(desk(9, false, "64° clear"), emptyList(), thermalStatus = 0, deskScale = scale, onDeskScale = { scale = it }) }
        rule.waitForIdle(); repeat(20) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
        rule.onNodeWithTag("desk_room_right").assertDoesNotExist()
        val small = rule.onNodeWithTag("desk_build").getBoundsInRoot()
        rule.runOnIdle { scale = 160 }
        rule.waitForIdle(); repeat(20) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
        val big = rule.onNodeWithTag("desk_build").getBoundsInRoot()
        assertTrue((big.right - big.left) > (small.right - small.left) * 1.5f)
        rule.onNodeWithTag("desk_room_right").assertIsDisplayed()
        val view = rule.activity.window.decorView
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
            .let { b -> File("build/flow-shots").apply { mkdirs() }.let { File(it, "desk-scale-160.png") }.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
}
