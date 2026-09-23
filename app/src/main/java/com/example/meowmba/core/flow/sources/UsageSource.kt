package com.example.meowmba.core.flow.sources

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.example.meowmba.core.flow.Evidence
import com.example.meowmba.core.flow.WorkContext

/**
 * Which app is in front, and whether the user is in a work app (usage access, owner D10).
 * Read on demand — screen on, unlock, Flow opened, work-state check — never polled in a loop.
 * Only package names and times are used; no content.
 */
class UsageSource(
    private val context: Context,
    /** Packages the user treats as work (learned or chosen in settings). */
    private val workApps: () -> Set<String>,
) {
    private val usm get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    /** Foreground changes between [since] and [now], newest last, as app evidence. */
    fun foregroundEvidence(since: Long, now: Long): List<Evidence> {
        if (!hasAccess()) return emptyList()
        val out = ArrayList<Evidence>()
        val events = usm.queryEvents(since, now) ?: return emptyList()
        val e = UsageEvents.Event()
        var lastPkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            if (e.packageName == lastPkg || e.packageName == context.packageName) continue
            lastPkg = e.packageName
            out += EvidenceMappers.fromForegroundApp(e.packageName, label(e.packageName), e.timeStamp, workApps())
        }
        return out.takeLast(MAX_EVENTS)
    }

    /** Work signals for [com.example.meowmba.core.flow.WorkStateDetector]; other fields come from place/desktop. */
    fun workContext(now: Long, lookbackMs: Long = 12 * 60 * 60 * 1000L): WorkContext {
        if (!hasAccess()) return WorkContext()
        val work = workApps()
        val events = usm.queryEvents(now - lookbackMs, now) ?: return WorkContext()
        val e = UsageEvents.Event()
        var lastWork: Long? = null
        var inWorkNow = false
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    inWorkNow = e.packageName in work
                    if (inWorkNow) lastWork = e.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> if (e.packageName in work) { lastWork = e.timeStamp; inWorkNow = false }
            }
        }
        return WorkContext(
            minutesSinceWorkApp = when {
                inWorkNow -> 0
                lastWork != null -> ((now - lastWork!!) / 60_000).toInt()
                else -> null
            },
            activelyWorking = inWorkNow,
        )
    }

    private fun label(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))

    companion object { const val MAX_EVENTS = 5 }
}
