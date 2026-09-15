package com.example.meowmba

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.ui.companion.*
import com.example.meowmba.ui.onboarding.AvatarFitPage
import com.example.meowmba.ui.onboarding.SetupAvatarPage
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Native welcome UI only: isolated state, no permission, onboarding, provider or avatar preference writes. */
class Onboarding133DeviceTest {
    @get:Rule val rule = createComposeRule()
    @Test fun pickAnimalReviewAutoFitAndAdjustOnPhone() {
        var accepted = 0f
        rule.setContent { MeowmbaTheme(darkTheme = true) {
            var animal by remember { mutableStateOf(AvatarAnimal.NONE) }
            var selected by remember { mutableStateOf(false) }
            var sizing by remember { mutableStateOf(false) }
            var adjusting by remember { mutableStateOf(false) }
            var manual by remember { mutableStateOf<Float?>(null) }
            androidx.compose.foundation.layout.BoxWithConstraints {
                val w = maxWidth.value; val h = maxHeight.value
                val fitted = autoAvatarScale(w, h, AvatarStyle.LUMEN, animal)
                if (!sizing) SetupAvatarPage(AvatarStyle.LUMEN, animal, selected, onStyle = { selected = true },
                    onAnimal = { animal = it; selected = true }, onNext = { sizing = true })
                else AvatarFitPage(AvatarStyle.LUMEN, animal, manual ?: fitted, fitted, w, h, adjusting,
                    onAdjust = { adjusting = true }, onScale = { manual = it },
                    onAuto = { manual = null; adjusting = false }, onNext = { accepted = manual ?: fitted })
            }
        } }
        rule.onNodeWithText("Fit to my screen").assertIsNotEnabled()
        rule.onNodeWithText("Rabbit").performScrollTo().performClick()
        rule.onNodeWithText("Fit to my screen").performScrollTo().performClick()
        rule.onNodeWithTag("onboarding-avatar-size").assertDoesNotExist()
        screenshot("automatic-rabbit.png")
        rule.onNodeWithText("Adjust size").performScrollTo().performClick()
        rule.onNodeWithTag("onboarding-avatar-size").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1.1f) }
        screenshot("manual-rabbit.png")
        rule.onNodeWithText("Use this size").performScrollTo().performClick()
        assertEquals(1.1f, accepted, .001f)
    }
    private fun screenshot(name: String) {
        rule.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        val bitmap = i.uiAutomation.takeScreenshot()
        val file = java.io.File(i.targetContext.getExternalFilesDir(null), "onboarding133/$name")
        file.parentFile?.mkdirs(); file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
