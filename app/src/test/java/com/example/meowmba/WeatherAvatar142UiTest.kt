package com.example.meowmba

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.ui.companion.*
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherAvatar142UiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun weatherNeverShowsDemoReadingAndLocationControlsAreExplicit() {
        var chosen: String? = null
        rule.setContent { MeowmbaTheme {
            WeatherScreen(null, false, {}, null, {}, {}, {}, {}, onLocationChange = { chosen = it })
        } }
        rule.onNodeWithText("tap to preview conditions").assertDoesNotExist()
        rule.onNodeWithText("PREVIEW").assertDoesNotExist()
        rule.onNodeWithText("72").assertDoesNotExist()
        rule.onNodeWithText("Weather location").performScrollTo().performTextInput("Phoenix, Arizona")
        rule.onNodeWithText("Use this city").performScrollTo().performClick()
        assertEquals("Phoenix, Arizona", chosen)
        rule.onNodeWithText("Use device location").performScrollTo().performClick()
        assertEquals("", chosen)
    }

    @Test fun avatarHasCompactColorsVisibleThemeAndNoDuplicateCreationOrAnimalButtons() {
        rule.setContent { MeowmbaTheme {
            AvatarScreen(AvatarStyle.PEARL, {}, emptyList(), {}, {})
        } }
        rule.onNodeWithText("Become anything").assertDoesNotExist()
        rule.onNodeWithText("Choose your character").assertDoesNotExist()
        rule.onNodeWithText("Act like a dog").assertDoesNotExist()
        rule.onNodeWithText("Act like a cat").assertDoesNotExist()
        rule.onNodeWithText("App theme").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Choose Eye color").performScrollTo().performClick()
        rule.onNodeWithText("Eye color").assertIsDisplayed()
        rule.onNodeWithText("Done").performClick()
    }

    @Test fun lumenCanSelectJarvisWithoutGeneration() {
        var selected: AvatarStyle? = null
        rule.setContent { MeowmbaTheme {
            AvatarScreen(AvatarStyle.LUMEN, { selected = it }, emptyList(), {}, {})
        } }
        rule.onNodeWithText("Choose your character").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Turn into Jarvis orb").performScrollTo().performClick()
        assertEquals(AvatarStyle.SIGNAL, selected)
    }

    @Test fun weatherLocationPersistsAndCanReturnToGps() {
        val context = rule.activity
        val repo = com.example.meowmba.core.weather.WeatherRepository(context)
        try {
            repo.setSelectedPlace(" Phoenix, Arizona ")
            assertEquals("Phoenix, Arizona", com.example.meowmba.core.weather.WeatherRepository(context).selectedPlace)
            repo.setSelectedPlace("")
            assertEquals("", com.example.meowmba.core.weather.WeatherRepository(context).selectedPlace)
        } finally { repo.setSelectedPlace("") }
    }
}
