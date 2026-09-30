package com.example.meowmba

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.meowmba.core.database.MemoryEntity
import com.example.meowmba.ui.companion.CompanionState
import com.example.meowmba.ui.companion.MemoryScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real dialog/IME path: Robolectric's native-graphics idle loop stalls after this dialog opens. */
class MemoryControlsDeviceTest {
    @get:Rule val compose = createComposeRule()
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
