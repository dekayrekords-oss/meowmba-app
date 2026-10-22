package com.example.meowmba

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.meowmba.ui.companion.ComputerReportedResultCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComputerResultCardUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `reported output has direct action without claiming a saved file`() {
        var saves = 0
        compose.setContent { MaterialTheme {
            ComputerReportedResultCard("reports/Review.md", false) { saves++ }
        } }
        compose.onNodeWithText("Review.md").assertIsDisplayed()
        compose.onNodeWithText("Reported output · not a verified local copy").assertIsDisplayed()
        compose.onNodeWithText("Save & preview").performClick()
        assertEquals(1, saves)
    }

    @Test fun `busy action cannot create duplicate downloads`() {
        compose.setContent { MaterialTheme {
            ComputerReportedResultCard("report.md", true) { fail("Busy action dispatched") }
        } }
        compose.onNodeWithText("Save & preview").assertIsNotEnabled()
    }
}
