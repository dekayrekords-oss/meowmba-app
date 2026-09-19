package com.example.meowmba.core.update

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.meowmba.core.thermal.ThermalGovernor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Daily Preview update check via WorkManager. Network + battery-not-low;
 * skips when thermal is MODERATE+. Also supports a lightweight on-resume
 * gate that respects the same 24h dedupe interval.
 *
 * Launch safety: never touch the network or WorkManager on the main thread
 * during cold start. A synchronous HTTP check in Activity.onResume with an
 * 8s connect timeout ANRs / looks like a launch crash when apk-serve is slow.
 */
object PreviewUpdateScheduler {
    const val UNIQUE_WORK = "preview-update-check"
    private const val PERIOD_HOURS = 24L
    private val resumeInFlight = AtomicBoolean(false)
    private val bg = Executors.newSingleThreadExecutor { r ->
        Thread(r, "preview-update-check").apply { isDaemon = true }
    }

    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        val request = PeriodicWorkRequestBuilder<PreviewUpdateWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .addTag(UNIQUE_WORK)
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Deferred recover for [Application.onCreate]: posts off the critical
     * path so a WorkManager / channel failure cannot block process start.
     */
    fun recover(context: Context) {
        val app = context.applicationContext
        val run = Runnable {
            runCatching {
                ensureScheduled(app)
                PreviewUpdateNotifier.ensureChannel(app)
            }
        }
        // Prefer a background thread; fall back to a posted main-thread task
        // only if the executor rejects (should not happen).
        runCatching { bg.execute(run) }.onFailure {
            Handler(Looper.getMainLooper()).post(run)
        }
    }

    /**
     * App-resume opportunistic check. Always runs off the main thread.
     * No-ops if last check was within 24h or another check is in flight.
     */
    fun checkOnResume(context: Context) {
        val app = context.applicationContext
        if (!resumeInFlight.compareAndSet(false, true)) return
        runCatching {
            bg.execute {
                try {
                    runCheck(app)
                } finally {
                    resumeInFlight.set(false)
                }
            }
        }.onFailure {
            resumeInFlight.set(false)
        }
    }

    private fun runCheck(app: Context) {
        runCatching {
            val store = PreviewUpdateStore(app)
            val now = System.currentTimeMillis()
            if (store.lastCheckEpochMs > 0L &&
                now - store.lastCheckEpochMs < PreviewUpdateStore.MIN_AUTO_CHECK_INTERVAL_MS
            ) {
                return
            }
            val thermal = ThermalGovernor.currentStatus()
            if (thermal >= PowerManager.THERMAL_STATUS_MODERATE) return
            val power = app.getSystemService(PowerManager::class.java)
            if (power?.isPowerSaveMode == true) return
            when (val result = PreviewUpdateChecker(app, store).check(force = false, nowMs = now)) {
                is PreviewUpdateResult.Available ->
                    PreviewUpdateNotifier.notifyIfNew(app, result, store)
                else -> Unit
            }
        }
    }
}

class PreviewUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val thermal = ThermalGovernor.currentStatus()
        if (thermal >= PowerManager.THERMAL_STATUS_MODERATE) return Result.retry()
        val power = app.getSystemService(PowerManager::class.java)
        if (power?.isPowerSaveMode == true) return Result.retry()
        return try {
            val store = PreviewUpdateStore(app)
            when (val result = PreviewUpdateChecker(app, store).check(force = true)) {
                is PreviewUpdateResult.Available -> {
                    PreviewUpdateNotifier.notifyIfNew(app, result, store)
                    Result.success()
                }
                is PreviewUpdateResult.UpToDate -> Result.success()
                is PreviewUpdateResult.Error -> Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
