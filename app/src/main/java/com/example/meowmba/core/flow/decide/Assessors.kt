package com.example.meowmba.core.flow.decide

import com.example.meowmba.core.flow.Activity
import com.example.meowmba.core.flow.Assessment
import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.Intention
import com.example.meowmba.core.flow.Judgment
import com.example.meowmba.core.flow.Situation
import com.example.meowmba.core.flow.SituationBuilder
import com.example.meowmba.core.flow.Urgency

/** Judges every open intention in the current situation. */
interface Assessor {
    val id: String
    suspend fun available(): Boolean
    /** Null = abstain; the controller moves to the next assessor in the chain. */
    suspend fun assess(situation: Situation, intentions: List<Intention>): List<Assessment>?
}

/**
 * The questions Meow Flow asks about each intention. Kept in one place so the
 * Laya calibration set, the Jev escalation and the replay tests share them.
 */
object FlowQuestions {
    const val RELEVANT = "relevant_now"
    const val CHANCE = "chance_passing"
    const val URGENCY = "urgency"

    val URGENCY_CRITERIA = linkedMapOf(
        "not_now" to "Better handled another time; nothing is lost by waiting.",
        "useful" to "Nice to do now with a small benefit; easily done later.",
        "worth_it" to "A real chance that will pass soon and fits the user's schedule.",
        "urgent" to "Delay causes a missed commitment, a cost, or a safety problem.",
    )

    fun forIntention(): List<Question> = listOf(
        Question.Noul(RELEVANT,
            "Given `activity`, `place`, `app`, `work` and `calendar`, does acting on `intention` make sense for the user right now?"),
        Question.Noul(CHANCE,
            "If the user does not act on `intention` within about 10 minutes, will this particular opportunity (for example `place_ahead` or a start time in `calendar`) be gone?"),
        Question.Choice(URGENCY, "How urgent is acting on `intention` right now?", URGENCY_CRITERIA),
    )

    fun urgencyOf(key: String): Urgency = when (key) {
        "urgent" -> Urgency.URGENT
        "worth_it" -> Urgency.WORTH_IT
        "useful" -> Urgency.USEFUL
        else -> Urgency.NOT_NOW
    }

    /** Maps one intention's answers to an [Assessment]; missing answers count as "no"/"not now". */
    fun toAssessment(intention: Intention, answers: List<Answer>, engine: String): Assessment {
        val byId = answers.associateBy { it.questionId }
        val urgencyAnswer = byId[URGENCY]
        return Assessment(
            intentionId = intention.id,
            relevantNow = byId[RELEVANT]?.probability ?: 0.0,
            chancePassing = byId[CHANCE]?.probability ?: 0.0,
            urgency = urgencyAnswer?.let { urgencyOf(it.answer) } ?: Urgency.NOT_NOW,
            urgencyConfidence = urgencyAnswer?.probability ?: 0.0,
            judgments = answers.map { it.toJudgment(intention.id, engine) },
        )
    }
}

/** Uses a typed-decision model (Laya on the phone, or Jev when the user allows it). */
class ModelAssessor(
    private val engine: DecisionEngine,
    private val builder: SituationBuilder = SituationBuilder(),
) : Assessor {
    override val id: String get() = engine.id
    override suspend fun available() = engine.available()

    override suspend fun assess(situation: Situation, intentions: List<Intention>): List<Assessment>? {
        val questions = FlowQuestions.forIntention()
        val out = ArrayList<Assessment>(intentions.size)
        for (intention in intentions) {
            val answers = engine.ask(builder.stateFor(situation, intention), questions) ?: return null
            out += FlowQuestions.toAssessment(intention, answers, engine.id)
        }
        return out
    }
}

/**
 * Deterministic fallback, always available. Conservative by design: it never
 * lets errands or leisure interrupt, and only time-bound items become urgent.
 */
class RulesAssessor : Assessor {
    override val id = "rules"
    override suspend fun available() = true

    override suspend fun assess(situation: Situation, intentions: List<Intention>): List<Assessment> =
        intentions.map { assessOne(situation, it) }

    private fun assessOne(s: Situation, i: Intention): Assessment {
        val minutes = i.dueAt?.let { (it - s.now) / 60_000.0 }
        val meeting = s.upcoming.firstOrNull { it.withPeople && it.startAt >= s.now }
            ?.takeIf { i.kind == FlowKind.PEOPLE && i.dueAt == it.startAt }
        val meetingIn = meeting?.let { (it.startAt - s.now) / 60_000.0 }
        val urgency = when {
            meetingIn != null && meetingIn <= 20 -> Urgency.URGENT
            minutes != null && minutes <= 120 -> Urgency.URGENT
            minutes != null && i.kind == FlowKind.MONEY && minutes <= 48 * 60 -> Urgency.WORTH_IT
            minutes != null && minutes <= 24 * 60 -> Urgency.WORTH_IT
            i.kind == FlowKind.ERRAND || i.kind == FlowKind.LEISURE -> Urgency.USEFUL
            else -> Urgency.USEFUL
        }
        val relevant = when (urgency) {
            Urgency.URGENT -> 0.9
            Urgency.WORTH_IT -> 0.7
            else -> if (s.activity == Activity.DRIVING) 0.2 else 0.4
        }
        // Rules never judge a passing chance: that needs a model (or a clear timed commitment).
        val chance = if (urgency == Urgency.URGENT && i.kind != FlowKind.ERRAND && i.kind != FlowKind.LEISURE) 0.8 else 0.0
        val because = when {
            meetingIn != null && meetingIn <= 20 -> "meeting with people in ${meetingIn.toInt()} min"
            minutes != null -> "due in ${minutes.toInt()} min"
            else -> "no deadline"
        }
        return Assessment(
            intentionId = i.id,
            relevantNow = relevant,
            chancePassing = chance,
            urgency = urgency,
            urgencyConfidence = 1.0,
            judgments = listOf(Judgment(i.id, FlowQuestions.URGENCY, urgency.name.lowercase(), 1.0, "rules:$because")),
        )
    }
}

/** Tries each assessor in order; the first that is available and does not abstain wins. */
class AssessorChain(private val chain: List<Assessor>) : Assessor {
    override val id: String get() = chain.joinToString(">") { it.id }
    override suspend fun available() = chain.isNotEmpty()

    /** Id of the assessor that produced the last result, for the "How Meow decided" panel. */
    var lastUsed: String? = null
        private set

    override suspend fun assess(situation: Situation, intentions: List<Intention>): List<Assessment>? {
        for (a in chain) {
            val result = try {
                if (a.available()) a.assess(situation, intentions) else null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null // a broken engine must never take Flow down; fall through to the next one
            }
            if (result != null) { lastUsed = a.id; return result }
        }
        return null
    }
}
