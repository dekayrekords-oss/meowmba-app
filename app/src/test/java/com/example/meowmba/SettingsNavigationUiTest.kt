package com.example.meowmba

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.core.media.*
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.SettingsScreen
import com.example.meowmba.ui.onboarding.ImageGenerationSetup
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
class SettingsNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val storage = mock(SecureStorageManager::class.java)

    @Test fun overviewHasCardsNotLongFormsAndLoadsNoModelCatalog() {
        var loads = 0
        compose.setContent { MaterialTheme {
            SettingsScreen(secureStorage = storage, onClose = {}, loadOpenRouterCatalog = { loads++; Result.success(emptyList()) })
        } }
        compose.onNodeWithText("Images").assertExists()
        compose.onNodeWithText("Companion Voice").assertDoesNotExist()
        compose.onNodeWithText("Workers AI API token").assertDoesNotExist()
        assertEquals(0, loads)
        render("settings-cards-light")
        compose.onNodeWithText("Voice & behavior").performClick()
        compose.onNodeWithText("Companion Voice").assertExists()
        compose.onNodeWithText("Images").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to settings").performClick()
        compose.onNodeWithText("Images").assertExists()
        assertEquals(0, loads)
    }

    @Test fun darkAppearancePageKeepsLightAndDarkOptions() {
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            SettingsScreen(secureStorage = storage, onClose = {})
        } }
        render("settings-cards-dark")
        compose.onNodeWithText("Appearance").performScrollTo().performClick()
        compose.onNodeWithText("Dark").assertExists()
        compose.onNodeWithText("Light").assertExists()
        compose.onNodeWithText("App theme").assertExists()
        compose.onNodeWithText("Music & video").assertDoesNotExist()
        render("settings-appearance-dark")
    }

    @Test fun imageSetupRequiresConsentAndPersistsTheExplicitChoice() {
        compose.setContent { MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { ImageGenerationSetup(storage) }
        } }
        compose.onNodeWithText("AI Horde").performScrollTo().performClick()
        compose.onNodeWithText("Use this option").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("I understand anonymous generations are shared for dataset use.").performScrollTo().performClick()
        compose.onNodeWithText("Use this option").performScrollTo().performClick()
        verify(storage).savePreference(ImageServiceSettings.PROVIDER, ImageService.HORDE.name)
        verify(storage).saveBooleanPreference(ImageServiceSettings.HORDE_CONSENT, true)
        compose.onNodeWithText("Saved on this phone.", substring = true).assertExists()
        render("settings-horde-consent")
    }

    @Test fun cloudflareNeedsAccountAndTokenAndMasksItsKey() {
        compose.setContent { MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { ImageGenerationSetup(storage) }
        } }
        compose.onNodeWithText("Cloudflare").performScrollTo().performClick()
        compose.onNodeWithText("Use this option").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Account ID (32 characters)").performScrollTo().performTextInput("a".repeat(32))
        compose.onNodeWithText("Workers AI API token").performScrollTo()
            .assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password))
            .performTextInput("test-cloudflare-token")
        compose.onNodeWithText("Use this option").performScrollTo().performClick()
        verify(storage).saveApiKey(ImageServiceSettings.CF_KEY, "test-cloudflare-token")
        verify(storage).savePreference(ImageServiceSettings.CF_ACCOUNT, "a".repeat(32))
        verify(storage).savePreference(ImageServiceSettings.PROVIDER, ImageService.CLOUDFLARE.name)
    }

    @Test fun editingMaskedProviderKeyDoesNotSaveMaskCharacters() {
        `when`(storage.getApiKey("openai")).thenReturn("original-test-key")
        var loads = 0
        compose.setContent { MaterialTheme {
            SettingsScreen(secureStorage = storage, onClose = {}, loadOpenRouterCatalog = { loads++; Result.success(emptyList()) })
        } }
        compose.onNodeWithText("AI & models").performClick()
        compose.onNodeWithText("API Key").performScrollTo()
            .assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password))
            .performTextReplacement("replacement-test-key")
        compose.onNodeWithContentDescription("Back to settings").performClick()
        verify(storage, atLeastOnce()).saveApiKey("openai", "replacement-test-key")
        verify(storage, never()).saveApiKey(eq("openai"), contains("•"))
        assertEquals(1, loads)
    }

    @Test fun imageProviderDraftIsNotSavedByMerelyBrowsingChoices() {
        compose.setContent { MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { ImageGenerationSetup(storage) }
        } }
        compose.onNodeWithText("AI Horde").performScrollTo().performClick()
        compose.onNodeWithText("Cloudflare").performScrollTo().performClick()
        verify(storage, never()).savePreference(anyString(), anyString())
        verify(storage, never()).saveApiKey(anyString(), anyString())
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
