package com.example.meowmba

import android.os.Looper
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.ui.companion.CompanionTerminalTarget
import com.example.meowmba.ui.companion.rememberTerminalTargets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TerminalTargetLoadingTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun closedChatDoesNotQueryAndOpeningLoadsOffMainOnceUntilReopened() {
        val owner = Any()
        val loads = AtomicInteger()
        val ranOnMain = AtomicBoolean()
        var visible by mutableStateOf(false)
        var tick by mutableIntStateOf(0)
        rule.setContent {
            val targets by rememberTerminalTargets(owner, visible) {
                ranOnMain.set(Looper.myLooper() == Looper.getMainLooper())
                loads.incrementAndGet()
                listOf(CompanionTerminalTarget.Local())
            }
            Text("$tick: ${targets.size}")
        }
        rule.waitForIdle()
        repeat(3) { rule.runOnIdle { tick++ } }
        assertEquals(0, loads.get())
        rule.runOnIdle { visible = true }
        rule.waitForIdle()
        rule.waitUntil(5_000) { loads.get() == 1 }
        repeat(3) { rule.runOnIdle { tick++ } }
        assertEquals(1, loads.get())
        assertFalse("Profile discovery must not block the UI thread", ranOnMain.get())
        rule.runOnIdle { visible = false }
        rule.waitForIdle()
        rule.runOnIdle { visible = true }
        rule.waitForIdle()
        rule.waitUntil(5_000) { loads.get() == 2 }
        assertFalse(ranOnMain.get())
    }
}
