package com.example.meowmba.core.audio

import android.content.Context
import android.util.Log
import java.io.File
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingManager

/**
 * "Is that actually you?" — a second gate after the wake phrase fires.
 *
 * Enrol a few samples of the owner saying the phrase; on each wake, the pre-roll audio is
 * embedded and compared against the enrolled prints. That rejects the television, a
 * podcast, or someone else in the room saying "yo meow", and it lets the keyword itself
 * stay sensitive because a false wake is now caught here instead of costing a response.
 *
 * Fails OPEN by design. If nothing is enrolled, the model is missing, or extraction
 * errors, [verify] returns true and behaviour is exactly as before. A false reject is
 * indistinguishable from "the wake word is broken", which is the failure mode this whole
 * area has already suffered enough of — so it never blocks on doubt.
 */
class SpeakerVerifier(
    private val context: Context,
    private val store: VoiceProfileStore,
) {

    private var extractor: SpeakerEmbeddingExtractor? = null
    private var manager: SpeakerEmbeddingManager? = null

    /** True when a model is loaded and at least one profile is enrolled. */
    val isActive: Boolean get() = manager != null && !store.isEmpty()

    /** True when the model file is on disk or still bundled as a debug asset. */
    fun isModelAvailable(): Boolean {
        if (VoiceModelStore(context).speakerReady()) return true
        return try {
            context.assets.open(MODEL).close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Loads the model and re-adds every stored embedding.
     *
     * Cheap to call repeatedly; rebuilds the manager so enrolment changes take effect.
     */
    fun reload(): Boolean {
        val profiles = store.all()
        if (profiles.isEmpty()) {
            manager = null
            return false
        }
        val ex = extractor ?: buildExtractor() ?: return false
        extractor = ex

        val mgr = SpeakerEmbeddingManager(ex.dim())
        var added = 0
        profiles.forEach { p ->
            p.embeddings.forEach { e ->
                // Same name across samples: the manager keeps them as multiple prints for
                // one speaker, which is the point of enrolling more than once.
                if (e.size == ex.dim() && mgr.add(p.name, e)) added++
            }
        }
        if (added == 0) {
            Log.w(TAG, "no usable embeddings (dim mismatch?); verification stays off")
            manager = null
            return false
        }
        manager = mgr
        Log.i(TAG, "verification active: $added print(s) across ${profiles.size} profile(s)")
        return true
    }

    /**
     * Embeds [samples] (16 kHz mono, -1..1) into a voice print, or null on failure.
     * Used for both enrolment and verification.
     */
    fun embed(samples: FloatArray, sampleRate: Int = 16_000): FloatArray? {
        if (samples.isEmpty()) return null
        val ex = extractor ?: buildExtractor() ?: return null
        extractor = ex
        return try {
            val stream = ex.createStream()
            try {
                stream.acceptWaveform(samples, sampleRate)
                // Tells the extractor no more audio is coming; without it the stream is
                // never "ready" and compute() has nothing to work with.
                stream.inputFinished()
                if (!ex.isReady(stream)) {
                    Log.w(TAG, "not enough audio to embed (${samples.size} samples)")
                    null
                } else {
                    ex.compute(stream)
                }
            } finally {
                runCatching { stream.release() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "embedding failed: ${e.message}")
            null
        }
    }

    /**
     * True if [samples] sound like an enrolled speaker — or if we cannot tell.
     *
     * @param threshold cosine similarity floor; see [VoiceProfileStore.DEFAULT_THRESHOLD].
     */
    /**
     * Matching enrolled name, or null when nobody clears the bar / we cannot tell.
     * Unlike [verify], this does not fail open — unknown is unknown.
     */
    fun identify(samples: FloatArray, threshold: Float = VoiceProfileStore.DEFAULT_THRESHOLD): String? {
        val mgr = manager ?: return null
        val embedding = embed(samples) ?: return null
        return try {
            mgr.search(embedding, threshold).trim().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "identify failed: ${e.message}")
            null
        }
    }

    fun identifyPcm(
        pcm: ShortArray,
        threshold: Float = VoiceProfileStore.DEFAULT_THRESHOLD,
    ): String? {
        if (pcm.isEmpty()) return null
        return identify(FloatArray(pcm.size) { pcm[it] / 32_768.0f }, threshold)
    }

    /** Save a named print and reload so the next identify can match them. */
    fun enroll(name: String, samples: FloatArray): Boolean {
        val emb = embed(samples) ?: return false
        store.addSample(name, emb)
        return reload()
    }

    fun enrollPcm(name: String, pcm: ShortArray): Boolean {
        if (pcm.isEmpty()) return false
        return enroll(name, FloatArray(pcm.size) { pcm[it] / 32_768.0f })
    }

    fun verify(samples: FloatArray, threshold: Float = VoiceProfileStore.DEFAULT_THRESHOLD): Boolean {
        val mgr = manager ?: return true          // not enrolled -> no gating
        val embedding = embed(samples) ?: return true   // cannot tell -> allow
        return try {
            val who = mgr.search(embedding, threshold)
            if (who.isBlank()) {
                Log.i(TAG, "wake rejected: voice did not match an enrolled speaker")
                false
            } else {
                Log.i(TAG, "wake accepted: matched '$who'")
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "verification errored, allowing: ${e.message}")
            true
        }
    }

    /**
     * [verify] for the PCM16 buffer the wake engine keeps. Converts to the -1..1 floats
     * the extractor expects.
     */
    fun verifyPcm(
        pcm: ShortArray,
        threshold: Float = VoiceProfileStore.DEFAULT_THRESHOLD,
    ): Boolean {
        if (pcm.isEmpty()) return true
        return verify(FloatArray(pcm.size) { pcm[it] / 32_768.0f }, threshold)
    }

    /** [embed] for a PCM16 buffer, used when enrolling from captured audio. */
    fun embedPcm(pcm: ShortArray): FloatArray? {
        if (pcm.isEmpty()) return null
        return embed(FloatArray(pcm.size) { pcm[it] / 32_768.0f })
    }

    fun release() {
        runCatching { manager?.release() }
        runCatching { extractor?.release() }
        manager = null
        extractor = null
    }

    private fun buildExtractor(): SpeakerEmbeddingExtractor? {
        if (!isModelAvailable()) return null
        return try {
            val onDisk = File(VoiceModelStore(context).root, MODEL)
            if (onDisk.isFile) {
                SpeakerEmbeddingExtractor(
                    assetManager = null,
                    config = SpeakerEmbeddingExtractorConfig(
                        model = onDisk.absolutePath,
                        numThreads = 1,
                    ),
                )
            } else {
                SpeakerEmbeddingExtractor(
                    assetManager = context.assets,
                    config = SpeakerEmbeddingExtractorConfig(model = MODEL, numThreads = 1),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "speaker model failed to load: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "SpeakerVerifier"

        /** wespeaker CAM++ trained on VoxCeleb (English), at the assets root. */
        const val MODEL = "speaker.onnx"

        /**
         * Enrolment samples to ask for. Three captures the natural variation in how a
         * phrase is said without making setup tedious.
         */
        const val RECOMMENDED_SAMPLES = 3
    }
}
