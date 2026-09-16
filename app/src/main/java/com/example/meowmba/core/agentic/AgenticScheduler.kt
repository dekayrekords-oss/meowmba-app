package com.example.meowmba.core.agentic

import android.content.Context
import android.os.PowerManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.meowmba.core.config.FeatureFlags
import com.example.meowmba.core.thermal.ThermalGovernor
import java.util.concurrent.TimeUnit

/**
 * Background ticker for open agentic goals when `agentic_runtime_v1` is ON.
 *
 * Uses WorkManager periodic work (15 min minimum, same pattern as
 * [com.example.meowmba.core.workflows.WorkflowRuntime]). Respects:
 * - Feature flag OFF → cancel unique work / no-op
 * - Thermal MODERATE+ → skip tick (retry later)
 * - Battery-not-low constraint
 * - Per-goal budgets enforced inside [AgenticRuntime.tickOpenGoals]
 *
 * Not a 24/7 daemon: force-stop suspends WorkManager until the user opens
 * the app again (Android platform limit — documented honestly).
 */
object AgenticScheduler {
    const val UNIQUE_WORK = "agentic-runtime-tick"
    private const val PERIOD_MINUTES = 15L

    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        val flags = FeatureFlags.from(app)
        val manager = WorkManager.getInstance(app)
        if (!flags.agenticRuntimeV1) {
            manager.cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<AgenticTickWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .addTag(UNIQUE_WORK)
            .build()
        manager.enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK)
    }

    /** Call from Application / MainActivity recover paths. */
    fun recover(context: Context) {
        ensureScheduled(context)
    }
}

class AgenticTickWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val flags = FeatureFlags.from(app)
        if (!flags.agenticRuntimeV1) {
            AgenticScheduler.cancel(app)
            return Result.success()
        }
        val thermal = ThermalGovernor.currentStatus()
        if (thermal >= PowerManager.THERMAL_STATUS_MODERATE) {
            return Result.retry()
        }
        val power = app.getSystemService(PowerManager::class.java)
        if (power?.isPowerSaveMode == true) {
            return Result.retry()
        }
        return try {
            val runtime = AgenticRuntime.from(app)
            if (!runtime.enabled) return Result.success()
            runtime.tickOpenGoals()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
