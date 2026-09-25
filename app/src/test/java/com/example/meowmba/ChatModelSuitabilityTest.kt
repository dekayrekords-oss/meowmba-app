package com.example.meowmba

import com.example.meowmba.core.agent.ChatModelSuitability as Model
import com.example.meowmba.core.agent.OpenRouterModels
import org.junit.Assert.*
import org.junit.Test

class ChatModelSuitabilityTest {
    @Test fun classifierModelsAreNotChatChoices() {
        assertFalse(Model.isChatModel("nvidia/nemotron-3.5-content-safety"))
        assertFalse(Model.isChatModel("nvidia/llama-3.1-nemotron-safety-guard-8b-v3"))
        assertTrue(Model.isChatModel("nvidia/nemotron-nano-9b-v2"))
        assertTrue(Model.isChatModel("openrouter/free"))
        val models = OpenRouterModels.parse("""{"data":[{"id":"nvidia/nemotron-3-content-safety:free"},{"id":"vendor/chat:free"}]}""")
        assertEquals(listOf("vendor/chat:free"), models.map { it.id })
    }
    @Test fun classifierLabelsAreNotRefusalsOrOrdinaryDiscussion() {
        assertTrue(Model.isClassifierReply("User Safety: safe"))
        assertTrue(Model.isClassifierReply("User Safety: unsafe\nResponse Safety: safe\nSafety Categories: Example"))
        assertFalse(Model.isClassifierReply("I can't help with that harmful request."))
        assertFalse(Model.isClassifierReply("User safety is important to this design."))
        assertFalse(Model.isClassifierReply("The example output is User Safety: safe."))
    }
    @Test fun partialClassifierLabelsAreHeldBeforeSpeech() {
        for (prefix in listOf("User", "User Saf", "User Safety: ", "User Safety: safe")) assertTrue(Model.holdPrefix(prefix))
        assertFalse(Model.holdPrefix("User safety is a design topic."))
        assertFalse(Model.holdPrefix("Hello there."))
    }
}
