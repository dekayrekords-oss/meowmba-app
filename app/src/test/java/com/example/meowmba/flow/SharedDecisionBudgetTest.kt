package com.example.meowmba.flow

import com.example.meowmba.core.flow.decide.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SharedDecisionBudgetTest {
    @Test fun flowAndGoalQuestionsShareOneHeatBudget() = runTest {
        var now = 0L
        var count = 0
        val delegate = object : DecisionEngine {
            override val id = "fake"
            override suspend fun available() = true
            override suspend fun ask(stateJson: String, questions: List<Question>): List<Answer> {
                count += questions.size
                return questions.map { Answer(it.id, "yes", 0.9) }
            }
        }
        val budget = SharedDecisionBudget(delegate, clock = { now })
        val question = Question.Noul("q", "Is this relevant?")
        assertNotNull(budget.ask("goal", listOf(question)))
        assertNotNull(budget.ask("flow", List(3) { question }))
        assertNull(budget.ask("flow", List(3) { question }))
        assertEquals(4, count)
        now = 3 * 60_000
        assertNotNull(budget.ask("flow", List(3) { question }))
        assertEquals(7, count)
    }
}
