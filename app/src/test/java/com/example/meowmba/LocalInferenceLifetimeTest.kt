package com.example.meowmba

import com.example.meowmba.core.agent.LocalNativeModelPool
import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.agent.NativeTextCallback
import com.example.meowmba.core.agent.NativeTextGeneration
import com.example.meowmba.core.agent.NativeTextSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalInferenceLifetimeTest {
    private class Engine(private val events: MutableList<String>, private val id: String) : AutoCloseable {
        var closes = 0
        override fun close() { closes++; events += "close engine $id" }
    }
    private class Session(val immediate: ((NativeTextCallback) -> Unit)? = null) : NativeTextSession {
        lateinit var callback: NativeTextCallback
        var starts = 0
        var cancels = 0
        var closes = 0
        override fun start(callback: NativeTextCallback) { starts++; this.callback = callback; immediate?.invoke(callback) }
        override fun cancel() { cancels++ }
        override fun close() { closes++ }
    }

    @Test fun `native deltas combine into complete response and natural completion avoids cancellation`() = runTest {
        val session = Session { it.partial("Hello "); it.partial("world."); it.complete() }
        val output = NativeTextGeneration.stream { session }.toList()
        assertEquals(ModelResponse.Complete("Hello world."), output.last())
        assertEquals(1, session.closes)
        assertEquals(0, session.cancels)
    }

    @Test fun `authoritative MediaPipe future includes final delta and late progress is ignored`() = runTest {
        val session = Session { it.partial("Hello "); it.complete("Hello world."); it.partial("world."); it.complete("duplicate") }
        val output = NativeTextGeneration.stream { session }.toList()
        assertEquals(listOf(ModelResponse.Partial("Hello "), ModelResponse.Partial("world."), ModelResponse.Complete("Hello world.")), output)
        assertEquals("Hello world.", output.filterIsInstance<ModelResponse.Partial>().joinToString("") { it.text })
        assertEquals(1, session.closes)
    }

    @Test fun `native callback errors terminate and release once`() = runTest {
        val session = Session { it.error(IllegalStateException("native failed")); it.complete("late") }
        assertEquals(listOf(ModelResponse.Error("native failed")), NativeTextGeneration.stream { session }.toList())
        assertEquals(1, session.closes)
        assertEquals(0, session.cancels)
    }

    @Test fun `consumer cancellation requests native stop and waits before destroying session`() = runTest {
        val session = Session()
        val job = launch { NativeTextGeneration.stream { session }.collect() }
        runCurrent()
        job.cancel()
        runCurrent()
        try {
            assertEquals(1, session.cancels)
            assertEquals(0, session.closes)
            assertFalse(job.isCompleted)
        } finally { session.callback.complete(); job.join() }
        assertEquals(1, session.closes)
        assertTrue(job.isCancelled)
    }

    @Test fun `cancellation after native allocation but before start still closes the handle`() = runTest {
        val session = Session()
        lateinit var job: Job
        job = launch { NativeTextGeneration.stream { job.cancel(); session }.collect() }
        job.join()
        assertEquals(0, session.starts)
        assertEquals(0, session.cancels)
        assertEquals(1, session.closes)
    }

    @Test fun `terminal full text survives callback backpressure`() = runTest {
        val session = Session { callback -> repeat(600) { callback.partial("a") }; callback.complete() }
        val output = NativeTextGeneration.stream { session }.toList()
        assertEquals(ModelResponse.Complete("a".repeat(600)), output.last())
        assertEquals("a".repeat(600), output.filterIsInstance<ModelResponse.Partial>().joinToString("") { it.text })
        assertEquals(1, output.count { it is ModelResponse.Complete })
    }

    @Test fun `separate wrappers for one model reuse one resident engine`() = runTest {
        val events = mutableListOf<String>()
        val pool = LocalNativeModelPool()
        val engine = Engine(events, "one")
        var loads = 0
        repeat(3) { pool.withModel("same-key", { loads++; engine }) { assertSame(engine, it) } }
        assertEquals(1, loads)
        assertEquals(0, engine.closes)
        assertTrue(pool.releaseIfIdle("same-key"))
        assertEquals(1, engine.closes)
        assertFalse(pool.isLoaded())
        assertFalse(pool.releaseIfIdle("same-key"))
    }

    @Test fun `model or runtime change closes old weights before loading new ones`() = runTest {
        val events = mutableListOf<String>()
        val pool = LocalNativeModelPool()
        pool.withModel("mediapipe:one", { events += "load one"; Engine(events, "one") }) { }
        pool.withModel("litert:two", { events += "load two"; Engine(events, "two") }) { }
        assertEquals(listOf("load one", "close engine one", "load two"), events)
        assertFalse(pool.isLoaded("mediapipe:one"))
        assertTrue(pool.isLoaded("litert:two"))
    }

    @Test fun `engine lease remains held until cancelled native callback acknowledges stop`() = runTest {
        val pool = LocalNativeModelPool()
        val engine = Engine(mutableListOf(), "one")
        val session = Session()
        val first = launch { pool.withModel("one", { engine }) { NativeTextGeneration.stream { session }.collect() } }
        runCurrent()
        first.cancel()
        runCurrent()
        var secondStarted = false
        val second = launch { pool.withModel<Engine, Unit>("one", { error("must reuse") }) { secondStarted = true } }
        runCurrent()
        try {
            assertFalse(secondStarted)
            assertFalse(pool.releaseIfIdle())
            assertEquals(0, session.closes)
            assertEquals(0, engine.closes)
        } finally { session.callback.complete(); first.join(); second.join() }
        assertTrue(secondStarted)
        assertEquals(1, session.closes)
    }

    @Test fun `cancelled queued request does not load another model`() = runTest {
        val pool = LocalNativeModelPool()
        val finish = CompletableDeferred<Unit>()
        val first = launch { pool.withModel("one", { Engine(mutableListOf(), "one") }) { finish.await() } }
        runCurrent()
        var secondLoads = 0
        val second = launch { pool.withModel("two", { secondLoads++; Engine(mutableListOf(), "two") }) { } }
        runCurrent()
        second.cancelAndJoin()
        finish.complete(Unit)
        first.join()
        assertEquals(0, secondLoads)
    }

    @Test fun `cancellation during noninterruptible load retains handle and skips generation`() = runTest {
        val pool = LocalNativeModelPool()
        val engine = Engine(mutableListOf(), "one")
        var generated = false
        val first = launch {
            val owner = currentCoroutineContext().job
            pool.withModel("one", { owner.cancel(); engine }) { generated = true }
        }
        first.join()
        assertFalse(generated)
        assertTrue(pool.isLoaded("one"))
        assertEquals(0, engine.closes)
        pool.withModel<Engine, Unit>("one", { error("must reuse the retained allocation") }) { assertSame(engine, it) }
    }

    @Test fun `idleness starts at native completion and remains shared across wrappers`() = runTest {
        var now = 100L
        val pool = LocalNativeModelPool { now }
        val finish = CompletableDeferred<Unit>()
        val job = launch { pool.withModel("one", { Engine(mutableListOf(), "one") }) { finish.await() } }
        runCurrent()
        now = 60_100
        assertEquals(0L, pool.idleMs("one"))
        finish.complete(Unit)
        job.join()
        now = 60_150
        assertEquals(50L, pool.idleMs("one"))
        assertFalse(pool.releaseIfIdle(minIdleMs = 51))
        assertTrue(pool.releaseIfIdle(minIdleMs = 50))
    }

    @Test fun `deferred lifecycle release waits for active generation instead of destroying it`() = runTest {
        val pool = LocalNativeModelPool()
        val engine = Engine(mutableListOf(), "one")
        val finish = CompletableDeferred<Unit>()
        val generation = launch { pool.withModel("one", { engine }) { finish.await() } }
        runCurrent()
        val release = launch { pool.releaseWhenIdle() }
        runCurrent()
        assertEquals(0, engine.closes)
        finish.complete(Unit)
        generation.join()
        release.join()
        assertEquals(1, engine.closes)
        assertFalse(pool.isLoaded())
    }

    @Test fun `failed native destruction retains ownership and blocks a second engine load`() = runTest {
        val pool = LocalNativeModelPool()
        pool.withModel("one", { AutoCloseable { error("native close failed") } }) { }
        var secondLoads = 0
        val result = runCatching { pool.withModel("two", { secondLoads++; Engine(mutableListOf(), "two") }) { } }
        assertTrue(result.isFailure)
        assertEquals(0, secondLoads)
        assertTrue(pool.isLoaded("one"))
    }
}
