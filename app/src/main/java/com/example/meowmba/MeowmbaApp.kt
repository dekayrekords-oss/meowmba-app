package com.example.meowmba

import android.app.Application
import com.example.meowmba.core.agentic.AgenticScheduler
import com.example.meowmba.core.update.PreviewUpdateScheduler
import com.example.meowmba.core.thermal.ThermalGovernor

/**
 * Process-wide application entry point.
 *
 * Exists solely so [ThermalGovernor] has somewhere guaranteed to be installed.
 * `WakeWordService`, `ScreenWatchService`, `DownloadService` and
 * `MeowmbaRuntimeService` can all start this process without an activity, so
 * MainActivity.onCreate is not a valid install site — a wake-word start would
 * otherwise run the microphone with a governor that still thinks the device is
 * cold.
 */
class MeowmbaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThermalGovernor.install(this)
        // Periodic agentic ticks when flag ON (no-op / cancelled when OFF).
        runCatching { AgenticScheduler.recover(this) }
        // Schedules WorkManager + channel off the critical path (see recover).
        runCatching { PreviewUpdateScheduler.recover(this) }
    }
}
