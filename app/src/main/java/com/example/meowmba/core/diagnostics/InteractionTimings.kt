package com.example.meowmba.core.diagnostics

import kotlin.math.ceil

/** Optional local timing counters. No text, audio, images, user identifiers,
 * persistence, or network; disabled until explicitly enabled. All interaction
 * timestamps come from one injected monotonic nanosecond clock. Frame timestamps
 * are deltas from the renderer's own monotonic frame clock, never mixed with it.
 *
 * Every callback must retain its original beginTurn ID. New turns, reset and
 * disable invalidate old IDs, so late recognizer/TTS work cannot finish a newer
 * measurement. The frame path writes primitive ring buffers without snapshots,
 * collection objects, formatting, or Compose state updates. */
class InteractionTimings(
    private val clockNanos: () -> Long = System::nanoTime,
    capacity: Int = 240,
) {
    enum class Metric(val label: String) {
        MIC_REQUEST_TO_READY("Mic request → ready"),
        MIC_READY_TO_FIRST_TRANSCRIPT("Mic ready → first transcript"),
        TURN_SUBMIT_TO_FIRST_SPEECH_OUTPUT("Submitted turn → first speech output"),
        INTERRUPT_TO_TTS_STOP_ACK("Interrupt → TTS stop acknowledgement"),
    }

    enum class CadenceSource { CONFIGURED, OBSERVED }

    data class Summary(
        val sampleCount: Int,
        val totalCount: Long,
        val medianMs: Double?,
        val p95Ms: Double?,
    )

    data class MetricSummary(val metric: Metric, val summary: Summary)
    data class Pending(val metric: Metric, val elapsedMs: Double?)
    data class FrameSummary(
        val gaps: Summary,
        val expectedIntervalMs: Double?,
        val cadenceSource: CadenceSource?,
        val comparedSamples: Int,
        /** Null means no frame had an available cadence, not zero late frames. */
        val gapsAboveCadence: Int?,
    )

    data class Snapshot(
        val enabled: Boolean,
        val capacity: Int,
        val metrics: List<MetricSummary>,
        val frames: FrameSummary,
        val pending: List<Pending>,
        val activeTurnId: Long?,
        val ignoredCallbacks: Long,
        val rejectedDurations: Long,
    ) {
        fun summary(metric: Metric): Summary = metrics.first { it.metric == metric }.summary
    }

    private val sampleCapacity = capacity.coerceIn(1, MAX_CAPACITY)
    private val samples = Array(Metric.entries.size) { Ring(sampleCapacity) }
    private val frames = Ring(sampleCapacity)
    // -1: no known cadence, 0: within threshold, 1: exceeds threshold.
    private val frameComparisons = IntArray(sampleCapacity) { -1 }
    private val starts = LongArray(Metric.entries.size) { UNSET }
    private val complete = BooleanArray(Metric.entries.size)
    @Volatile private var enabled = false
    private var serial = 0L
    private var currentTurn = 0L
    private var readyAt = UNSET
    private var lastFrame = UNSET
    private var expectedFrameInterval = UNSET
    private var cadenceSource: CadenceSource? = null
    private var ignored = 0L
    private var rejected = 0L

    val isEnabled: Boolean get() = enabled

    @Synchronized fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        invalidateTurn()
        lastFrame = UNSET
    }

    /** Retains the user's enable switch and cadence, discards all measurements. */
    @Synchronized fun reset() {
        samples.forEach { it.clear() }
        frames.clear()
        frameComparisons.fill(-1)
        ignored = 0L
        rejected = 0L
        invalidateTurn()
        lastFrame = UNSET
    }

    /** 0 is the disabled/no-session token and is never accepted as a callback ID. */
    @Synchronized fun beginTurn(): Long {
        if (!enabled) return 0L
        invalidateTurn()
        serial = if (serial == Long.MAX_VALUE) 1L else serial + 1L
        currentTurn = serial
        return currentTurn
    }

    /** Use on cancellation/error when no callback can complete the pending stages. */
    @Synchronized fun endTurn(id: Long) {
        if (accept(id)) invalidateTurn()
    }

    @Synchronized fun microphoneRequested(id: Long) {
        if (!accept(id)) return
        start(Metric.MIC_REQUEST_TO_READY, clockNanos())
    }

    @Synchronized fun microphoneReady(id: Long) {
        if (!accept(id)) return
        val now = clockNanos()
        if (finish(Metric.MIC_REQUEST_TO_READY, now)) {
            readyAt = now
            // A transcript may have arrived before a separately collected ready
            // signal. Its first-arrival time cannot be reconstructed afterward.
            if (!complete[Metric.MIC_READY_TO_FIRST_TRANSCRIPT.ordinal]) {
                start(Metric.MIC_READY_TO_FIRST_TRANSCRIPT, now)
            }
        }
    }

    /** First recognizer transcript callback, NOT acoustic speech onset. */
    @Synchronized fun firstTranscript(id: Long) {
        if (!accept(id)) return
        if (readyAt == UNSET) {
            // Do not let a later final transcript stand in for a first partial
            // that preceded readiness observation. Mark unavailable/completed.
            complete[Metric.MIC_READY_TO_FIRST_TRANSCRIPT.ordinal] = true
            ignored = increment(ignored)
            return
        }
        finish(Metric.MIC_READY_TO_FIRST_TRANSCRIPT, clockNanos())
    }

    /** Also works for text submissions, which need no microphone timing stages. */
    @Synchronized fun turnSubmitted(id: Long) {
        if (!accept(id)) return
        start(Metric.TURN_SUBMIT_TO_FIRST_SPEECH_OUTPUT, clockNanos())
    }

    /** Call from a real first-output/TTS callback, not when speech is queued. */
    @Synchronized fun firstSpeechOutput(id: Long) {
        if (!accept(id)) return
        finish(Metric.TURN_SUBMIT_TO_FIRST_SPEECH_OUTPUT, clockNanos())
    }

    @Synchronized fun interruptRequested(id: Long) {
        if (!accept(id)) return
        start(Metric.INTERRUPT_TO_TTS_STOP_ACK, clockNanos())
    }

    /** Omit when the backend offers no actual stop acknowledgement. */
    @Synchronized fun ttsStoppedAcknowledged(id: Long) {
        if (!accept(id)) return
        finish(Metric.INTERRUPT_TO_TTS_STOP_ACK, clockNanos())
    }

    /** Supply the display/renderer cadence explicitly. Null records absolute
     * frame gaps only; we never infer a refresh rate from slow callback gaps. */
    @Synchronized fun setFrameCadence(intervalNanos: Long?, source: CadenceSource = CadenceSource.CONFIGURED) {
        val interval = intervalNanos?.takeIf { it in MIN_FRAME_INTERVAL_NS..MAX_FRAME_INTERVAL_NS } ?: UNSET
        val effectiveSource = source.takeIf { interval != UNSET }
        if (interval == expectedFrameInterval && effectiveSource == cadenceSource) return
        expectedFrameInterval = interval
        cadenceSource = effectiveSource
        lastFrame = UNSET
        // Relative statistics from different refresh rates are not one sample
        // window. Keep the absolute intervals but invalidate prior comparisons.
        frameComparisons.fill(-1)
    }

    /** Call only for active visible renderer frames, using its frame timestamp. */
    @Synchronized fun framePresented(frameTimeNanos: Long) {
        if (!enabled || frameTimeNanos == UNSET) return
        val previous = lastFrame
        if (previous == UNSET) { lastFrame = frameTimeNanos; return }
        if (frameTimeNanos <= previous) return // Late/duplicate frames cannot move the baseline.
        val gap = frameTimeNanos - previous
        if (gap <= 0L) return // Overflow; keep the previous valid baseline.
        lastFrame = frameTimeNanos
        if (gap > MAX_SAMPLE_NS) { rejected = increment(rejected); return }
        val index = frames.writeIndex
        frames.add(gap)
        frameComparisons[index] = if (expectedFrameInterval == UNSET) -1
            else if (gap.toDouble() > expectedFrameInterval.toDouble() * FRAME_GAP_MULTIPLIER) 1 else 0
    }

    /** Call when hidden/paused, so a lifecycle pause is not presented as a frame gap. */
    @Synchronized fun pauseFrames() { lastFrame = UNSET }

    /** Allocate/sort only on deliberate UI refresh, never from framePresented. */
    @Synchronized fun snapshot(): Snapshot {
        val now = if (enabled) clockNanos() else UNSET
        val pending = Metric.entries.mapNotNull { metric ->
            val index = metric.ordinal
            if (!enabled || currentTurn == 0L || starts[index] == UNSET || complete[index]) null
            else Pending(metric, elapsed(starts[index], now)?.toDouble()?.div(NS_PER_MS))
        }
        var compared = 0
        var over = 0
        for (i in 0 until frames.size) {
            if (frameComparisons[i] >= 0) compared++
            if (frameComparisons[i] == 1) over++
        }
        return Snapshot(enabled, sampleCapacity,
            Metric.entries.map { MetricSummary(it, samples[it.ordinal].summary()) },
            FrameSummary(frames.summary(), expectedFrameInterval.takeIf { it != UNSET }?.toDouble()?.div(NS_PER_MS),
                cadenceSource, compared, over.takeIf { compared > 0 }),
            pending, currentTurn.takeIf { it != 0L }, ignored, rejected)
    }

    private fun start(metric: Metric, now: Long) {
        val index = metric.ordinal
        if (now == UNSET || starts[index] != UNSET || complete[index]) { ignored = increment(ignored); return }
        starts[index] = now
    }

    private fun finish(metric: Metric, now: Long): Boolean {
        val index = metric.ordinal
        val start = starts[index]
        if (start == UNSET || complete[index]) { ignored = increment(ignored); return false }
        val duration = elapsed(start, now)
        if (duration == null) { rejected = increment(rejected); return false }
        complete[index] = true
        if (duration <= MAX_SAMPLE_NS) samples[index].add(duration)
        else rejected = increment(rejected)
        return true
    }

    private fun accept(id: Long): Boolean {
        if (!enabled) return false
        if (id == 0L || id != currentTurn) { ignored = increment(ignored); return false }
        return true
    }

    private fun invalidateTurn() {
        currentTurn = 0L
        readyAt = UNSET
        starts.fill(UNSET)
        complete.fill(false)
    }

    private class Ring(capacity: Int) {
        private val values = LongArray(capacity)
        var writeIndex = 0; private set
        var size = 0; private set
        private var total = 0L
        fun add(value: Long) {
            values[writeIndex] = value
            writeIndex = (writeIndex + 1) % values.size
            size = minOf(size + 1, values.size)
            total = increment(total)
        }
        fun clear() { values.fill(0L); writeIndex = 0; size = 0; total = 0L }
        fun summary(): Summary {
            if (size == 0) return Summary(0, total, null, null)
            val sorted = values.copyOf(size).also { it.sort() }
            val middle = size / 2
            val median = if (size % 2 == 1) sorted[middle].toDouble()
                else (sorted[middle - 1].toDouble() + sorted[middle].toDouble()) / 2.0
            val p95 = sorted[(ceil(size * .95).toInt() - 1).coerceIn(0, size - 1)].toDouble()
            return Summary(size, total, median / NS_PER_MS, p95 / NS_PER_MS)
        }
    }

    companion object {
        const val MAX_CAPACITY = 512
        const val FRAME_GAP_MULTIPLIER = 1.5
        private const val NS_PER_MS = 1_000_000.0
        private const val MAX_SAMPLE_NS = 300_000_000_000L
        private const val MIN_FRAME_INTERVAL_NS = 2_000_000L
        private const val MAX_FRAME_INTERVAL_NS = 1_000_000_000L
        private const val UNSET = Long.MIN_VALUE
        private fun increment(value: Long): Long = if (value == Long.MAX_VALUE) value else value + 1L
        private fun elapsed(start: Long, end: Long): Long? {
            if (start == UNSET || end == UNSET) return null
            val delta = end - start
            return delta.takeIf { it >= 0L && end >= start }
        }
    }
}
