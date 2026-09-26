package com.example.meowmba

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.ui.companion.DevelopmentStudioPanel
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Does not connect to Ubuntu, call a model, install a tool or install a generated APK. */
class DevelopmentStudio180DeviceTest {
    @get:Rule val rule = createComposeRule()
    @Test fun developerControlsRequireAnExplicitSessionAndVerifiedArtifact() {
        rule.setContent { MeowmbaTheme { Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState())) { DevelopmentStudioPanel() }
        } } }
        rule.onNodeWithText("Development Studio").assertIsDisplayed()
        rule.onNodeWithText("Close session").assertIsNotEnabled()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.getExternalFilesDir(null), "development-studio-180.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        rule.onNodeWithText("Fetch built APK").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Review installation in Android").assertDoesNotExist()
        rule.onNodeWithText("Open development terminal").performScrollTo().performClick()
        rule.onNodeWithText("Start developer session?").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("No session output yet.").assertExists()
    }
}
