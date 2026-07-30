package com.example.meowmba.core.audio

/**
 * Fixed-size ring buffer of the most recent audio.
 *
 * A keyword spotter tells you the wake phrase fired, but hands back no transcript. So
 * unlike the old ASR loop — where "hey meow what time is it" arrived as one string and
 * the command came free — the words spoken AFTER the wake phrase are only in the audio.
 * Starting recognition from scratch at that point clips them.
 *
 * Keeping the last couple of seconds means the command can be replayed into the
 * recogniser, so one-breath requests still work.
 *
 * Not thread-safe: the capture loop owns it.
 */
class AudioPreRoll(private val capacity: Int) {

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val buf = ShortArray(capacity)

    /** Next write position. */
    private var head = 0

    /** Samples currently held, capped at [capacity]. */
    var size = 0
        private set

    fun append(samples: ShortArray, count: Int = samples.size) {
        val n = count.coerceAtMost(samples.size)
        if (n <= 0) return

        // More than the whole buffer: only the tail can survive.
        if (n >= capacity) {
            System.arraycopy(samples, n - capacity, buf, 0, capacity)
            head = 0
            size = capacity
            return
        }

        val firstChunk = minOf(n, capacity - head)
        System.arraycopy(samples, 0, buf, head, firstChunk)
        val remainder = n - firstChunk
        if (remainder > 0) System.arraycopy(samples, firstChunk, buf, 0, remainder)

        head = (head + n) % capacity
        size = minOf(size + n, capacity)
    }

    /** Contents oldest-first. */
    fun snapshot(): ShortArray {
        val out = ShortArray(size)
        if (size == 0) return out
        val start = ((head - size) % capacity + capacity) % capacity
        val firstChunk = minOf(size, capacity - start)
        System.arraycopy(buf, start, out, 0, firstChunk)
        if (size > firstChunk) {
            System.arraycopy(buf, 0, out, firstChunk, size - firstChunk)
        }
        return out
    }

    fun clear() {
        head = 0
        size = 0
    }

    companion object {
        /**
         * How much audio to retain. Two seconds at 16 kHz mono covers a wake phrase plus
         * the beginning of a command without holding meaningful memory (64 KB).
         */
        fun forSeconds(seconds: Float, sampleRate: Int = 16_000) =
            AudioPreRoll((seconds * sampleRate).toInt())
    }
}
