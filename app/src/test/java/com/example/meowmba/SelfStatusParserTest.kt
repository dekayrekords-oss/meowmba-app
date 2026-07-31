package com.example.meowmba

import com.example.meowmba.core.agent.SelfStatusParser
import com.example.meowmba.core.agent.SelfStatusParser.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * These questions must never reach the model. It cannot inspect its own runtime, so it
 * answers with a promise to go and look — measured on device as "I'm looking at your
 * device right now to tell you what model is running", followed by nothing at all.
 */
class SelfStatusParserTest {

    @Test
    fun `asks which model`() {
        listOf(
            "what model are you using",
            "What model are you using?",
            "which model are you running",
            "what's your model",
            "hey, what model is this?",
            "what model just answered",
        ).forEach { assertEquals(it, Question.MODEL, SelfStatusParser.parse(it)) }
    }

    @Test
    fun `asks where it runs`() {
        listOf(
            "are you running locally",
            "are you local or cloud",
            "what are you running on",
            "are you on device",
        ).forEach { assertEquals(it, Question.WHERE, SelfStatusParser.parse(it)) }
    }

    @Test
    fun `leaves everything else alone`() {
        listOf(
            "what model of car should I buy",
            "download a new model",
            "what's the weather",
            "are you there",
            "who are you",
            "what can you do",
        ).forEach { assertNull("must not hijack: $it", SelfStatusParser.parse(it)) }
    }
}
