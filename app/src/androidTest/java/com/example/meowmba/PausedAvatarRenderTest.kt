package com.example.meowmba

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.meowmba.ui.companion.AvatarRenderer
import com.example.meowmba.ui.companion.AvatarStyle
import com.example.meowmba.ui.companion.CompanionState
import com.example.meowmba.ui.theme.MeowmbaTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A thermal pause must also stop target smoothing, not only the ambient clock.
 * Synthetic sensor targets exercise the real Compose renderer without heating
 * the phone or overriding Android's thermal service. */
class PausedAvatarRenderTest {
    @get:Rule val rule = createComposeRule()

    @Test fun pausedSensorChangesDoNotRunVsyncSmoothing() {
        val gaze = mutableFloatStateOf(0f)
        val draws = AtomicInteger()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MeowmbaTheme(darkTheme = true) {
                AvatarRenderer(
                    state = CompanionState(avatarStyle = AvatarStyle.FULL_FLY, isCameraEnabled = true),
                    onZoneClick = {}, paused = true,
                    faceGazeX = gaze.floatValue, faceGazeY = 0f,
                    faceYawDeg = gaze.floatValue * 30f,
                    modifier = Modifier.drawWithContent { draws.incrementAndGet(); drawContent() },
                )
            }
        }
        rule.mainClock.advanceTimeBy(2000); rule.waitForIdle()
        draws.set(0)
        repeat(6) { sample ->
            rule.runOnIdle { gaze.floatValue = if (sample % 2 == 0) .6f else -.6f }
            // Synchronize each real Android draw so a single test-clock jump
            // cannot coalesce a running spring and accidentally hide its work.
            repeat(12) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
        }
        assertTrue("Paused renderer drew ${draws.get()} times for six sensor updates", draws.get() <= 24)
    }
}
