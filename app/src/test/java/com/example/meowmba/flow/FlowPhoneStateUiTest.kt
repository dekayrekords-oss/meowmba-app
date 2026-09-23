package com.example.meowmba.flow

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.meowmba.core.flow.Activity
import com.example.meowmba.core.flow.FlowPolicy
import com.example.meowmba.core.flow.FlowSettings
import com.example.meowmba.core.flow.InterruptLedger
import com.example.meowmba.core.flow.Signal
import com.example.meowmba.core.flow.Situation
import com.example.meowmba.core.flow.WorkStateDetector
import com.example.meowmba.core.flow.WorkContext
import com.example.meowmba.core.flow.decide.RulesAssessor
import com.example.meowmba.core.flow.sources.AppIntentions
import com.example.meowmba.ui.flow.MeowFlowHome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** What the owner's phone shows in Flow with today's real data (no calendar events, no special access yet). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h960dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlowPhoneStateUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun morningWithAppDataIsNotEmptyAndHasDatelineNavigation() {
        val now = Fx.at(8, 25)
        val intentions = AppIntentions.build(now, Fx.zone,
            listOf(AppIntentions.Reminder(1, "Stretch", "09:00", true, true, 0)),
            listOf(AppIntentions.Project(7, "Meowmba", "App", now - 3_600_000L)), emptyList(),
            AppIntentions.Access(notifications = false, usage = false, calendar = true))
        val work = WorkStateDetector(Fx.zone).estimate(now, WorkContext())
        val situation = Situation(now, Activity.STILL, work = work,
            signals = listOf(Signal("wx", "82° cloudy"), Signal("cal", "Free today")))
        val assessments = runBlocking { RulesAssessor().assess(situation, intentions) }
        val snap = FlowPolicy(Fx.zone).decide(situation, intentions, assessments, FlowSettings(), InterruptLedger()).snapshot
        assertTrue("morning must not be evening", !snap.evening)
        var menu = false; var desk = false
        rule.setContent { MeowFlowHome(snap, FlowSettings(), "Tue 22 Sep · 8:25 AM", onMenu = { menu = true }, onDesk = { desk = true }) }
        rule.mainClock.advanceTimeBy(4_000); rule.waitForIdle()
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(android.graphics.Canvas(bmp))
        File("build/flow-shots").apply { mkdirs() }.resolve("flow-phone-morning.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithTag("flow_main").assertIsDisplayed()
        rule.onNodeWithTag("flow_menu").performClick(); assertTrue(menu)
        rule.onNodeWithTag("flow_desk").performClick(); assertTrue(desk)
    }

    @Test fun longSignalsWrapAndMicShowsListening() {
        val snap = com.example.meowmba.core.flow.FlowSnapshot.EMPTY.copy(at = Fx.at(13, 25),
            signals = listOf(Signal("cal", "Free today"), Signal("wx", "82° cloudy"), Signal("move", "Walking"),
                Signal("msg", "Dana messaged in WhatsApp"), Signal("app", "Working in Android Studio")))
        var taps = 0
        rule.setContent { MeowFlowHome(snap, FlowSettings(), "Wed 23 Sep · 1:25 PM", onMic = { taps++ },
            onMenu = {}, onDesk = {}, voice = com.example.meowmba.ui.flow.FlowVoice.Listening("remind me to call Dana")) }
        rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle()
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(android.graphics.Canvas(bmp))
        File("build/flow-shots").apply { mkdirs() }.resolve("flow-signals-wrap-listening.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Every signal is shown whole (no "…"), on as many rows as it needs.
        rule.onNodeWithText("WORKING IN ANDROID STUDIO").assertIsDisplayed()
        rule.onNodeWithText("DANA MESSAGED IN WHATSAPP").assertIsDisplayed()
        rule.onNodeWithText("“remind me to call Dana”").assertIsDisplayed()
        rule.onNodeWithTag("flow_mic").performClick()
        assertTrue(taps == 1)
    }
}
