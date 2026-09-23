package com.example.meowmba.core.flow.laya

import com.example.meowmba.core.flow.Assessment
import com.example.meowmba.core.flow.Intention
import com.example.meowmba.core.flow.Situation
import com.example.meowmba.core.flow.SituationBuilder
import com.example.meowmba.core.flow.decide.Assessor
import com.example.meowmba.core.flow.decide.DecisionEngine
import com.example.meowmba.core.flow.decide.FlowQuestions
import com.example.meowmba.core.flow.decide.RulesAssessor

/**
 * Laya in the assessor chain, with a heat budget. Flow re-plans every minute and every
 * question is a full ModernBERT-large pass, so:
 *  - an intention's answers are reused while its *material* situation is unchanged
 *    (activity, place, app, work state, the item itself, due/calendar time buckets), up to [maxAgeMs];
 *  - at most [maxPerRun] intentions are (re)asked per run; the rest use a cached answer
 *    or the rules, so one run never becomes a long burst of inference.
 * Returns null (whole chain falls through to rules) only when Laya abstains outright.
 */
class LayaAssessor(
    private val engine: DecisionEngine,
    private val builder: SituationBuilder = SituationBuilder(),
    private val rules: Assessor = RulesAssessor(),
    /** Three questions per item at ~1 s each: two items is a ~6 s burst at most. */
    private val maxPerRun: Int = 2,
    /** Quiet time between bursts; within it only cached answers and rules are used. */
    private val minGapMs: Long = 3 * 60_000L,
    private val maxAgeMs: Long = 30 * 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) : Assessor {
    override val id: String get() = engine.id
    override suspend fun available() = engine.available()

    private class Cached(val key: String, val at: Long, val assessment: Assessment)
    private val cache = HashMap<String, Cached>()
    private var lastBurstAt = Long.MIN_VALUE / 2

    override suspend fun assess(situation: Situation, intentions: List<Intention>): List<Assessment>? {
        val now = clock()
        // Rules are cheap and cover whatever Laya does not reach this run.
        val fallback = rules.assess(situation, intentions).orEmpty().associateBy { it.intentionId }
        // Within the quiet gap Laya asks nothing new: cached answers where they still hold, rules elsewhere.
        var asked = if (now - lastBurstAt < minGapMs) maxPerRun else 0
        var anyModel = false
        val out = ArrayList<Assessment>(intentions.size)
        for (i in intentions) {
            val key = materialKey(situation, i)
            val hit = cache[i.id]?.takeIf { it.key == key && now - it.at < maxAgeMs }
            if (hit != null) { out += hit.assessment; anyModel = true; continue }
            if (asked < maxPerRun) {
                val answers = engine.ask(builder.stateFor(situation, i), FlowQuestions.forIntention())
                asked++
                lastBurstAt = now
                if (answers != null) {
                    val a = FlowQuestions.toAssessment(i, answers, engine.id)
                    cache[i.id] = Cached(key, now, a); out += a; anyModel = true; continue
                }
                if (!anyModel && asked == 1 && answers == null) return null // Laya unavailable: let the chain use rules
            }
            out += fallback[i.id] ?: continue
        }
        cache.keys.retainAll(intentions.map { it.id }.toSet())
        return out
    }

    /** What would change Laya's answer. Minute-level clock changes are bucketed away. */
    internal fun materialKey(s: Situation, i: Intention): String {
        fun bucket(min: Long): String = when {
            min < 0 -> "past"; min <= 15 -> "15"; min <= 60 -> "60"; min <= 180 -> "180"; else -> "later"
        }
        val due = i.dueAt?.let { bucket((it - s.now) / 60_000) } ?: "-"
        val cal = s.upcoming.filter { it.endAt >= s.now }.sortedBy { it.startAt }.take(2)
            .joinToString(",") { it.title + "@" + bucket((it.startAt - s.now) / 60_000) }
        val ahead = s.opportunities.firstOrNull { it.intentionId == i.id }?.let { "${it.placeName}:${it.distanceMeters / 250}" } ?: "-"
        return listOf(s.activity, s.place, s.foregroundApp, s.work.state, i.text, i.kind, i.status, due, cal,
            s.route?.destination, ahead, i.waitingPeople.size).joinToString("|")
    }
}
