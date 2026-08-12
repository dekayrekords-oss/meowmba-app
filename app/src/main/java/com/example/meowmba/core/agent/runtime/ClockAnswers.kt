package com.example.meowmba.core.agent.runtime

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Deterministic answers to "what time is it" / "what day is it".
 *
 * The chat model had no clock: asked for the time it answered from training
 * data — confidently, and wrong. These questions are the phone's job, not the
 * model's, so they are recognized here and answered from [System.currentTimeMillis]
 * in the device timezone, with no model call in the path at all. Pure and
 * unit-testable; the ViewModel supplies the instant and zone.
 */
object ClockAnswers {

    /** Phrases that fix the question as asking the time. */
    private val TIME_PATTERNS = listOf(
        "what time is it", "what time is it now", "what time is it right now",
        "what's the time", "whats the time", "what is the time",
        "tell me the time", "current time", "the current time",
    )

    /** Phrases that fix the question as asking the day or date. */
    private val DATE_PATTERNS = listOf(
        "what day is it", "what day is today", "what day is it today",
        "what day is this", "what is today", "what's today", "whats today",
        "what date is it", "what is the date", "what's the date", "whats the date",
        "what is today's date", "what's today's date", "whats today's date",
        "today's date", "todays date",
    )

    /** Short bare forms, only trusted when the utterance is basically just that. */
    private val SHORT_PATTERNS = listOf(
        "what time", "the time", "what day", "what date",
    )

    private const val MAX_SHORT_WORDS = 4

    /** Only these words legitimately follow "in" in a bare clock question ("in right now"). */
    private val IN_FUNCTION_WORDS = setOf("it", "now", "right")

    /**
     * "what time is it IN NEW YORK" asks about another timezone — the device clock
     * cannot answer that, so it falls through to the model instead of being given
     * the local time as if it were the place's. Any non-function word after "in"
     * is treated as a location hint.
     */
    private fun mentionsOtherTimezone(t: String): Boolean {
        val inIdx = t.indexOf(" in ")
        if (inIdx < 0) return false
        val after = t.substring(inIdx + 4).trim()
        val firstWord = after.substringBefore(' ').trim()
        return firstWord.isNotEmpty() && firstWord !in IN_FUNCTION_WORDS
    }

    fun isClockQuestion(normalized: String): Boolean {
        val t = normalized.trim()
        if (t.isEmpty() || mentionsOtherTimezone(t)) return false
        if (TIME_PATTERNS.any { t.contains(it) } || DATE_PATTERNS.any { t.contains(it) }) {
            return true
        }
        val words = t.split(Regex("\\s+")).size
        return words <= MAX_SHORT_WORDS && SHORT_PATTERNS.any { t.contains(it) }
    }

    fun isTimeQuestion(normalized: String): Boolean =
        TIME_PATTERNS.any { normalized.contains(it) }

    fun isDateQuestion(normalized: String): Boolean =
        DATE_PATTERNS.any { normalized.contains(it) }

    /** Spoken answer from the given instant, in the given timezone. */
    fun answer(normalized: String, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val date = Date(nowMs)
        return if (isTimeQuestion(normalized) && !isDateQuestion(normalized)) {
            "It's " + SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = zone }
                .format(date) + "."
        } else {
            "Today is " + SimpleDateFormat("EEEE, MMMM d", Locale.US).apply { timeZone = zone }
                .format(date) + "."
        }
    }
}
