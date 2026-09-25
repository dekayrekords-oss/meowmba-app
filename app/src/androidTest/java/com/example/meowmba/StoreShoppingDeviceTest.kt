package com.example.meowmba

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.example.meowmba.ui.companion.ChatOverlay
import com.example.meowmba.ui.companion.ChatUiMessage
import com.example.meowmba.ui.companion.DrawerApp
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StoreShoppingDeviceTest {
    @get:Rule val rule = createComposeRule()

    @Test fun shoppingSourcesAreShownAndOpenOnlyWhenTapped() {
        val url = "https://www.target.com/deals"
        val reply = "I found a deal source for Target.\n\nSearch excerpt (unverified): Members only; coupon required.\n$url"
        var opened: String? = null
        rule.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened = uri }
            }) {
                MeowmbaTheme { ChatOverlay(true, {}, messages = listOf(
                    ChatUiMessage(1, "Meow", reply, true),
                    ChatUiMessage(2, "Meow", "Paws finished: store lookup", true, isAssistantReply = false),
                ), installedApps = emptyList<DrawerApp>()) }
            }
        }
        rule.onNodeWithText(reply).assertIsDisplayed()
        rule.onNodeWithText("Paws finished: store lookup").assertDoesNotExist()
        rule.runOnIdle { assertNull(opened) }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(reply).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val point = layouts.single().getBoundingBox(reply.indexOf(url) + 4).center
        rule.onNodeWithText(reply).performTouchInput { click(point) }
        rule.runOnIdle { assertEquals(url, opened) }
    }

    @Test fun aTypedStoreQuestionWaitsForSend() {
        var sent: String? = null
        rule.setContent { MeowmbaTheme { ChatOverlay(true, {}, onSend = { sent = it }, installedApps = emptyList<DrawerApp>()) } }
        rule.onNode(hasSetTextAction()).performTextInput("what store am I in?")
        rule.runOnIdle { assertNull(sent) }
        rule.onNodeWithContentDescription("Send").performClick()
        rule.runOnIdle { assertEquals("what store am I in?", sent) }
    }
}
