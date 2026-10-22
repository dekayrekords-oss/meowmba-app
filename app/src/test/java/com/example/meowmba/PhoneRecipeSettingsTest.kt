package com.example.meowmba

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.ui.companion.PhoneRecipeSettings
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
class PhoneRecipeSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun learningSwitchAndForgetRevokeActiveRecipeEpoch() {
        val prefs = compose.activity.getSharedPreferences("phone_recipe_preferences", 0)
        prefs.edit().clear().commit()
        val file = File(compose.activity.filesDir, "phone_recipes/recipes.json")
        file.parentFile!!.mkdirs(); file.writeText("""{"version":1,"recipes":[]}""")
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { PhoneRecipeSettings() } } }
        compose.onNodeWithText("Learn search workflows").assertExists()
        compose.onNode(isToggleable()).assertIsOn().performClick()
        assertFalse(prefs.getBoolean("enabled", true)); assertEquals(1, prefs.getInt("epoch", 0))
        compose.onNodeWithText("Forget workflows").performScrollTo().performClick()
        assertFalse(file.exists()); assertEquals(2, prefs.getInt("epoch", 0))
        val output = File("build/reports/verified-agent/phone-recipe-settings.png").apply { parentFile!!.mkdirs() }
        compose.waitForIdle()
        compose.runOnIdle {
            val root = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
