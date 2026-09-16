package com.example.meowmba.ui.companion

import com.example.meowmba.core.audio.VoiceExpression
import java.util.Locale

enum class ExpressionIntensity(val label: String, val strength: Float) {
    SUBTLE("Subtle", .55f), NATURAL("Natural", .82f), EXPRESSIVE("Expressive", 1f);
    companion object { fun fromKey(key: String?) = entries.firstOrNull { it.name == key } ?: NATURAL }
}

enum class Playfulness(val label: String) {
    QUIET("Quiet"), BALANCED("Balanced"), LIVELY("Lively");
    companion object { fun fromKey(key: String?) = entries.firstOrNull { it.name == key } ?: BALANCED }
}

/** One bounded acting proposal for the visual rig and supported speech controls.
 * It describes the character's delivery, never a diagnosis or a robot command.
 */
data class ExpressionPlan(
    val primary: CompanionEmotion,
    val secondary: CompanionEmotion = CompanionEmotion.ATTENTIVE,
    val secondaryWeight: Float = .15f,
    val intensity: Float = .82f,
    val repairing: Boolean = false,
) {
    fun voice(): VoiceExpression {
        val amount = intensity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .82f
        val (pitch, rate) = when (primary) {
            CompanionEmotion.HAPPY, CompanionEmotion.PLAYFUL -> 1.06f to 1.04f
            CompanionEmotion.PROUD -> 1.03f to 1.02f
            CompanionEmotion.AFFECTIONATE -> 1.01f to .97f
            CompanionEmotion.CONCERNED, CompanionEmotion.APOLOGETIC -> .98f to .96f
            CompanionEmotion.CONFUSED -> 1f to .97f
            else -> 1f to 1f
        }
        return VoiceExpression(1f + (pitch - 1f) * amount, 1f + (rate - 1f) * amount)
    }
}

/** Explicit language only. Unknown context stays attentive; no extra inference call
 * blocks listening. Quiet context outlives the short expression and expires locally.
 */
internal class ConversationExpressionPlanner(private val clockMs: () -> Long) {
    private var quietUntil = Long.MIN_VALUE
    private var repairUntil = Long.MIN_VALUE
    private val correction = Regex("^(?:no[,!. ]+)?(?:that(?:['’]s| is) not what i (?:said|meant)|you (?:misheard|misunderstood)|no[,!. ]+i (?:said|meant)|please listen|stop interrupting|let me (?:speak|finish))\\b")
    private val serious = Regex("^(?:i(?:['’]m| am| feel) (?:sad|upset|scared|worried|overwhelmed|grieving)|(?:my|our) .{0,35} (?:died|passed away)|this is serious|please (?:be serious|stop joking))\\b")
    fun allowsPlay(): Boolean = clockMs() >= quietUntil
    fun user(text: String, intensity: ExpressionIntensity): ExpressionPlan {
        val words = text.trim().lowercase(Locale.ROOT).take(400)
        val now = clockMs()
        if (correction.containsMatchIn(words)) {
            quietUntil = now + 90_000L
            repairUntil = now + 30_000L
            return ExpressionPlan(CompanionEmotion.ATTENTIVE, CompanionEmotion.APOLOGETIC, .20f, intensity.strength, true)
        }
        if (serious.containsMatchIn(words) || ConversationAffect.user(text) == CompanionEmotion.CONCERNED) {
            quietUntil = now + 90_000L
            repairUntil = Long.MIN_VALUE
            return ExpressionPlan(CompanionEmotion.CONCERNED, CompanionEmotion.ATTENTIVE, .30f, intensity.strength)
        }
        repairUntil = Long.MIN_VALUE
        val feeling = ConversationAffect.user(text)
        return ExpressionPlan(if (!allowsPlay() && feeling == CompanionEmotion.CURIOUS) CompanionEmotion.ATTENTIVE else feeling,
            intensity = intensity.strength)
    }
    fun reply(text: String, current: CompanionEmotion, intensity: ExpressionIntensity): ExpressionPlan {
        val proposed = ConversationAffect.reply(text, current)
        val feeling = if (!allowsPlay() && proposed in setOf(CompanionEmotion.HAPPY, CompanionEmotion.PLAYFUL, CompanionEmotion.PROUD))
            CompanionEmotion.UNDERSTANDING else proposed
        val repair = clockMs() < repairUntil
        if (feeling in setOf(CompanionEmotion.APOLOGETIC, CompanionEmotion.CONFUSED, CompanionEmotion.CONCERNED)) {
            quietUntil = maxOf(quietUntil, clockMs() + 45_000L)
        }
        return ExpressionPlan(if (repair && feeling == CompanionEmotion.ATTENTIVE) CompanionEmotion.UNDERSTANDING else feeling,
            if (repair) CompanionEmotion.APOLOGETIC else CompanionEmotion.ATTENTIVE,
            if (repair) .15f else .12f, intensity.strength, repair)
    }
    fun interruption(intensity: ExpressionIntensity): ExpressionPlan {
        quietUntil = maxOf(quietUntil, clockMs() + 15_000L)
        return ExpressionPlan(CompanionEmotion.ATTENTIVE, intensity = intensity.strength)
    }
}
