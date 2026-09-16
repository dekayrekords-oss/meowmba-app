package com.example.meowmba.ui.companion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import com.example.meowmba.core.config.FeatureFlags
import com.example.meowmba.core.thermal.ThermalGovernor

/** One display-rate clock, consumed in draw only. Pausing preserves the phase. */
internal class SceneClock {
    private var elapsedMs by mutableStateOf(0.0)

    fun advance(deltaNanos: Long) {
        elapsedMs += deltaNanos.coerceAtLeast(0L) / 1_000_000.0
    }

    fun phase(periodMs: Int): Float = ((elapsedMs % periodMs) / periodMs).toFloat()
}

/**
 * [interacting] = a clip is performing or a face is being tracked. Ambient
 * scenes (weather, backgrounds) pass false: their cycles are seconds long and
 * use the idle cadence (~half of Avatar FPS target). Active paths admit the
 * user Avatar FPS target when cool (default ~30); LIGHT thermal halves FPS;
 * AvatarWorkPolicy still stops work at THERMAL_STATUS_MODERATE+.
 */
@Composable
internal fun rememberSceneClock(paused: Boolean, interacting: Boolean = true): SceneClock {
    val clock = remember { SceneClock() }
    val context = LocalContext.current
    val fpsTarget = FeatureFlags.from(context).avatarFpsTarget
    val active = rememberAvatarWorkAllowed() && !paused
    LaunchedEffect(active, interacting, fpsTarget) {
        if (!active) return@LaunchedEffect
        var previous = 0L
        val cadence = AvatarFrameCadence()
        while (true) {
            withFrameNanos { now ->
                if (!cadence.admit(
                        now,
                        interacting,
                        fpsTarget = fpsTarget,
                        thermalStatus = ThermalGovernor.currentStatus(),
                    )
                ) return@withFrameNanos
                if (previous != 0L) clock.advance(now - previous)
                previous = now
            }
        }
    }
    return clock
}
