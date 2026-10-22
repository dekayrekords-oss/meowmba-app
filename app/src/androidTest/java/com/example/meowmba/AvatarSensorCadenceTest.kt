package com.example.meowmba

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.meowmba.ui.companion.*
import com.example.meowmba.ui.theme.MeowmbaTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AvatarSensorCadenceTest {
    @get:Rule val rule = createComposeRule()

    @Test fun imuSamplesCannotRedrawWhenAvatarClockIsPaused() {
        val lean = mutableFloatStateOf(0f)
        val timestamp = mutableLongStateOf(1L)
        val draws = AtomicInteger()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MeowmbaTheme(darkTheme = true) {
                AvatarRenderer(state = CompanionState(avatarStyle = AvatarStyle.LUMEN),
                    onZoneClick = {}, paused = true,
                    motionLeanState = lean, motionLurchState = lean,
                    motionScreenLean = lean, motionObservedAtMs = timestamp,
                    neuralWingPower = { draws.incrementAndGet(); 0f })
            }
        }
        rule.mainClock.advanceTimeBy(2000); rule.waitForIdle()
        rule.waitUntil(2000) { draws.get() > 0 }
        draws.set(0)
        repeat(20) { index ->
            rule.runOnIdle { lean.floatValue = if (index % 2 == 0) .8f else -.8f; timestamp.longValue++ }
            rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
            android.os.SystemClock.sleep(32)
        }
        assertTrue("IMU bypassed avatar clock: ${draws.get()} draws", draws.get() <= 2)
    }
}
