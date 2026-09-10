package com.example.meowmba.core.audio

/**
 * Display-only stability, not acoustic confidence. A word must survive several
 * audio updates for a short interval before appearing. Revised suffixes retract
 * immediately; nothing here can commit a turn or replace the final recognizer.
 */
internal class StreamingTranscriptPreview(
    private val stableForMs: Long = 240L,
    private val resetAfterGapMs: Long = 2_000L,
) {
    private var words = emptyList<String>()
    private var since = emptyList<Long>()
    private var lastAt: Long? = null
    private val spaces = Regex("\\s+")

    init { require(stableForMs >= 0L && resetAfterGapMs > stableForMs) }

    fun reset() {
        words = emptyList()
        since = emptyList()
        lastAt = null
    }

    fun update(text: String, nowMs: Long): String {
        val previousAt = lastAt
        if (nowMs < 0L || text.length > 2_048 || text.isBlank() || SpokenText.isNonSpeechAnnotation(text)) {
            reset()
            return ""
        }
        if (previousAt != null && (nowMs < previousAt || nowMs - previousAt > resetAfterGapMs)) reset()
        val next = text.trim().split(spaces)
        var unchanged = 0
        while (unchanged < minOf(words.size, next.size) && words[unchanged].equals(next[unchanged], ignoreCase = true)) {
            unchanged++
        }
        since = List(next.size) { index -> if (index < unchanged) since[index] else nowMs }
        words = next
        lastAt = nowMs
        val stableCount = since.takeWhile { nowMs - it >= stableForMs }.size
        return words.take(stableCount).joinToString(" ").takeIf(SpokenText::isUsable).orEmpty()
    }
}

/** Keep the complete endpoint interval plus bounded trailing/chunk headroom. */
internal object AsrCaptureBounds {
    const val SAMPLE_RATE = 16_000
    const val MAX_ENDPOINT_SECONDS = 30
    const val PCM_CAPACITY = SAMPLE_RATE * (MAX_ENDPOINT_SECONDS + 2)
}

/** Numeric capture diagnostics only: no words, PCM content, or audio persistence. */
internal data class AsrPcmSummary(
    val samples: Int,
    val audioMs: Long,
    val peak: Int,
    val nearFullScaleSamples: Int,
    val zeroSamples: Int,
) {
    companion object {
        fun from(pcm: ShortArray, sampleRate: Int): AsrPcmSummary {
            require(sampleRate > 0)
            var peak = 0
            var nearFullScale = 0
            var zeros = 0
            for (sample in pcm) {
                val magnitude = kotlin.math.abs(sample.toInt())
                peak = maxOf(peak, magnitude)
                if (magnitude >= 32_760) nearFullScale++
                if (sample == 0.toShort()) zeros++
            }
            return AsrPcmSummary(pcm.size, pcm.size.toLong() * 1000L / sampleRate, peak, nearFullScale, zeros)
        }
    }
}
