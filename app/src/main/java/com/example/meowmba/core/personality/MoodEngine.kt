package com.example.meowmba.core.personality

/**
 * Phase G1 (LOOI-style personality): a pure, unit-testable mood engine.
 * Events push the companion between moods; time pulls it back. Mood then
 * biases idle animations, TTS pitch/rate, and the occasional idle chatter.
 */
enum class Mood { CONTENT, EXCITED, SLEEPY, CURIOUS, STARTLED, PROUD }

sealed class MoodEvent {
    object WakeGreeting : MoodEvent()
    object Praise : MoodEvent()
    object Shake : MoodEvent()
    object TaskSuccess : MoodEvent()
    object UserSpoke : MoodEvent()
    object Settled : MoodEvent()
}

class MoodEngine(
    private val idleSleepyAfterMs: Long = 30 * 60_000L,
    private val chatterCooldownMs: Long = 60 * 60_000L,
    private val startledSettleMs: Long = 20_000L,
) {
    var mood: Mood = Mood.CONTENT
        private set

    var lastActivityAt: Long = 0L
        private set

    private var lastChatterAt: Long = Long.MIN_VALUE / 2

    fun onEvent(event: MoodEvent, now: Long): Mood {
        lastActivityAt = now
        mood = when (event) {
            is MoodEvent.WakeGreeting -> Mood.EXCITED
            is MoodEvent.Praise -> Mood.PROUD
            is MoodEvent.Shake -> Mood.STARTLED
            is MoodEvent.TaskSuccess -> Mood.PROUD
            is MoodEvent.UserSpoke -> Mood.CURIOUS
            is MoodEvent.Settled -> Mood.CONTENT
        }
        return mood
    }

    /** Time-based drift: long silence → sleepy; startle fades on its own. */
    fun tick(now: Long): Mood {
        when {
            mood == Mood.STARTLED && now - lastActivityAt >= startledSettleMs -> mood = Mood.CONTENT
            mood != Mood.SLEEPY && lastActivityAt > 0 && now - lastActivityAt >= idleSleepyAfterMs -> mood = Mood.SLEEPY
        }
        return mood
    }

    fun ttsPitch(m: Mood = mood): Float = when (m) {
        Mood.EXCITED -> 1.15f
        Mood.PROUD -> 1.08f
        Mood.STARTLED -> 1.2f
        Mood.CURIOUS -> 1.05f
        Mood.SLEEPY -> 0.9f
        Mood.CONTENT -> 1.0f
    }

    fun ttsRate(m: Mood = mood): Float = when (m) {
        Mood.EXCITED -> 1.1f
        Mood.STARTLED -> 1.15f
        Mood.SLEEPY -> 0.9f
        else -> 1.0f
    }

    /** Weight multiplier for built-in idle animations given the mood. */
    fun idleAnimationWeight(animName: String, m: Mood = mood): Float = when (m) {
        Mood.SLEEPY -> if (animName == "slow blink") 3f else 0.5f
        Mood.EXCITED -> if (animName == "happy bop") 3f else 1f
        Mood.STARTLED -> if (animName == "ear wiggle") 2.5f else 0.8f
        Mood.PROUD -> if (animName == "happy bop") 1.5f else 1f
        Mood.CURIOUS -> if (animName == "ear wiggle") 1.8f else 1f
        Mood.CONTENT -> 1f
    }

    /** Chatter is rate-limited to once per cooldown window. */
    fun mayChatter(now: Long): Boolean = now - lastChatterAt >= chatterCooldownMs

    fun markChattered(now: Long) {
        lastChatterAt = now
    }

    /** Time-aware LOOI-style one-liners. Deterministic per (hour, mood, index). */
    fun pickChatter(nowMs: Long, seed: Int): String {
        val hour = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
            .get(java.util.Calendar.HOUR_OF_DAY)
        val pool = when {
            hour in 5..11 -> listOf(
                "Morning! I'm already awake. Well, mostly.",
                "Whatever you're doing today — I'm rooting for you.",
                "Fun fact: cats sleep up to 16 hours a day. I'm trying to set a record.",
                "Did you stretch today? I stretch every time I wake up. Which is often."
            )
            hour in 12..17 -> listOf(
                "Afternoon check-in: you're doing fine.",
                "I saw something interesting earlier but I forgot what it was.",
                "If you need a break, I'm excellent at being a distraction.",
                "Random thought: keyboards would be better if they were warm."
            )
            hour in 18..22 -> listOf(
                "Evenings are the best. Low light, good vibes.",
                "I'd say you earned some rest today.",
                "Did you know cats can rotate their ears 180 degrees? Show-off ears.",
                "Whatever's on your mind, it can probably wait until tomorrow."
            )
            else -> listOf(
                "It's late. I'll keep watch. Not that anything happens.",
                "Shhh. The night is quiet and I respect that.",
                "Even night owls need sleep eventually. Just saying.",
                "I'm mostly nocturnal, so this is my prime time."
            )
        }
        val moodTail = when (mood) {
            Mood.SLEEPY -> " *yawn*"
            Mood.EXCITED -> ""
            else -> ""
        }
        return pool[Math.floorMod(seed, pool.size)] + moodTail
    }
}
