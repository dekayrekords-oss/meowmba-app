package com.example.meowmba

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.ui.companion.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssetBrowserUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun filesTileOpensItsRealRoute() {
        var selected = ""
        compose.setContent { MaterialTheme { BackgroundTiles(true, {}, { selected = it }) } }
        compose.onNodeWithText("Files").performClick()
        assertEquals("Files", selected)
    }
    @Test fun aiSearchExplainsDataSharingBeforeCallingProvider() {
        var calls = 0
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            AssetBrowserScreen(askAi = { calls++; "{\"matches\":[]}" }, onProjects = {}, onClose = {})
        } }
        compose.onNodeWithText("AI file manager").assertExists()
        compose.onNodeWithText("Search your files").performTextInput("cat photos")
        compose.onNodeWithText("AI search").performClick()
        compose.onNodeWithText("Uses your configured AI provider", substring = true).assertExists()
        assertEquals(0, calls)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, calls)
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.waitForIdle()
        render("dark")
    }
    @Test fun lightThemeKeepsImportDiscoverable() {
        compose.setContent { MaterialTheme {
            AssetBrowserScreen(askAi = { "{\"matches\":[]}" }, onProjects = {}, onClose = {})
        } }
        compose.onNodeWithContentDescription("Add files or folder").performClick()
        compose.onNodeWithText("Import files").assertExists()
        compose.onNodeWithText("Connect phone folder").assertExists()
        compose.onNodeWithText("New folder").assertExists()
        compose.onNodeWithText("Refresh").performClick()
        render("light")
    }
    private fun render(theme: String) {
        compose.waitForIdle()
        // PixelCopy's redraw callback is unavailable in Robolectric; draw the actual View.
        compose.runOnIdle {
            val root = compose.activity.window.decorView
            val image = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(image))
            val output = File("build/reports/asset-browser-$theme.png").apply { parentFile?.mkdirs() }
            output.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
