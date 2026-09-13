package com.example.meowmba

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.core.neural.*
import com.example.meowmba.ui.companion.*
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
class CompanionSetupUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun neuralChoicesExplainAuthorityAndOnlyChangeOnSelection() {
        var selectedMode: NeuralMode? = null
        var selectedCore: NeuralSubstrate? = null
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                NeuralSettingsSection(showNeuralCoreControls = true,
                    onModeChanged = { selectedMode = it }, onSubstrateChanged = { selectedCore = it })
            }
        } }
        assertNull(selectedMode); assertNull(selectedCore)
        render("neural-setup-dark")
        rule.onNodeWithText("Shadow · compare").assertExists()
        rule.onNodeWithText("Neural · control").performScrollTo().performClick()
        assertEquals(NeuralMode.NEURAL, selectedMode)
        rule.onNodeWithText("Spiking · experimental").performScrollTo().performClick()
        assertEquals(NeuralSubstrate.SPIKING, selectedCore)
        rule.onNodeWithText("Show diagnostics").performScrollTo().performClick()
        rule.onNodeWithText("Reset neural state").assertExists()
        rule.onNodeWithText("Enable agentic (tasks / SSH / model)").assertDoesNotExist()
    }

    @Test fun avatarTabsKeepEverydayControlsSeparateAndPreserveSelection() {
        var selections = 0
        rule.setContent { MaterialTheme {
            AvatarScreen(style = AvatarStyle.FULL_FLY, animations = listOf("Warm Hello"),
                onSelectStyle = { selections++ }, onPlayAnimation = {}, onClose = {})
        } }
        rule.onNodeWithText("Eye color").assertDoesNotExist()
        rule.onNodeWithText("Space").performScrollTo().performClick()
        rule.onNodeWithText("Size & play space").assertExists()
        render("avatar-space-light")
        rule.onNodeWithText("Behavior").performScrollTo().performClick()
        rule.onNodeWithText("Expression strength").assertExists()
        rule.onNodeWithText("Search all animations").assertDoesNotExist()
        rule.onNodeWithText("Animations").performScrollTo().performClick()
        rule.onNodeWithText("Search all animations").assertExists()
        assertEquals(0, selections)
    }

    private fun render(name: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            java.io.File("build/reports/$name.png").apply { parentFile.mkdirs() }.outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
