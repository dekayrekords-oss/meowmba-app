package com.example.meowmba.core.context.live

import com.example.meowmba.core.flow.Activity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns the journal into prompt context (docs/MEOW_SCENARIOS.md §16):
 *  - [now]: one "right now" line for every chat turn, so Meow always answers in context;
 *  - [forQuestion]: stamped journal lines when the user asks when/where/what they said or did.
 */
object JournalRecall {
    private val WHEN = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a", Locale.US)

    private val RECALL = Regex(
        "(?i)\\b(when did i|where was i|where did i|what did i (say|tell|mention)|did i (say|tell|mention)|" +
            "remember when|last time i|what time did i|where('s| is| are) my|where did i (park|put|leave)|" +
            "what was i doing|what did i do|when was i|how long (ago|since)|yesterday|this morning|last night|earlier today)\\b")

    private val STOP = setOf("when", "where", "what", "did", "i", "say", "tell", "you", "about", "the", "a", "an", "to", "was",
        "my", "me", "is", "are", "of", "in", "on", "at", "it", "that", "this", "do", "time", "remember", "last", "mention",
        "mentioned", "said", "told", "put", "leave", "left", "how", "long", "ago", "since", "doing", "yesterday", "morning",
        "night", "earlier", "today", "i'm", "im", "for", "and", "with", "from", "we", "our")

    fun isRecallQuestion(text: String) = RECALL.containsMatchIn(text)

    /** "Right now: driving (42 mph) · near 33.41,-111.93 · charging · car audio · next: Standup Wed 9:00 AM". */
    fun now(c: ContextNow, nowMs: Long, nextEvent: String?, weather: String?): String {
        val parts = ArrayList<String>()
        parts += when (c.activity) {
            Activity.DRIVING -> "driving" + (c.speedMph?.takeIf { it >= 1 }?.let { " (${it.toInt()} mph)" } ?: "")
            Activity.WALKING -> "walking"; Activity.STILL -> "not moving"; Activity.PASSENGER -> "riding"
            Activity.UNKNOWN -> "activity unknown"
        }
        if (c.hasFix && nowMs - c.fixAt < 30 * 60_000L) parts += c.place?.let { "at $it" } ?: "near %.4f,%.4f".format(Locale.US, c.lat, c.lon)
        if (c.charging) parts += "charging"
        c.audio?.let { parts += "$it audio connected" }
        weather?.let { parts += it }
        nextEvent?.let { parts += "next: $it" }
        return "Right now (" + WHEN.format(Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())) + "): " + parts.joinToString(" · ")
    }

    /** Journal rows that answer [question], newest first, as prompt lines; empty when it isn't a recall question. */
    suspend fun forQuestion(question: String, dao: LifeJournalDao, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<String> {
        if (!isRecallQuestion(question)) return emptyList()
        val lower = question.lowercase(Locale.ROOT)
        val rows = LinkedHashMap<Long, LifeEventEntity>()
        // Parking: the last time the car was left.
        if (Regex("\\bpark(ed)?\\b|my car").containsMatchIn(lower)) dao.search("Left the car", 3).forEach { rows[it.rowId] = it }
        // Time words narrow to a window.
        window(lower, nowMs, zone)?.let { (from, to) -> dao.between(from, to).takeLast(20).forEach { rows[it.rowId] = it } }
        // Content words find what was said.
        val words = Regex("[\\p{L}\\p{N}']+").findAll(lower).map { it.value }.filter { it !in STOP && it.length > 2 }.toList()
        words.take(4).forEach { w -> dao.search(w, 6).forEach { rows[it.rowId] = it } }
        return rows.values.sortedByDescending { it.at }.take(10).map { line(it, zone) }
    }

    fun line(e: LifeEventEntity, zone: ZoneId = ZoneId.systemDefault()): String {
        val whenS = WHEN.format(Instant.ofEpochMilli(e.at).atZone(zone))
        val where = e.place ?: if (e.lat != null && e.lon != null) "near %.4f,%.4f".format(Locale.US, e.lat, e.lon) else "place unknown"
        val doing = e.activity.lowercase(Locale.ROOT).let { if (it == "driving" && e.speedMph != null) "driving ${e.speedMph.toInt()} mph" else it }
        val around = listOfNotNull(e.noise, e.audio?.let { "$it audio" }, if (e.charging) "charging" else null,
            e.app?.let { "in $it" }, e.weather).joinToString(", ")
        val what = if (e.kind == LifeEventKind.SAID.name) "you said: \"${e.text}\"" else e.text
        return "$whenS · $where · $doing" + (if (around.isNotEmpty()) " · $around" else "") + " — $what"
    }

    private fun window(lower: String, nowMs: Long, zone: ZoneId): Pair<Long, Long>? {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        fun at(d: java.time.LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
        return when {
            "yesterday" in lower -> at(today.minusDays(1), 0) to at(today, 0)
            "last night" in lower -> at(today.minusDays(1), 18) to at(today, 6)
            "this morning" in lower -> at(today, 4) to at(today, 12)
            "earlier today" in lower || "today" in lower -> at(today, 0) to nowMs
            else -> null
        }
    }
}
