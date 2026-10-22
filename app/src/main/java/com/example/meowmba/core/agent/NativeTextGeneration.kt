package com.example.meowmba.core.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

internal interface NativeTextSession : AutoCloseable {
    fun start(callback: NativeTextCallback)
    fun cancel()
}

internal interface NativeTextCallback {
    fun partial(text: String)
    fun complete(fullText: String? = null)
    fun error(error: Throwable)
}

/**
 * Closing a Flow does not stop native inference. Request native cancellation,
 * wait for its done/error callback, then destroy the session and release its
 * engine lease. Never cancel a Future merely to make it appear natively done.
 */
internal object NativeTextGeneration {
    fun stream(create: () -> NativeTextSession): Flow<ModelResponse> = callbackFlow {
        var session: NativeTextSession? = null
        var started = false
        val finished = CompletableDeferred<ModelResponse>()
        val callbackLock = Any()
        val text = StringBuilder()
        val pending = StringBuilder()
        var accepting = true
        var terminal = false
        // Cancel only this producer, not its collector's response/network Job.
        val stopSignal = currentCoroutineContext()[LocalInferenceCancellation]
        val stopRegistration = stopSignal?.register(this)
        val callback = object : NativeTextCallback {
            override fun partial(value: String) = synchronized(callbackLock) {
                if (!terminal && accepting && value.isNotEmpty()) {
                    text.append(value)
                    pending.append(value)
                    // Native callbacks cannot suspend. Coalesce a burst into
                    // one pending delta and let the producer flush it later.
                    if (trySend(ModelResponse.Partial(pending.toString())).isSuccess) pending.clear()
                }
                Unit
            }
            override fun complete(fullText: String?) = synchronized(callbackLock) {
                if (!terminal) {
                    terminal = true
                    // MediaPipe resolves its full Future before invoking the
                    // final progress callback. Partial-driven consumers still
                    // need that last suffix before they receive Complete.
                    if (accepting && fullText != null && fullText.startsWith(text.toString())) {
                        pending.append(fullText.substring(text.length))
                    }
                    finished.complete(ModelResponse.Complete(fullText ?: text.toString()))
                }
                Unit
            }
            override fun error(error: Throwable) = synchronized(callbackLock) {
                if (!terminal) {
                    terminal = true
                    finished.complete(ModelResponse.Error(error.message ?: "Native inference failed"))
                }
                Unit
            }
        }
        try {
            currentCoroutineContext().ensureActive()
            stopSignal?.ensureNotStopped()
            session = create()
            currentCoroutineContext().ensureActive()
            stopSignal?.ensureNotStopped()
            session.start(callback)
            started = true
            val response = finished.await()
            val remaining = synchronized(callbackLock) { pending.toString().also { pending.clear() } }
            if (remaining.isNotEmpty()) send(ModelResponse.Partial(remaining))
            send(response)
            close()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            trySend(ModelResponse.Error(error.message ?: "Native inference failed"))
            close()
        } finally {
            stopRegistration?.close()
            synchronized(callbackLock) { accepting = false }
            withContext(NonCancellable) {
                if (started && !finished.isCompleted) {
                    // A failed cancel request cannot authorize destruction under a
                    // running callback; wait for natural native completion instead.
                    runCatching { session?.cancel() }
                    finished.await()
                }
                session?.close()
            }
        }
    }.buffer(256)
}
