package com.example.meowmba

import com.example.meowmba.core.diagnostics.InteractionTimings
import com.example.meowmba.core.diagnostics.InteractionTimings.Metric
import org.junit.Assert.*
import org.junit.Test

class InteractionTimingsTest {
    private class Clock(var now: Long = 0L) {
        fun ms(value: Long) { now = value * 1_000_000L }
    }

    @Test fun `disabled by default does not read clock or create completed timings`() {
        var clockCalls = 0
        val recorder = InteractionTimings({ clockCalls++; 123L })
        assertEquals(0L, recorder.beginTurn())
        recorder.microphoneRequested(0L)
        recorder.microphoneReady(0L)
        recorder.firstTranscript(0L)
        recorder.turnSubmitted(0L)
        recorder.firstSpeechOutput(0L)
        recorder.interruptRequested(0L)
        recorder.ttsStoppedAcknowledged(0L)
        recorder.framePresented(100L)
        recorder.framePresented(100_000_000L)
        val snapshot = recorder.snapshot()
        assertFalse(snapshot.enabled)
        assertNull(snapshot.activeTurnId)
        assertTrue(snapshot.pending.isEmpty())
        assertTrue(snapshot.metrics.all { it.summary.sampleCount == 0 && it.summary.medianMs == null && it.summary.p95Ms == null })
        assertEquals(0, snapshot.frames.gaps.sampleCount)
        assertEquals(0, clockCalls)
    }

    @Test fun `one monotonic turn records four distinct callback intervals`() {
        val clock = Clock()
        val recorder = InteractionTimings({ clock.now }).also { it.setEnabled(true) }
        val id = recorder.beginTurn()
        recorder.microphoneRequested(id)
        clock.ms(20); recorder.microphoneReady(id)
        clock.ms(100); recorder.firstTranscript(id)
        clock.ms(150); recorder.turnSubmitted(id)
        clock.ms(600); recorder.firstSpeechOutput(id)
        clock.ms(650); recorder.interruptRequested(id)
        clock.ms(675); recorder.ttsStoppedAcknowledged(id)
        val snapshot = recorder.snapshot()
        val expected = mapOf(Metric.MIC_REQUEST_TO_READY to 20.0,
            Metric.MIC_READY_TO_FIRST_TRANSCRIPT to 80.0,
            Metric.TURN_SUBMIT_TO_FIRST_SPEECH_OUTPUT to 450.0,
            Metric.INTERRUPT_TO_TTS_STOP_ACK to 25.0)
        expected.forEach { (metric, value) ->
            val summary = snapshot.summary(metric)
            assertEquals(1, summary.sampleCount)
            assertEquals(value, summary.medianMs!!, .00001)
            assertEquals(value, summary.p95Ms!!, .00001)
        }
        assertTrue(snapshot.pending.isEmpty())
    }

    @Test fun `out of order and duplicate callbacks cannot invent or double count a measurement`() {
        val clock = Clock()
        val recorder = InteractionTimings({ clock.now }).also { it.setEnabled(true) }
        val id = recorder.beginTurn()
        recorder.microphoneReady(id)
        recorder.firstTranscript(id)
        recorder.firstSpeechOutput(id)
        recorder.ttsStoppedAcknowledged(id)
        assertTrue(recorder.snapshot().metrics.all { it.summary.medianMs == null })
        recorder.microphoneRequested(id)
        clock.ms(30); recorder.microphoneReady(id)
        clock.ms(50); recorder.firstTranscript(id)
        recorder.microphoneReady(id)
        recorder.firstTranscript(id)
        recorder.turnSubmitted(id)
        clock.ms(90); recorder.firstSpeechOutput(id)
        recorder.firstSpeechOutput(id)
        val snapshot = recorder.snapshot()
        assertEquals(1, snapshot.summary(Metric.MIC_REQUEST_TO_READY).sampleCount)
        assertEquals(0, snapshot.summary(Metric.MIC_READY_TO_FIRST_TRANSCRIPT).sampleCount)
        assertEquals(1, snapshot.summary(Metric.TURN_SUBMIT_TO_FIRST_SPEECH_OUTPUT).sampleCount)
        assertNull(snapshot.summary(Metric.INTERRUPT_TO_TTS_STOP_ACK).medianMs)
        assertTrue(snapshot.ignoredCallbacks >= 7)
    }

    @Test fun `first transcript preceding readiness stays unavailable after late ready and final callbacks`() {
        val clock = Clock()
        val recorder = InteractionTimings({ clock.now }).also { it.setEnabled(true) }
        val id = recorder.beginTurn()
        recorder.microphoneRequested(id)
        clock.ms(10); recorder.firstTranscript(id)
        clock.ms(20); recorder.microphoneReady(id)
        var snapshot = recorder.snapshot()
        assertEquals(20.0, snapshot.summary(Metric.MIC_REQUEST_TO_READY).medianMs!!, .001)
        assertNull(snapshot.summary(Metric.MIC_READY_TO_FIRST_TRANSCRIPT).medianMs)
        assertTrue(snapshot.pending.isEmpty())
        clock.ms(500); recorder.firstTranscript(id)
        snapshot = recorder.snapshot()
        assertEquals(0, snapshot.summary(Metric.MIC_READY_TO_FIRST_TRANSCRIPT).sampleCount)
        assertNull(snapshot.summary(Metric.MIC_READY_TO_FIRST_TRANSCRIPT).p95Ms)
        assertTrue(snapshot.pending.isEmpty())
        val next = recorder.beginTurn()
        recorder.microphoneRequested(next)
        clock.ms(520); recorder.microphoneReady(next)
        clock.ms(560); recorder.firstTranscript(next)
        assertEquals(40.0, recorder.snapshot().summary(Metric.MIC_READY_TO_FIRST_TRANSCRIPT).medianMs!!, .001)
    }

    @Test fun `new turn and reset reject late callbacks carrying their original sequence ids`() {
        val clock = Clock()
        val recorder = InteractionTimings({ clock.now }).also { it.setEnabled(true) }
        val old = recorder.beginTurn()
        recorder.microphoneRequested(old)
        val current = recorder.beginTurn()
        recorder.microphoneRequested(current)
        clock.ms(15); recorder.microphoneReady(old)
        recorder.firstTranscript(old)
        assertNull(recorder.snapshot().summary(Metric.MIC_REQUEST_TO_READY).medianMs)
        clock.ms(20); recorder.microphoneReady(current)
        assertEquals(20.0, recorder.snapshot().summary(Metric.MIC_REQUEST_TO_READY).medianMs!!, .001)
        recorder.reset()
        val afterReset = recorder.beginTurn()
        assertNotEquals(current, afterReset)
        recorder.microphoneReady(current)
        recorder.firstTranscript(current)
        recorder.firstSpeechOutput(old)
        val snapshot = recorder.snapshot()
        assertTrue(snapshot.metrics.all { it.summary.sampleCount == 0 })
        assertTrue(snapshot.pending.isEmpty())
        assertEquals(afterReset, snapshot.activeTurnId)
    }

    @Test fun `disable discards pending work while preserving finished counters until reset`() {
        val clock = Clock()
        val recorder = InteractionTimings({ clock.now }).also { it.setEnabled(true) }
        val id = recorder.beginTurn()
        recorder.microphoneRequested(id)
        clock.ms(10); recorder.microphoneReady(id)
        assertEquals(Metric.MIC_READY_TO_FIRST_TRANSCRIPT, recorder.snapshot().pending.single().metric)
        recorder.setEnabled(false)
        clock.ms(50); recorder.firstTranscript(id)
        assertTrue(recorder.snapshot().pending.isEmpty())
        assertEquals(1, recorder.snapshot().summary(Metric.MIC_REQUEST_TO_READY).sampleCount)
        assertNull(recorder.snapshot().summary(Metric.MIC_READY_TO_FIRST_TRANSCRIPT).medianMs)
        recorder.setEnabled(true)
        assertNotEquals(id, recorder.beginTurn())
        recorder.reset()
        assertTrue(recorder.isEnabled)
        assertTrue(recorder.snapshot().metrics.all { it.summary.sampleCount == 0 })
    }

    @Test fun `in progress is explicit and missing or backward time never becomes zero latency`() {
        val clock = Clock().also { it.ms(100) }
        val recorder = InteractionTimings({ clock.now }).also { it.setEnabled(true) }
        val id = recorder.beginTurn()
        recorder.microphoneRequested(id)
        clock.ms(145)
        var snapshot = recorder.snapshot()
        assertEquals(45.0, snapshot.pending.single().elapsedMs!!, .001)
        assertNull(snapshot.summary(Metric.MIC_REQUEST_TO_READY).medianMs)
        clock.ms(99); recorder.microphoneReady(id)
        snapshot = recorder.snapshot()
        assertNull(snapshot.pending.single().elapsedMs)
        assertNull(snapshot.summary(Metric.MIC_REQUEST_TO_READY).medianMs)
        assertEquals(1L, snapshot.rejectedDurations)
        clock.ms(150); recorder.microphoneReady(id)
        assertEquals(50.0, recorder.snapshot().summary(Metric.MIC_REQUEST_TO_READY).medianMs!!, .001)
    }

    @Test fun `ring storage caps samples and reports median and nearest-rank p95 for retained window`() {
        val clock = Clock()
        val recorder = InteractionTimings({ clock.now }, capacity = 3).also { it.setEnabled(true) }
        for (duration in 1L..6L) {
            val id = recorder.beginTurn()
            recorder.turnSubmitted(id)
            clock.now += duration * 1_000_000L
            recorder.firstSpeechOutput(id)
        }
        val summary = recorder.snapshot().summary(Metric.TURN_SUBMIT_TO_FIRST_SPEECH_OUTPUT)
        assertEquals(3, summary.sampleCount)
        assertEquals(6L, summary.totalCount)
        assertEquals(5.0, summary.medianMs!!, .001)
        assertEquals(6.0, summary.p95Ms!!, .001)
        assertEquals(1, InteractionTimings(capacity = -50).snapshot().capacity)
        assertEquals(InteractionTimings.MAX_CAPACITY, InteractionTimings(capacity = Int.MAX_VALUE).snapshot().capacity)
    }

    @Test fun `observed same-clock completion is a valid zero while unavailable remains null`() {
        val recorder = InteractionTimings({ 0L }).also { it.setEnabled(true) }
        val id = recorder.beginTurn()
        recorder.turnSubmitted(id)
        recorder.firstSpeechOutput(id)
        val snapshot = recorder.snapshot()
        assertEquals(1, snapshot.summary(Metric.TURN_SUBMIT_TO_FIRST_SPEECH_OUTPUT).sampleCount)
        assertEquals(0.0, snapshot.summary(Metric.TURN_SUBMIT_TO_FIRST_SPEECH_OUTPUT).medianMs!!, 0.0)
        assertNull(snapshot.summary(Metric.MIC_REQUEST_TO_READY).medianMs)
    }

    @Test fun `frame gaps compare to explicit cadence and ignore duplicates old callbacks and pauses`() {
        val recorder = InteractionTimings().also { it.setEnabled(true) }
        val interval = 16_666_667L
        recorder.setFrameCadence(interval)
        recorder.framePresented(0)
        recorder.framePresented(interval)
        recorder.framePresented(interval) // Duplicate is not a zero-duration frame.
        recorder.framePresented(interval / 2) // Old callback must not rewind the baseline.
        recorder.framePresented(interval * 3)
        recorder.framePresented(interval * 4)
        var frame = recorder.snapshot().frames
        assertEquals(3, frame.gaps.sampleCount)
        assertEquals(interval / 1_000_000.0, frame.gaps.medianMs!!, .000001)
        assertEquals(2 * interval / 1_000_000.0, frame.gaps.p95Ms!!, .000001)
        assertEquals(3, frame.comparedSamples)
        assertEquals(1, frame.gapsAboveCadence)
        recorder.pauseFrames()
        recorder.framePresented(100_000_000_000L)
        assertEquals(3, recorder.snapshot().frames.gaps.sampleCount)
        recorder.framePresented(100_000_000_000L + interval)
        frame = recorder.snapshot().frames
        assertEquals(4, frame.gaps.sampleCount)
        assertEquals(1, frame.gapsAboveCadence)
    }

    @Test fun `unknown or changed cadence never fabricates relative frame performance`() {
        val recorder = InteractionTimings().also { it.setEnabled(true) }
        recorder.framePresented(0)
        recorder.framePresented(16_000_000)
        assertEquals(1, recorder.snapshot().frames.gaps.sampleCount)
        assertNull(recorder.snapshot().frames.gapsAboveCadence)
        recorder.setFrameCadence(8_000_000, InteractionTimings.CadenceSource.OBSERVED)
        recorder.framePresented(40_000_000)
        recorder.framePresented(48_000_000)
        var frame = recorder.snapshot().frames
        assertEquals(2, frame.gaps.sampleCount)
        assertEquals(1, frame.comparedSamples)
        assertEquals(0, frame.gapsAboveCadence)
        assertEquals(InteractionTimings.CadenceSource.OBSERVED, frame.cadenceSource)
        recorder.setFrameCadence(16_000_000)
        frame = recorder.snapshot().frames
        assertEquals(0, frame.comparedSamples)
        assertNull(frame.gapsAboveCadence)
        recorder.setFrameCadence(-1)
        assertNull(recorder.snapshot().frames.expectedIntervalMs)
        assertNull(recorder.snapshot().frames.cadenceSource)
    }

    @Test fun `frame storage and duration rejection stay bounded without payload records`() {
        val recorder = InteractionTimings(capacity = 5).also { it.setEnabled(true) }
        recorder.setFrameCadence(2_000_000)
        for (frame in 0..10_000) recorder.framePresented(frame * 1_000_000L)
        var snapshot = recorder.snapshot()
        assertEquals(5, snapshot.frames.gaps.sampleCount)
        assertEquals(10_000L, snapshot.frames.gaps.totalCount)
        assertEquals(5, snapshot.frames.comparedSamples)
        recorder.framePresented(400_000_000_000L)
        snapshot = recorder.snapshot()
        assertEquals(5, snapshot.frames.gaps.sampleCount)
        assertEquals(1L, snapshot.rejectedDurations)
        val clock = Clock()
        val interaction = InteractionTimings({ clock.now }).also { it.setEnabled(true) }
        val id = interaction.beginTurn()
        interaction.turnSubmitted(id)
        clock.ms(300_001); interaction.firstSpeechOutput(id)
        assertNull(interaction.snapshot().summary(Metric.TURN_SUBMIT_TO_FIRST_SPEECH_OUTPUT).medianMs)
        assertTrue(interaction.snapshot().pending.isEmpty())
        assertEquals(1L, interaction.snapshot().rejectedDurations)
    }

    @Test fun `ending a failed turn clears pending stages and rejects its late acknowledgements`() {
        val clock = Clock()
        val recorder = InteractionTimings({ clock.now }).also { it.setEnabled(true) }
        val id = recorder.beginTurn()
        recorder.interruptRequested(id)
        recorder.endTurn(id)
        clock.ms(12); recorder.ttsStoppedAcknowledged(id)
        val snapshot = recorder.snapshot()
        assertTrue(snapshot.pending.isEmpty())
        assertNull(snapshot.activeTurnId)
        assertNull(snapshot.summary(Metric.INTERRUPT_TO_TTS_STOP_ACK).medianMs)
    }
}
