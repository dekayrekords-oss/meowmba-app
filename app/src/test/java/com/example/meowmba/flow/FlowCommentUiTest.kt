package com.example.meowmba.flow

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.meowmba.core.flow.*
import com.example.meowmba.core.flow.sources.AppIntentions
import com.example.meowmba.ui.flow.*
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h960dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlowCommentUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun event() = AppIntentions.build(Fx.at(9), Fx.zone, emptyList(), emptyList(),
        listOf(CalendarItem("Dentist", Fx.at(12), Fx.at(13), false, 7)),
        AppIntentions.Access(true, true, true)).single()

    @Test fun cancellationTypingDoesNotRunUntilExplicitSubmit() {
        var submitted: String? = null
        rule.setContent { MeowmbaTheme { FlowCommentSheet(event(), false, null, { submitted = it }, {}, {}) } }
        rule.onNodeWithTag("flow_comment_input").performTextInput("this event is canceled")
        rule.runOnIdle { assertNull(submitted) }
        rule.onNodeWithText("Cancel this occurrence").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("this event is canceled", submitted) }
    }
    @Test fun plainCommentIsNotLabeledAsCalendarChange() {
        var submitted: String? = null
        rule.setContent { MeowmbaTheme { FlowCommentSheet(event(), false, null, { submitted = it }, {}, {}) } }
        rule.onNodeWithTag("flow_comment_input").performTextInput("Bring my notes")
        rule.onNodeWithText("Save comment").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("Bring my notes", submitted) }
        rule.onNodeWithText("Cancel this occurrence").assertDoesNotExist()
    }
    @Test fun nonCalendarCommentCannotCancelAndBusyCannotDoubleSubmit() {
        rule.setContent { MeowmbaTheme { FlowCommentSheet(event().copy(id = "msg-1"), false, null, { error("must not submit") }, {}, {}) } }
        rule.onNodeWithTag("flow_comment_input").performTextInput("cancel it")
        rule.onNodeWithTag("flow_comment_submit").assertIsNotEnabled()
    }
    @Test fun guideDistinguishesReadAccessFromWriteAndRechecksIt() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.WRITE_CALENDAR)
        rule.setContent { MeowmbaTheme { AccessSetupGuide({}) } }
        rule.onNodeWithText("Read only — changes are not enabled").assertIsDisplayed()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.WRITE_CALENDAR)
        rule.onNodeWithText("Check access again").performScrollTo().performClick()
        rule.onNodeWithTag("calendar_access_status").performScrollTo().assertTextEquals("Ready to read and change calendars")
    }
}
