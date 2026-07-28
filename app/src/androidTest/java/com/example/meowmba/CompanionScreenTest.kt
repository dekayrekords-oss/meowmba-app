package com.example.meowmba

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.meowmba.ui.companion.CompanionScreen
import com.example.meowmba.ui.companion.CompanionViewModel
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Rule
import org.junit.Test

class CompanionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun micButtonClick_showsListeningCaption() {
        val viewModel = CompanionViewModel()
        
        composeTestRule.setContent {
            MeowmbaTheme(darkTheme = true) {
                CompanionScreen(viewModel = viewModel)
            }
        }

        // Initially no caption
        composeTestRule.onNodeWithText("Listening...").assertDoesNotExist()

        // Click mic
        composeTestRule.onNodeWithContentDescription("Microphone").performClick()

        // Should show listening caption
        composeTestRule.onNodeWithText("Listening...").assertExists()
    }
}

// Helper extension if not found
private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.onNodeWithContentDescription(label: String) = 
    onNode(androidx.compose.ui.test.hasContentDescription(label))
