package com.example.meowmba

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.core.agent.*
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
class LocalModelLibraryUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val storage = mock(SecureStorageManager::class.java)
    private val device = PhoneModelDevice(12 * PhoneModelDiscovery.GIB, 30 * PhoneModelDiscovery.GIB, true, false)
    private val file = InstalledModelFile("qwen.task", 600_000_000, "My local Qwen", 1_700_000_000_000)
    private val candidate = PhoneModelCandidate(
        "hf:qwen", "litert-community/Qwen3-0.6B", "abc", "qwen.litertlm", "qwen.litertlm",
        "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/abc/qwen.litertlm",
        600_000_000, 600_000_000, "INT4", 1_700_000_000_000, 1_710_000_000_000,
        25_000, 140, "apache-2.0", false, PhoneModelFormat.LITERT_LM,
        PhoneModelDiscovery.assess(device, 600_000_000, 600_000_000), description = "Compact local conversational model.")

    @Test fun localCardOpensLibraryWithoutChangingProviderOrFetchingRemoteData() {
        var selections = 0
        var loads = 0
        compose.setContent { MaterialTheme {
            ModelManagerScreen(CompanionState(), secureStorage = storage,
                onModelSelected = { selections++ }, loadOpenRouterCatalog = { loads++; Result.success(emptyList()) },
                onClose = {})
        } }
        compose.onNodeWithText("Manage models, downloads & settings").performScrollTo().performClick()
        compose.onNodeWithText("Local AI models").assertExists()
        compose.onNodeWithText("Search this phone for models").assertExists()
        assertEquals(0, selections)
        assertEquals(0, loads)
        compose.onNodeWithContentDescription("Back to LLM").performClick()
        compose.onNodeWithText("LLM").assertExists()
    }

    @Test fun scanIsWiredAndInstalledFilesAreVisibleWithoutRedownloading() {
        var scans = 0
        var downloads = 0
        compose.setContent { MaterialTheme {
            LocalModelSettingsScreen(CompanionState(installedModels = listOf(file)), storage,
                onScan = { scans++ }, onDownload = { downloads++ }, deviceOverride = device, onClose = {})
        } }
        compose.onNodeWithText("Search this phone for models").performClick()
        assertEquals(1, scans)
        compose.onNodeWithText("My local Qwen").performScrollTo().assertExists()
        compose.onNodeWithText("Saved on phone", substring = true).assertExists()
        assertEquals(0, downloads)
        render("local-model-installed")
    }

    @Test fun deletingRequiresConfirmationAndDoesNotAffectOtherFiles() {
        var deleted: String? = null
        compose.setContent { MaterialTheme {
            LocalModelSettingsScreen(CompanionState(installedModels = listOf(file)), storage,
                onDelete = { deleted = it }, deviceOverride = device, onClose = {})
        } }
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        assertNull(deleted)
        compose.onNodeWithText("Keep").performClick()
        assertNull(deleted)
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onNodeWithText("Delete model", substring = false).performClick()
        assertEquals(file.fileName, deleted)
    }

    @Test fun hubSearchIsExplicitAndDownloadNeedsSeparateConfirmation() {
        var searches = 0
        var downloads = 0
        compose.setContent { MaterialTheme {
            LocalModelSettingsScreen(CompanionState(), storage, deviceOverride = device,
                searchModels = { _, _, _, _ -> searches++; Result.success(PhoneModelSearchPage(listOf(candidate), null, 1, emptyList())) },
                onDownload = { downloads++ }, onClose = {})
        } }
        compose.onNodeWithText("Hugging Face", substring = false).performClick()
        assertEquals(0, searches)
        compose.onNodeWithText("Search Hugging Face models").performTextInput("Qwen")
        compose.onNodeWithText("Search library").performClick()
        compose.waitUntil(5000) { searches == 1 }
        compose.onNodeWithText("Qwen3-0.6B").performScrollTo().assertExists()
        compose.onNodeWithText("Download", substring = false).performScrollTo().performClick()
        assertEquals(0, downloads)
        compose.onNodeWithText("Download model", substring = false).performClick()
        assertEquals(1, downloads)
        render("local-model-hugging-face")
    }

    @Test fun offlineHubLeavesInstalledLibraryUsable() {
        compose.setContent { MaterialTheme {
            LocalModelSettingsScreen(CompanionState(installedModels = listOf(file)), storage, deviceOverride = device,
                searchModels = { _, _, _, _ -> Result.failure(Exception("Offline. Retry when connected.")) }, onClose = {})
        } }
        compose.onNodeWithText("Hugging Face", substring = false).performClick()
        compose.onNodeWithText("Search library").performClick()
        compose.onNodeWithText("Offline. Retry when connected.").performScrollTo().assertExists()
        compose.onNodeWithText("Installed", substring = false).performClick()
        compose.onNodeWithText("My local Qwen").performScrollTo().assertExists()
    }

    @Test fun tokenIsMaskedAndOnlySavedFromItsNewLocation() {
        compose.setContent { MaterialTheme {
            LocalModelSettingsScreen(CompanionState(), storage, deviceOverride = device, onClose = {})
        } }
        compose.onNodeWithText("Settings", substring = false).performClick()
        compose.onNodeWithText("Get a Hugging Face token").assertExists()
        compose.onNodeWithText("Hugging Face token (optional)")
            .assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password))
            .performTextInput("hf_test_only")
        verify(storage, never()).saveApiKey(anyString(), anyString())
        compose.onNodeWithText("Save download access").performScrollTo().performClick()
        verify(storage).saveApiKey("huggingface", "hf_test_only")
        compose.onNodeWithText("Token saved securely on this phone.").assertExists()
        render("local-model-download-settings")
    }

    @Test fun unsupportedRouterWeightsCannotBeChosenAsAChatModel() {
        compose.setContent { MaterialTheme {
            LocalModelSettingsScreen(CompanionState(installedModels = listOf(InstalledModelFile("needle2.cact", 13_700_000))),
                storage, deviceOverride = device, onClose = {})
        } }
        compose.onNodeWithText("Use local AI").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Action-routing weights, not a conversational model.").assertExists()
    }

    private fun render(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val root = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            java.io.File("build/reports/$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
