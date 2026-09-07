package com.example.meowmba.core.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/** One ordered worker per listening session. Stopping discards queued work and
 * forbids late delivery; native decode itself may finish before cancellation.
 * The bound prevents runaway RAM/CPU backlog. Overflow is reported to the UI,
 * never silently accepted as a successful transcription.
 */
internal class SpeechFinalizer(
    scope: CoroutineScope,
    private val refine: suspend (ShortArray) -> String?,
    private val deliver: suspend (String) -> Unit,
    capacity: Int = 8,
) {
    private data class Utterance(val text: String, val pcm: ShortArray)
    private val pending = Channel<Utterance>(capacity)
    private val worker = scope.launch {
        for (utterance in pending) {
            coroutineContext.ensureActive()
            // Hardware/OS-muted capture is exact digital zero. No acoustic
            // threshold: even the quietest non-zero voice keeps full decoding.
            if (utterance.pcm.isNotEmpty() && utterance.pcm.all { it == 0.toShort() }) continue
            val refined = try {
                refine(utterance.pcm)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null // Recover from unavailable refiner using streaming ASR.
            }
            coroutineContext.ensureActive()
            SpokenText.refinedTranscript(utterance.text, refined)?.let { deliver(it) }
        }
    }

    fun offer(text: String, pcm: ShortArray): Boolean = pending.trySend(Utterance(text, pcm)).isSuccess

    fun close() {
        pending.cancel()
        worker.cancel()
    }
}
