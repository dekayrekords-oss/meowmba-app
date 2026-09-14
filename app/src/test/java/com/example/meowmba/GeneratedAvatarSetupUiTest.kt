package com.example.meowmba

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.meowmba.ui.companion.*
import com.example.meowmba.ui.theme.MeowmbaTheme
import com.example.meowmba.ui.theme.Surface
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
class GeneratedAvatarSetupUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val dragon = GeneratedAvatarExamples.dragon().copy(id = "dragon", name = "Purple dragon")
    private val cup = GeneratedAvatarExamples.coffeeCup().copy(id = "cup", name = "Walking coffee cup")

    @Composable private fun Frame(largeText: Boolean = false, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.5f else density.fontScale)) {
            MeowmbaTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { content() }
                }
            }
        }
    }

    @Test fun draftAndExamplesDoNotChangeAvatarUntilCreate() {
        val requests = mutableListOf<String>()
        rule.setContent { Frame {
            GeneratedAvatarControls(null, emptyList(), false, null, false, { requests += it }, {}, {}, {})
        } }
        rule.onNodeWithTag("generated-avatar-create").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Purple dragon").performScrollTo().performClick()
        assertTrue(requests.isEmpty())
        rule.onNodeWithTag("generated-avatar-request").assertTextContains("Purple dragon")
        rule.onNodeWithTag("generated-avatar-create").performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf("Purple dragon"), requests)
        render("create-form-portrait")
    }

    @Test fun inputIsBoundedAndSubmittedTrimmed() {
        val requests = mutableListOf<String>()
        rule.setContent { Frame {
            GeneratedAvatarControls(null, emptyList(), false, null, false, { requests += it }, {}, {}, {})
        } }
        val field = rule.onNodeWithTag("generated-avatar-request")
        field.performScrollTo().performTextInput("x".repeat(700))
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("x".repeat(GeneratedAvatarForms.MAX_REQUEST))))
        field.performTextReplacement("   a silver moon robot   ")
        rule.onNodeWithTag("generated-avatar-create").performScrollTo().performClick()
        assertEquals(listOf("a silver moon robot"), requests)
        field.performScrollTo().performTextReplacement("     ")
        rule.onNodeWithTag("generated-avatar-create").performScrollTo().assertIsNotEnabled()
    }

    @Test fun rotationRestoresDraftWithoutStartingGeneration() {
        val restoration = StateRestorationTester(rule)
        var requests = 0
        restoration.setContent { Frame {
            GeneratedAvatarControls(null, emptyList(), false, null, false, { requests++ }, {}, {}, {})
        } }
        rule.onNodeWithTag("generated-avatar-request").performScrollTo().performTextInput("A jellyfish astronaut")
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("generated-avatar-request").assertTextContains("A jellyfish astronaut")
        assertEquals(0, requests)
    }

    @Test fun generationOffersCancelAndAllowsSavedFormSelection() {
        var cancelled = 0
        var selected: String? = null
        val requests = mutableListOf<String>()
        var busy by mutableStateOf(false)
        rule.setContent { Frame {
            GeneratedAvatarControls(dragon, listOf(dragon, cup), busy, "Creating a new form…", false,
                { requests += it }, { cancelled++ }, { selected = it }, {})
        } }
        rule.onNodeWithText("Tiny spaceship").performScrollTo().performClick()
        rule.runOnIdle { busy = true }
        rule.onNodeWithTag("generated-avatar-create").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("generated-avatar-request").assertIsNotEnabled()
        rule.onNodeWithTag("generated-avatar-progress").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("generated-avatar-cancel").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, cancelled)
        rule.onNodeWithTag("generated-avatar-select-cup").performScrollTo().assertIsEnabled().performClick()
        assertEquals("cup", selected)
        assertTrue(requests.isEmpty())
        render("creating-with-saved-forms")
    }

    @Test fun failureKeepsDraftAndCurrentFormAvailableForRetry() {
        var busy by mutableStateOf(false)
        var failure by mutableStateOf(false)
        val requests = mutableListOf<String>()
        rule.setContent { Frame {
            GeneratedAvatarControls(dragon, listOf(dragon), busy,
                if (failure) "The selected model could not create a form. Your avatar is unchanged." else null,
                failure, { requests += it }, {}, {}, {})
        } }
        rule.onNodeWithTag("generated-avatar-request").performScrollTo().performTextInput("An emerald jellyfish")
        rule.onNodeWithTag("generated-avatar-create").performScrollTo().performClick()
        rule.runOnIdle { busy = true }
        rule.runOnIdle { busy = false; failure = true }
        rule.onNodeWithTag("generated-avatar-message").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("generated-avatar-select-dragon").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("generated-avatar-request").assertTextContains("An emerald jellyfish")
        rule.onNodeWithTag("generated-avatar-create").performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf("An emerald jellyfish", "An emerald jellyfish"), requests)
        render("generation-failure-retry")
    }

    @Test fun savedFormsUseStableIdsAndHaveSeparateRemovalActions() {
        val selected = mutableListOf<String>()
        val removed = mutableListOf<String>()
        rule.setContent { Frame {
            GeneratedAvatarControls(dragon, listOf(dragon, cup), false, null, false, {}, {}, { selected += it }, { removed += it })
        } }
        rule.onNodeWithTag("generated-avatar-select-dragon").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("generated-avatar-select-cup").performScrollTo().assertIsNotSelected().performClick()
        assertEquals(listOf("cup"), selected)
        rule.onNodeWithContentDescription("Remove Purple dragon from saved forms").performScrollTo().performClick()
        assertEquals(listOf("dragon"), removed)
        assertEquals(listOf("cup"), selected)
    }

    @Test fun generatedSelectionDoesNotClaimPresetOrShowInactiveColorControls() {
        var selectedStyle: AvatarStyle? = null
        rule.setContent { MeowmbaTheme(darkTheme = true) {
            AvatarScreen(style = AvatarStyle.LUMEN, animal = AvatarAnimal.WIZARD,
                animations = emptyList(), onSelectStyle = { selectedStyle = it }, onPlayAnimation = {}, onClose = {},
                generatedAvatarForm = dragon, savedGeneratedForms = listOf(dragon))
        } }
        rule.onNodeWithText("Lumen · Purple dragon").assertExists()
        rule.onNodeWithText("Eye color").assertDoesNotExist()
        rule.onNodeWithText("Lumen").performScrollTo().assertIsNotSelected().performClick()
        assertEquals(AvatarStyle.LUMEN, selectedStyle)
        rule.onNodeWithText(AvatarAnimal.WIZARD.label).performScrollTo().assertIsNotSelected()
    }

    @Test @Config(qualifiers = "w768dp-h420dp")
    fun landscapeLargeTextKeepsDraftCancelAndSavedActionsReachable() {
        rule.setContent { Frame(largeText = true) {
            GeneratedAvatarControls(dragon, listOf(dragon, cup), true, "Creating a tiny spaceship…", false, {}, {}, {}, {})
        } }
        rule.onNodeWithTag("generated-avatar-request").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("generated-avatar-cancel").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("generated-avatar-select-cup").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Remove Walking coffee cup from saved forms").performScrollTo().assertIsDisplayed()
        render("saved-forms-landscape-large-text")
    }

    private fun render(name: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            java.io.File("build/reports/forms119/$name.png").apply { parentFile.mkdirs() }.outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
