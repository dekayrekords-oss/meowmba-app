package com.example.meowmba

import com.example.meowmba.core.agent.LocalBackendRetry
import com.example.meowmba.core.agent.LocalNativeModelPool
import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.agent.NativeTextCallback
import com.example.meowmba.core.agent.NativeTextGeneration
import com.example.meowmba.core.agent.NativeTextSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LocalBackendRetryTest {
    private val bufferError = ModelResponse.Error("INTERNAL: ERROR: [convert_tensor_buffer.h:134] [litert_tensor_buffer.h:861]")

    @Test fun `GPU callback failure closes session and engine before CPU generation`() = runTest {
        val events = mutableListOf<String>()
        val pool = LocalNativeModelPool()
        class Engine(val id: String) : AutoCloseable {
            init { events += "load $id" }
            override fun close() { events += "close $id engine" }
        }
        fun attempt(id: String) = flow {
            pool.withModel(id, { Engine(id) }) {
                NativeTextGeneration.stream {
                    object : NativeTextSession {
                        override fun start(callback: NativeTextCallback) {
                            events += "start $id"
                            if (id == "gpu") callback.error(IllegalStateException(bufferError.message))
                            else { callback.partial("Purple dragon"); callback.complete() }
                        }
                        override fun cancel() { error("Completed sessions must not be cancelled") }
                        override fun close() { events += "close $id session" }
                    }
                }.collect { emit(it) }
            }
        }
        val output = LocalBackendRetry.stream({ attempt("gpu") }, { true }, { attempt("cpu") }).toList()
        assertEquals(listOf(ModelResponse.Partial("Purple dragon"), ModelResponse.Complete("Purple dragon")), output)
        assertEquals(listOf("load gpu", "start gpu", "close gpu session", "close gpu engine", "load cpu", "start cpu", "close cpu session"), events)
        assertTrue(pool.isLoaded("cpu"))
        assertFalse(pool.isBusy)
        pool.releaseIfIdle()
    }

    @Test fun `initialization fallback already using CPU is not retried`() = runTest {
        var calls = 0
        assertEquals(listOf(bufferError), LocalBackendRetry.stream({ flowOf(bufferError) }, { false }, { calls++; flowOf(ModelResponse.Complete("wrong")) }).toList())
        assertEquals(0, calls)
    }

    @Test fun `partial answer prevents restarting and duplicating the response`() = runTest {
        val output = listOf(ModelResponse.Partial("Purple"), bufferError)
        assertEquals(output, LocalBackendRetry.stream({ flowOf(*output.toTypedArray()) }, { true }, { error("Must not retry after output") }).toList())
    }

    @Test fun `successful completion never retries even if empty`() = runTest {
        assertEquals(listOf(ModelResponse.Complete("")), LocalBackendRetry.stream({ flowOf(ModelResponse.Complete("")) }, { true }, { error("Must not retry a completion") }).toList())
    }

    @Test fun `unrelated failures remain visible and are not retried`() = runTest {
        val failure = ModelResponse.Error("Invalid response format")
        assertEquals(listOf(failure), LocalBackendRetry.stream({ flowOf(failure) }, { true }, { error("Must not retry unrelated errors") }).toList())
    }

    @Test fun `CPU failure stays visible without a third attempt`() = runTest {
        var calls = 0
        assertEquals(listOf(bufferError), LocalBackendRetry.stream({ flowOf(bufferError) }, { true }, { calls++; flowOf(bufferError) }).toList())
        assertEquals(1, calls)
    }

    @Test fun `caller cancellation propagates without CPU work`() = runTest {
        val stop = CancellationException("User stopped generation")
        try {
            LocalBackendRetry.stream({ flow { throw stop } }, { true }, { error("Must not retry cancellation") }).toList()
            fail("Expected cancellation")
        } catch (actual: CancellationException) { assertSame(stop, actual) }
    }

    @Test fun `failed native cleanup prevents CPU replacement`() = runTest {
        val failure = IllegalStateException("Native destruction failed")
        try {
            LocalBackendRetry.stream({ flow { emit(bufferError); throw failure } }, { true }, { error("Must finish cleanup first") }).toList()
            fail("Expected cleanup failure")
        } catch (actual: IllegalStateException) { assertSame(failure, actual) }
    }

    @Test fun `downstream failure propagates without retry`() = runTest {
        val failure = IllegalStateException("Collector stopped")
        try {
            LocalBackendRetry.stream({ flowOf(ModelResponse.Partial("Purple")) }, { true }, { error("Must not retry collector failures") }).collect { throw failure }
            fail("Expected collector failure")
        } catch (actual: IllegalStateException) { assertSame(failure, actual) }
    }
}
