package com.example.meowmba.ui.companion

import java.util.concurrent.atomic.AtomicLong

/** Small lifetime counters for device endurance recording; no timer, IO or frame history. */
internal object Fly3DPhoneTelemetry {
    val submitted = AtomicLong()
    val presented = AtomicLong()
    val drawWallNanos = AtomicLong()
    @Volatile var stageMode = false
    @Volatile var room = ""
    @Volatile var active = false
    @Volatile var renderPixels = 0
}
