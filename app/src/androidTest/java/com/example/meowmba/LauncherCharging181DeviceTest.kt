package com.example.meowmba

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.ui.companion.WirelessAvatarSettings
import com.example.meowmba.ui.onboarding.HomeLauncherSetup
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** UI only. Does not change the system launcher, permissions, or charging settings. */
class LauncherCharging181DeviceTest {
    @get:Rule val rule = createComposeRule()
    @Test fun launcherPromptIsOptional() {
        var continued = false
        rule.setContent { MeowmbaTheme { Surface(color = MaterialTheme.colorScheme.background) {
            HomeLauncherSetup { continued = true }
        } } }
        rule.onNodeWithText("Make Meow your home screen?").assertIsDisplayed()
        screenshot("launcher")
        rule.onNodeWithText("Not now").performScrollTo().performClick()
        assertTrue(continued)
    }
    @Test fun chargerSetupExplainsWirelessNotMagnetAndDefaultsOff() {
        rule.setContent { MeowmbaTheme { Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState())) { WirelessAvatarSettings() }
        } } }
        rule.onNodeWithText("Wireless-charger avatar").assertIsDisplayed()
        rule.onAllNodes(isToggleable())[0].assertIsOff()
        screenshot("charging")
    }
    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name-181.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
