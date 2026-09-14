package com.example.meowmba

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.BackgroundTiles
import com.example.meowmba.ui.companion.SettingsScreen
import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.FilterChip
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CartoonInterfaceUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test @Config(qualifiers = "w320dp-h720dp")
    fun compactLightMenuKeepsAllDestinationsReachable() = menuToSettings("320-light", false)

    @Test @Config(qualifiers = "w375dp-h812dp")
    fun darkPhoneMenuKeepsAllDestinationsReachable() = menuToSettings("375-dark", true)

    @Test @Config(qualifiers = "w414dp-h896dp")
    fun largeTextMenuKeepsAllDestinationsReachable() = menuToSettings("414-large-text", false, 1.4f)

    @Test @Config(qualifiers = "w768dp-h432dp")
    fun landscapeMenuKeepsAllDestinationsReachable() = menuToSettings("768-landscape", true)

    private fun menuToSettings(name: String, dark: Boolean, fontScale: Float = 1f) {
        val storage = mock(SecureStorageManager::class.java)
        var settings by mutableStateOf(false)
        var avatarReturns = 0
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MeowmbaTheme(darkTheme = dark) {
                    if (settings) SettingsScreen(storage, onClose = { settings = false })
                    else BackgroundTiles(visible = true, onClose = { avatarReturns++ },
                        onTileClick = { if (it == "Settings") settings = true })
                }
            }
        }
        rule.onNodeWithText("Memba").assertIsDisplayed()
        render("cartoon-menu-$name")
        rule.onNodeWithContentDescription("Return to avatar").assertIsDisplayed().performClick()
        assertEquals(1, avatarReturns)
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Settings"))
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Finish setup").assertExists()
        render("cartoon-settings-$name")
        rule.onNodeWithText("Appearance").performScrollTo().performClick()
        rule.onNodeWithText("Dark").assertExists()
        rule.onNodeWithText("Light").assertExists()
        rule.onNodeWithContentDescription("Back to settings").performClick()
        rule.onNodeWithContentDescription("Close settings").performClick()
        rule.onNodeWithText("Memba").assertExists()
    }

    @Test @Config(qualifiers = "w375dp-h812dp")
    fun inkControlsRetainDisabledAndSelectionSemantics() {
        var calls = 0
        var selected by mutableStateOf(false)
        rule.setContent { MeowmbaTheme(darkTheme = false) {
            Column(Modifier.padding(20.dp)) {
                Button(onClick = { calls++ }, enabled = false) { Text("Busy") }
                Button(onClick = { calls++ }) { Text("Run") }
                FilterChip(selected, { selected = !selected }, { Text("Play habitat") })
            }
        } }
        rule.onNodeWithText("Busy").assertIsNotEnabled().performClick()
        assertEquals(0, calls)
        rule.onNodeWithText("Run").performClick()
        assertEquals(1, calls)
        rule.onNodeWithText("Play habitat").assertIsNotSelected().performClick().assertIsSelected()
        render("cartoon-control-states")
    }

    private fun render(name: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/reports/cartoon111/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
