package com.example.meowmba.core.diagnostics

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-feature loop-rate and busy-time accounting.
 *
 * Perfetto callstack sampling could only attribute one of seven busy
 * `DefaultDispatch` workers: everything else unwound to no symbol at all,
 * because a debug build is interpreted and most frames carry no name. Rather
 * than guess again, each loop now reports its own cost. If a worker is hot and
 * is not in this list, the problem is a loop nobody instrumented yet — which is
 * itself the answer.
 *
 * `busyNanos` is time spent inside the loop's real work. It deliberately
 * EXCLUDES time spent blocked in `delay()` or in a blocking read: those are
 * idle, and counting them would report wall time as if it were CPU.
 *
 * Timed with [System.nanoTime] rather than `android.os.SystemClock` on purpose.
 * These counters are reached from production code that the unit tests exercise
 * without Robolectric, where an unmocked `SystemClock` throws. Switching to
 * `SystemClock.elapsedRealtime` breaks the voice tests; do not "fix" it.
 */
object LoopTelemetry {

    private const val TAG = "MeowLoop"

    private class Acc {
        val iterations = AtomicLong(0)
        val busyNanos = AtomicLong(0)
        val startedNanos = AtomicLong(0)
        /**
         * Marks for the rolling window, advanced only by [drain].
         *
         * Without them every reading is a process-lifetime average, and a loop
         * that stopped minutes ago still reports a slowly decaying percentage
         * and rate that look exactly like a loop that is running right now.
         * That misreading cost a whole profiling session: a dead ASR loop
         * printed "4.0% core 3.94 Hz" for twenty minutes.
         */
        val markIters = AtomicLong(0)
        val markBusyNanos = AtomicLong(0)
        val markAtNanos = AtomicLong(0)
    }

    private val accs = ConcurrentHashMap<String, Acc>()

    private fun acc(feature: String): Acc =
        accs.getOrPut(feature) {
            Acc().also {
                val now = System.nanoTime()
                it.startedNanos.set(now)
                it.markAtNanos.set(now)
            }
        }

    /** Count one pass of [feature]'s loop. */
    fun hit(feature: String) {
        acc(feature).iterations.incrementAndGet()
    }

    /** Charge already-measured busy time to [feature]. */
    fun addBusyNanos(feature: String, nanos: Long) {
        if (nanos > 0) acc(feature).busyNanos.addAndGet(nanos)
    }

    /**
     * Run [block], charge its wall time to [feature] and count one pass. Only
     * wrap work that actually burns CPU — never a suspending or blocking call.
     *
     * The iteration count is not optional decoration: a feature driven only by
     * [measure] (rather than [hit]) otherwise reports 0 iterations and 0.00 Hz
     * while sitting at the top of the table, which is exactly the row a
     * regression hunt needs to read. Observed on device with `asr.decode`.
     */
    inline fun <T> measure(feature: String, block: () -> T): T {
        val t0 = System.nanoTime()
        try {
            return block()
        } finally {
            addBusyNanos(feature, System.nanoTime() - t0)
            // hit(), not the private accessor: a public inline function cannot
            // reach non-public members.
            hit(feature)
        }
    }

    data class Stat(
        val feature: String,
        val iterations: Long,
        val busyMs: Long,
        val windowMs: Long,
        val hz: Double,
        /** Busy time as a percentage of one core over the window. */
        val corePct: Double,
        // --- rolling window: only what happened since the previous drain() ---
        val recentIters: Long,
        val recentBusyMs: Long,
        val recentWindowMs: Long,
        val recentHz: Double,
        val recentCorePct: Double,
    ) {
        /** Ran at least once since the last drain. False means: this is not your heat. */
        val isLive: Boolean get() = recentIters > 0
    }

    /** Every feature over its whole lifetime, busiest first. */
    fun snapshot(): List<Stat> {
        val now = System.nanoTime()
        return accs.map { (name, a) ->
            val windowNs = (now - a.startedNanos.get()).coerceAtLeast(1)
            val windowMs = windowNs / 1_000_000L
            val busyMs = a.busyNanos.get() / 1_000_000L
            val iters = a.iterations.get()
            Stat(
                feature = name,
                iterations = iters,
                busyMs = busyMs,
                windowMs = windowMs,
                hz = iters * 1_000_000_000.0 / windowNs,
                corePct = busyMs * 100.0 / windowMs.coerceAtLeast(1),
                recentIters = iters,
                recentBusyMs = busyMs,
                recentWindowMs = windowMs,
                recentHz = iters * 1_000_000_000.0 / windowNs,
                recentCorePct = busyMs * 100.0 / windowMs.coerceAtLeast(1),
            )
        }.sortedByDescending { it.busyMs }
    }

    /**
     * Every feature over the window since the previous call, busiest first.
     *
     * This is the one to read when hunting CPU: it is the only view that
     * distinguishes a loop that is burning the device right now from one that
     * burned it twenty minutes ago and stopped. Advancing the marks is a side
     * effect, so call it from exactly one place.
     */
    fun drain(): List<Stat> {
        val now = System.nanoTime()
        return accs.map { (name, a) ->
            val iters = a.iterations.get()
            val busyNanos = a.busyNanos.get()
            val markIters = a.markIters.getAndSet(iters)
            val markBusy = a.markBusyNanos.getAndSet(busyNanos)
            val markAt = a.markAtNanos.getAndSet(now)

            val rIters = (iters - markIters).coerceAtLeast(0)
            val rBusyMs = ((busyNanos - markBusy) / 1_000_000L).coerceAtLeast(0)
            val rWindowNs = (now - markAt).coerceAtLeast(1)
            val rWindowMs = rWindowNs / 1_000_000L

            val lifeNs = (now - a.startedNanos.get()).coerceAtLeast(1)
            val lifeMs = lifeNs / 1_000_000L
            val lifeBusyMs = busyNanos / 1_000_000L

            Stat(
                feature = name,
                iterations = iters,
                busyMs = lifeBusyMs,
                windowMs = lifeMs,
                hz = iters * 1_000_000_000.0 / lifeNs,
                corePct = lifeBusyMs * 100.0 / lifeMs.coerceAtLeast(1),
                recentIters = rIters,
                recentBusyMs = rBusyMs,
                recentWindowMs = rWindowMs,
                recentHz = rIters * 1_000_000_000.0 / rWindowNs,
                recentCorePct = rBusyMs * 100.0 / rWindowMs.toDouble().coerceAtLeast(1.0),
            )
        }.sortedWith(
            compareByDescending<Stat> { it.recentBusyMs }.thenByDescending { it.busyMs }
        )
    }

    /**
     * Log the ranked table, window-first. [top] keeps the noise down; nothing
     * is dropped from the accumulators.
     *
     * Two columns because one is not enough. `win` is since the previous dump
     * and is the answer to "what is hot right now"; `life` is since the loop
     * first ran and is the answer to "what has this cost me overall". A loop
     * that has stopped is tagged IDLE on the window column — reading the
     * lifetime column alone is what made a dead ASR loop look like it was
     * still decoding at 4 Hz.
     */
    fun dump(top: Int = 12) {
        val stats = drain().take(top)
        if (stats.isEmpty()) {
            Log.i(TAG, "no instrumented loops have run")
            return
        }
        val b = StringBuilder(
            "loop telemetry (busy = CPU inside the loop body; win = since last dump)\n")
        for (s in stats) {
            b.append("  win %5.1f%% %6.2f Hz | life %5.1f%% %6.2f Hz | %9d iters  %s%s\n".format(
                s.recentCorePct, s.recentHz,
                s.corePct, s.hz,
                s.iterations, s.feature,
                if (s.isLive) "" else "  [IDLE]"))
        }
        Log.i(TAG, b.toString())
    }

    /** Forget everything and restart the windows. */
    fun reset() = accs.clear()
}

/**
 * Java stacks for every thread running our code, straight to logcat.
 *
 * A SIGQUIT dump (`kill -3`) lands in `/data/anr/`, which is owned by
 * `tombstoned` and mode 0660 — unreadable by shell, so it cannot be pulled off
 * a non-rooted device. This gets the same information through a channel we can
 * actually read, and is the tool to reach for next time a worker is anonymous.
 */
object ThreadDump {
    private const val TAG = "MeowThreads"
    private const val PREFIX = "com.example.meowmba"

    fun log(maxFrames: Int = 14) {
        val all = Thread.getAllStackTraces()
        val ours = all.entries
            .filter { (_, stack) -> stack.any { it.className.startsWith(PREFIX) } }
            .sortedBy { it.key.name }
        if (ours.isEmpty()) {
            Log.i(TAG, "no thread is running $PREFIX code")
            return
        }
        val b = StringBuilder("${ours.size} of ${all.size} threads in app code\n")
        for (entry in ours) {
            val thread = entry.key
            b.append("[${thread.name}] state=${thread.state} daemon=${thread.isDaemon}\n")
            // Filter to our frames rather than stopping at the first foreign
            // one. A coroutine worker's stack starts in kotlinx.coroutines, so
            // the old "break at the first frame that is not ours" printed the
            // thread name and nothing else — useless precisely for the
            // DefaultDispatch workers this was written to identify.
            val appFrames = entry.value.filter { it.className.startsWith(PREFIX) }
            for (frame in appFrames.take(maxFrames)) {
                b.append("    at ").append(frame).append('\n')
            }
            if (appFrames.size > maxFrames) {
                b.append("    ... ${appFrames.size - maxFrames} more app frames\n")
            }
        }
        // logcat drops anything past ~4 KB in one message, so a full dump has to
        // be split or the interesting threads are the ones that get cut.
        val text = b.toString()
        var at = 0
        var part = 1
        while (at < text.length) {
            val end = (at + CHUNK).coerceAtMost(text.length)
            val cut = text.lastIndexOf('\n', end - 1).takeIf { it > at } ?: end
            Log.i(TAG, "($part) " + text.substring(at, cut))
            at = cut + 1
            part++
        }
    }

    private const val CHUNK = 3_500
}
