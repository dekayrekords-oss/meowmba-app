package com.example.meowmba

import com.example.meowmba.core.agent.ModelProvider
import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.agent.OmniRoute
import com.example.meowmba.core.agent.OmniRoute.RouteTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The router runs on the phone, so its decisions have to hold with no network and no
 * server to ask. These pin the two things that make it usable: it picks something
 * sensible, and it never leaves the user with nothing because one backend is down.
 */
class OmniRouteTest {

    private class FakeProvider(
        override val name: String,
        private val reply: String? = "ok",
        private val failWith: String? = null,
    ) : ModelProvider {
        var calls = 0

        override suspend fun generate(prompt: String): ModelResponse {
            calls++
            return failWith?.let { ModelResponse.Error(it) }
                ?: ModelResponse.Complete("$name: $reply")
        }

        override fun generateStreaming(prompt: String): Flow<ModelResponse> = flow {
            calls++
            failWith?.let {
                emit(ModelResponse.Error(it))
                return@flow
            }
            emit(ModelResponse.Partial("$name: "))
            emit(ModelResponse.Complete("$name: $reply"))
        }
    }

    private fun route(
        id: String,
        provider: ModelProvider,
        tags: Set<RouteTag>,
        priority: Int = 50,
        offline: Boolean = false,
    ) = OmniRoute.Route(id, id, provider, tags, priority, offline)

    @Test
    fun `code goes to a coding route`() {
        val router = OmniRoute(emptyList(), log = {})
        assertEquals(RouteTag.CODING, router.classify("write a function that reverses a list"))
        assertEquals(RouteTag.CODING, router.classify("why does this code fail?\n```\nfun x()\n```"))
    }

    @Test
    fun `short commands are fast lane`() {
        val router = OmniRoute(emptyList(), log = {})
        assertEquals(RouteTag.FAST, router.classify("what time is it"))
        assertEquals(RouteTag.FAST, router.classify("turn the lights off"))
    }

    @Test
    fun `thinking work is reasoning`() {
        val router = OmniRoute(emptyList(), log = {})
        assertEquals(RouteTag.REASONING, router.classify("compare the two plans and explain why"))
        // Long prompts are reasoning regardless of wording.
        assertEquals(RouteTag.REASONING, router.classify("tell me about ".repeat(40)))
    }

    @Test
    fun `the best-tagged route wins`() = runTest {
        val coder = FakeProvider("coder")
        val chatter = FakeProvider("chatter")
        val router = OmniRoute(
            log = {},
            routes = listOf(
                route("chat", chatter, setOf(RouteTag.CHAT), priority = 10),
                route("code", coder, setOf(RouteTag.CODING), priority = 40),
            )
        )
        val result = router.generate("refactor this for me")
        assertEquals(ModelResponse.Complete("coder: ok"), result)
        assertEquals("code", router.lastRoute.value)
    }

    /** The reason the router exists on a phone: backends come and go. */
    @Test
    fun `a dead route falls through to the next`() = runTest {
        val dead = FakeProvider("lan", failWith = "connection refused")
        val local = FakeProvider("on-device")
        val router = OmniRoute(
            log = {},
            routes = listOf(
                route("lan", dead, setOf(RouteTag.CHAT), priority = 10),
                route("local", local, setOf(RouteTag.CHAT), priority = 90, offline = true),
            )
        )
        val result = router.generate("how are you doing today my friend")
        assertEquals(ModelResponse.Complete("on-device: ok"), result)
        assertEquals(1, dead.calls)
        assertEquals("local", router.lastRoute.value)
    }

    @Test
    fun `streaming falls through before any text, not after`() = runTest {
        val dead = FakeProvider("lan", failWith = "timeout")
        val local = FakeProvider("on-device")
        val router = OmniRoute(
            log = {},
            routes = listOf(
                route("lan", dead, setOf(RouteTag.CHAT), priority = 10),
                route("local", local, setOf(RouteTag.CHAT), priority = 90, offline = true),
            )
        )
        val emitted = router.generateStreaming("how are you doing today my friend").toList()
        assertTrue(emitted.none { it is ModelResponse.Error })
        assertEquals(ModelResponse.Complete("on-device: ok"), emitted.last())
    }

    @Test
    fun `offline route is the last resort, never the first pick`() {
        val router = OmniRoute(
            log = {},
            routes = listOf(
                route("local", FakeProvider("local"), setOf(RouteTag.FAST), priority = 90, offline = true),
                route("lan", FakeProvider("lan"), setOf(RouteTag.REASONING), priority = 10),
            )
        )
        val plan = router.plan("compare these two designs and explain the trade-offs")
        assertEquals("lan", plan.first().id)
        assertEquals("local", plan.last().id)
    }

    /**
     * The bug this caught on device: the router sees the assembled prompt — persona,
     * memories, tool list — and "what time is it" came out as CODING because the
     * boilerplate contains braces.
     */
    @Test
    fun `the user's words decide, not the prompt scaffolding`() {
        val router = OmniRoute(emptyList(), log = {})
        val assembled = """
            You are Meow. Capabilities: { "tools": [ "file.write" ] }
            Recalled: the user likes oat milk
            User: what time is it
        """.trimIndent()

        // Without a hint it recovers the last user turn from the prompt.
        assertEquals(RouteTag.FAST, router.classify(assembled))

        // With a hint it uses exactly what was said.
        router.hintUserText("what time is it")
        assertEquals(RouteTag.FAST, router.classify(assembled))

        router.hintUserText("refactor this kotlin function")
        assertEquals(RouteTag.CODING, router.classify(assembled))
    }

    @Test
    fun `no routes is an honest error, not a crash`() = runTest {
        val result = OmniRoute(emptyList(), log = {}).generate("anything")
        assertTrue(result is ModelResponse.Error)
    }
}
