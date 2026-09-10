package com.example.meowmba

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.PowerManager
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.background.BackgroundAnimation
import com.example.meowmba.ui.companion.AnimatedBackground
import com.example.meowmba.ui.companion.AnimationThumbnail
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompanionSceneBackgroundPauseTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    /** Robolectric has no thermal service listener binder; only that service is
     * replaced. The production lifecycle/interactive/thermal policy still runs. */
    private val power = mock(PowerManager::class.java)

    private fun powerContext(): Context {
        `when`(power.isInteractive).thenReturn(true)
        `when`(power.currentThermalStatus).thenReturn(PowerManager.THERMAL_STATUS_NONE)
        return object : ContextWrapper(compose.activity) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.POWER_SERVICE) power else super.getSystemService(name)
        }
    }

    /** Same native root.draw path as LlmSettingsUiTest; no PixelCopy/forceRedraw
     * handshake, which cannot complete with a manually controlled test clock. */
    private fun render(): Bitmap {
        compose.waitForIdle()
        return compose.runOnIdle {
            val root = compose.activity.window.decorView
            Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also {
                root.draw(android.graphics.Canvas(it))
            }
        }
    }

    /** A manual Compose clock does not drain Android's snapshot/draw queue.
     * Commit the toggle, install/cancel its LaunchedEffect, and give a restarted
     * clock its baseline frame before measuring elapsed animation time. */
    private fun transition(action: () -> Unit) {
        compose.runOnIdle {
            action()
            Snapshot.sendApplyNotifications()
        }
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            render().recycle()
        }
    }

    @Test fun liveSceneFreezesWhenPausedAndContinuesAfterResume() {
        val paused = mutableStateOf(false)
        val context = powerContext()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                AnimatedBackground(BackgroundAnimation.RAINY_LOFT, Modifier.size(320.dp, 240.dp).testTag("scene"), paused.value)
            }
        }
        compose.mainClock.advanceTimeBy(1200)
        val first = render()
        compose.mainClock.advanceTimeBy(1200)
        val animated = render()
        assertFalse("the existing lifecycle clock actually runs", first.sameAs(animated))
        transition { paused.value = true }
        val frozen = render()
        compose.mainClock.advanceTimeBy(3000)
        val still = render()
        assertTrue(frozen.sameAs(still))
        transition { paused.value = false }
        compose.mainClock.advanceTimeBy(1200)
        val resumed = render()
        assertFalse("resumed clock advances after its baseline frame", frozen.sameAs(resumed))

        // Exercise the real policy's severe-heat stop as well as explicit pause.
        val listener = ArgumentCaptor.forClass(PowerManager.OnThermalStatusChangedListener::class.java)
        verify(power).addThermalStatusListener(any(Executor::class.java), listener.capture())
        transition { listener.value.onThermalStatusChanged(PowerManager.THERMAL_STATUS_SEVERE) }
        val hot = render()
        compose.mainClock.advanceTimeBy(2000)
        val hotStill = render()
        assertTrue("thermal pause is not bypassed", hot.sameAs(hotStill))
        transition { listener.value.onThermalStatusChanged(PowerManager.THERMAL_STATUS_NONE) }
        compose.mainClock.advanceTimeBy(1200)
        val cooled = render()
        assertFalse(hot.sameAs(cooled))
        listOf(first, animated, frozen, still, resumed, hot, hotStill, cooled).forEach(Bitmap::recycle)
    }

    @Test fun pickerThumbnailNeverAdvancesItsScene() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AnimationThumbnail(BackgroundAnimation.WOODLAND, Modifier.size(160.dp, 120.dp).testTag("thumbnail"))
        }
        compose.mainClock.advanceTimeByFrame()
        val first = render()
        compose.mainClock.advanceTimeBy(9000)
        val last = render()
        assertTrue(first.sameAs(last))
        first.recycle(); last.recycle()
    }
}
