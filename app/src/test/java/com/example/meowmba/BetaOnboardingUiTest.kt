package com.example.meowmba

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.ui.companion.BetaHelp
import com.example.meowmba.ui.companion.BetaHelpCard
import com.example.meowmba.ui.onboarding.SetupWizardRoute
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BetaOnboardingUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var storageMock: org.mockito.MockedConstruction<com.example.meowmba.core.security.SecureStorageManager>
    @Before fun fakeAndroidKeystoreForUiOnly() {
        storageMock = org.mockito.Mockito.mockConstruction(com.example.meowmba.core.security.SecureStorageManager::class.java)
    }
    @After fun closeStorageMock() { storageMock.close() }

    @Test fun firstRunCanBeExploredWithoutKeysOrPermissionPrompt() {
        var finished = false
        var permissions = 0
        compose.setContent { MaterialTheme {
            SetupWizardRoute(onFinished = { finished = true }, onEnablePermissions = { permissions++ })
        } }
        compose.onNodeWithText("Hi.").assertExists()
        compose.onNodeWithText("Let’s go").assertExists()
        compose.onNodeWithText("API key").assertDoesNotExist()
        compose.onNodeWithText("OpenAI (GPT)").assertDoesNotExist()
        render("onboarding-light")
        compose.onNodeWithText("Skip").assertIsDisplayed().assertIsEnabled().performClick()
        assertTrue(finished)
        assertEquals(0, permissions)
    }

    @Test fun skipStaysVisibleWhileScrolledToTheEndWithNoWorkingAi() {
        var finished = false
        compose.setContent { MaterialTheme { SetupWizardRoute(onFinished = { finished = true }) } }
        compose.onNodeWithText("AI ready · Continue").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Skip").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Finish later in Settings → Start setup", substring = true).assertIsDisplayed()
        render("onboarding-skip-scrolled")
        compose.onNodeWithText("Skip").performClick()
        assertTrue(finished)
    }

    @Test fun skipRemainsAvailableOnEveryOptionalStepWithoutRequestingPermissions() {
        var finished = false
        var permissions = 0
        // A previously verified brain lets this UI test reach the optional
        // steps without a real network request or touching device credentials.
        storageMock.close()
        storageMock = org.mockito.Mockito.mockConstruction(com.example.meowmba.core.security.SecureStorageManager::class.java) { storage, _ ->
            org.mockito.Mockito.`when`(storage.getPreference("brain_provider")).thenReturn("openrouter")
            org.mockito.Mockito.`when`(storage.getApiKey("openrouter")).thenReturn("test-key")
            org.mockito.Mockito.`when`(storage.getModelName("openrouter")).thenReturn("test:free")
            org.mockito.Mockito.`when`(storage.getPreference("chat_verified_openrouter")).thenReturn(
                com.example.meowmba.core.setup.PlannerReadiness.cloudStamp("test:free", "test-key"))
        }
        compose.setContent { MaterialTheme {
            SetupWizardRoute(onFinished = { finished = true }, onEnablePermissions = { permissions++ })
        } }
        listOf("Let’s go", "I already have it", "Next", "I did that").forEachIndexed { index, action ->
            compose.onNodeWithText("Skip").assertExists()
            compose.onNodeWithText("Skip").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithText(action).performScrollTo().performClick()
        }
        compose.onNodeWithText("Have a computer on the internet?").assertExists()
        compose.onNodeWithText("Skip").assertIsDisplayed().assertIsEnabled().performClick()
        assertTrue(finished)
        assertEquals(0, permissions)
    }

    @Test fun cloudChoicesAreOptionalAndCanBeCollapsed() {
        compose.setContent { MaterialTheme { SetupWizardRoute(onFinished = {}) } }
        compose.onNodeWithText("Paste your OpenRouter key").assertDoesNotExist()
        compose.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Paste your OpenRouter key").assertExists()
        compose.onNodeWithText("Get an OpenRouter API key ↗").performScrollTo()
        render("onboarding-openrouter")
        compose.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Paste your OpenRouter key").assertDoesNotExist()
    }

    @Test fun betaHelpStartsCompactAndExplainsOptIn() {
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            Column(Modifier.verticalScroll(rememberScrollState())) { BetaHelpCard() }
        } }
        compose.onNodeWithText("Write feedback").assertDoesNotExist()
        compose.onNodeWithText("Beta help & feedback").performClick()
        compose.onNodeWithText("Write feedback").assertExists()
        compose.onNodeWithText("Joining the email list alone", substring = true).assertExists()
        compose.onNodeWithText("Share tester instructions").assertExists()
        render("beta-help-dark")
    }

    @Test fun feedbackOpensAnEditableEmailDraftWithoutAttachingFiles() {
        compose.setContent { MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { BetaHelpCard() }
        } }
        compose.onNodeWithText("Beta help & feedback").performClick()
        compose.onNodeWithText("Write feedback").performScrollTo().performClick()
        val intent = org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_SENDTO, intent.action)
        val uri = intent.data.toString()
        assertTrue(uri.startsWith("mailto:${BetaHelp.EMAIL}?subject="))
        assertTrue(uri.contains("&body="))
        assertNull(intent.getStringExtra(android.content.Intent.EXTRA_STREAM))
    }

    @Test fun cloudKeyInputIsMasked() {
        compose.setContent { MaterialTheme { SetupWizardRoute(onFinished = {}) } }
        compose.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Paste your OpenRouter key").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password))
    }

    @Test fun recruitmentAndFeedbackAreHonestAndMinimal() {
        assertTrue(BetaHelp.invitation.contains("After access is confirmed"))
        assertTrue(BetaHelp.invitation.contains("14 days"))
        assertTrue(BetaHelp.invitation.contains("No public rating required"))
        val body = BetaHelp.feedback("1.3.27", "16", "Test phone")
        assertTrue(body.contains("1.3.27"))
        assertTrue(body.contains("No keys, chat history, files, or logs are attached automatically"))
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
        }
    }
}
