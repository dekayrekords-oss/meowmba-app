package com.example.meowmba.core.agent.tools

import java.time.LocalDate
import java.util.Locale

/** Parse explicit cancellation, never questions, quoted reports or hypothetical/negated changes. */
object CalendarCancellation {
    data class Request(val title: String, val day: LocalDate?)
    fun parse(raw: String, today: LocalDate = LocalDate.now()): Request? {
        var text = raw.lowercase(Locale.ROOT).replace('’', '\'').trim().trimEnd('.', '!')
        if (text.contains('?') || Regex("\\b(if|unless|might|maybe|not|don't|dont|do not|isn't|isnt)\\b").containsMatchIn(text)) return null
        text = text.removePrefix("please ").removeSuffix(" please")
        val subject = Regex("^(?:cancel|cancelled|canceled) (.+)$").matchEntire(text)?.groupValues?.get(1)
            ?: Regex("^(.+?) (?:is |was |has been |got )?cancel(?:ed|led)$").matchEntire(text)?.groupValues?.get(1)
            ?: return null
        val day = when {
            Regex("\\btomorrow(?:'s)?\\b").containsMatchIn(subject) -> today.plusDays(1)
            Regex("\\btoday(?:'s)?\\b").containsMatchIn(subject) -> today
            else -> Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b").find(subject)?.value?.let {
                runCatching { LocalDate.parse(it) }.getOrNull() ?: return null
            }
        }
        val title = subject.replace(Regex("\\b(?:today|tomorrow)(?:'s)?\\b|\\b\\d{4}-\\d{2}-\\d{2}\\b"), " ")
            .replace(Regex("\\b(?:my|the|our|calendar|event|appointment|meeting|on|for)\\b"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (title.isBlank() && day == null) return null
        // Unsupported relative dates must not widen a destructive search silently.
        if (Regex("\\b(next|week|monday|tuesday|wednesday|thursday|friday|saturday|sunday|all|every|series)\\b").containsMatchIn(title)) return null
        return Request(title, day)
    }

    fun matchesTitle(request: Request, title: String): Boolean {
        val words = title.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").split(' ').toSet()
        return request.title.isBlank() || request.title.split(Regex("\\s+")).all { it in words }
    }
}
