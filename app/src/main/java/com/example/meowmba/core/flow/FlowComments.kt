package com.example.meowmba.core.flow

import com.example.meowmba.core.calendar.CalendarOccurrence
import com.example.meowmba.core.flow.sources.AppIntentions
import java.util.Locale

/** Local, explicit commands only. Card text is evidence, not an instruction to execute. */
object FlowComments {
    const val TEXT = "flow_comment"
    const val AT = "flow_comment_at"
    fun target(item: Intention): CalendarOccurrence? {
        if (!item.id.startsWith(AppIntentions.CALENDAR)) return null
        val id = item.facts["calendar_event_id"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val begin = item.facts["calendar_begin"]?.toLongOrNull() ?: return null
        val end = item.facts["calendar_end"]?.toLongOrNull()?.takeIf { it >= begin } ?: return null
        return CalendarOccurrence(id, item.facts["calendar_title"] ?: return null, begin, end)
    }

    fun requestsCancellation(raw: String): Boolean {
        val text = raw.lowercase(Locale.ROOT).trim().trimEnd('.', '!').replace(Regex("\\s+"), " ")
            .removePrefix("please ").removeSuffix(" please")
        val subject = "(?:this|it|this (?:event|even|meeting|appointment|occurrence)|the (?:event|meeting|appointment))"
        return Regex("(?:cancel $subject|$subject (?:is |was |has been |got )?cancel(?:ed|led))").matches(text)
    }

    /** Source refreshes must not erase the user's comment. */
    fun retainComment(fresh: Map<String, String>, old: Map<String, String>): Map<String, String> =
        fresh + old.filterKeys { it == TEXT || it == AT }
}
