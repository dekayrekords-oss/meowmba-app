package com.example.meowmba

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.setup.BrainSetupModel
import com.example.meowmba.ui.companion.*
import com.example.meowmba.ui.onboarding.AgentBrainSetup
import kotlinx.coroutines.cancel
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrainSetupUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @get:Rule val temp = TemporaryFolder()
    private var model: BrainSetupModel? = null
    @After fun closeModel() { model?.viewModelScope?.cancel() }
    private fun screen(backend: FakeBrainSetupBackend = FakeBrainSetupBackend(temp.root), onContinue: () -> Unit = {}) {
        compose.runOnUiThread { model = BrainSetupModel(backend) }
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            AgentBrainSetup(onContinue, modelOverride = model)
        } } }
    }
    @Test fun pasteEnablesSaveWithoutAnyCatalogOrModelSelection() {
        screen()
        compose.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Save key & connect OpenRouter").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Paste your OpenRouter key").performScrollTo().performTextInput("test-key")
        compose.onNodeWithText("Save key & connect OpenRouter").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Automatic · free models only").assertDoesNotExist()
    }
    @Test fun keyButtonOpensTheOfficialKeysPageAndInstructionsStayVisible() {
        screen()
        compose.onNodeWithText("Get an OpenRouter API key ↗").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("https://openrouter.ai/settings/keys", org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity.data.toString())
        compose.onNodeWithText("Choose Create Key", substring = true).assertExists()
    }
    @Test fun successfulConnectionUnlocksContinueAndShowsTheSavedKeyState() {
        var continued = false
        screen(onContinue = { continued = true })
        compose.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Paste your OpenRouter key").performScrollTo().performTextInput("test-key")
        compose.onNodeWithText("Save key & connect OpenRouter").performScrollTo().performClick()
        compose.onNodeWithText("API key saved securely on this phone.", substring = true).assertExists()
        compose.onNodeWithText("AI ready · Continue").performScrollTo().assertIsEnabled().performClick()
        assertTrue(continued)
    }
    @Test fun hardwareModelIsSelectedByTappingTheWholeTile() {
        var selected = 0
        compose.setContent { MaterialTheme {
            LoadedModelCard({}, "OpenRouter · cloud", "Test model", listOf("API key saved"), LoadStatus.STANDBY,
                onClick = { selected++ }, actionLabel = "Tap to use OpenRouter")
        } }
        compose.onNodeWithText("Test model").performClick()
        assertEquals(1, selected)
        compose.onNodeWithText("Change").assertDoesNotExist()
    }
}
