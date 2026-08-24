package com.example.meowmba.core.audio

import java.io.ByteArrayOutputStream

/**
 * PCM16 mono → RIFF/WAV bytes.
 *
 * Cloud speech APIs take a container, not the raw frames the recognizers pass
 * around internally. Kept pure (no android.*) so the header layout is a JVM
 * unit test — a wrong offset here produces a file the API rejects with an
 * unhelpful 400, which is miserable to debug on device.
 */
object WavEncoder {

    private const val HEADER_BYTES = 44
    private const val PCM_FORMAT = 1.toShort()
    private const val BITS_PER_SAMPLE = 16

    /** Encodes [pcm] (mono, signed 16-bit) at [sampleRate] into a WAV file. */
    fun encode(pcm: ShortArray, sampleRate: Int): ByteArray {
        val dataBytes = pcm.size * 2
        val out = ByteArrayOutputStream(HEADER_BYTES + dataBytes)
        val channels = 1
        val byteRate = sampleRate * channels * BITS_PER_SAMPLE / 8
        val blockAlign = channels * BITS_PER_SAMPLE / 8

        out.writeAscii("RIFF")
        out.writeIntLe(36 + dataBytes)          // chunk size = file size - 8
        out.writeAscii("WAVE")

        out.writeAscii("fmt ")
        out.writeIntLe(16)                      // PCM fmt chunk size
        out.writeShortLe(PCM_FORMAT)
        out.writeShortLe(channels.toShort())
        out.writeIntLe(sampleRate)
        out.writeIntLe(byteRate)
        out.writeShortLe(blockAlign.toShort())
        out.writeShortLe(BITS_PER_SAMPLE.toShort())

        out.writeAscii("data")
        out.writeIntLe(dataBytes)
        for (s in pcm) out.writeShortLe(s)

        return out.toByteArray()
    }

    /** Seconds of audio [pcm] represents at [sampleRate]. */
    fun durationSeconds(pcm: ShortArray, sampleRate: Int): Float =
        if (sampleRate <= 0) 0f else pcm.size.toFloat() / sampleRate

    private fun ByteArrayOutputStream.writeAscii(s: String) {
        for (c in s) write(c.code)
    }

    private fun ByteArrayOutputStream.writeIntLe(v: Int) {
        write(v and 0xFF)
        write((v ushr 8) and 0xFF)
        write((v ushr 16) and 0xFF)
        write((v ushr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeShortLe(v: Short) {
        val i = v.toInt()
        write(i and 0xFF)
        write((i ushr 8) and 0xFF)
    }
}
