package com.example.meowmba

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import com.example.meowmba.ui.companion.*
import com.example.meowmba.ui.theme.MeowmbaTheme
import kotlinx.coroutines.cancel
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Vending142UiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private var vm: CompanionViewModel? = null
    @After fun cleanup() { vm?.viewModelScope?.cancel() }

    @Test fun everyLaneOpensAndGenerationRequiresAnExplicitRequest() {
        val model = CompanionViewModel().also { vm = it }
        rule.setContent { MeowmbaTheme { VendingMachineScreen(model, {}) } }
        fun lane(key: String) = rule.onNodeWithTag("vending-hub").performScrollToNode(hasTestTag("vending-lane-$key")).let {
            rule.onNodeWithTag("vending-lane-$key").performClick()
        }
        fun back() = rule.onNodeWithTag("vending-back").performClick()
        lane("avatar-simple")
        rule.onNodeWithTag("generated-avatar-create").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Add reference photo").assertExists()
        back()
        lane("avatar-workshop")
        rule.onNodeWithTag("workshop-subject").performScrollTo().assertIsDisplayed()
        back()
        lane("prop-generate")
        rule.onNodeWithTag("prop-dispense").performScrollTo().assertIsNotEnabled()
        back()
        lane("prop-library")
        rule.onNodeWithTag("prop-library").assertExists()
        back()
        lane("animation-generate")
        rule.onNodeWithTag("animation-dispense").performScrollTo().assertIsNotEnabled()
        back()
        lane("background")
        rule.onNodeWithText("Generate background").performScrollTo().assertIsNotEnabled()
        back()
        lane("studio-archive")
        rule.onNodeWithText("Made for you").assertExists()
        Assert.assertFalse(model.uiState.value.avatarFormGenerating)
        Assert.assertFalse(model.uiState.value.propInventGenerating)
    }
}
