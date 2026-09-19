package com.example.meowmba

import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
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
class AvatarCreationReviewUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun state(reviewing: Boolean = true, candidate: GeneratedAvatarForm? = null): CompanionState {
        val base = CompanionState()
        return base.copy(features = base.features.copy(avatarCreationReview = AvatarCreationReview(
            "a turquoise moon creature", InventReasonLevel.FAST, candidate = candidate, reviewing = reviewing)))
    }

    @Test fun `failed first preview offers retry and displays the current failure`() {
        val choices = mutableListOf<Boolean>()
        var accepted = 0
        var dismissed = 0
        var current by mutableStateOf(state().copy(avatarFormGenerating = true,
            avatarFormMessage = "Creating the first example"))
        rule.setContent { MeowmbaTheme {
            AvatarCreationReviewDialog(current, { choices += it }, {}, { accepted++ }, { dismissed++ })
        } }
        rule.onNodeWithText("Creating the first example").assertIsDisplayed()
        rule.runOnIdle { current = current.copy(avatarFormGenerating = false, avatarFormError = true,
            avatarFormMessage = "The selected model did not finish. Your avatar is unchanged.") }
        rule.onNodeWithText("Creating the first example").assertDoesNotExist()
        rule.onNodeWithTag("avatar-example-message").assertTextEquals(
            "The selected model did not finish. Your avatar is unchanged.").assertIsDisplayed()
        rule.onNodeWithText("Retry example").assertIsEnabled().performClick()
        rule.onNodeWithText("Accept & become").assertDoesNotExist()
        rule.onNodeWithTag("avatar-example-preview").assertDoesNotExist()
        assertEquals(listOf(true), choices)
        assertEquals(0, accepted)
        assertEquals(0, dismissed)
    }

    @Test fun `retry becomes unavailable during work and cancel remains reachable`() {
        var retries = 0
        var dismissed = 0
        var current by mutableStateOf(state().copy(avatarFormError = true, avatarFormMessage = "Request timed out."))
        rule.setContent { MeowmbaTheme {
            AvatarCreationReviewDialog(current, {
                assertTrue(it)
                retries++
                current = current.copy(avatarFormGenerating = true, avatarFormError = false,
                    avatarFormMessage = "Creating your example again")
            }, {}, {}, { dismissed++ })
        } }
        rule.onNodeWithText("Retry example").performClick()
        rule.onNodeWithText("Retry example").assertDoesNotExist()
        rule.onNodeWithTag("avatar-example-confirm").assertIsNotEnabled()
        rule.onNodeWithText("Request timed out.").assertDoesNotExist()
        rule.onNodeWithTag("avatar-example-message").assertTextEquals("Creating your example again")
        rule.onNodeWithText("Cancel").assertIsEnabled().performClick()
        assertEquals(1, retries)
        assertEquals(1, dismissed)
    }

    @Test fun `failed refinement preserves candidate draft and acceptance instead of restarting creation`() {
        val form = GeneratedAvatarExamples.coffeeCup()
        val choices = mutableListOf<Boolean>()
        val refinements = mutableListOf<String>()
        var accepted = 0
        var current by mutableStateOf(state(candidate = form))
        rule.setContent { MeowmbaTheme {
            AvatarCreationReviewDialog(current, { choices += it }, { refinements += it }, { accepted++ }, {})
        } }
        rule.onNodeWithTag("avatar-example-preview").assertIsDisplayed()
        rule.onNodeWithTag("avatar-example-refinement").performScrollTo().performTextInput("give it small wings")
        rule.runOnIdle { current = current.copy(avatarFormError = true,
            avatarFormMessage = "The revision failed. The previous example is still available.") }
        rule.onNodeWithTag("avatar-example-message").performScrollTo().assertTextEquals(
            "The revision failed. The previous example is still available.")
        rule.onNodeWithTag("avatar-example-preview").assertExists()
        rule.onNodeWithTag("avatar-example-refinement").assertTextContains("give it small wings")
        rule.onNodeWithText("Retry example").assertDoesNotExist()
        rule.onNodeWithText("Update example").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithText("Accept & become").assertIsEnabled().performClick()
        assertEquals(listOf("give it small wings"), refinements)
        assertEquals(1, accepted)
        assertTrue(choices.isEmpty())
        assertEquals(form, current.features.avatarCreationReview?.candidate)
    }

    @Test @Config(qualifiers = "w768dp-h420dp")
    fun `landscape large text keeps actual preview refinement and fixed actions reachable`() {
        val form = GeneratedAvatarExamples.coffeeCup()
        var accepted = 0
        var dismissed = 0
        val refinements = mutableListOf<String>()
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MeowmbaTheme {
                    AvatarCreationReviewDialog(state(candidate = form).copy(avatarFormError = true,
                        avatarFormMessage = "The revision failed. The previous example is still available."),
                        {}, { refinements += it }, { accepted++ }, { dismissed++ })
                }
            }
        }
        rule.onNodeWithTag("avatar-example-preview").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Accept & become").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
        rule.onNodeWithTag("avatar-example-refinement").performScrollTo().performTextInput("add little wings")
        rule.onNodeWithText("Update example").performScrollTo().performClick()
        rule.onNodeWithTag("avatar-example-message").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Accept & become").assertIsDisplayed().performClick()
        rule.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        assertEquals(listOf("add little wings"), refinements)
        assertEquals(1, accepted)
        assertEquals(1, dismissed)
    }

    @Test fun `initial preview choice remains explicit and missing failure text stays helpful`() {
        val choices = mutableListOf<Boolean>()
        var current by mutableStateOf(state(reviewing = false))
        rule.setContent { MeowmbaTheme {
            AvatarCreationReviewDialog(current, { choices += it }, {}, {}, {})
        } }
        rule.onNodeWithText("Retry example").assertDoesNotExist()
        rule.onNodeWithText("Yes, show example").performClick()
        rule.onNodeWithText("No, just make it").performClick()
        assertEquals(listOf(true, false), choices)
        rule.runOnIdle { current = state().copy(avatarFormError = true, avatarFormMessage = null) }
        rule.onNodeWithTag("avatar-example-message").assertTextEquals(
            "Could not create the example. Try again; your current avatar is unchanged.")
        rule.onNodeWithText("Retry example").assertIsEnabled()
    }
}
