package com.example.meowmba

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.AvatarAnimal
import com.example.meowmba.ui.onboarding.SetupWizardRoute
import com.example.meowmba.ui.onboarding.welcomeFeatures
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BetaOnboardingUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var storageMock: org.mockito.MockedConstruction<SecureStorageManager>
    @Before fun fakeAndroidKeystoreForUiOnly() {
        storageMock = Mockito.mockConstruction(SecureStorageManager::class.java)
    }
    @After fun closeStorageMock() { storageMock.close() }
    private fun tap(label: String) { compose.onNodeWithText(label).performScrollTo().performClick() }

    @Test fun quickPathRequiresAvatarChoiceAndReviewsAutomaticFitWithoutPermissionsOrKeys() {
        var finished = false; var permissions = 0; var picked: AvatarAnimal? = null; var size = 0f
        compose.setContent { MeowmbaTheme(darkTheme = true) {
            SetupWizardRoute(onFinished = { finished = true }, onEnablePermissions = { permissions++ },
                onAnimalPicked = { picked = it }, onAvatarSized = { size = it })
        } }
        compose.onNodeWithText("Finish later").performClick()
        assertFalse(finished)
        compose.onNodeWithText("Fit to my screen").assertIsNotEnabled()
        tap("Rabbit"); assertEquals(AvatarAnimal.RABBIT, picked)
        tap("Fit to my screen")
        compose.onNodeWithText("Fitted to your screen").assertExists()
        compose.onNodeWithTag("onboarding-avatar-size").assertDoesNotExist()
        render("automatic-rabbit-dark")
        tap("View at full size")
        compose.onNodeWithText("Full-size preview").assertIsDisplayed()
        compose.onNodeWithText("Back to sizing").performClick()
        compose.onNodeWithText("Full-size preview").assertDoesNotExist()
        tap("Keep this size")
        assertTrue(size in .5f..2.5f)
        Mockito.verify(storageMock.constructed().first()).savePreference("avatar_scale", size.toString())
        compose.onNodeWithText("Finish later").performClick()
        tap("Meet my avatar")
        assertTrue(finished); assertEquals(0, permissions)
    }

    @Test fun manualSizePersistsAndAutoFitCanBeRestored() {
        var size = 0f
        compose.setContent { MeowmbaTheme(darkTheme = false) { SetupWizardRoute(onFinished = {}, onAvatarSized = { size = it }) } }
        compose.onNodeWithText("Finish later").performClick(); tap("Owl"); tap("Fit to my screen"); tap("Adjust size")
        compose.onNodeWithTag("onboarding-avatar-size").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1.1f) }
        render("manual-owl-light"); tap("Use this size"); assertEquals(1.1f, size, .001f)
        compose.onNodeWithText("Back").performClick(); tap("Auto-fit again")
        compose.onNodeWithTag("onboarding-avatar-size").assertDoesNotExist(); tap("Keep this size")
        assertNotEquals(1.1f, size, .01f)
    }

    @Test fun currentFeaturesAndAllProviderPathsAreReachableWithoutTermux() {
        compose.setContent { MeowmbaTheme(darkTheme = true) { SetupWizardRoute(onFinished = {}) } }
        tap("Let’s go")
        welcomeFeatures.forEach { (title, _) -> compose.onNodeWithText(title).performScrollTo().assertIsDisplayed() }
        tap("Choose my avatar"); tap("Panda"); tap("Fit to my screen"); tap("Keep this size"); tap("Next")
        tap("Set up Computer later"); tap("Not now")
        listOf("OpenAI", "Claude", "OpenRouter", "OpenCode Zen", "Custom API", "On this phone").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        render("providers-dark")
        tap("Continue setup"); compose.onNodeWithText("GitHub for projects").assertExists()
    }

    @Test fun rotationRestoresSelectionAndManualAdjustment() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MeowmbaTheme { SetupWizardRoute(onFinished = {}) } }
        compose.onNodeWithText("Finish later").performClick(); tap("Duck"); tap("Fit to my screen"); tap("Adjust size")
        compose.onNodeWithTag("onboarding-avatar-size").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1.2f) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Fitted to your screen").assertExists()
        compose.onNodeWithTag("onboarding-avatar-size").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(1.2f, .5f..3f))
    }

    @Test @Config(qualifiers = "w768dp-h432dp")
    fun landscapeLargeTextKeepsSizingActionsReachable() {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 1.6f)) {
                MeowmbaTheme(darkTheme = false) { SetupWizardRoute(onFinished = {}) }
            }
        }
        compose.onNodeWithText("Finish later").performClick(); tap("Cat"); tap("Fit to my screen")
        render("landscape-large-text")
        tap("Adjust size"); tap("Use this size")
        compose.onNodeWithText("Pick a background").assertExists()
    }

    private fun render(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val root = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/reports/onboarding133/$name.png")
            file.parentFile?.mkdirs(); file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
}
