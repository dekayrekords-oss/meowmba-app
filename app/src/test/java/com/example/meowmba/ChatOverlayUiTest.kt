package com.example.meowmba

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.example.meowmba.ui.companion.ChatOverlay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class ChatOverlayUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `drafts stay with their project after state restoration`() {
        val project = mutableStateOf("A")
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            ChatOverlay(true, {}, conversationKey = project.value)
        } } }
        compose.onNode(hasSetTextAction()).performTextInput("Draft A")
        compose.runOnIdle { project.value = "B" }
        compose.onNode(hasSetTextAction()).performTextInput("Draft B")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasSetTextAction()).assertTextContains("Draft B")
        compose.runOnIdle { project.value = "A" }
        compose.onNode(hasSetTextAction()).assertTextContains("Draft A")
    }

    @Test fun `draft survives collapse and reopening`() {
        val expanded = mutableStateOf(true)
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            ChatOverlay(expanded.value, onClose = { expanded.value = false })
        } } }
        compose.onNode(hasSetTextAction()).performTextInput("Keep this draft")
        compose.onNodeWithContentDescription("Collapse chat").performClick()
        compose.waitForIdle()
        compose.runOnIdle { expanded.value = true }
        compose.onNode(hasSetTextAction()).assertTextContains("Keep this draft")
    }

    @Test fun `streaming answer is visible before it enters history`() {
        val reply = mutableStateOf("The answer starts here")
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            ChatOverlay(true, {}, liveReply = reply.value)
        } } }
        compose.onNodeWithText("The answer starts here").assertIsDisplayed()
        compose.runOnIdle { reply.value = "The answer starts here and continues" }
        compose.onNodeWithText("The answer starts here and continues").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show full conversation").performClick()
        compose.onNodeWithText("The answer starts here and continues").assertIsDisplayed()
    }

    @Test fun `mic transcript does not overwrite typed draft`() {
        val partial = mutableStateOf<String?>(null)
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            ChatOverlay(true, {}, listening = true, listenPartial = partial.value)
        } } }
        compose.onNode(hasSetTextAction()).performTextInput("My draft")
        compose.runOnIdle { partial.value = "Different spoken words" }
        compose.onNode(hasSetTextAction()).assertTextContains("My draft")
    }
    @Test fun `voice buttons respond and permission feedback remains visible without a transcript`() {
        var micClicks = 0; var callClicks = 0
        val caption = mutableStateOf<String?>("Microphone permission is off. Allow access to speak.")
        val listening = mutableStateOf(false)
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            ChatOverlay(true, {}, listening = listening.value, listenPartial = caption.value,
                onMicClick = { micClicks++ }, onCallClick = { callClicks++ })
        } } }
        compose.onNodeWithText("Microphone permission is off. Allow access to speak.").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Speak").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Start voice call").assertIsDisplayed().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals("microphone", 1, micClicks); org.junit.Assert.assertEquals("phone", 1, callClicks) }
        compose.runOnIdle { listening.value = true; caption.value = null }
        compose.onNodeWithText("Listening… Tap the mic to finish.").assertIsDisplayed()
    }

}
