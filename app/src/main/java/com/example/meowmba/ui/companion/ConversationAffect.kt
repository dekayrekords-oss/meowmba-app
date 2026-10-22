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

    /**
     * Affective colouring of an emotion, -1..1, for the continuous drive layer.
     *
     * Deliberately shallow. This biases a simulation, it does not set a mood:
     * a failed operation should take the edge off rather than plunge the
     * companion into gloom, and praise should warm it rather than force a grin.
     * The `when` is exhaustive, so a new emotion fails to compile until it is
     * given a valence here.
     */
    fun valence(emotion: CompanionEmotion): Float = when (emotion) {
        CompanionEmotion.AFFECTIONATE -> 0.8f
        CompanionEmotion.HAPPY -> 0.7f
        CompanionEmotion.PROUD -> 0.6f
        CompanionEmotion.PLAYFUL -> 0.5f
        CompanionEmotion.RELIEVED -> 0.4f
        CompanionEmotion.UNDERSTANDING -> 0.2f
        CompanionEmotion.CURIOUS -> 0.15f
        CompanionEmotion.DETERMINED -> 0.1f
        CompanionEmotion.ATTENTIVE -> 0.05f
        CompanionEmotion.IDLE -> 0f
        CompanionEmotion.ALERT -> 0f
        CompanionEmotion.SLEEPY -> -0.05f
        CompanionEmotion.SURPRISED -> -0.1f
        CompanionEmotion.CONFUSED -> -0.35f
        CompanionEmotion.APOLOGETIC -> -0.5f
        CompanionEmotion.CONCERNED -> -0.6f
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
