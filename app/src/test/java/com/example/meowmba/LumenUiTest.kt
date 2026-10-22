package com.example.meowmba

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.meowmba.ui.companion.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LumenUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lumenShowsEveryMorphChoice() {
        var selected: AvatarAnimal? = null
        compose.setContent { MaterialTheme {
            AvatarScreen(AvatarStyle.LUMEN, {}, emptyList(), {}, {}, onSelectAnimal = { selected = it })
        } }
        compose.onNodeWithText("Squirrel").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AvatarAnimal.SQUIRREL, selected) }
        compose.onNodeWithText("Raccoon").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AvatarAnimal.RACCOON, selected) }
        for (key in listOf("australian_shepherd", "owl", "duck", "rabbit", "pig", "alien", "robot", "panda")) {
            val form = AvatarAnimal.fromKey(key)
            compose.onNodeWithText(form.label).performScrollTo().performClick()
            compose.runOnIdle { assertEquals(form, selected) }
        }
        compose.onNodeWithText("Eyes only").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AvatarAnimal.NONE, selected) }
    }

    @Test fun lumenIsSelectableAndAnimalPreviewIsAnExplicitAction() {
        var style by mutableStateOf(AvatarStyle.NEO)
        var animal by mutableStateOf(AvatarAnimal.NONE)
        var played: String? = null
        compose.setContent {
            MaterialTheme {
                AvatarScreen(style, { style = it }, emptyList(), { played = it }, {},
                    animal = animal, onSelectAnimal = { animal = it })
            }
        }
        compose.onNodeWithText("Lumen").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AvatarStyle.LUMEN, style); assertEquals(AvatarAnimal.NONE, animal) }
        compose.onNodeWithText("Dog").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AvatarAnimal.DOG, animal); assertEquals(null, played) }
        compose.onNodeWithText("Act like a cat").assertDoesNotExist()
        compose.runOnIdle { assertEquals(null, played) }
        compose.onNodeWithText("Eyes only").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AvatarAnimal.NONE, animal) }
    }

    @Test fun catUsesSameColorControlsAndAnimationSearchPlaysTheSelectedResult() {
        var style by mutableStateOf(AvatarStyle.LUMEN)
        var played: String? = null
        compose.setContent {
            MaterialTheme { AvatarScreen(style, { style = it }, AvatarPerformanceCatalog.names(),
                { played = it }, {}) }
        }
        compose.onNodeWithText("Lumen Cat").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AvatarStyle.LUMEN_CAT, style) }
        compose.onNodeWithText("Choose Eye color").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Choose Feature color").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Choose Glow color").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Animations").performScrollTo().performClick()
        compose.onNodeWithText("Search all animations").performScrollTo().performTextInput("snow")
        compose.onNodeWithText("Snow Wonder").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Snow Wonder", played) }
    }

    @Test fun catFeaturesCanBeRecoloredWithoutChangingTheEyesOrGlow() {
        var eye by mutableStateOf(AvatarColors.DEFAULT_EYE)
        var glow by mutableStateOf(AvatarColors.DEFAULT_GLOW)
        var feature by mutableStateOf(0xFFE8D5B9L)
        compose.setContent {
            MaterialTheme { AvatarScreen(AvatarStyle.LUMEN_CAT, {}, emptyList(), {}, {},
                eyeColor = eye, glowColor = glow, featureColor = feature,
                onSelectEyeColor = { eye = it }, onSelectGlowColor = { glow = it },
                onSelectFeatureColor = { feature = it }) }
        }
        compose.onNodeWithText("Choose Feature color").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Feature color Pink").performClick()
        compose.runOnIdle {
            assertEquals(0xFFFFA6C9L, feature)
            assertEquals(AvatarColors.DEFAULT_EYE, eye); assertEquals(AvatarColors.DEFAULT_GLOW, glow)
        }
        compose.onNodeWithText("Choose Eye color").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Eye color Violet").performClick()
        compose.runOnIdle { assertEquals(0xFFA371F7L, eye); assertEquals(0xFFFFA6C9L, feature) }
    }
}
