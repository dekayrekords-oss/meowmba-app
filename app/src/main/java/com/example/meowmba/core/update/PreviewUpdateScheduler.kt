package com.example.meowmba.core.update

import android.content.Context
import android.os.PowerManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.meowmba.core.thermal.ThermalGovernor
import java.util.concurrent.TimeUnit

/**
 * Daily Preview update check via WorkManager. Network + battery-not-low;
 * skips when thermal is MODERATE+. Also supports a lightweight on-resume
 * gate that respects the same 24h dedupe interval.
 */
object PreviewUpdateScheduler {
    const val UNIQUE_WORK = "preview-update-check"
    private const val PERIOD_HOURS = 24L

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

    fun recover(context: Context) {
        ensureScheduled(context)
        PreviewUpdateNotifier.ensureChannel(context)
    }

    /**
     * App-resume opportunistic check. No-ops if last check was within 24h.
     * Posts a notification when a newer feed version exists (deduped by vc).
     */
    fun checkOnResume(context: Context) {
        val app = context.applicationContext
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
