package com.example.meowmba.ui.companion

import com.example.meowmba.core.personality.Mood
import java.util.Locale

/**
 * Bounded local acting cues from explicit words and task outcomes. This does not
 * diagnose a user's mood from their face/voice. Ordinary speech keeps a neutral
 * attentive manner; an apology or failed operation must never force a grin.
 */
internal object ConversationAffect {
    fun mood(mood: Mood): CompanionEmotion = when (mood) {
        Mood.CONTENT -> CompanionEmotion.IDLE
        Mood.EXCITED -> CompanionEmotion.HAPPY
        Mood.SLEEPY -> CompanionEmotion.SLEEPY
        Mood.CURIOUS -> CompanionEmotion.CURIOUS
        Mood.STARTLED -> CompanionEmotion.SURPRISED
        Mood.PROUD -> CompanionEmotion.PROUD
    }

    private val thanks = Regex("^(?:thank you|thanks|well done|good job|nice work|great job|awesome)[!. ,]*$")
    private val affection = Regex("^(?:i )?love you[!. ,]*$")
    private val concern = Regex("^i(?: feel| am|['’]m) (?:sad|upset|scared|worried|overwhelmed)\\b")
    private val apology = Regex("^(?:i(?:['’]m| am) (?:really )?sorry|sorry|my mistake|i made a mistake)\\b")
    private val unsure = Regex("^(?:i(?:'m| am) not sure|i don(?:'t|’t) know|i can(?:'t|not) (?:confirm|verify)|could you clarify)\\b")
    private val failure = Regex("^(?:i (?:couldn['’]t|can(?:['’]t|not)) (?:open|find|read|write|save|send|connect|complete|finish|access|download|load|hear|understand|do|perform)|unable to|failed to|there (?:was|is) (?:an? )?(?:error|problem)|no .{0,25}permission)\\b")

    fun user(text: String): CompanionEmotion {
        val words = text.trim().lowercase(Locale.ROOT)
        return when {
            affection.matches(words) -> CompanionEmotion.AFFECTIONATE
            thanks.matches(words) -> CompanionEmotion.PROUD
            concern.containsMatchIn(words) -> CompanionEmotion.CONCERNED
            else -> CompanionEmotion.CURIOUS
        }
    }

    fun reply(text: String, current: CompanionEmotion): CompanionEmotion {
        val words = text.trim().lowercase(Locale.ROOT).take(240)
        return when {
            apology.containsMatchIn(words) -> CompanionEmotion.APOLOGETIC
            unsure.containsMatchIn(words) -> CompanionEmotion.CONFUSED
            failure.containsMatchIn(words) -> CompanionEmotion.CONCERNED
            current == CompanionEmotion.CURIOUS || current == CompanionEmotion.ALERT ||
                current == CompanionEmotion.ATTENTIVE -> CompanionEmotion.UNDERSTANDING
            else -> current
        }
    }
}
