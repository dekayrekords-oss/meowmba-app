package com.example.meowmba.flow

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.meowmba.core.flow.EveningLook
import com.example.meowmba.core.flow.FlowItem
import com.example.meowmba.core.flow.FlowSettings
import com.example.meowmba.core.flow.FlowSnapshot
import com.example.meowmba.ui.flow.MeowFlowHome
import com.example.meowmba.ui.flow.FlowSamples
import com.example.meowmba.ui.flow.FlowAdjustSheet
import com.example.meowmba.core.flow.Urgency
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders Meow Flow's approved moments at the reference size (412 × 960 dp) and writes
 * PNGs to app/build/flow-shots/ for side-by-side comparison with docs/design/screens/.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h960dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MeowFlowHomeUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun show(snapshot: FlowSnapshot, settings: FlowSettings = FlowSettings(), date: String = "Tue 22 Sept · 9:40 AM",
                     remaining: Int? = null, onPrimary: (FlowItem) -> Unit = {}, onAdjust: () -> Unit = {}) {
        rule.setContent {
            MeowFlowHome(snapshot, settings, date, distanceRemainingMeters = remaining, onPrimary = onPrimary, onAdjust = onAdjust)
        }
        rule.mainClock.advanceTimeBy(4_000)
        rule.waitForIdle()
    }

    /** Draws the decor view into a bitmap (Robolectric doesn't support captureToImage's PixelCopy wait). */
    private fun capture(): Bitmap {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        return bmp
    }

    private fun shoot(name: String) {
        val bmp = capture()
        val dir = File("build/flow-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun deepWork() {
        var pressed: String? = null
        show(FlowSamples.deepWork, onPrimary = { pressed = it.intentionId })
        rule.onNodeWithText("The idle heat fix is ready to hand off.").assertIsDisplayed()
        rule.onNodeWithText("ALSO FOR YOU").assertIsDisplayed()
        rule.onNodeWithTag("flow_item_note").assertIsDisplayed()
        shoot("flow-02-deep-work")
        rule.onNodeWithText("Hand off to Codex").performClick()
        assertEquals("handoff", pressed)
    }

    @Test fun interruptBandPushesContent() {
        show(FlowSamples.drivingPastStore, date = "Tue 22 Sept · 4:58 PM", remaining = 145)
        rule.onNodeWithTag("flow_band").assertIsDisplayed()
        rule.onNodeWithText("STOP HERE? · WORTH IT").assertIsDisplayed()
        rule.onNodeWithText("Add the stop").assertIsDisplayed()
        rule.onNodeWithText("475 ft").assertIsDisplayed()
        shoot("flow-05-interrupt-band")
    }

    @Test fun heldInterruptShowsReason() {
        show(FlowSamples.heldInterrupt, date = "Tue 22 Sept · 4:58 PM")
        rule.onNodeWithText("HELD: THIS IS \"WORTH IT\", AND YOU ONLY ALLOW \"URGENT\" TO INTERRUPT.").assertIsDisplayed()
        shoot("flow-06-interrupt-held")
    }

    @Test fun eveningUsesNightPaperAndLeisureHeader() {
        show(FlowSamples.workWraps, date = "Tue 22 Sept · 6:40 PM")
        rule.onNodeWithText("IF YOU FEEL LIKE IT").assertIsDisplayed()
        shoot("flow-09-work-wraps-night")
        val bmp = capture()
        // Sample the paper margin, not the center where new card actions can move text.
        val p = bmp.getPixel(bmp.width - 2, bmp.height / 2)
        assertTrue("night paper should be dark", (p shr 16 and 0xFF) < 60)
    }

    @Test fun themesAndAdjust() {
        var adjusted = false
        show(FlowSamples.deepWork, FlowSettings(theme = "linen"), onAdjust = { adjusted = true })
        shoot("flow-13-theme-linen")
        rule.onNodeWithTag("flow_adjust").performClick()
        assertTrue(adjusted)
    }

    @Test fun graphiteAndForcedNight() {
        show(FlowSamples.deepWork, FlowSettings(theme = "graphite"))
        shoot("flow-14-theme-graphite")
    }

    @Test fun paperNightBandUsesDarkText() {
        show(FlowSamples.drivingPastStore, FlowSettings(eveningLook = EveningLook.NIGHT), date = "Tue 22 Sept · 4:58 PM", remaining = 145)
        shoot("flow-17-interrupt-paper-night")
    }

    @Test fun adjustSheetReportsChangesAndHasNoServerOption() {
        var latest = FlowSettings()
        rule.setContent {
            FlowAdjustSheet(latest, "weekdays, about 8:30 to 6:00, from 3 weeks of your days", night = false,
                onChange = { latest = it }, onDone = {})
        }
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        shoot("flow-11-settings-top")
        assertEquals(0, rule.onAllNodesWithText("Runs on").fetchSemanticsNodes().size)
        rule.onNodeWithTag("flow_theme_stone").performClick()
        assertEquals("stone", latest.theme)
        rule.onNodeWithText("Urgent").performClick()
        assertEquals(Urgency.URGENT, latest.minUrgency)
        rule.onNodeWithText("30m").performClick()
        assertEquals(30, latest.minGapMinutes)
    }
}
