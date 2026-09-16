package com.example.meowmba.core.media

/** Container validation only: an audio payload is not evidence of musical
 * quality. Reject error pages, truncated chunks and empty/provider placeholders. */
internal object WavValidation {
    fun hasAudio(bytes: ByteArray): Boolean {
        if (bytes.size < 44 || bytes.size > 32 * 1024 * 1024) return false
        fun tag(offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)
        fun u32(offset: Int): Long = (0..3).fold(0L) { value, i ->
            value or ((bytes[offset + i].toLong() and 255L) shl (8 * i))
        }
        fun u16(offset: Int): Int = (bytes[offset].toInt() and 255) or
            ((bytes[offset + 1].toInt() and 255) shl 8)
        if (tag(0) != "RIFF" || tag(8) != "WAVE" || u32(4) + 8 != bytes.size.toLong()) return false
        var offset = 12
        var align = 0
        var dataSize = 0L
        while (offset + 8 <= bytes.size) {
            val size = u32(offset + 4)
            val start = offset + 8
            val end = start.toLong() + size
            if (end > bytes.size) return false
            when (tag(offset)) {
                "fmt " -> {
                    if (align != 0 || size < 16) return false
                    val format = u16(start)
                    val channels = u16(start + 2)
                    val sampleRate = u32(start + 4)
                    val byteRate = u32(start + 8)
                    align = u16(start + 12)
                    val bits = u16(start + 14)
                    if (format !in setOf(1, 3, 0xfffe) || channels !in 1..32 ||
                        sampleRate !in 1L..384_000L || bits !in setOf(8, 16, 24, 32, 64) ||
                        align != channels * (bits / 8) || byteRate != sampleRate * align) return false
                    if (format == 3 && bits !in setOf(32, 64)) return false
                    if (format == 0xfffe) {
                        if (size < 40 || u16(start + 16) < 22 || u16(start + 18) !in 1..bits) return false
                        val subFormat = u32(start + 24)
                        val standardTail = byteArrayOf(0, 0, 16, 0, -128, 0, 0, -86, 0, 56, -101, 113)
                        if (subFormat !in setOf(1L, 3L) ||
                            !bytes.copyOfRange(start + 28, start + 40).contentEquals(standardTail) ||
                            (subFormat == 3L && bits !in setOf(32, 64))) return false
                    }
                }
                "data" -> {
                    if (align == 0 || size == 0L || size % align != 0L) return false
                    dataSize += size
                }
            }
            offset = (end + size % 2).toInt()
        }
        return offset == bytes.size && align > 0 && dataSize > 0
    }
}
