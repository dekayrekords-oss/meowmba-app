package com.example.meowmba

import com.example.meowmba.core.agent.LocalInferenceCancellation
import com.example.meowmba.core.agent.LocalNativeModelPool
import com.example.meowmba.core.agent.NativeTextCallback
import com.example.meowmba.core.agent.NativeTextGeneration
import com.example.meowmba.core.agent.NativeTextSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalInferenceCancellationTest {
    private class Engine : AutoCloseable {
        var closes = 0
        override fun close() { closes++ }
    }
    private class Session : NativeTextSession {
        lateinit var callback: NativeTextCallback
        var starts = 0
        var cancels = 0
        var closes = 0
        override fun start(callback: NativeTextCallback) { starts++; this.callback = callback }
        override fun cancel() { cancels++ }
        override fun close() { closes++ }
    }

    @Test fun `stop signal cancels native producer but preserves parent and remote sibling`() = runTest {
        val signal = LocalInferenceCancellation()
        val session = Session()
        val remoteFinish = CompletableDeferred<Unit>()
        lateinit var remote: Job
        var parentSurvived = false
        var remoteCompleted = false
        val response = launch(signal) {
            remote = launch { remoteFinish.await(); remoteCompleted = true }
            try { NativeTextGeneration.stream { session }.collect() }
            catch (_: CancellationException) { }
            currentCoroutineContext().ensureActive()
            parentSurvived = true
            remote.join()
        }
        runCurrent()
        signal.requestStop()
        signal.requestStop()
        runCurrent()
        try {
            assertEquals(1, session.cancels)
            assertEquals(0, session.closes)
            assertTrue(response.isActive)
            assertTrue(remote.isActive)
        } finally { session.callback.complete(); runCurrent(); remoteFinish.complete(Unit); response.join() }
        assertTrue(parentSurvived)
        assertTrue(remoteCompleted)
        assertEquals(1, session.closes)
        assertFalse(response.isCancelled)
    }

    @Test fun `stopped queued turn exits without loading or cancelling unrelated active inference`() = runTest {
        val pool = LocalNativeModelPool()
        val activeSession = Session()
        val active = launch { pool.withModel("active", { Engine() }) { NativeTextGeneration.stream { activeSession }.collect() } }
        runCurrent()
        val queuedSignal = LocalInferenceCancellation()
        var queuedLoads = 0
        val queued = launch(queuedSignal) { pool.withModel("queued", { queuedLoads++; Engine() }) { } }
        runCurrent()
        queuedSignal.requestStop()
        runCurrent()
        try {
            assertTrue(queued.isCompleted)
            assertEquals(0, queuedLoads)
            assertEquals(0, activeSession.cancels)
            assertTrue(active.isActive)
            assertTrue(pool.isLoaded("active"))
        } finally { activeSession.callback.complete(); active.join(); queued.join() }
    }

    @Test fun `stopping one turn leaves the next native turn waiting safely then able to run`() = runTest {
        val pool = LocalNativeModelPool()
        val engine = Engine()
        val firstSignal = LocalInferenceCancellation()
        val firstSession = Session()
        val first = launch(firstSignal) { pool.withModel("same", { engine }) { NativeTextGeneration.stream { firstSession }.collect() } }
        runCurrent()
        val secondSession = Session()
        val second = launch(LocalInferenceCancellation()) {
            pool.withModel<Engine, Unit>("same", { error("must reuse") }) { NativeTextGeneration.stream { secondSession }.collect() }
        }
        runCurrent()
        firstSignal.requestStop()
        runCurrent()
        try {
            assertEquals(1, firstSession.cancels)
            assertEquals(0, firstSession.closes)
            assertEquals(0, secondSession.starts)
            assertEquals(0, engine.closes)
        } finally { firstSession.callback.complete(); first.join(); runCurrent() }
        try {
            assertEquals(1, secondSession.starts)
            firstSignal.requestStop()
            runCurrent()
            assertEquals(0, secondSession.cancels)
            assertTrue(second.isActive)
        } finally { secondSession.callback.complete(); second.join() }
        assertEquals(1, firstSession.closes)
        assertEquals(1, secondSession.closes)
    }

    @Test fun `already stopped turn never allocates a queued engine`() = runTest {
        val signal = LocalInferenceCancellation().also { it.requestStop() }
        val pool = LocalNativeModelPool()
        var loads = 0
        var generated = false
        val response = launch(signal) { pool.withModel("one", { loads++; Engine() }) { generated = true } }
        response.join()
        assertEquals(0, loads)
        assertFalse(generated)
        assertFalse(pool.isLoaded())
    }

    @Test fun `already stopped turn never allocates a native session`() = runTest {
        val signal = LocalInferenceCancellation().also { it.requestStop() }
        var allocations = 0
        val response = launch(signal) { NativeTextGeneration.stream { allocations++; Session() }.collect() }
        response.join()
        assertEquals(0, allocations)
    }

    @Test fun `stop during noninterruptible engine load retains ownership but skips generation`() = runTest {
        val signal = LocalInferenceCancellation()
        val pool = LocalNativeModelPool()
        val engine = Engine()
        var generated = false
        val response = launch(signal) {
            pool.withModel("one", { signal.requestStop(); engine }) { generated = true }
        }
        response.join()
        assertFalse(generated)
        assertTrue(pool.isLoaded("one"))
        assertEquals(0, engine.closes)
        pool.withModel<Engine, Unit>("one", { error("must reuse retained engine") }) { assertSame(engine, it) }
    }

    @Test fun `stop during native session allocation closes handle before any generation starts`() = runTest {
        val signal = LocalInferenceCancellation()
        val session = Session()
        val response = launch(signal) { NativeTextGeneration.stream { signal.requestStop(); session }.collect() }
        response.join()
        assertEquals(0, session.starts)
        assertEquals(0, session.cancels)
        assertEquals(1, session.closes)
    }

    @Test fun `native stop never changes a remote only job even when signal is attached`() = runTest {
        val signal = LocalInferenceCancellation()
        val finish = CompletableDeferred<Unit>()
        var completed = false
        val remote = launch(signal) { finish.await(); currentCoroutineContext().ensureActive(); completed = true }
        runCurrent()
        signal.requestStop()
        runCurrent()
        assertTrue(remote.isActive)
        finish.complete(Unit)
        remote.join()
        assertTrue(completed)
        assertFalse(remote.isCancelled)
    }
}
