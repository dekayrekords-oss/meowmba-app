package com.example.meowmba.flow

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.meowmba.core.flow.FlowSnapshot
import com.example.meowmba.ui.desk.DeskFacts
import com.example.meowmba.ui.desk.DeskState
import com.example.meowmba.ui.flow.DeskWorkspace
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Runs the real focused text field and IME path; Robolectric cannot idle this dialog. */
class DeskAskMeowDeviceTest {
    @get:Rule val rule = createComposeRule()

    @Test fun askMeowSubmitsTheTypedQuestionWithoutLeavingDesk() {
        var asked: String? = null
        rule.setContent {
            DeskWorkspace(
                desk = DeskState.from(FlowSnapshot.EMPTY, DeskFacts()),
                tasks = emptyList(),
                paused = true,
                onAsk = { asked = it },
            )
        }

        rule.onNodeWithTag("desk_ask").performClick()
        rule.onNodeWithTag("desk_question").performTextInput("  What is next?  ")
        rule.onNodeWithTag("desk_question_send").assertIsEnabled().performClick()

        rule.runOnIdle { assertEquals("What is next?", asked) }
        rule.onNodeWithTag("desk_habitat").assertIsDisplayed()
        rule.onNodeWithTag("desk_question").assertDoesNotExist()
    }
}
