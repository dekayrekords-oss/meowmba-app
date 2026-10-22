package com.example.meowmba.core.flow

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds the compact, redacted state a decision engine sees for one intention.
 *
 * Laya's context is 512–1024 tokens, so the state has a hard budget
 * ([maxChars] ≈ 4 chars/token). When over budget it drops, in order: extra
 * calendar items, the source quote, then facts. Secrets never enter the state.
 */
class SituationBuilder(
    private val zone: ZoneId = ZoneId.systemDefault(),
    val maxChars: Int = DEFAULT_MAX_CHARS,
) {
    private val dayTime = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US)
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun stateFor(situation: Situation, intention: Intention): String {
        var calendarLimit = 2
        var includeSource = true
        var includeFacts = true
        while (true) {
            val text = build(situation, intention, calendarLimit, includeSource, includeFacts).toString()
            if (text.length <= maxChars) return text
            when {
                calendarLimit > 0 -> calendarLimit--
                includeSource -> includeSource = false
                includeFacts -> includeFacts = false
                else -> return text.take(maxChars) // last resort; engines treat as best-effort
            }
        }
    }

    internal fun build(
        s: Situation,
        i: Intention,
        calendarLimit: Int,
        includeSource: Boolean,
        includeFacts: Boolean,
    ): JsonObject = buildJsonObject {
        put("now", fmt(dayTime, s.now))
        put("activity", s.activity.name.lowercase(Locale.ROOT))
        s.place?.let { put("place", redact(it)) }
        s.foregroundApp?.let { put("app", redact(it)) }
        put("work", s.work.state.name.lowercase(Locale.ROOT))
        if (calendarLimit > 0) {
            val next = s.upcoming.filter { it.endAt >= s.now }.sortedBy { it.startAt }.take(calendarLimit)
            if (next.isNotEmpty()) put("calendar", buildJsonArray {
                next.forEach { c ->
                    add(buildJsonObject {
                        put("title", redact(c.title)); put("at", fmt(time, c.startAt))
                        put("in_min", ((c.startAt - s.now) / 60_000).toInt())
                        if (c.withPeople) put("with_people", true)
                    })
                }
            })
        }
        s.route?.let { r ->
            putJsonObject("route") {
                put("destination", redact(r.destination)); put("eta_min", r.etaMinutes); put("buffer_min", r.bufferMinutes)
            }
        }
        s.opportunities.firstOrNull { it.intentionId == i.id }?.let { o ->
            putJsonObject("place_ahead") {
                put("name", redact(o.placeName)); put("distance_m", o.distanceMeters)
                put("detour_min", o.detourMinutes); o.open?.let { put("open", it) }; o.side?.let { put("side", it) }
                put("carries_item", o.certainty.name.lowercase(Locale.ROOT))
            }
        }
        putJsonObject("intention") {
            put("text", redact(i.text))
            put("kind", i.kind.name.lowercase(Locale.ROOT))
            i.dueAt?.let { put("due_in_min", ((it - s.now) / 60_000).toInt()) }
            if (i.waitingPeople.isNotEmpty()) put("people_waiting", i.waitingPeople.size)
            if (includeSource) put("source", redact(i.source))
            if (includeFacts) {
                val safe = i.facts.filterKeys { k -> SECRET_KEYS.none { k.contains(it, ignoreCase = true) } }
                if (safe.isNotEmpty()) putJsonObject("facts") { safe.forEach { (k, v) -> put(k, redact(v)) } }
            }
        }
    }

    private fun fmt(f: DateTimeFormatter, at: Long) = f.format(Instant.ofEpochMilli(at).atZone(zone))

    companion object {
        /** ≈ 400 tokens: leaves room for questions inside Laya's context window. */
        const val DEFAULT_MAX_CHARS = 1600

        private val SECRET_KEYS = listOf("password", "pin", "token", "secret", "account", "card", "iban", "ssn", "body")
        private val LONG_DIGITS = Regex("""\d[\d\s-]{7,}\d""")
        private val EMAIL = Regex("""[\w.+-]+@[\w-]+\.[\w.]+""")

        /** Masks account/card-like digit runs and email addresses. Short numbers (7443, 5:10) stay. */
        fun redact(text: String): String =
            text.replace(LONG_DIGITS, "[number]").replace(EMAIL, "[email]").take(240)
    }
}
