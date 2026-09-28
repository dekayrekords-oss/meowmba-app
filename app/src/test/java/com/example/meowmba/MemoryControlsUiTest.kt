package com.example.meowmba

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.core.database.MemoryEntity
import com.example.meowmba.ui.companion.CompanionState
import com.example.meowmba.ui.companion.MemoryScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemoryControlsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun correctionAndPinRequireTheVisibleUserChoice() {
        var corrected: Pair<Long, String>? = null
        var pinned: Pair<Long, Boolean>? = null
        compose.setContent { MaterialTheme { MemoryScreen(
            state = CompanionState(allMemories = listOf(MemoryEntity(id = 7, content = "Afternoon appointments"))),
            onSearch = {}, onDelete = {}, onClearAll = {}, onRetentionChange = {}, onPausedChange = {},
            onExport = { "{}" }, onClose = {}, onCorrect = { id, text -> corrected = id to text },
            onPin = { id, value -> pinned = id to value }
        ) } }
        compose.onNodeWithText("Correct", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Corrected memory").performTextReplacement("Morning appointments")
        compose.onNodeWithText("Save correction").performClick()
        compose.runOnIdle { assertEquals(7L to "Morning appointments", corrected); assertNull(pinned) }
        compose.onNodeWithText("Pin", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Keep this memory?").assertExists()
        compose.runOnIdle { assertNull(pinned) }
        compose.onNodeWithText("Pin memory").performClick()
        compose.runOnIdle { assertEquals(7L to true, pinned) }
    }
}
