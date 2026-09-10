package com.example.meowmba.core.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import java.util.concurrent.atomic.AtomicInteger

/** One ordered worker per listening session. Stopping discards queued work and
 * forbids late delivery; native decode itself may finish before cancellation.
 * The bound prevents runaway RAM/CPU backlog. Overflow is reported to the UI,
 * never silently accepted as a successful transcription.
 */
internal class SpeechFinalizer(
    scope: CoroutineScope,
    private val refine: suspend (ShortArray) -> String?,
    /** The final and its original audio cross the delivery boundary together. */
    private val deliver: suspend (String, ShortArray) -> Unit,
    capacity: Int = 8,
    private val discard: suspend () -> Unit = {},
) {
    private data class Utterance(val text: String, val pcm: ShortArray)
    private val pending = Channel<Utterance>(capacity)
    private val outstanding = AtomicInteger(0)
    val hasPending: Boolean get() = outstanding.get() > 0 && worker.isActive
    private val worker = scope.launch {
        for (utterance in pending) {
            try {
            coroutineContext.ensureActive()
            // Hardware/OS-muted capture is exact digital zero. No acoustic
            // threshold: even the quietest non-zero voice keeps full decoding.
            if (utterance.pcm.isNotEmpty() && utterance.pcm.all { it == 0.toShort() }) { discard(); continue }
            val refined = try {
                refine(utterance.pcm)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null // Recover from unavailable refiner using streaming ASR.
            }
            coroutineContext.ensureActive()
            val text = SpokenText.refinedTranscript(utterance.text, refined)
            if (text != null) deliver(text, utterance.pcm) else discard()
            } finally { outstanding.decrementAndGet() }
        }
    }

    fun offer(text: String, pcm: ShortArray): Boolean {
        outstanding.incrementAndGet()
        val accepted = pending.trySend(Utterance(text, pcm)).isSuccess
        if (!accepted) outstanding.decrementAndGet()
        return accepted
    }

    fun close() {
        pending.cancel()
        worker.cancel()
    }
}
