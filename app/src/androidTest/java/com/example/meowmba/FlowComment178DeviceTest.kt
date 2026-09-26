package com.example.meowmba

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.meowmba.core.flow.*
import com.example.meowmba.ui.flow.*
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Fixture-only UI checks: no user calendar, account, messages, or permission grants. */
class FlowComment178DeviceTest {
    @get:Rule val rule = createComposeRule()
    private fun event() = Intention("cal-event-7-1790438400000", "Dentist", FlowKind.OTHER, "calendar", 1,
        facts = mapOf("calendar_event_id" to "7", "calendar_title" to "Dentist",
            "calendar_begin" to "1790438400000", "calendar_end" to "1790442000000"))
    @Test fun commentWaitsForExplicitCancellationSubmission() {
        var sent: String? = null
        rule.setContent { MeowmbaTheme { FlowCommentSheet(event(), false, null, { sent = it }, {}, {}) } }
        rule.onNodeWithTag("flow_comment_input").performTextInput("this event is canceled")
        rule.runOnIdle { assertNull(sent) }
        rule.onNodeWithText("Cancel this occurrence").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("this event is canceled", sent) }
    }
    @Test fun replyLaterAndCommentHaveSeparateTargets() {
        val item = FlowSamples.deepWork.main!!.copy(primary = FlowAction("reply", "Reply"), secondary = FlowAction("later", "Later"))
        var primary = false; var later = false; var comment: String? = null
        rule.setContent { MeowmbaTheme { MeowFlowHome(FlowSamples.deepWork.copy(main = item, more = emptyList()),
            FlowSettings(), "Friday", reduceMotion = true, onPrimary = { primary = true }, onLater = { later = true },
            onComment = { comment = it.intentionId }) } }
        rule.onNodeWithText("Reply").assertIsDisplayed()
        rule.onNodeWithText("Later").assertIsDisplayed()
        rule.onNodeWithText("Comment").performClick()
        rule.runOnIdle { assertEquals(item.intentionId, comment); assertFalse(primary); assertFalse(later) }
    }
    @Test fun guideExplainsCalendarAndGmailWithoutGrantingAnything() {
        var closed = false
        rule.setContent { MeowmbaTheme { AccessSetupGuide({ closed = true }) } }
        rule.onNodeWithText("Connect apps & tools").assertIsDisplayed()
        rule.onNodeWithText("Allow calendar access").performScrollTo().assertExists()
        rule.onNodeWithText("2 · Gmail and email").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("4 · On-screen app tools (optional)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Done").performScrollTo().performClick()
        rule.runOnIdle { assertTrue(closed) }
    }
    @Test fun workingCommentCannotBeSubmittedTwice() {
        rule.setContent { MeowmbaTheme { FlowCommentSheet(event(), true, "Working", { error("Duplicate submission") }, {}, {}) } }
        rule.onNodeWithTag("flow_comment_input").assertIsNotEnabled()
        rule.onNodeWithTag("flow_comment_submit").performScrollTo().assertIsNotEnabled()
    }
}
