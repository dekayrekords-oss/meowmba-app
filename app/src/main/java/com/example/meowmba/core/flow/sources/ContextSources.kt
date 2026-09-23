package com.example.meowmba.core.flow.sources

import com.example.meowmba.core.car.DrivingState
import com.example.meowmba.core.flow.Activity
import com.example.meowmba.core.flow.CalendarItem
import com.example.meowmba.core.flow.Evidence
import com.example.meowmba.core.flow.EvidenceType
import com.example.meowmba.core.flow.IntentionStore
import com.example.meowmba.core.flow.Opportunity
import com.example.meowmba.core.flow.RouteFacts
import com.example.meowmba.core.flow.Signal
import com.example.meowmba.core.flow.Situation
import com.example.meowmba.core.flow.SituationProvider
import com.example.meowmba.core.flow.Surface
import com.example.meowmba.core.flow.WorkContext
import com.example.meowmba.core.flow.WorkStateDetector
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns raw phone events into [Evidence] (master plan §7.3). Pure functions so each
 * source can be unit-tested; the Android listeners that call them are wired by the
 * integrator (notification listener, usage events, geofences, DrivingDetector, calendar).
 *
 * Privacy: notification bodies are never read into evidence — only app, category and
 * sender/title (which the user already sees in the shade).
 */
object EvidenceMappers {

    fun fromDriving(state: DrivingState, at: Long): Evidence = Evidence(
        // Own key so a driving update never replaces the last known place.
        type = EvidenceType.MOVEMENT, at = at, key = "move",
        label = if (state.isDriving) "Driving" else "Stopped",
        payload = mapOf("activity" to if (state.isDriving) Activity.DRIVING.name else Activity.STILL.name,
            "speed_mph" to state.speedMph.toInt().toString()),
    )

    /** Null for notifications Flow should ignore (ongoing, media, our own, silent groups). */
    fun fromNotification(
        packageName: String, appLabel: String, category: String?, title: String?, at: Long,
        ongoing: Boolean = false, ownPackage: String = "com.example.meowmba",
    ): Evidence? {
        if (ongoing || packageName == ownPackage || packageName.startsWith("com.meow.")) return null
        if (category in IGNORED_CATEGORIES) return null
        val key = when (category) {
            "email" -> "mail"
            "msg", "social" -> "msg"
            "call", "missed_call" -> "call"
            "event", "reminder" -> "cal"
            else -> "note"
        }
        val who = title?.trim()?.takeIf { it.isNotEmpty() }?.take(40)
        return Evidence(EvidenceType.NOTIFICATION, at, key, "New from ${who ?: appLabel}",
            buildMap { put("app", appLabel); category?.let { put("category", it) }; who?.let { put("from", it) } })
    }

    fun fromForegroundApp(packageName: String, appLabel: String, at: Long, workApps: Set<String>): Evidence =
        Evidence(EvidenceType.APP, at, "app", appLabel,
            mapOf("package" to packageName, "work" to (packageName in workApps).toString()))

    fun fromPlace(name: String, entering: Boolean, at: Long): Evidence =
        Evidence(EvidenceType.PLACE, at, "loc", if (entering) name else "Left $name", mapOf("entering" to entering.toString()))

    /** Something the user said that became an intention; shows as the "need" signal. */
    fun fromCapturedNeed(label: String, at: Long): Evidence =
        Evidence(EvidenceType.SPEECH, at, "need", label)

    private val IGNORED_CATEGORIES = setOf("transport", "service", "progress", "sys", "status", "navigation")
}

/**
 * Builds the current [Situation] from the latest evidence per key plus calendar, work
 * state and opportunities. Implements [SituationProvider] for FlowController.
 */
class SituationAssembler(
    private val store: IntentionStore,
    private val work: WorkStateDetector,
    private val workContext: () -> WorkContext,
    private val detectWorkEnd: () -> Boolean,
    private val calendar: suspend (now: Long) -> List<CalendarItem>,
    private val surface: () -> Surface,
    private val route: () -> RouteFacts? = { null },
    private val opportunities: suspend (now: Long, place: String?) -> List<Opportunity> = { _, _ -> emptyList() },
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val calendarAvailable: () -> Boolean = { true },
) : SituationProvider {

    private val latest = LinkedHashMap<String, Evidence>()

    /** Feed every evidence event here (collect EvidenceBus.events). */
    @Synchronized fun observe(e: Evidence) { latest[e.key] = e }

    @Synchronized private fun snapshotEvidence() = latest.values.toList()

    override suspend fun current(now: Long): Situation {
        val ev = snapshotEvidence()
        val movement = ev.filter { it.type == EvidenceType.MOVEMENT }.maxByOrNull { it.at }
        val activity = movement?.payload?.get("activity")?.let { runCatching { Activity.valueOf(it) }.getOrNull() } ?: Activity.UNKNOWN
        val place = ev.filter { it.type == EvidenceType.PLACE && it.payload["entering"] == "true" }.maxByOrNull { it.at }?.label
        val app = ev.firstOrNull { it.key == "app" }?.label
        val events = if (calendarAvailable()) calendar(now) else emptyList()
        val estimate = work.estimate(now, workContext(), detectWorkEnd())
        return Situation(
            now = now, activity = activity, place = place, foregroundApp = app, upcoming = events,
            route = route(), signals = signals(now, ev, events, activity, place),
            work = estimate, opportunities = opportunities(now, place), surface = surface(),
        )
    }

    /**
     * Dateline, at most [MAX_SIGNALS]: where → what's open → time; newer arrivals replace the
     * least relevant slot and are marked fresh for [FRESH_MS].
     */
    internal fun signals(now: Long, ev: List<Evidence>, events: List<CalendarItem>, activity: Activity, place: String?): List<Signal> {
        val out = ArrayList<Signal>()
        val loc = when {
            activity == Activity.DRIVING -> place?.let { "Driving · $it" } ?: "Driving"
            place != null -> place
            else -> null
        }
        loc?.let { out += Signal("loc", it) }
        ev.firstOrNull { it.key == "app" }?.let { out += Signal("app", it.label, fresh = now - it.at < FRESH_MS) }
        if (calendarAvailable()) calendarSignal(now, events)?.let { out += it }
        else out += Signal("cal", "Calendar not connected")
        val arrivals = ev.filter { it.key !in setOf("loc", "move", "app", "cal") && now - it.at < RECENT_MS }.sortedByDescending { it.at }
        for (a in arrivals) {
            val s = Signal(a.key, a.label, fresh = now - a.at < FRESH_MS)
            if (out.size < MAX_SIGNALS) out += s else { out[out.lastIndex] = s; break }
        }
        return out.take(MAX_SIGNALS)
    }

    private fun calendarSignal(now: Long, events: List<CalendarItem>): Signal? {
        events.firstOrNull { it.startAt <= now && it.endAt > now }?.let {
            return Signal("cal", "${it.title.take(18)} now")
        }
        val next = events.filter { it.startAt >= now }.minByOrNull { it.startAt }
        val t = DateTimeFormatter.ofPattern("h:mm", Locale.US)
        return when {
            next == null -> Signal("cal", if (Instant.ofEpochMilli(now).atZone(zone).hour >= 17) "Nothing tonight" else "Free today")
            next.startAt - now <= 60 * 60_000L -> {
                val mins = ((next.startAt - now) / 60_000).toInt()
                Signal("cal", "${next.title.take(18)} in $mins min", fresh = mins <= 20)
            }
            else -> Signal("cal", "Free till " + t.format(Instant.ofEpochMilli(next.startAt).atZone(zone)))
        }
    }

    companion object {
        const val MAX_SIGNALS = 3
        const val FRESH_MS = 2 * 60_000L
        const val RECENT_MS = 30 * 60_000L
    }
}
