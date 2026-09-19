package com.example.meowmba

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.meowmba.core.agentic.AgenticScheduler
import com.example.meowmba.core.update.PreviewUpdateScheduler
import com.example.meowmba.core.thermal.ThermalGovernor
import java.util.concurrent.Executors

/**
 * Process-wide application entry point.
 *
 * Exists solely so [ThermalGovernor] has somewhere guaranteed to be installed.
 * `WakeWordService`, `ScreenWatchService`, `DownloadService` and
 * `MeowmbaRuntimeService` can all start this process without an activity, so
 * MainActivity.onCreate is not a valid install site — a wake-word start would
 * otherwise run the microphone with a governor that still thinks the device is
 * cold.
 *
 * Launch safety (vc133): nothing non-critical may throw or block the main
 * thread here. WorkManager / encrypted prefs / channel setup are deferred and
 * soft-failed so one subsystem cannot kill cold start.
 */
class MeowmbaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { ThermalGovernor.install(this) }
            .onFailure { Log.w(TAG, "ThermalGovernor install failed", it) }
        // Defer WorkManager + encrypted FeatureFlags off the critical path.
        defer("agentic-recover") {
            runCatching { AgenticScheduler.recover(this@MeowmbaApp) }
                .onFailure { Log.w(TAG, "AgenticScheduler.recover failed", it) }
        }
        runCatching { PreviewUpdateScheduler.recover(this) }
            .onFailure { Log.w(TAG, "PreviewUpdateScheduler.recover failed", it) }
    }

    private fun defer(name: String, block: () -> Unit) {
        val run = Runnable {
            runCatching(block).onFailure { Log.w(TAG, "$name failed", it) }
        }
        runCatching { bg.execute(run) }.onFailure {
            Handler(Looper.getMainLooper()).post(run)
        }
    }

    companion object {
        private const val TAG = "MeowmbaApp"
        private val bg = Executors.newSingleThreadExecutor { r ->
            Thread(r, "meowmba-app-init").apply { isDaemon = true }
        }
    }
}
