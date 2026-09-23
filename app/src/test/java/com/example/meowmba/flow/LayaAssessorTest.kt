package com.example.meowmba.flow

import com.example.meowmba.core.flow.Activity
import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.Intention
import com.example.meowmba.core.flow.Situation
import com.example.meowmba.core.flow.decide.Answer
import com.example.meowmba.core.flow.decide.DecisionEngine
import com.example.meowmba.core.flow.decide.Question
import com.example.meowmba.core.flow.laya.LayaAssessor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Laya's heat budget: reuse answers, at most two items per burst, a quiet gap between bursts. */
class LayaAssessorTest {
    private class CountingEngine(var up: Boolean = true) : DecisionEngine {
        var asks = 0
        override val id = "laya"
        override suspend fun available() = up
        override suspend fun ask(stateJson: String, questions: List<Question>): List<Answer>? {
            if (!up) return null
            asks++
            return questions.map { q -> if (q is Question.Choice) Answer(q.id, "useful", 0.7) else Answer(q.id, "yes", 0.8) }
        }
    }

    private val items = (1..5).map { Intention("i$it", "Thing $it", FlowKind.OTHER, "", 0L) }
    private fun sit(now: Long, activity: Activity = Activity.STILL) = Situation(now, activity)

    @Test fun capsBurstsReusesAnswersAndWaitsBetweenBursts() = runTest {
        var now = Fx.at(9)
        val engine = CountingEngine()
        val a = LayaAssessor(engine, clock = { now })
        assertEquals(5, a.assess(sit(now), items)!!.size)   // every item gets an assessment…
        assertEquals(2, engine.asks)                         // …but only two go to Laya
        now += 60_000
        a.assess(sit(now), items)
        assertEquals(2, engine.asks)                         // inside the 3-minute gap: nothing new
        now += 3 * 60_000
        a.assess(sit(now), items)
        assertEquals(4, engine.asks)                         // next burst: the next two items
        now += 3 * 60_000
        a.assess(sit(now), items)
        assertEquals(5, engine.asks)                         // the last one; the first four are reused
        now += 3 * 60_000
        a.assess(sit(now, Activity.DRIVING), items)
        assertEquals(7, engine.asks)                         // situation changed: re-ask, still two at a time
    }

    @Test fun unavailableLayaLetsTheChainUseRules() = runTest {
        val a = LayaAssessor(CountingEngine(up = false), clock = { Fx.at(9) })
        assertNull(a.assess(sit(Fx.at(9)), items))
    }
}
