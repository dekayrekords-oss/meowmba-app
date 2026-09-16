package com.example.meowmba.core.audio

import android.speech.SpeechRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** One ordered, cancellable refinement queue per mic session. A cancelled
 * request can neither answer a new turn nor pair its text with newer audio. */
abstract class RefiningSpeechEngine(
    protected val local: SpeechEngine,
    private val scope: CoroutineScope,
    private val onIssue: (String?) -> Unit = {},
    private val timeoutMs: Long = 15_000L,
    private val localFallback: (suspend (ShortArray) -> String?)? = null,
) : SpeechEngine, SpeechAudioCapture {
    override val continuousCapture get() = local.continuousCapture
    override val deliversFinalResults = true
    override val finalizationPending get() = finalizer?.hasPending == true || local.finalizationPending
    override val isListening get() = local.isListening
    override val partialResults get() = local.partialResults
    override val isAvailable get() = local.isAvailable
    private var generation = 0L
    private var finalizer: SpeechFinalizer? = null
    private var result: ((String) -> Unit)? = null
    private var error: ((Int) -> Unit)? = null
    private var audio: CapturedSpeechAudio? = null

    protected abstract suspend fun transcribe(pcm: ShortArray): String?

    override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
        stopListening()
        val session = ++generation
        result = onResult
        error = onError
        audio = null
        val queue = SpeechFinalizer(scope,
            refine = { pcm ->
                var failure = "Cloud transcription unavailable."
                val text = try {
                    withTimeoutOrNull(timeoutMs) { transcribe(pcm) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (e: CloudTranscriptionFailure) { failure = e.message ?: failure; null }
                catch (_: Exception) { null }
                if (text != null) {
                    if (generation == session) onIssue(null)
                    text
                } else {
                    if (generation == session) onIssue("$failure Trying local recognition.")
                    val recovered = try {
                        withTimeoutOrNull(4_000L) { localFallback?.invoke(pcm) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                    if (generation == session) onIssue("$failure " + if (recovered != null)
                        "Using local Whisper for this turn." else "Using the local transcript for this turn.")
                    recovered
                }
            },
            deliver = { text, pcm ->
                if (generation == session) {
                    audio = CapturedSpeechAudio(pcm, 16_000)
                    result?.invoke(text)
                }
            },
            capacity = 2,
            discard = { if (generation == session) result?.invoke("") },
        )
        finalizer = queue
        local.startListening(onResult = { text ->
            if (generation == session) {
                val captured = (local as? SpeechAudioCapture)?.snapshotCapturedAudio()
                if (captured == null || !captured.isUsable || captured.sampleRate != 16_000) {
                    if (text.isBlank() && finalizationPending) return@startListening
                    // Empty terminal clears a discarded preview; it is not a new recording.
                    audio = captured
                    if (text.isNotBlank()) onIssue("Audio unavailable for cloud transcription. Using local text.")
                    result?.invoke(text)
                } else if (!queue.offer(text, captured.pcm16.copyOf())) {
                    onIssue("Speech processing is busy. Please try again.")
                    error?.invoke(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
                }
            }
        }, onError = { if (generation == session) error?.invoke(it) })
    }

    override fun swapCallbacks(onResult: (String) -> Unit, onError: (Int) -> Unit) {
        result = onResult
        error = onError
    }

    override fun snapshotCapturedAudio() = audio?.let { CapturedSpeechAudio(it.pcm16.copyOf(), it.sampleRate) }
    override fun setFocusedListening(on: Boolean) = local.setFocusedListening(on)
    override fun finishInput() = finalizationPending || local.finishInput()
    override fun stopListening() {
        generation++
        result = null
        error = null
        finalizer?.close()
        finalizer = null
        local.stopListening()
    }
    override fun destroy() { stopListening(); local.destroy() }
}
