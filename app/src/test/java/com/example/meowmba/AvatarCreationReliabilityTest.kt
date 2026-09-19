package com.example.meowmba

import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.agent.tools.AvatarFormInventor
import com.example.meowmba.ui.companion.GeneratedAvatarExamples
import com.example.meowmba.ui.companion.GeneratedAvatarForms
import com.example.meowmba.ui.companion.InventReasonLevel
import com.example.meowmba.ui.companion.InventTimeBudget
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/** Delayed/model-error fixtures cover control flow, not live provider availability. */
@OptIn(ExperimentalCoroutinesApi::class)
class AvatarCreationReliabilityTest {
    private val form = GeneratedAvatarExamples.coffeeCup()
    private val json get() = GeneratedAvatarForms.toJson(form)

    @Test fun `remote avatar queues get bounded time without increasing local generation budgets`() {
        assertEquals(listOf(180_000L, 210_000L, 240_000L),
            InventReasonLevel.entries.map { InventTimeBudget.avatar(true, it) })
        assertEquals(listOf(75_000L, 90_000L, 120_000L),
            InventReasonLevel.entries.map { InventTimeBudget.avatar(false, it) })
        assertEquals(180_000L, InventTimeBudget.prop(true, InventReasonLevel.FAST))
        assertEquals(60_000L, InventTimeBudget.prop(false, InventReasonLevel.FAST))
    }

    @Test fun `queued remote fast draft may finish beyond the old seventy five second deadline`() = runTest {
        var calls = 0
        val inventor = AvatarFormInventor {
            calls++
            delay(100_000)
            ModelResponse.Complete(json)
        }
        val result = withTimeout(InventTimeBudget.avatar(true, InventReasonLevel.FAST)) {
            inventor.invent("a walking coffee cup", InventReasonLevel.FAST)
        }
        assertEquals(form, result)
        assertEquals(1, calls)
        assertNull(inventor.completionNotice)
    }

    @Test fun `local and remote unfinished drafts still stop at their own deadlines`() = runTest {
        for (remote in listOf(false, true)) {
            var calls = 0
            val inventor = AvatarFormInventor { calls++; awaitCancellation() }
            val failure = runCatching {
                withTimeout(InventTimeBudget.avatar(remote, InventReasonLevel.FAST)) {
                    inventor.invent("a walking coffee cup", InventReasonLevel.FAST)
                }
            }.exceptionOrNull()
            assertTrue(failure is TimeoutCancellationException)
            assertEquals(1, calls)
            assertNull(inventor.lastValidated)
            assertNull(inventor.completionNotice)
        }
    }

    @Test fun `failed optional provider review keeps the actual draft with a neutral notice`() = runTest {
        var calls = 0
        val inventor = AvatarFormInventor {
            if (++calls == 1) ModelResponse.Complete(json)
            else ModelResponse.Error("HTTP 429 provider-secret-do-not-display")
        }
        assertEquals(form, inventor.invent("a walking coffee cup", InventReasonLevel.THOROUGH))
        assertEquals(form, inventor.lastValidated)
        assertEquals(2, calls)
        assertTrue(inventor.completionNotice.orEmpty().contains("Extra polish"))
        assertFalse(inventor.completionNotice.orEmpty().contains("provider-secret"))
        assertFalse(inventor.completionNotice.orEmpty().contains("429"))
    }

    @Test fun `incomplete optional review is never parsed as a replacement design`() = runTest {
        var calls = 0
        val inventor = AvatarFormInventor {
            if (++calls == 1) ModelResponse.Complete(json) else ModelResponse.Partial("{\"parts\":[")
        }
        assertEquals(form, inventor.invent("a walking coffee cup", InventReasonLevel.BALANCED))
        assertEquals(2, calls)
        assertNotNull(inventor.completionNotice)
    }

    @Test fun `thrown optional provider failure keeps draft without a network retry`() = runTest {
        var calls = 0
        val inventor = AvatarFormInventor {
            if (++calls == 1) ModelResponse.Complete(json) else throw IOException("private-host-token")
        }
        assertEquals(form, inventor.invent("a walking coffee cup", InventReasonLevel.THOROUGH))
        assertEquals(2, calls)
        assertFalse(inventor.completionNotice.orEmpty().contains("private-host-token"))
    }

    @Test fun `invalid optional review keeps validated geometry and explains the failed check`() = runTest {
        var calls = 0
        val inventor = AvatarFormInventor {
            if (++calls == 1) ModelResponse.Complete(json) else ModelResponse.Complete("{\"code\":\"draw()\"}")
        }
        assertEquals(form, inventor.invent("a walking coffee cup", InventReasonLevel.BALANCED))
        assertEquals(2, calls)
        assertTrue(inventor.completionNotice.orEmpty().contains("drawing checks"))
    }

    @Test fun `first pass provider failure is typed and never becomes a successful avatar`() = runTest {
        val cases = listOf(
            ModelResponse.Error("Server disconnected") to AvatarFormInventor.ProviderFailureKind.UNAVAILABLE,
            ModelResponse.Partial("{") to AvatarFormInventor.ProviderFailureKind.INCOMPLETE,
        )
        for ((response, kind) in cases) {
            var calls = 0
            val inventor = AvatarFormInventor { calls++; response }
            val failure = runCatching { inventor.invent("a walking coffee cup") }.exceptionOrNull()
            assertTrue(failure is AvatarFormInventor.ProviderFailure)
            assertEquals(kind, (failure as AvatarFormInventor.ProviderFailure).kind)
            assertEquals(1, calls)
            assertNull(inventor.lastValidated)
            assertNull(inventor.completionNotice)
        }
    }

    @Test fun `refinement review failure keeps only the newly validated revision`() = runTest {
        val revised = GeneratedAvatarExamples.dragon()
        var calls = 0
        val inventor = AvatarFormInventor {
            if (++calls == 1) ModelResponse.Complete(GeneratedAvatarForms.toJson(revised))
            else ModelResponse.Error("Polish service unavailable")
        }
        assertEquals(revised, inventor.refine(form, "give it small wings", InventReasonLevel.BALANCED))
        assertEquals(revised, inventor.lastValidated)
        assertNotNull(inventor.completionNotice)

        val failed = AvatarFormInventor { ModelResponse.Error("No provider") }
        assertTrue(runCatching { failed.refine(form, "give it small wings") }.isFailure)
        assertNull(failed.lastValidated)
        assertNull(failed.completionNotice)
    }

    @Test fun `reusing inventor resets prior candidate and notice before a new failing request`() = runTest {
        var calls = 0
        val inventor = AvatarFormInventor {
            if (++calls == 1) ModelResponse.Complete(json) else ModelResponse.Error("No provider")
        }
        assertEquals(form, inventor.invent("a walking coffee cup"))
        assertNotNull(inventor.completionNotice)
        assertTrue(runCatching { inventor.invent("a tiny spaceship") }.isFailure)
        assertNull(inventor.lastValidated)
        assertNull(inventor.completionNotice)
    }

    @Test fun `cancellation during optional review never returns candidate as success`() = runTest {
        var calls = 0
        val inventor = AvatarFormInventor {
            if (++calls == 1) ModelResponse.Complete(json) else awaitCancellation()
        }
        val work = async { inventor.invent("a walking coffee cup") }
        runCurrent()
        assertEquals(form, inventor.lastValidated)
        work.cancel()
        assertTrue(runCatching { work.await() }.exceptionOrNull() is CancellationException)
        assertNull(inventor.completionNotice)
    }

    @Test fun `noncooperative late review error cannot convert cancellation into success`() = runTest {
        var calls = 0
        val released = CompletableDeferred<Unit>()
        val inventor = AvatarFormInventor {
            if (++calls == 1) ModelResponse.Complete(json) else {
                withContext(NonCancellable) { released.await() }
                throw IOException("Late cleanup failure")
            }
        }
        val work = async { inventor.invent("a walking coffee cup") }
        runCurrent()
        work.cancel()
        released.complete(Unit)
        assertTrue(runCatching { work.await() }.exceptionOrNull() is CancellationException)
        assertNull(inventor.completionNotice)
    }

    @Test fun `host review timeout still propagates with validated candidate available to owner`() = runTest {
        var calls = 0
        val inventor = AvatarFormInventor {
            if (++calls == 1) ModelResponse.Complete(json) else awaitCancellation()
        }
        val failure = runCatching {
            withTimeout(100) { inventor.invent("a walking coffee cup") }
        }.exceptionOrNull()
        assertTrue(failure is TimeoutCancellationException)
        assertEquals(form, inventor.lastValidated)
        assertNull(inventor.completionNotice)
    }
}
