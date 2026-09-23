package com.example.meowmba.flow

import com.example.meowmba.core.flow.Activity
import com.example.meowmba.core.flow.CalendarItem
import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.FlowPolicy
import com.example.meowmba.core.flow.IntentionCapture
import com.example.meowmba.core.flow.Urgency
import com.example.meowmba.core.flow.decide.Answer
import com.example.meowmba.core.flow.decide.AssessorChain
import com.example.meowmba.core.flow.decide.DecisionEngine
import com.example.meowmba.core.flow.decide.FlowQuestions
import com.example.meowmba.core.flow.decide.ModelAssessor
import com.example.meowmba.core.flow.decide.Question
import com.example.meowmba.core.flow.decide.RulesAssessor
import com.example.meowmba.flow.Fx.at
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Answers from a fixed table; records what it was asked. */
internal class ScriptedEngine(
    override val id: String = "scripted",
    private val up: Boolean = true,
    private val answer: (String, List<Question>) -> List<Answer>?,
) : DecisionEngine {
    val calls = mutableListOf<Pair<String, List<Question>>>()
    override suspend fun available() = up
    override suspend fun ask(stateJson: String, questions: List<Question>): List<Answer>? {
        calls += stateJson to questions
        return answer(stateJson, questions)
    }
}

class FlowDecideTest {

    @Test fun questionsSerializeToLayaShape() {
        val wire = FlowQuestions.forIntention().map { it.toWire().toString() }
        assertEquals(3, wire.size)
        assertTrue(wire[0].startsWith("{\"type\":\"noul\",\"instructions\":"))
        assertTrue(wire[2].contains("\"type\":\"choice\"") && wire[2].contains("\"criteria\":{\"not_now\":"))
    }

    @Test fun modelAssessorMapsAnswers() = runTest {
        val engine = ScriptedEngine { _, _ ->
            listOf(
                Answer(FlowQuestions.RELEVANT, "yes", 0.91),
                Answer(FlowQuestions.CHANCE, "yes", 0.92),
                Answer(FlowQuestions.URGENCY, "worth_it", 0.74, mapOf("worth_it" to 0.74, "urgent" to 0.1)),
            )
        }
        val a = ModelAssessor(engine).assess(Fx.drivingPastStore(), listOf(Fx.bulb))!!.single()
        assertEquals(Urgency.WORTH_IT, a.urgency)
        assertEquals(0.92, a.chancePassing, 1e-9)
        assertEquals(3, a.judgments.size)
        assertTrue(engine.calls.single().first.contains("Main Street Auto Parts"))
    }

    @Test fun chainFallsBackWhenModelAbstainsOrThrows() = runTest {
        val abstain = ScriptedEngine("laya") { _, _ -> null }
        val broken = ScriptedEngine("jev") { _, _ -> error("network") }
        val chain = AssessorChain(listOf(ModelAssessor(abstain), ModelAssessor(broken), RulesAssessor()))
        val result = chain.assess(Fx.drivingPastStore(), listOf(Fx.bulb, Fx.bill))
        assertNotNull(result)
        assertEquals("rules", chain.lastUsed)
    }

    @Test fun unavailableModelIsSkippedWithoutBeingAsked() = runTest {
        val down = ScriptedEngine("laya", up = false) { _, _ -> error("must not be called") }
        val chain = AssessorChain(listOf(ModelAssessor(down), RulesAssessor()))
        chain.assess(Fx.drivingPastStore(), listOf(Fx.bulb))
        assertTrue(down.calls.isEmpty())
    }

    @Test fun rulesNeverLetErrandsOrLeisureInterrupt() = runTest {
        val a = RulesAssessor().assess(Fx.drivingPastStore(), listOf(Fx.bulb, Fx.walk))
        assertTrue(a.all { it.chancePassing == 0.0 })
        assertTrue(a.all { !it.urgency.atLeast(Urgency.WORTH_IT) })
    }

    @Test fun rulesMakeImminentMeetingsUrgent() = runTest {
        val call = Fx.handoff.copy(id = "sam", kind = FlowKind.PEOPLE, text = "Call with Sam", dueAt = at(15, 30))
        val s = Fx.drivingPastStore().copy(now = at(15, 12), activity = Activity.STILL, opportunities = emptyList(),
            upcoming = listOf(CalendarItem("Tester check-in", at(15, 30), at(15, 50), withPeople = true)))
        val a = RulesAssessor().assess(s, listOf(call)).single()
        assertEquals(Urgency.URGENT, a.urgency)
        assertTrue(a.chancePassing >= 0.7)
    }
}

class IntentionCaptureTest {
    private val lookup = IntentionCapture.FactLookup { type, obj ->
        if (type == "fix" && obj.contains("light")) mapOf("item" to "7443 bulb") else emptyMap()
    }

    @Test fun prefilterSkipsChitChat() {
        val c = IntentionCapture(null)
        assertTrue(c.mightExpressNeed("ugh, the rear light is out again"))
        assertTrue(c.mightExpressNeed("remind me to call the dentist"))
        assertTrue(!c.mightExpressNeed("that was a great movie"))
        assertTrue(!c.mightExpressNeed("hi"))
    }

    @Test fun parseSelectsSpansNotInventedText() {
        val c = IntentionCapture(null)
        assertEquals("Fix the rear light", c.parse("Ugh, the rear light is out again.")!!.text)
        assertEquals("Buy coffee beans", c.parse("we ran out of coffee beans")!!.text)
        assertEquals("Call the dentist", c.parse("remind me to call the dentist")!!.text)
        assertNull(c.parse("I need"))
    }

    @Test fun captureCreatesIntentionWhenModelAgrees() = runTest {
        val engine = ScriptedEngine { _, _ -> listOf(Answer(IntentionCapture.Q_NEED, "yes", 0.95), Answer(IntentionCapture.Q_TYPE, "fix", 0.8)) }
        val i = IntentionCapture(engine, lookup, Fx.zone).capture("the rear light is out again", at(19, 14))!!
        assertEquals("Fix the rear light", i.text)
        assertEquals(FlowKind.ERRAND, i.kind)
        assertEquals("7443 bulb", i.facts["item"])
        assertEquals("need", i.facts[FlowPolicy.FACT_SIGNALS])
        assertTrue(i.source.startsWith("said Tue 19:14:"))
    }

    @Test fun lowConfidenceOrNoEngineCapturesNothing() = runTest {
        val unsure = ScriptedEngine { _, _ -> listOf(Answer(IntentionCapture.Q_NEED, "no", 0.4)) }
        assertNull(IntentionCapture(unsure).capture("the rear light is out again", at(19)))
        // No model (rules mode): explicit phrasing is kept, vague phrasing is not.
        assertEquals("Fix the rear light", IntentionCapture(null).capture("the rear light is out again", at(19))?.text)
        assertNull(IntentionCapture(null).capture("maybe I should call someone", at(19)))
    }
}
