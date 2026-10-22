package com.example.meowmba.core.ring

import android.accessibilityservice.AccessibilityService
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RingBackDeliveryTest {
    private class Harness {
        val service = Robolectric.buildService(RingAccessibilityService::class.java).create().get()
        val ring = mock(AndroidSmartRing::class.java)
        val state = MutableStateFlow(RingState(controlsConsent = true, connected = true, paused = false,
            allApps = true, sleepEnabled = false, verifiedGestures = setOf("Single tap", "Double tap")))
        val root = mock(AccessibilityNodeInfo::class.java)
        val device = mock(InputDevice::class.java)
        private val onKey = RingAccessibilityService::class.java.getDeclaredMethod("onKeyEvent", KeyEvent::class.java)
            .apply { isAccessible = true }
        init {
            `when`(ring.state).thenReturn(state); `when`(ring.verifiedDescriptor).thenReturn("test-ring")
            `when`(root.packageName).thenReturn("test.app")
            val window = mock(AccessibilityWindowInfo::class.java)
            `when`(window.type).thenReturn(AccessibilityWindowInfo.TYPE_APPLICATION)
            `when`(window.isFocused).thenReturn(true); `when`(window.root).thenReturn(root)
            shadowOf(service).setWindows(listOf(window))
            for ((name, value) in listOf("ring" to ring, "clickTarget" to RingClickTarget(service))) {
                RingAccessibilityService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
            }
            `when`(device.name).thenReturn("R08 test"); `when`(device.descriptor).thenReturn("test-ring")
        }
        fun key(code: Int, action: Int, time: Long, down: Long = time, repeat: Int = 0): Boolean {
            val event = mock(KeyEvent::class.java)
            `when`(event.device).thenReturn(device); `when`(event.deviceId).thenReturn(1)
            `when`(event.keyCode).thenReturn(code); `when`(event.action).thenReturn(action)
            `when`(event.downTime).thenReturn(down); `when`(event.eventTime).thenReturn(time)
            `when`(event.repeatCount).thenReturn(repeat)
            return onKey.invoke(service, event) as Boolean
        }
        fun press(code: Int, time: Long, down: Long = time) {
            assertTrue(key(code, KeyEvent.ACTION_DOWN, time, down))
            assertTrue(key(code, KeyEvent.ACTION_UP, time + 5, down))
        }
        fun finish() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(451))
        fun backs(count: Int) = assertEquals(count,
            shadowOf(service).globalActionsPerformed.count { it == AccessibilityService.GLOBAL_ACTION_BACK })
    }

    @Test fun twoFastReleasedTapsPerformExactlyOneBack() {
        val h = Harness()
        h.press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 100)
        h.press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 140)
        h.backs(0); h.finish(); h.backs(1)
        verify(h.ring).recordGesture("Double tap")
    }
    @Test fun releasedPressesAreDistinctEvenWhenTheDriverReusesDownTime() {
        val h = Harness()
        h.press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 100, down = 100)
        h.press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 250, down = 100)
        h.finish(); h.backs(1)
    }
    @Test fun duplicateHeldDownAndRepeatNeverTurnOneTapIntoBack() {
        val h = Harness(); val code = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        assertTrue(h.key(code, KeyEvent.ACTION_DOWN, 100))
        assertTrue(h.key(code, KeyEvent.ACTION_DOWN, 105, down = 100))
        assertTrue(h.key(code, KeyEvent.ACTION_DOWN, 180, down = 100, repeat = 1))
        assertTrue(h.key(code, KeyEvent.ACTION_UP, 200, down = 100))
        h.finish(); h.backs(0); verify(h.ring).recordGesture("Single tap")
    }
    @Test fun overlappingMediaAliasesAreOnePressButReleasedAliasesAreTwoTaps() {
        val h = Harness()
        assertTrue(h.key(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.ACTION_DOWN, 100))
        assertTrue(h.key(KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.ACTION_DOWN, 105))
        assertTrue(h.key(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.ACTION_UP, 110, down = 100))
        assertTrue(h.key(KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.ACTION_UP, 115, down = 105))
        h.finish(); h.backs(0); verify(h.ring).recordGesture("Single tap")
        h.press(KeyEvent.KEYCODE_MEDIA_PLAY, 900)
        h.press(KeyEvent.KEYCODE_MEDIA_PAUSE, 940)
        h.finish(); h.backs(1)
    }
    @Test fun overlappingNativeBackAliasesDeliverExactlyOnce() {
        val h = Harness()
        assertTrue(h.key(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN, 100))
        assertTrue(h.key(KeyEvent.KEYCODE_ESCAPE, KeyEvent.ACTION_DOWN, 105))
        assertTrue(h.key(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP, 110, down = 100))
        assertTrue(h.key(KeyEvent.KEYCODE_ESCAPE, KeyEvent.ACTION_UP, 115, down = 105))
        h.finish(); h.backs(1)
    }
    @Test fun consecutiveNativeBackPressesEachDeliverOnceDespiteReusedDownTime() {
        val h = Harness()
        h.press(KeyEvent.KEYCODE_BACK, 100, down = 100)
        h.press(KeyEvent.KEYCODE_BACK, 300, down = 100)
        h.backs(2)
    }
    @Test fun tapPendingBeforePauseOrForegroundChangeCannotNavigateBack() {
        for (pause in listOf(true, false)) {
            val h = Harness()
            h.press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 100)
            h.press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 250)
            if (pause) h.state.value = h.state.value.copy(paused = true)
            else `when`(h.root.packageName).thenReturn("other.app")
            h.finish(); h.backs(0)
        }
    }
    @Test fun unverifiedDoubleTapNeverPerformsBack() {
        val h = Harness(); h.state.value = h.state.value.copy(verifiedGestures = setOf("Single tap"))
        h.press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 100)
        h.press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 250)
        h.finish(); h.backs(0)
    }
    @Test fun sleepingDoubleTapWakesOnlyAndTheFollowingDoubleTapGoesBack() {
        val h = Harness(); h.state.value = h.state.value.copy(sleeping = true)
        doAnswer {
            val sleeping = h.state.value.sleeping
            if (sleeping && it.getArgument<String>(0) == "Double tap") h.state.value = h.state.value.copy(sleeping = false)
            sleeping
        }.`when`(h.ring).consumeSleepingGesture(anyString())
        h.press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 100); h.press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 250)
        h.finish(); assertFalse(h.state.value.sleeping); h.backs(0)
        h.press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 900); h.press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 1050)
        h.finish(); h.backs(1)
    }
}
