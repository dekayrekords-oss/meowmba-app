package com.example.meowmba

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.meowmba.ui.companion.ArtifactResultCard
import com.example.meowmba.ui.companion.GeneratedMedia
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArtifactResultUiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temp = TemporaryFolder()

    @Test fun `saved document remains readable with working buttons when no viewer opened`() {
        val file = temp.newFile("Draft.txt").apply { writeText("Meow promotion draft") }
        var opened = 0
        var files = 0
        var dismissed = 0
        compose.setContent { MaterialTheme {
            ArtifactResultCard(GeneratedMedia(file.path, GeneratedMedia.MediaType.DOCUMENT, file.name),
                "Saved successfully, but no viewer opened.", { opened++ }, { files++ }, { dismissed++ })
        } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Meow promotion draft").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Meow promotion draft").assertIsDisplayed()
        compose.onNodeWithText("Saved successfully, but no viewer opened.").assertIsDisplayed()
        compose.onNodeWithText("Open result").performClick()
        compose.onNodeWithText("Files").performClick()
        compose.onNodeWithText("Dismiss").performClick()
        assertEquals(1, opened)
        assertEquals(1, files)
        assertEquals(1, dismissed)
    }
}
