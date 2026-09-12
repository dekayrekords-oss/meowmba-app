package com.example.meowmba

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

    @Test fun firstRunCanBeSkippedWithoutKeys() {
        var finished = false
        var permissions = 0
        compose.setContent { MaterialTheme {
            SetupWizardRoute(onFinished = { finished = true }, onEnablePermissions = { permissions++ })
        } }
        compose.onNodeWithText("Hi.").assertExists()
        compose.onNodeWithText("Let’s go").assertExists()
        compose.onNodeWithText("AI needs a key").assertDoesNotExist()
        compose.onNodeWithText("Skip").assertIsDisplayed().assertIsEnabled().performClick()
        assertTrue(finished)
        assertEquals(0, permissions)
    }

    @Test fun apiKeyStepOffersDoneAndKeepGoing() {
        compose.setContent { MaterialTheme {
            SetupWizardRoute(onFinished = {}, onStartOpenCode = {})
        } }
        listOf("Let’s go", "I already have it", "Next", "I did that").forEach { action ->
            compose.onNodeWithText(action).performScrollTo().performClick()
        }
        compose.onNodeWithText("OpenCode in Terminal").assertExists()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("Not now").performScrollTo().performClick()
        compose.onNodeWithText("AI needs a key").assertExists()
        compose.onNodeWithText("Get a free key").assertExists()
        compose.onNodeWithText("Start using Meow", substring = true).assertExists()
        compose.onNodeWithText("Keep going", substring = true).assertExists()
        compose.onNodeWithText("Keep going", substring = true).performClick()
        compose.onNodeWithText("GitHub for projects").assertExists()
        compose.onNodeWithText("Skip for now").assertExists()
    }
}
