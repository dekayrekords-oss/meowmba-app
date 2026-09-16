package com.example.meowmba.core.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One resident conversation engine and one native generation lease across both
 * Android runtimes. Java wrappers may be recreated by routing, setup and jobs;
 * that must not multiply multi-gigabyte engines or overlap native generations.
 * The dispatcher coordinates JNI lifetime only; native inference thread counts
 * and acceleration settings are unchanged.
 */
object LocalInferenceRuntime {
    internal val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "meow-llm").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }.asCoroutineDispatcher()
    internal val models = LocalNativeModelPool()
    private val cleanupScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val releaseQueued = AtomicBoolean(false)

    val isLoaded: Boolean get() = models.isLoaded()
    val isGenerating: Boolean get() = models.isBusy
    fun idleMs(nowMs: Long = System.currentTimeMillis()): Long = models.idleMs(nowMs = nowMs)

    /** Native close stays off Main. Recheck idleness after taking the session lock. */
    suspend fun releaseIdle(minIdleMs: Long = 0): Boolean = withContext(dispatcher) {
        models.releaseIfIdle(minIdleMs = minIdleMs)
    }

    /** Lifecycle cleanup survives ViewModel cancellation and waits for native work to finish. */
    fun requestRelease() {
        if (!releaseQueued.compareAndSet(false, true)) return
        cleanupScope.launch {
            try { runCatching { models.releaseWhenIdle() } }
            finally { releaseQueued.set(false) }
        }
    }

    internal fun <T : AutoCloseable> warmUp(key: String, create: () -> T) {
        if (models.isLoaded(key) || models.isBusy) return
        cleanupScope.launch { runCatching { models.withModel(key, create) { } } }
    }
}

/** JVM-testable resource policy; callers keep the lease through native callback completion. */
internal class LocalNativeModelPool(private val clock: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    @Volatile private var resident: Resident? = null
    @Volatile private var busy = false
    private class Resident(val key: String, val resource: AutoCloseable, @Volatile var lastCompletedAt: Long)

    val isBusy: Boolean get() = busy
    fun isLoaded(key: String? = null): Boolean = resident?.let { key == null || it.key == key } == true
    fun idleMs(key: String? = null, nowMs: Long = clock()): Long {
        val entry = resident?.takeIf { key == null || it.key == key } ?: return Long.MAX_VALUE
        return if (busy) 0 else (nowMs - entry.lastCompletedAt).coerceAtLeast(0)
    }

    suspend fun <T : AutoCloseable, R> withModel(key: String, create: () -> T, block: suspend (T) -> R): R = withLocalInferenceCancellation {
        currentCoroutineContext()[LocalInferenceCancellation]?.ensureNotStopped()
        mutex.lock()
        try {
            currentCoroutineContext().ensureActive()
            currentCoroutineContext()[LocalInferenceCancellation]?.ensureNotStopped()
            busy = true
            if (resident?.key != key) {
                closeResident()
                currentCoroutineContext()[LocalInferenceCancellation]?.ensureNotStopped()
                // Retain a constructed engine even when its caller was cancelled
                // during non-interruptible JNI initialization. Never lose the handle
                // at a withContext return boundary and load a second copy next time.
                resident = Resident(key, create(), clock())
            }
            currentCoroutineContext().ensureActive()
            currentCoroutineContext()[LocalInferenceCancellation]?.ensureNotStopped()
            @Suppress("UNCHECKED_CAST")
            block(checkNotNull(resident).resource as T)
        } finally {
            resident?.lastCompletedAt = clock()
            busy = false
            mutex.unlock()
        }
    }

    /** Does not close an active engine, even when a UI lifecycle call races a job. */
    fun releaseIfIdle(key: String? = null, minIdleMs: Long = 0): Boolean {
        if (!mutex.tryLock()) return false
        return try {
            if (!isLoaded(key) || idleMs(key) < minIdleMs) false else closeResident()
        } finally { mutex.unlock() }
    }

    suspend fun releaseWhenIdle(key: String? = null): Boolean {
        mutex.lock()
        return try { if (isLoaded(key)) closeResident() else false }
        finally { mutex.unlock() }
    }

    private fun closeResident(): Boolean {
        val entry = resident ?: return false
        // If native destruction fails, retain ownership and propagate the error.
        // Forgetting this handle would let another engine load on top of it.
        entry.resource.close()
        resident = null
        return true
    }
}
