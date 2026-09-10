package com.example.meowmba

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.core.agent.OpenRouterModel
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LlmSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val storage = mock(SecureStorageManager::class.java)
    private val catalog = listOf(OpenRouterModel("chat:free", "Chat only", false, true, true),
        OpenRouterModel("vision:free", "Vision capable", true, true, true))

    @Test fun llmPageHasProviderAndVisionCardsButLoadsNoCatalogUntilOpened() {
        var loads = 0
        compose.setContent { MaterialTheme {
            ModelManagerScreen(CompanionState(), secureStorage = storage,
                loadOpenRouterCatalog = { loads++; Result.success(catalog) }, onClose = {})
        } }
        compose.onNodeWithText("LLM").assertExists()
        compose.onNodeWithText("OpenRouter settings").performScrollTo().assertExists()
        compose.onNodeWithText("OpenAI settings").performScrollTo().assertExists()
        compose.onNodeWithText("Claude / Anthropic settings").performScrollTo().assertExists()
        compose.onNodeWithText("Custom API settings").performScrollTo().assertExists()
        compose.onNodeWithText("OpenCode Zen settings").performScrollTo().assertExists()
        compose.onNodeWithText("Vision settings").performScrollTo().assertExists()
        compose.onNodeWithText("OpenRouter API key").assertDoesNotExist()
        assertEquals(0, loads)
        compose.onNodeWithText("OpenRouter settings").performScrollTo().performClick()
        compose.onNodeWithText("OpenRouter API key").performScrollTo().assertExists()
        assertEquals(1, loads)
        render("llm-openrouter-settings")
    }

    @Test fun openingOrEditingCardDoesNotChangeActiveProviderOrSaveSecrets() {
        var selections = 0
        `when`(storage.getApiKey("openai")).thenReturn("original-test-key")
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            CloudModelCard("openai", storage, "chat-model", "https://api.openai.com", true, LoadStatus.STANDBY,
                onSelect = { selections++ }, onSaved = {}, loadProviderCatalog = { _, _, _, _ -> Result.success(emptyList()) })
        } } }
        compose.onNodeWithText("OpenAI settings").performClick()
        compose.onNodeWithText("OpenAI API key").performScrollTo()
            .assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password))
            .performTextReplacement("replacement-test-key")
        compose.onNodeWithText("OpenAI chat model").performScrollTo().performTextReplacement("another-model")
        assertEquals(0, selections)
        assertFalse(mockingDetails(storage).invocations.any { it.method.name.startsWith("save") })
    }

    @Test fun saveOpenaiCommitsRealKeyModelAndEndpointAndSelectsIt() {
        var saved = false
        `when`(storage.getApiKey("openai")).thenReturn("original-test-key")
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            CloudProviderSettings("openai", storage, { saved = true }, loadProviderCatalog = { _, _, _, _ -> Result.success(emptyList()) })
        } } }
        compose.onNodeWithText("OpenAI API key").performScrollTo().performTextReplacement("replacement-test-key")
        compose.onNodeWithText("OpenAI chat model").performScrollTo().performClick().performTextReplacement("chat-model")
        compose.onNodeWithText("Use model ID: chat-model").performClick()
        compose.onNodeWithText("Save & use OpenAI").performScrollTo().assertIsNotEnabled()
        compose.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Save & use OpenAI").performScrollTo().performClick()
        compose.waitUntil(5_000) { saved }
        verify(storage).saveSetupSelection(mapOf("brain_provider" to "openai", "default_brain" to "openai",
            "selected_model" to "Omniroute (Local router)"), "openai", "replacement-test-key", "chat-model", "")
        compose.onNodeWithText("Connection not tested", substring = true).assertExists()
    }

    @Test fun modelCatalogFailureStillAllowsCustomIdAndSavingOpenrouter() {
        var saved = false
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            CloudProviderSettings("openrouter", storage, { saved = true }, { Result.failure(Exception("offline")) })
        } } }
        compose.onNodeWithText("OpenRouter API key").performScrollTo().performTextInput("test-key")
        compose.onNodeWithText("OpenRouter chat model").performScrollTo().performClick().performTextReplacement("custom/model:free")
        compose.onNodeWithText("Use model ID: custom/model:free").performClick()
        compose.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Save & use OpenRouter").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5_000) { saved }
        verify(storage).saveSetupSelection(mapOf("brain_provider" to "openrouter", "default_brain" to "openrouter",
            "selected_model" to "Omniroute (Local router)"), "openrouter", "test-key", "custom/model:free", "")
    }

    @Test fun visionFiltersCatalogAndSavesWithoutChangingKeysOrChat() {
        var saved = false
        `when`(storage.getModelName("openrouter_vision")).thenReturn("vision:free")
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            VisionModelSettingsCard(storage, { saved = true }, { Result.success(catalog) })
        } } }
        compose.onNodeWithText("Vision settings").performClick()
        compose.onNodeWithText("OpenRouter vision model").performScrollTo().performClick()
        compose.onNodeWithText("Chat only").assertDoesNotExist()
        compose.onAllNodesWithText("Vision capable").onLast().performClick()
        compose.onNodeWithText("OpenAI vision model").performScrollTo().performTextReplacement("image-model")
        compose.onNodeWithText("Save vision settings").performScrollTo().performClick()
        compose.waitUntil(5_000) { saved }
        verify(storage).saveSetupSelection(emptyMap(), models = mapOf("openrouter_vision" to "vision:free", "openai_vision" to "image-model"))
        verify(storage, never()).saveApiKey(anyString(), anyString())
        render("llm-vision-settings")
    }

    @Test fun providerKeyLinkOpensOfficialPageWithoutSendingAKey() {
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            CloudProviderSettings("openrouter", storage, {}, { Result.success(emptyList()) })
        } } }
        compose.onNodeWithText("Get an OpenRouter API key ↗").performClick()
        assertEquals("https://openrouter.ai/settings/keys", org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity.data.toString())
    }

    @Test fun failedSaveKeepsTheDraftAndDoesNotReportAConfiguredProvider() {
        var saved = false
        `when`(storage.getApiKey("openai")).thenReturn("test-key")
        `when`(storage.getPreference("brain_provider")).thenReturn("openai")
        doThrow(IllegalStateException("disk unavailable")).`when`(storage).saveSetupSelection(
            mapOf("brain_provider" to "openai", "default_brain" to "openai", "selected_model" to "Omniroute (Local router)"),
            "openai", "test-key", "gpt-4o-mini", "")
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            CloudProviderSettings("openai", storage, { saved = true }, loadProviderCatalog = { _, _, _, _ -> Result.success(emptyList()) })
        } } }
        compose.onNodeWithText("Save & use OpenAI").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Could not save settings.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(saved)
        compose.onNodeWithText("Save & use OpenAI").assertIsEnabled()
        compose.onNodeWithText("OpenAI API key").performScrollTo().assertTextContains("test-key")
    }

    @Test fun customClaudeFormatSavesKeyEndpointAndControllerProviderTogether() {
        var saved = false
        `when`(storage.getApiKey("custom")).thenReturn("custom-test-key")
        `when`(storage.getModelName("custom")).thenReturn("custom-model")
        `when`(storage.getEndpoint("custom")).thenReturn("https://fixture.invalid/v1")
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            CloudProviderSettings("custom", storage, { saved = true }, loadProviderCatalog = { _, _, _, _ -> Result.success(emptyList()) })
        } } }
        compose.onNodeWithText("Claude Messages").performScrollTo().performClick()
        compose.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Save & use Custom API").performScrollTo().performClick()
        compose.waitUntil(5_000) { saved }
        verify(storage).saveSetupSelection(mapOf("brain_provider" to "custom", "default_brain" to "custom",
            "selected_model" to MODEL_OMNIROUTE, "custom_api_format" to "anthropic"),
            "custom", "custom-test-key", "custom-model", "https://fixture.invalid/v1")
    }

    @Test fun authenticatedDropdownRefreshPreservesSelectionAndFetchesUpdatedModels() {
        var loads = 0
        `when`(storage.getApiKey("openai")).thenReturn("catalog-test-key")
        `when`(storage.getModelName("openai")).thenReturn("my-selected-model")
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            CloudProviderSettings("openai", storage, {}, loadProviderCatalog = { provider, _, key, _ ->
                assertEquals("openai", provider); assertEquals("catalog-test-key", key)
                loads++
                Result.success(listOf(OpenRouterModel("new-model-$loads", "New model $loads", false, false)))
            })
        } } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitUntil(5_000) { loads >= 1 }
        compose.onNodeWithText("OpenAI chat model").performScrollTo().assertTextContains("my-selected-model")
        val previous = loads
        compose.onNodeWithText("Refresh model list").performScrollTo().performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeUntil(5_000) { loads > previous }
        compose.onNodeWithText("OpenAI chat model").performScrollTo().assertTextContains("my-selected-model").performClick()
        compose.onNodeWithText("New model $loads").assertExists()
        assertFalse(mockingDetails(storage).invocations.any { it.method.name.startsWith("save") })
    }

    private fun render(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val root = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            java.io.File("build/reports/$name.png").apply { parentFile.mkdirs() }.outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
