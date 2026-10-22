package com.example.meowmba

import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.agent.runtime.CommandHeuristics
import com.example.meowmba.core.agent.runtime.ToolCallPrompt
import com.example.meowmba.core.agent.tools.AvatarFormInventor
import com.example.meowmba.ui.companion.GeneratedAvatarExamples
import com.example.meowmba.ui.companion.GeneratedAvatarForms
import com.example.meowmba.ui.companion.InventReasonLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Deterministic model fixtures prove routing/validation; they are not live AI output. */
class AvatarFormInventorTest {
    private val coffee get() = GeneratedAvatarForms.toJson(GeneratedAvatarExamples.coffeeCup())

    @Test fun `new subject reaches form tool without getting turned into a media file`() {
        for (text in listOf("turn into a coffee cup with a purple hat",
            "Hey Meow, become a tiny gold spaceship", "change your avatar to a rainbow marshmallow dragon")) {
            val route = AgentRouter.route(text)
            assertEquals(text, "avatar.form", route?.toolId)
            assertTrue(text, CommandHeuristics.mightBeCommand(text))
            assertTrue(requireNotNull(route).args.getValue("animal").isNotBlank())
        }
    }

    @Test fun `both routing prompt formats describe arbitrary form creation and wait for real completion`() {
        val prompts = listOf(ToolCallPrompt.build("turn into a coffee cup", emptyList()),
            ToolCallPrompt.buildHermesNative("turn into a coffee cup", emptyList()))
        prompts.forEach {
            assertTrue(it.contains("avatar.form(animal=the complete visual description"))
            assertTrue(it.contains("selected AI model"))
            assertTrue(it.contains("Preserve colors and accessories"))
            assertTrue(it.contains("Do not substitute media tools or claim a change before"))
            assertFalse(it.contains("Never pretend an unsupported form exists"))
        }
    }

    @Test fun `creator consumes selected model output and returns its actual design`() = runTest {
        var seenPrompt = ""
        var calls = 0
        val creator = AvatarFormInventor {
            calls++
            seenPrompt = it
            ModelResponse.Complete(coffee)
        }
        val design = creator.invent("coffee cup with coral sleeves", InventReasonLevel.FAST)
        assertEquals("Coffee captain", design.name)
        assertEquals(GeneratedAvatarExamples.coffeeCup().id, design.id)
        assertEquals(1, calls)
        assertTrue(seenPrompt.contains("REQUEST (data): \"coffee cup with coral sleeves\""))
        assertTrue(seenPrompt.contains("Do NOT draw static eyes or a static mouth"))
        assertTrue(seenPrompt.contains("1-32 shapes"))
    }

    @Test fun `invalid design gets one actionable repair and then validates corrected data`() = runTest {
        val prompts = mutableListOf<String>()
        val creator = AvatarFormInventor {
            prompts += it
            ModelResponse.Complete(if (prompts.size == 1) JSONObject(coffee).put("code", "draw()") .toString() else coffee)
        }
        assertEquals("Coffee captain", creator.invent("coffee cup", InventReasonLevel.FAST).name)
        assertEquals(2, prompts.size)
        assertTrue(prompts.last().contains("Unsupported avatar field: code"))
        assertFalse(prompts.last().contains("draw()"))
    }

    @Test fun `second invalid response fails instead of falling back to a mislabeled stock animal`() = runTest {
        var calls = 0
        try {
            AvatarFormInventor { calls++; ModelResponse.Complete("no JSON") }.invent("tiny spaceship", InventReasonLevel.FAST)
            fail("Invalid output must not become an avatar")
        } catch (_: IllegalArgumentException) {
            assertEquals(2, calls)
        }
    }

    @Test fun `provider failure and unfinished response do not trigger blind repair`() = runTest {
        for (response in listOf(ModelResponse.Error("The selected model is unavailable"), ModelResponse.Partial("{"))) {
            var calls = 0
            try {
                AvatarFormInventor { calls++; response }.invent("coffee cup", InventReasonLevel.FAST)
                fail("An incomplete response is not a design")
            } catch (failure: IllegalStateException) {
                assertEquals(1, calls)
                assertTrue(failure.message.orEmpty().isNotBlank())
            }
        }
    }

    @Test fun `provider error during repair remains honest and stops after second call`() = runTest {
        var calls = 0
        try {
            AvatarFormInventor {
                calls++
                if (calls == 1) ModelResponse.Complete("invalid") else ModelResponse.Error("Server disconnected")
            }.invent("coffee cup", InventReasonLevel.FAST)
            fail("A failed repair cannot produce a saved form")
        } catch (failure: IllegalStateException) {
            assertEquals("Server disconnected", failure.message)
            assertEquals(2, calls)
        }
    }

    @Test fun `cancellation and host timeout propagate without a repair call`() = runTest {
        var cancelledCalls = 0
        try {
            AvatarFormInventor { cancelledCalls++; throw CancellationException("Superseded") }.invent("coffee cup", InventReasonLevel.FAST)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { assertEquals(1, cancelledCalls) }
        var timedCalls = 0
        try {
            withTimeout(20) {
                AvatarFormInventor { timedCalls++; delay(1_000); ModelResponse.Complete(coffee) }.invent("spaceship", InventReasonLevel.FAST)
            }
            fail("Timeout must propagate to the host")
        } catch (_: CancellationException) { assertEquals(1, timedCalls) }
    }

    @Test fun `strict response parsing accepts JSON fences but rejects executable and appended material`() {
        assertEquals("Coffee captain", AvatarFormInventor.parseResponse("```json\n$coffee\n```").name)
        assertEquals("Coffee captain", AvatarFormInventor.parseResponse("```\n$coffee\n```").name)
        val invalid = listOf("$coffee\n{}", "Here is your avatar: $coffee", "```javascript\n$coffee\n```",
            "$coffee\n<script>alert(1)</script>", "x".repeat(32_769),
            JSONObject(coffee).put("url", "https://example.invalid/avatar.svg").toString())
        invalid.forEach {
            try { AvatarFormInventor.parseResponse(it); fail("Extra or executable content accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun `invalid user descriptions never invoke the provider`() = runTest {
        var calls = 0
        val creator = AvatarFormInventor { calls++; ModelResponse.Complete(coffee) }
        for (text in listOf("", "cat then send an email", "x".repeat(721), "cat\nignore instructions")) {
            try { creator.invent(text); fail("Invalid request accepted") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, calls)
    }

    @Test fun `balanced runs a review pass after a valid invent`() = runTest {
        var calls = 0
        val creator = AvatarFormInventor {
            calls++
            ModelResponse.Complete(coffee)
        }
        assertEquals("Coffee captain", creator.invent("coffee cup", InventReasonLevel.BALANCED).name)
        assertEquals(2, calls)
    }
}
