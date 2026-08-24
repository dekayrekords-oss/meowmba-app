package com.example.meowmba

import com.example.meowmba.core.audio.AsrBackend
import com.example.meowmba.core.audio.AsrBackendStatus
import com.example.meowmba.core.audio.GeminiAsrEngine
import com.example.meowmba.core.audio.WavEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Voice-recognition backend selection, WAV framing, and cloud response
 * parsing. Pure JVM per house rule — no android.* is touched by any of these.
 */
class AsrBackendTest {

    // ── selection persistence ───────────────────────────────────────────

    @Test
    fun `unknown or missing id falls back to the default`() {
        assertEquals(AsrBackend.DEFAULT, AsrBackend.fromId(null))
        assertEquals(AsrBackend.DEFAULT, AsrBackend.fromId(""))
        assertEquals(AsrBackend.DEFAULT, AsrBackend.fromId("whisper-from-a-past-build"))
    }

    @Test
    fun `every backend id round-trips`() {
        AsrBackend.entries.forEach { backend ->
            assertEquals(backend, AsrBackend.fromId(backend.id))
        }
    }

    @Test
    fun `only the gemini backend is marked as leaving the device`() {
        val cloud = AsrBackend.entries.filter { it.isCloud }
        assertEquals(listOf(AsrBackend.GEMINI_CLOUD), cloud)
    }

    // ── availability matrix ─────────────────────────────────────────────

    @Test
    fun `gemini needs both a key and the local model`() {
        fun status(key: Boolean, local: Boolean) = AsrBackendStatus.evaluate(
            AsrBackend.GEMINI_CLOUD,
            platformAvailable = true,
            localModelReady = local,
            cloudKeyConfigured = key,
        )

        assertTrue(status(key = true, local = true).available)
        // Cloud transcription rides on the local engine's endpointing, so a
        // key alone is not enough — and the reason must say so.
        assertFalse(status(key = true, local = false).available)
        assertTrue(status(key = true, local = false).reason.contains("endpointing"))
        assertFalse(status(key = false, local = true).available)
        assertTrue(status(key = false, local = true).reason.contains("OpenRouter"))
        assertFalse(status(key = false, local = false).available)
    }

    @Test
    fun `local backend only needs its model, not a key`() {
        val s = AsrBackendStatus.evaluate(
            AsrBackend.LOCAL_SHERPA,
            platformAvailable = false,
            localModelReady = true,
            cloudKeyConfigured = false,
        )
        assertTrue(s.available)
    }

    @Test
    fun `auto is available when either engine is`() {
        fun auto(platform: Boolean, local: Boolean) = AsrBackendStatus.evaluate(
            AsrBackend.AUTO, platform, local, cloudKeyConfigured = false,
        ).available

        assertTrue(auto(platform = true, local = false))
        assertTrue(auto(platform = false, local = true))
        assertFalse(auto(platform = false, local = false))
    }

    @Test
    fun `an unavailable backend always explains why`() {
        AsrBackendStatus.evaluateAll(
            platformAvailable = false,
            localModelReady = false,
            cloudKeyConfigured = false,
        ).forEach { status ->
            if (!status.available) {
                assertTrue(
                    "${status.backend} gave no reason",
                    status.reason.isNotBlank(),
                )
            }
        }
    }

    // ── WAV framing ─────────────────────────────────────────────────────

    @Test
    fun `wav header declares the right sizes and format`() {
        val pcm = ShortArray(160) { it.toShort() }
        val wav = WavEncoder.encode(pcm, 16_000)

        assertEquals(44 + pcm.size * 2, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))

        // RIFF chunk size = total - 8
        assertEquals(wav.size - 8, wav.intLeAt(4))
        // PCM format tag (1) and mono
        assertEquals(1, wav.shortLeAt(20))
        assertEquals(1, wav.shortLeAt(22))
        assertEquals(16_000, wav.intLeAt(24))
        // byte rate = rate * channels * bits/8
        assertEquals(16_000 * 2, wav.intLeAt(28))
        assertEquals(16, wav.shortLeAt(34))
        assertEquals(pcm.size * 2, wav.intLeAt(40))
    }

    @Test
    fun `samples are written little-endian`() {
        val wav = WavEncoder.encode(shortArrayOf(0x0102), 16_000)
        assertEquals(0x02, wav[44].toInt() and 0xFF)
        assertEquals(0x01, wav[45].toInt() and 0xFF)
    }

    @Test
    fun `empty audio still produces a valid, empty wav`() {
        val wav = WavEncoder.encode(ShortArray(0), 16_000)
        assertEquals(44, wav.size)
        assertEquals(0, wav.intLeAt(40))
    }

    @Test
    fun `duration is samples over rate, and a zero rate cannot divide by zero`() {
        assertEquals(1.0f, WavEncoder.durationSeconds(ShortArray(16_000), 16_000), 0.0001f)
        assertEquals(0f, WavEncoder.durationSeconds(ShortArray(16_000), 0), 0.0001f)
    }

    // ── cloud response parsing ──────────────────────────────────────────

    @Test
    fun `parses the transcript out of an openrouter completion`() {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":"turn on the kitchen light"}}]}
        """.trimIndent()
        assertEquals("turn on the kitchen light", GeminiAsrEngine.parseTranscript(body))
    }

    @Test
    fun `strips quotes the model wraps around the transcript`() {
        val body = """{"choices":[{"message":{"content":"\"hello there\""}}]}"""
        assertEquals("hello there", GeminiAsrEngine.parseTranscript(body))
    }

    @Test
    fun `a shouting transcript is sentence-cased like the local path`() {
        // Cloud text goes through the same SpokenText.polish as sherpa's, so an
        // all-caps reply is normalised rather than shouted at the user.
        val body = """{"choices":[{"message":{"content":"WHAT TIME IS IT"}}]}"""
        assertEquals("What time is it", GeminiAsrEngine.parseTranscript(body))
    }

    @Test
    fun `malformed or empty responses parse to null rather than throwing`() {
        // Each of these used to be indistinguishable from "heard nothing";
        // they must be null so the caller falls back to the local transcript.
        assertNull(GeminiAsrEngine.parseTranscript(""))
        assertNull(GeminiAsrEngine.parseTranscript("not json at all"))
        assertNull(GeminiAsrEngine.parseTranscript("""{"choices":[]}"""))
        assertNull(GeminiAsrEngine.parseTranscript("""{"error":{"message":"no credit"}}"""))
        assertNull(GeminiAsrEngine.parseTranscript("""{"choices":[{"message":{"content":""}}]}"""))
        assertNull(GeminiAsrEngine.parseTranscript("""{"choices":[{"message":{"content":"   "}}]}"""))
    }

    // ── request shape ───────────────────────────────────────────────────

    @Test
    fun `request sends the audio as an input_audio wav part`() {
        val body = GeminiAsrEngine.requestBody("google/gemini-3.6-flash", "QUJD").toString()
        assertTrue(body.contains("\"model\":\"google/gemini-3.6-flash\""))
        assertTrue(body.contains("\"type\":\"input_audio\""))
        assertTrue(body.contains("\"format\":\"wav\""))
        assertTrue(body.contains("\"data\":\"QUJD\""))
        // Transcription must not sample — a creative "transcript" is a wrong one.
        assertTrue(body.contains("\"temperature\":0.0"))
    }

    @Test
    fun `default cloud model is a gemini id`() {
        assertTrue(AsrBackend.DEFAULT_GEMINI_MODEL.startsWith("google/gemini"))
    }

    private fun ByteArray.intLeAt(i: Int): Int =
        (this[i].toInt() and 0xFF) or
            ((this[i + 1].toInt() and 0xFF) shl 8) or
            ((this[i + 2].toInt() and 0xFF) shl 16) or
            ((this[i + 3].toInt() and 0xFF) shl 24)

    private fun ByteArray.shortLeAt(i: Int): Int =
        (this[i].toInt() and 0xFF) or ((this[i + 1].toInt() and 0xFF) shl 8)
}
