package com.example.meowmba

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.PowerManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.meowmba.core.background.BackgroundAnimation
import com.example.meowmba.core.background.BackgroundKind
import com.example.meowmba.core.background.BackgroundSelection
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.AnimatedBackground
import com.example.meowmba.ui.companion.AnimationThumbnail
import com.example.meowmba.ui.companion.BackgroundScreen
import com.example.meowmba.ui.companion.isScenicBackground
import com.example.meowmba.ui.theme.MeowmbaTheme
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.MockedConstruction
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScenicBackgroundUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val scenes = BackgroundAnimation.entries.filter(::isScenicBackground)
    private lateinit var storage: MockedConstruction<SecureStorageManager>

    @Before fun isolateUnavailableAndroidKeyStore() { storage = mockConstruction(SecureStorageManager::class.java) }
    @After fun releasePreferencesMock() { storage.close() }

    @Test @Config(qualifiers = "w320dp-h720dp")
    fun allFiveAreSelectableInTheLightCompactPickerWithoutChangingOtherPreferences() = chooseScenes(false)

    @Test @Config(qualifiers = "w840dp-h400dp")
    fun allFiveAreSelectableInTheDarkLandscapePickerWithoutChangingOtherPreferences() = chooseScenes(true)

    private fun chooseScenes(dark: Boolean) {
        var selection by mutableStateOf(BackgroundSelection(dimPercent = 47))
        var habitat by mutableStateOf(true)
        var changes = 0
        var sizeChanges = 0
        compose.setContent {
            MeowmbaTheme(darkTheme = dark) {
                BackgroundScreen(selection, onSelect = { selection = it; changes++ }, onClose = {},
                    habitatMode = habitat, onHabitatMode = { habitat = it }, avatarScale = 2.35f,
                    onAvatarScale = { sizeChanges++ })
            }
        }
        compose.onNodeWithText("Animated").performScrollTo().performClick()
        assertEquals("browsing alone changes no selection", 0, changes)
        scenes.forEachIndexed { index, scene ->
            compose.onNode(hasText(scene.label) and hasClickAction()).performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
            assertEquals(index + 1, changes)
            assertEquals(BackgroundKind.ANIMATION, selection.kind)
            assertEquals(scene.id, selection.value)
            assertEquals(47, selection.dimPercent)
            assertFalse("backdrop selection exits play habitat", habitat)
            assertEquals(scene, BackgroundSelection.parse(selection.kind.name, selection.value, selection.dimPercent.toString()).animation)
        }
        compose.onNodeWithContentDescription("Background dimming").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Avatar size").performScrollTo().assertIsDisplayed()
        assertEquals("backdrop choices never alter avatar scale", 0, sizeChanges)
        compose.onNode(hasText(BackgroundAnimation.AURORA.label) and hasClickAction()).performScrollTo().assertIsDisplayed()
    }

    @Test fun everyNewSceneUsesTheExistingPauseLifecycleAndThermalClock() {
        val paused = mutableStateOf(false)
        val power = mock(PowerManager::class.java)
        `when`(power.isInteractive).thenReturn(true)
        `when`(power.currentThermalStatus).thenReturn(PowerManager.THERMAL_STATUS_NONE)
        val context = object : ContextWrapper(compose.activity) {
            override fun getSystemService(name: String): Any? = if (name == Context.POWER_SERVICE) power else super.getSystemService(name)
        }
        val owner = TestLifecycleOwner()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalLifecycleOwner provides owner) {
                Column { scenes.forEach { AnimatedBackground(it, Modifier.size(240.dp, 120.dp), paused.value) } }
            }
        }
        compose.mainClock.advanceTimeBy(1200)
        val first = render()
        compose.mainClock.advanceTimeBy(1200)
        val moving = render()
        assertFalse("shared production clock advances", first.sameAs(moving))
        transition { paused.value = true }
        assertFrozenFor(2400, "explicit pause")
        val pausedFrame = render()
        transition { paused.value = false }
        compose.mainClock.advanceTimeBy(1200)
        val resumed = render()
        assertFalse("resumes from paused phase", pausedFrame.sameAs(resumed))

        transition { owner.registry.currentState = Lifecycle.State.CREATED }
        assertFrozenFor(2400, "stopped lifecycle")
        val stopped = render()
        transition { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(1200)
        val restarted = render()
        assertFalse("lifecycle resumes normally", stopped.sameAs(restarted))

        val listeners = ArgumentCaptor.forClass(PowerManager.OnThermalStatusChangedListener::class.java)
        verify(power, times(5)).addThermalStatusListener(any(Executor::class.java), listeners.capture())
        transition { listeners.allValues.forEach { it.onThermalStatusChanged(PowerManager.THERMAL_STATUS_CRITICAL) } }
        assertFrozenFor(2400, "OS critical thermal protection")
        val hot = render()
        transition { listeners.allValues.forEach { it.onThermalStatusChanged(PowerManager.THERMAL_STATUS_NONE) } }
        compose.mainClock.advanceTimeBy(1200)
        val cooled = render()
        assertFalse("cooling resumes existing clock", hot.sameAs(cooled))
        listOf(first, moving, pausedFrame, resumed, stopped, restarted, hot, cooled).forEach(Bitmap::recycle)
    }

    @Test fun allFivePickerPreviewsAreFrozenWithoutAnyClockOrPowerListener() {
        val power = mock(PowerManager::class.java)
        val context = object : ContextWrapper(compose.activity) {
            override fun getSystemService(name: String): Any? = if (name == Context.POWER_SERVICE) power else super.getSystemService(name)
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                Column { scenes.forEach { AnimationThumbnail(it, Modifier.size(160.dp, 96.dp)) } }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val first = render()
        compose.mainClock.advanceTimeBy(9000)
        val later = render()
        assertTrue("picker thumbnails do not animate", first.sameAs(later))
        verifyNoInteractions(power)
        first.recycle(); later.recycle()
    }

    private fun assertFrozenFor(millis: Long, reason: String) {
        val before = render()
        compose.mainClock.advanceTimeBy(millis)
        val after = render()
        assertTrue(reason, before.sameAs(after))
        before.recycle(); after.recycle()
    }

    private fun transition(action: () -> Unit) {
        compose.runOnIdle { action(); Snapshot.sendApplyNotifications() }
        repeat(2) { compose.mainClock.advanceTimeByFrame(); render().recycle() }
    }

    private fun render(): Bitmap {
        compose.waitForIdle()
        return compose.runOnIdle {
            val root = compose.activity.window.decorView
            Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(android.graphics.Canvas(it)) }
        }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
