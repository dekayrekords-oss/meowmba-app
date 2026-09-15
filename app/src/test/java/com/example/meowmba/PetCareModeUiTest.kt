package com.example.meowmba

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.core.behavior.PetCareState
import com.example.meowmba.ui.companion.PetCarePanel
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PetCareModeUiTest {
    @get:Rule val rule=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test @Config(qualifiers="w320dp-h720dp") fun careCanSwitchSpacesWithoutChangingCarePreferences() = exercise(false)
    @Test @Config(qualifiers="w840dp-h400dp") fun landscapeCareCanReturnToHabitat() = exercise(true)
    private fun exercise(dark:Boolean) {
        var habitat by mutableStateOf(true)
        var changes=0
        var careChanges=0
        rule.setContent { MeowmbaTheme(darkTheme=dark) {
            PetCarePanel(care=PetCareState(),onCareChanged={careChanges++},onAction={},onFeedback={},
                onLearningChanged={},learningEnabled=false,onResetLearning={},habitatMode=habitat,
                onHabitatMode={habitat=it;changes++})
        } }
        rule.onNodeWithText("Care").performClick()
        rule.onNodeWithText("Habitat").assertIsSelected()
        rule.runOnIdle {
            val root=rule.activity.window.decorView
            val b=android.graphics.Bitmap.createBitmap(root.width,root.height,android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(b))
            val out=java.io.File("build/reports/habitat134/care-${if(dark)"dark-landscape" else "light-compact"}.png")
            out.parentFile?.mkdirs()
            out.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            b.recycle()
        }
        repeat(3) {
            rule.onNodeWithText("Backdrop").performScrollTo().performClick().assertIsSelected()
            assertFalse(habitat)
            rule.onNodeWithText("Habitat").performClick().assertIsSelected()
            assertTrue(habitat)
        }
        assertEquals(6,changes)
        assertEquals(0,careChanges)
        rule.onNodeWithText("Close").performClick()
        rule.onNodeWithText("Care").assertIsDisplayed()
    }
}
