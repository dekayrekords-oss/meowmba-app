package com.example.meowmba.flow

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import com.example.meowmba.core.attach.Attachments
import com.example.meowmba.core.flow.FlowSettings
import com.example.meowmba.core.flow.FlowSnapshot
import com.example.meowmba.ui.companion.SpeechMode
import com.example.meowmba.ui.flow.MeowFlowHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Owner review 2026-09-23: talks too much, needs a mic switch, needs + for photos and files. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h900dp")
class TalkLessAttachTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun spokenRepliesAreShortTheRestStaysOnScreen() {
        val long = "Your package left the Phoenix facility this morning. It should arrive Thursday. " +
            "UPS lists it as ground shipping with signature required. I added it to Flow."
        assertEquals("Your package left the Phoenix facility this morning. It should arrive Thursday.", SpeechMode.shorten(long))
        assertEquals("Okay.", SpeechMode.shorten("Okay."))
        assertTrue(SpeechMode.shorten("x ".repeat(300)).length <= 241)
        assertEquals(SpeechMode.IMPORTANT, SpeechMode.fromKey(null)) // proactive, but quiet unless it matters
    }

    @Test fun findsTrackingNumbersNotPhoneNumbers() {
        val ups = Attachments.findTracking("UPS Ground  Tracking # 1Z 999 AA1 01 2345 6784".replace(" ", "").let { "UPS $it" })
        assertEquals("UPS", ups.single().carrier)
        val usps = Attachments.findTracking("USPS TRACKING # 9400 1000 0000 0000 0000 00")
        assertEquals(listOf("USPS" to "9400100000000000000000"), usps.map { it.carrier to it.number })
        assertEquals("Amazon", Attachments.findTracking("Amazon Logistics TBA123456789012").single().carrier)
        assertTrue(Attachments.findTracking("Call us at 6025551234 or 18005551234").isEmpty())
        assertTrue(Attachments.findTracking("FedEx tracking 123456789012").single().url.contains("fedex"))
    }

    @Test fun flowHasMicSwitchAndPlusButton() {
        var toggles = 0; var attaches = 0
        rule.setContent {
            MeowFlowHome(FlowSnapshot.EMPTY, FlowSettings(), "Wed 23 Sep", micOn = false,
                onMicToggle = { toggles++ }, onAttach = { attaches++ })
        }
        rule.onNodeWithText("Mic is off. Type, or tap the mic to talk.").assertIsDisplayed()
        rule.onNodeWithTag("flow_mic_toggle").performClick()
        rule.onNodeWithTag("flow_attach").performClick()
        assertEquals(1, toggles); assertEquals(1, attaches)
    }
}
