package com.example.meowmba

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.ui.companion.DevelopmentStudioPanel
import com.example.meowmba.ui.companion.DevStudioSession
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DevelopmentStudioUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun openingStudioDoesNotConnectDownloadOrInstall() {
        rule.setContent { MeowmbaTheme { Column(Modifier.verticalScroll(rememberScrollState())) { DevelopmentStudioPanel() } } }
        rule.onNodeWithText("No session output yet.").assertExists()
        rule.onNodeWithText("Close session").assertIsNotEnabled()
        rule.onNodeWithText("Fetch built APK").assertIsNotEnabled()
        rule.onNodeWithText("Review installation in Android").assertDoesNotExist()
        rule.onNodeWithText("Open development terminal").performScrollTo().performClick()
        rule.onNodeWithText("Start developer session?").assertExists()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("No session output yet.").assertExists()
    }

    @Test fun leavingThePageKeepsTheSession() {
        DevStudioSession.output = "Project: /root/MeowProjects/demo\nAction: "
        var shown by mutableStateOf(true)
        rule.setContent { MeowmbaTheme { Column(Modifier.verticalScroll(rememberScrollState())) { if (shown) DevelopmentStudioPanel() } } }
        rule.onNodeWithText("Project: /root/MeowProjects/demo", substring = true).assertExists()
        shown = false // Back / navigation away
        rule.waitForIdle()
        shown = true
        rule.onNodeWithText("Project: /root/MeowProjects/demo", substring = true).assertExists()
        rule.onNodeWithText("Your session keeps running if you leave this page", substring = true).assertExists()
        DevStudioSession.output = ""
    }

    @Test fun rawTimeoutsAreExplained() {
        val message = DevStudioSession.friendly(java.net.SocketTimeoutException("timeout"))
        assert(message.startsWith("Ubuntu didn't answer in time")) { message }
        assert(DevStudioSession.friendly(IllegalStateException("timeout")).startsWith("Ubuntu didn't answer"))
    }
}
