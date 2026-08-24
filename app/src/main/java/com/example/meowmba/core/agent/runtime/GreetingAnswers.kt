package com.example.meowmba.core.agent.runtime

import com.example.meowmba.core.audio.EnrollPhraseParser
import com.example.meowmba.ui.companion.SpeechIntents

/**
 * Instant spoken replies for "what's up" / "how are you".
 *
 * The 20M zipformer turns "yo, what's up buddy" into "Ulat's up buddy", the
 * chat model then asks what that means, and OmniRoute can sit 20s waiting
 * for a first token. These check-ins do not need a model.
 */
object GreetingAnswers {

    private val BARE = setOf(
        "yo", "hey", "hi", "hello", "sup", "wassup",
        "hey buddy", "yo buddy", "hi buddy",
    )

    /** 20M zipformer plus normal spellings of "what's up". */
    private val WHATS_UP = Regex(
        """\b(what'?s|whats|ulat'?s|ulats|oot'?s|oots|ovites|ovite'?s|wot'?s|wats)\s+up\b""",
    )

    private val HOW_ARE = Regex(
        """\b(how'?s it going|hows it going|how are you|how're you|how you doing|how ya doing)\b""",
    )

    /** Any "<word>'s up" reading. The ASR mangles "what's up" differently
     *  every session ("oot's up", "ulat's up", "no it's up"), so the pinned
     *  spellings can't keep up — accept a short unknown check-in instead. */
    private val ANY_S_UP = Regex("""\b\w+'?s\s+up\b""")

    fun isGreeting(normalized: String): Boolean {
        val t = SpeechIntents.stripAddress(normalized).trim()
        if (t.isEmpty()) return false
        if (t in BARE) return true
        val words = t.split(Regex("\\s+")).filter { it.isNotBlank() }
        // A check-in is SHORT. Without this cap, "what's up with my calendar
        // today" greeted back instead of routing to the weather/calendar path
        // — the pinned regexes match anywhere in the sentence.
        if (words.size > 5) return false
        return WHATS_UP.containsMatchIn(t) || HOW_ARE.containsMatchIn(t) ||
            ANY_S_UP.containsMatchIn(t)
    }

    private val VARIANTS: List<(String?) -> String> = listOf(
        { who -> who?.let { "Hey $it. What's up?" } ?: "Hey. What's up?" },
        { who -> who?.let { "Yo $it. What do you need?" } ?: "Yo. What do you need?" },
        { _ -> "What's happening?" },
        { who -> who?.let { "Hey $it, how's it going?" } ?: "Hey, how's it going?" },
        { who -> who?.let { "Oh hey $it." } ?: "Oh hey." },
    )
    private var pickCount = 0

    fun reply(speakerName: String?): String {
        val who = speakerName?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { EnrollPhraseParser.displayName(it) }
        // Rotate lines — the fixed "Hey. What's up?" every time read robotic
        // over a session of check-ins.
        val line = VARIANTS[pickCount++ % VARIANTS.size]
        return line(who)
    }

    /** Tests pin individual variants by index. */
    internal fun resetVariantCounter(value: Int = 0) {
        pickCount = value
    }
}
