package com.example.meowmba.core.audio

/** Bounded primitive PCM storage: no boxed Short per sample or shifting on overflow. */
internal class PcmRingBuffer(private val capacity: Int) {
    init { require(capacity > 0) }
    private val samples = ShortArray(capacity)
    private var writeIndex = 0
    var size = 0
        private set

    fun append(pcm: ShortArray, count: Int = pcm.size, repeatEach: Int = 1) {
        require(count in 0..pcm.size && repeatEach in 1..2)
        for (i in 0 until count) repeat(repeatEach) {
            samples[writeIndex] = pcm[i]
            writeIndex = (writeIndex + 1) % capacity
            if (size < capacity) size++
        }
    }

    fun snapshot(): ShortArray {
        val start = (writeIndex - size + capacity) % capacity
        val first = minOf(size, capacity - start)
        return ShortArray(size).also {
            samples.copyInto(it, 0, start, start + first)
            samples.copyInto(it, first, 0, size - first)
        }
    }

    fun clear() { writeIndex = 0; size = 0 }
}
