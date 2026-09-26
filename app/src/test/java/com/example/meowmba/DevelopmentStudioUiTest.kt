package com.example.meowmba

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.ui.companion.DevelopmentStudioPanel
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
}
