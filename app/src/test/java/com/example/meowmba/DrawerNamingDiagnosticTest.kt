package com.example.meowmba

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.meowmba.ui.companion.AppDrawerScreen
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Temporary native diagnostic: compare an isolated Material field to the actual drawer.
 * Manual-clock cases observe frames without changing any production animation or timeout.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w375dp-h812dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawerNamingDiagnosticTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun plainMaterialNamingDialogWithDefaultClock() = plainNaming(manual = false)

    @Test fun plainMaterialNamingDialogWithManualClock() = plainNaming(manual = true)

    private fun plainNaming(manual: Boolean) {
        rule.mainClock.autoAdvance = !manual
        val counts = Counts()
        val observer = counts.observe()
        var shown by mutableStateOf(false)
        var name by mutableStateOf("")
        try {
            rule.setContent { MaterialTheme {
                SideEffect { counts.compositions.incrementAndGet() }
                TextButton(onClick = { shown = true }) { Text("Open naming") }
                if (shown) AlertDialog(onDismissRequest = { shown = false }, title = { Text("New folder") },
                    text = {
                        SideEffect { counts.dialogCompositions.incrementAndGet() }
                        Column(Modifier.fillMaxWidth().onGloballyPositioned { counts.layouts.incrementAndGet() },
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Folder name")
                            OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, singleLine = true,
                                placeholder = { Text("e.g. Mission control") }, modifier = Modifier.fillMaxWidth().testTag("diagnostic-name"))
                            Text("${name.length}/24")
                        }
                    },
                    confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {}) { Text("Save") } },
                    dismissButton = { TextButton(onClick = { shown = false }) { Text("Cancel") } },
                )
            } }
            if (manual) rule.mainClock.advanceTimeBy(100)
            rule.onNodeWithText("Open naming").performClick()
            if (manual) rule.mainClock.advanceTimeBy(100)
            counts.print("plain after open; manual=$manual")
            rule.onNodeWithText("Save").assertIsNotEnabled()
            if (manual) {
                repeat(60) { rule.mainClock.advanceTimeByFrame() }
                counts.print("plain after 60 frames")
                println("DIAGNOSTIC focused fields=" + rule.onAllNodes(isFocused()).fetchSemanticsNodes().size)
                rule.onNodeWithTag("diagnostic-name").performTextInput("Orbit")
                rule.mainClock.advanceTimeBy(100)
                rule.onNodeWithText("Save").assertIsEnabled()
            }
        } finally {
            counts.print("plain final; manual=$manual")
            observer.dispose()
        }
    }

    @Test fun realDrawerNamingWithManualClock() {
        rule.mainClock.autoAdvance = false
        val counts = Counts()
        val observer = counts.observe()
        try {
            rule.setContent { MeowmbaTheme(darkTheme = true) {
                SideEffect { counts.compositions.incrementAndGet() }
                AppDrawerScreen(onClose = {}, installedApps = emptyList())
            } }
            rule.mainClock.advanceTimeBy(100)
            rule.onNodeWithText("Organize").performClick()
            rule.mainClock.advanceTimeBy(100)
            rule.onNodeWithText("New folder").performClick()
            rule.mainClock.advanceTimeBy(100)
            counts.print("drawer after open")
            rule.onNodeWithText("Save").assertIsNotEnabled()
            repeat(60) { rule.mainClock.advanceTimeByFrame() }
            counts.print("drawer after 60 frames")
            println("DIAGNOSTIC focused fields=" + rule.onAllNodes(isFocused()).fetchSemanticsNodes().size)
            rule.onNodeWithTag("folder-name").performTextInput("Orbit")
            rule.mainClock.advanceTimeBy(100)
            rule.onNodeWithText("Save").assertIsEnabled()
        } finally {
            counts.print("drawer final")
            observer.dispose()
        }
    }

    private class Counts {
        val compositions = AtomicInteger()
        val dialogCompositions = AtomicInteger()
        val layouts = AtomicInteger()
        val applications = AtomicInteger()
        val initialChanges = CopyOnWriteArrayList<String>()
        fun observe() = Snapshot.registerApplyObserver { changed, _ ->
            applications.incrementAndGet()
            changed.forEach { if (initialChanges.size < 25) initialChanges += it.toString().take(160) }
        }
        fun print(stage: String) {
            println("DIAGNOSTIC $stage compositions=${compositions.get()} dialogCompositions=${dialogCompositions.get()} layouts=${layouts.get()} snapshotApplies=${applications.get()}")
            println("DIAGNOSTIC initial changes=$initialChanges")
        }
    }
}
