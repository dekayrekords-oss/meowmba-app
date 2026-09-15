package com.example.meowmba

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CartoonMenuSurfacesTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun lightDropdownAndDialogRetainDismissalAndDisabledActions() = check(false)
    @Test fun darkDropdownAndDialogRetainDismissalAndDisabledActions() = check(true)
    private fun check(dark: Boolean) {
        var calls = 0
        rule.setContent { MeowmbaTheme(darkTheme = dark) {
            var menu by remember { mutableStateOf(false) }
            var dialog by remember { mutableStateOf(false) }
            Box {
                Button({ menu = true }) { Text("Options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Review") }, { menu = false; dialog = true })
                }
            }
            if (dialog) AlertDialog(onDismissRequest = { dialog = false }, title = { Text("Review changes") },
                text = { Text("Nothing is sent until you confirm.") },
                confirmButton = { Button({ calls++ }, enabled = false) { Text("Confirm") } },
                dismissButton = { OutlinedButton({ dialog = false }) { Text("Cancel") } })
        } }
        rule.onNodeWithText("Options").performClick()
        rule.onNodeWithText("Review").assertIsDisplayed().performClick()
        rule.onNodeWithText("Review changes").assertIsDisplayed()
        rule.onNodeWithText("Confirm").assertIsNotEnabled().performClick()
        assertEquals(0, calls)
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Review changes").assertDoesNotExist()
        rule.onNodeWithText("Options").performClick()
        rule.onNodeWithText("Review").assertIsDisplayed()
    }
}
