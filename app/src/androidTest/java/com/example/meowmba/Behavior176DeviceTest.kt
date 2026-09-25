package com.example.meowmba

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.meowmba.ui.companion.*
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Behavior176DeviceTest {
    @get:Rule val rule = createComposeRule()
    @Test fun swipeDownMenuHasDirectAvatarLaunch() {
        var selected: String? = null
        rule.setContent { MeowmbaTheme { BackgroundTiles(true, {}, { selected = it }) } }
        rule.onNodeWithText("Show avatar").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals("Show avatar", selected) }
    }
    @Test fun appCompletionEditsDraftAndWaitsForSend() {
        var sent: String? = null
        rule.setContent { MeowmbaTheme { ChatOverlay(true, {}, onSend = { sent = it },
            installedApps = listOf(DrawerApp("Spotify", "com.spotify.music", "spotify music"))) } }
        rule.onNode(hasSetTextAction()).performTextInput("open spot")
        rule.onNodeWithText("open Spotify", useUnmergedTree = true).performClick()
        rule.runOnIdle { assertNull(sent) }
        rule.onNode(hasSetTextAction()).assertTextEquals("open Spotify")
        rule.onNodeWithContentDescription("Send").performClick()
        rule.runOnIdle { assertEquals("open Spotify", sent) }
    }
}
