package com.example.meowmba.core.flow

import com.example.meowmba.core.flow.decide.DecisionEngine
import com.example.meowmba.core.flow.decide.Question
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Turns things the user says to Meow into tracked intentions (owner decision D6:
 * life as a whole, no required lists). Master plan §7.4.
 *
 * 1. A cheap code prefilter drops chit-chat without calling any model.
 * 2. The engine answers: does this express a need/intention? what type?
 * 3. The intention text is **selected from the utterance** by patterns, never generated.
 *    Extra facts (e.g. bulb 7443 for a car model) come from [FactLookup] tables in code.
 */
class IntentionCapture(
    private val engine: DecisionEngine?,
    private val lookup: FactLookup = FactLookup.NONE,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val threshold: Double = 0.7,
) {
    fun interface FactLookup {
        /** Facts for an intention object, e.g. "rear brake light" → {item=7443 bulb}. */
        fun factsFor(type: String, objectPhrase: String): Map<String, String>

        companion object { val NONE = FactLookup { _, _ -> emptyMap() } }
    }

    /** Fast, model-free filter. True when the sentence might express a need. */
    fun mightExpressNeed(utterance: String): Boolean {
        val t = utterance.lowercase(Locale.ROOT)
        return t.length in 6..400 && CUES.any { it.containsMatchIn(t) }
    }

    suspend fun capture(utterance: String, now: Long): Intention? {
        if (!mightExpressNeed(utterance)) return null
        val parsed = parse(utterance) ?: return null
        val engine = engine ?: return null
        val state = buildJsonObject {
            put("utterance", SituationBuilder.redact(utterance))
            put("candidate", parsed.text)
        }.toString()
        val answers = engine.ask(state, QUESTIONS) ?: return null
        val need = answers.firstOrNull { it.questionId == Q_NEED }?.probability ?: 0.0
        if (need < threshold) return null
        val type = answers.firstOrNull { it.questionId == Q_TYPE }?.answer ?: parsed.type
        val kind = KIND_BY_TYPE[type] ?: FlowKind.OTHER
        val stamp = STAMP.format(Instant.ofEpochMilli(now).atZone(zone))
        val quote = utterance.trim().take(120)
        return Intention(
            id = "cap-" + UUID.nameUUIDFromBytes("$now|$quote".toByteArray()).toString().take(8),
            text = parsed.text,
            kind = kind,
            source = "said $stamp: '$quote'",
            createdAt = now,
            facts = lookup.factsFor(type, parsed.objectPhrase) + (FlowPolicy.FACT_SIGNALS to "need"),
        )
    }

    data class Parsed(val type: String, val objectPhrase: String, val text: String)

    /** Pattern-based span selection. Returns null when no pattern yields a usable phrase. */
    fun parse(utterance: String): Parsed? {
        val t = utterance.trim().trimEnd('.', '!', '?').replace(Regex("\\s+"), " ")
        for ((type, regex, template) in PATTERNS) {
            val m = regex.find(t) ?: continue
            val obj = m.groupValues[1].trim().trimEnd(',', ';').take(60)
            if (obj.isBlank() || obj.split(' ').size > 8) continue
            return Parsed(type, obj, template(obj).replaceFirstChar { it.uppercase() })
        }
        return null
    }

    companion object {
        const val Q_NEED = "expresses_need"
        const val Q_TYPE = "intention_type"

        private val TYPES = linkedMapOf(
            "buy" to "The user needs to buy or get something.",
            "fix" to "Something is broken or out and needs fixing or replacing.",
            "reply" to "The user owes someone a reply or message.",
            "call" to "The user needs to phone someone.",
            "attend" to "The user needs to be somewhere at a time.",
            "return" to "The user needs to return or send something back.",
            "pick_up" to "The user needs to collect something or someone.",
            "other" to "Some other task the user intends to do.",
        )

        val QUESTIONS: List<Question> = listOf(
            Question.Noul(Q_NEED, "Does `utterance` express something the user needs or intends to do later (not a question, joke or passing remark)?"),
            Question.Choice(Q_TYPE, "What kind of task does `utterance` describe?", TYPES),
        )

        private val KIND_BY_TYPE = mapOf(
            "buy" to FlowKind.ERRAND, "fix" to FlowKind.ERRAND, "return" to FlowKind.ERRAND, "pick_up" to FlowKind.ERRAND,
            "reply" to FlowKind.PEOPLE, "call" to FlowKind.PEOPLE, "attend" to FlowKind.PEOPLE, "other" to FlowKind.OTHER,
        )

        private val CUES = listOf(
            Regex("""\b(need|needs|gotta|have to|must|should|remind me|don't let me forget|out of|ran out|run out)\b"""),
            Regex("""\b(is|are|'s) (out|broken|dead|busted|leaking|not working)\b"""),
            Regex("""\b(buy|pick up|return|call|reply|text|email|fix|replace|book)\b"""),
        )

        private data class Pattern(val type: String, val regex: Regex, val template: (String) -> String)

        private val PATTERNS = listOf(
            Pattern("fix", Regex("""(?i)\b(?:my|the|our)\s+(.+?)\s+(?:is|are|'s)\s+(?:out|broken|dead|busted|leaking|not working)""")) { "Fix the $it" },
            Pattern("buy", Regex("""(?i)\b(?:ran|run|we're|i'm|we are|i am)\s+out of\s+(.+)""")) { "Buy $it" },
            Pattern("buy", Regex("""(?i)\bneed to (?:buy|get|pick up)\s+(.+)""")) { "Buy $it" },
            Pattern("return", Regex("""(?i)\b(?:return|send back)\s+(?:the\s+)?(.+)""")) { "Return the $it" },
            Pattern("call", Regex("""(?i)\b(?:call|phone|ring)\s+(.+)""")) { "Call $it" },
            Pattern("reply", Regex("""(?i)\b(?:reply to|text|email|message)\s+(.+)""")) { "Reply to $it" },
            Pattern("pick_up", Regex("""(?i)\bpick up\s+(.+)""")) { "Pick up $it" },
            Pattern("other", Regex("""(?i)\bremind me to\s+(.+)""")) { it },
            Pattern("buy", Regex("""(?i)\bi need (?:a |an |some |more )?(.+)""")) { "Get $it" },
        )

        private val STAMP = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US)
    }
}
