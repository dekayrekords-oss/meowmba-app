package com.example.meowmba.ui.companion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/** One display-rate clock, consumed in draw only. Pausing preserves the phase. */
internal class SceneClock {
    private var elapsedMs by mutableStateOf(0.0)

    fun advance(deltaNanos: Long) {
        elapsedMs += deltaNanos.coerceAtLeast(0L) / 1_000_000.0
    }

    fun phase(periodMs: Int): Float = ((elapsedMs % periodMs) / periodMs).toFloat()
}

@Composable
internal fun rememberSceneClock(paused: Boolean): SceneClock {
    val clock = remember { SceneClock() }
    val active = rememberAvatarWorkAllowed() && !paused
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L) clock.advance(now - previous)
                previous = now
            }
        }
    }
    return clock
}
