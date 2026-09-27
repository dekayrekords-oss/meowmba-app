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
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.meowmba.ui.desk.*
import com.example.meowmba.ui.desk.DeskPainter.drawCat
import com.example.meowmba.ui.flow.DeskWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/** Owner request 2026-09-26: a more alive Desk cat, and a setting for its room trips. */
class DeskRoomTripsTest {
    @Test fun settingKeysRoundTripWithASafeDefault() {
        DeskRoomTrips.entries.forEach { assertEquals(it, DeskRoomTrips.of(it.key)) }
        assertEquals(DeskRoomTrips.SOMETIMES, DeskRoomTrips.of(null))
        assertEquals(DeskRoomTrips.SOMETIMES, DeskRoomTrips.of("bogus"))
        assertTrue(DeskRoomTrips.entries.zipWithNext().all { (a, b) -> a.chance < b.chance })
    }

    @Test fun neverStaysInTheRoomAndOftenLeavesMoreThanRarely() {
        fun leaves(chance: Float): Int {
            val random = Random(26)
            return (1..400).count {
                DeskRoom.of(DeskCatBehavior.apartmentIdle(random, DeskCatPose.REST, DeskRoom.BEDROOM, false, false, false, false, chance).spot.x) != DeskRoom.BEDROOM
            }
        }
        assertEquals(0, leaves(DeskRoomTrips.NEVER.chance))
        assertTrue(leaves(DeskRoomTrips.OFTEN.chance) > leaves(DeskRoomTrips.RARELY.chance) * 2)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h600dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeskCatMotionRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun shot(): Bitmap {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        return Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
    }

    @Test fun walkCycleBreathingSitAndLoafAllMoveTheDrawing() {
        var pose by mutableStateOf(DeskCatPose.WALK)
        var gait by mutableFloatStateOf(0f)
        var breath by mutableFloatStateOf(0f)
        var squash by mutableFloatStateOf(0f)
        rule.setContent {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(androidx.compose.ui.graphics.Color(0xFF1D343C))
                withTransform({ scale(size.width / 200f, size.width / 200f, Offset.Zero) }) {
                    drawCat(40f, 20f, pose == DeskCatPose.WALK, 0, 0f, 0f, 0f, false, pose, 0f,
                        gait = if (pose == DeskCatPose.WALK) gait else -1f, breath = breath, squash = squash)
                }
            }
        }
        val out = File("build/flow-shots").apply { mkdirs() }
        val a = shot(); rule.runOnIdle { gait = 1.6f }; val b = shot()
        assertFalse("legs must move through the walk cycle", a.sameAs(b))
        File(out, "desk-cat-walk.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.runOnIdle { pose = DeskCatPose.REST; breath = -1f }; val c = shot()
        rule.runOnIdle { breath = 1f }; val d = shot()
        assertFalse("breathing must move the chest", c.sameAs(d))
        rule.runOnIdle { breath = 0f; squash = .8f }; val e = shot()
        assertFalse("a crouch must squash the cat", e.sameAs(c))
        for (p in listOf(DeskCatPose.SIT, DeskCatPose.LOAF)) {
            rule.runOnIdle { pose = p; squash = 0f }
            val s = shot()
            assertFalse("$p must differ from standing", s.sameAs(c))
            File(out, "desk-cat-${p.name.lowercase()}.png").outputStream().use { s.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeskRoutineSheetTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun longPressTheCatToChooseHowOftenItVisitsRooms() {
        var chosen by mutableStateOf(DeskRoomTrips.SOMETIMES)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            DeskWorkspace(DeskState.from(com.example.meowmba.core.flow.FlowSnapshot.EMPTY, DeskFacts()), emptyList(), thermalStatus = 0,
                roomTrips = chosen, onRoomTrips = { chosen = it })
        }
        rule.waitForIdle(); repeat(30) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
        rule.onNodeWithTag("desk_cat_pet").performTouchInput { longClick() }
        rule.waitForIdle(); repeat(40) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
        rule.onNodeWithTag("desk_routine").assertIsDisplayed()
        rule.onNodeWithTag("desk_trips_often").performClick()
        rule.waitForIdle()
        assertEquals(DeskRoomTrips.OFTEN, chosen)
    }
}
