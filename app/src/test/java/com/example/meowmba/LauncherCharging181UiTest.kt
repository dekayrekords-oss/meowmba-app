package com.example.meowmba

import android.app.role.RoleManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.core.setup.WirelessAvatarMonitor
import com.example.meowmba.ui.companion.WirelessAvatarSettings
import com.example.meowmba.ui.onboarding.HomeLauncherSetup
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherCharging181UiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun decliningLauncherContinuesWithoutOpeningAndroidSettings() {
        var continued = false
        rule.setContent { MeowmbaTheme { HomeLauncherSetup { continued = true } } }
        rule.onNodeWithText("Make Meow your home screen?").assertExists()
        rule.onNodeWithText("Not now").performScrollTo().performClick()
        assertTrue(continued)
        assertNull(shadowOf(rule.activity).nextStartedActivity)
    }
    @Test fun launcherSetupRequiresSystemChoiceAndDoesNotPretendItSucceeded() {
        val role = rule.activity.getSystemService(RoleManager::class.java)
        shadowOf(role).addAvailableRole(RoleManager.ROLE_HOME)
        var continued = false
        rule.setContent { MeowmbaTheme { HomeLauncherSetup { continued = true } } }
        rule.onNodeWithText("Set up home screen").performScrollTo().performClick()
        assertEquals("android.app.role.action.REQUEST_ROLE", shadowOf(rule.activity).nextStartedActivity.action)
        assertFalse(continued)
        rule.onNodeWithText("Not now").assertExists()
    }
    @Test fun chargerAndBackgroundLaunchAreSeparatePersistedOptIns() {
        val prefs = rule.activity.getSharedPreferences(WirelessAvatarMonitor.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        rule.setContent { MeowmbaTheme { Column(Modifier.verticalScroll(rememberScrollState())) { WirelessAvatarSettings() } } }
        rule.onAllNodes(isToggleable())[0].assertIsOff().performClick()
        assertTrue(prefs.getBoolean(WirelessAvatarMonitor.ENABLED, false))
        rule.onAllNodes(isToggleable())[1].performScrollTo().assertIsOff().performClick()
        assertTrue(prefs.getBoolean(WirelessAvatarMonitor.BACKGROUND, false))
        rule.onNodeWithText("Allow display over other apps in Android").assertExists()
        assertNull(shadowOf(rule.activity).nextStartedActivity)
    }
}
