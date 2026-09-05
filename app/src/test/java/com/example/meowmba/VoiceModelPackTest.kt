package com.example.meowmba

import com.example.meowmba.core.audio.VoiceModelPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class VoiceModelPackTest {

    @Test
    fun `kws required set is what sherpa will open`() {
        assertEquals(
            listOf(
                VoiceModelPack.ENCODER,
                VoiceModelPack.DECODER,
                VoiceModelPack.JOINER,
                VoiceModelPack.TOKENS,
                VoiceModelPack.KEYWORDS,
            ),
            VoiceModelPack.kwsRequired,
        )
        VoiceModelPack.kwsRequired.forEach { name ->
            assertTrue(VoiceModelPack.kwsRel(name).startsWith(VoiceModelPack.KWS_DIR))
        }
    }

    @Test
    fun `download urls are https github releases`() {
        assertTrue(VoiceModelPack.KWS_TAR_URL.startsWith("https://github.com/k2-fsa/sherpa-onnx/"))
        assertTrue(VoiceModelPack.ASR_TAR_URL.startsWith("https://github.com/k2-fsa/sherpa-onnx/"))
        assertTrue(VoiceModelPack.VAD_URL.startsWith("https://"))
        assertTrue(VoiceModelPack.SPEAKER_URL.startsWith("https://"))
        assertEquals(3, VoiceModelPack.whisperDownloads.size)
        assertTrue(VoiceModelPack.whisperDownloads.values.all { it.startsWith("https://huggingface.co/") })
    }

    /**
     * The official 2023-06-26 int8 decoder really is ~540KB.
     *
     * This test used to assert the opposite — that a 540,688-byte decoder was
     * a truncated download to be rejected — on the theory that the genuine
     * file was 1.3MB. It is not: re-downloading the k2-fsa release archive
     * (2026-08-22), verifying it with `bzip2 -t` and extracting it yields
     * exactly 540,688 bytes. The old floor rejected every correctly-downloaded
     * primary pack, so the app silently ran the weaker 20M fallback forever —
     * which is what "voice recognition is terrible" actually was.
     *
     * Its near-identical size to the fallback pack's decoder (539,499) is a
     * coincidence of vocabulary size, not evidence of a wrong file.
     */
    @Test
    fun `primary asr pack accepts the real 540KB decoder`() {
        val dir = kotlin.io.path.createTempDirectory("asr-primary").toFile()
        try {
            sizedFile(File(dir, VoiceModelPack.ASR_ENCODER), 70_108_816L)
            sizedFile(File(dir, VoiceModelPack.ASR_DECODER), 540_688L)
            sizedFile(File(dir, VoiceModelPack.ASR_JOINER), 259_416L)
            File(dir, VoiceModelPack.ASR_TOKENS).writeText("t".repeat(1200))
            assertTrue(VoiceModelPack.ASR_PRIMARY.isComplete(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * A genuinely half-written unpack must still be rejected — that is the
     * abort this guard exists to prevent (onnxruntime SIGSEGVs on a partial
     * .onnx rather than failing gracefully).
     */
    @Test
    fun `primary asr pack still rejects a genuinely truncated unpack`() {
        val dir = kotlin.io.path.createTempDirectory("asr-truncated").toFile()
        try {
            sizedFile(File(dir, VoiceModelPack.ASR_ENCODER), 70_108_816L)
            sizedFile(File(dir, VoiceModelPack.ASR_DECODER), 40_000L)
            sizedFile(File(dir, VoiceModelPack.ASR_JOINER), 259_416L)
            File(dir, VoiceModelPack.ASR_TOKENS).writeText("t".repeat(1200))
            assertTrue(!VoiceModelPack.ASR_PRIMARY.isComplete(dir))

            // A stalled encoder download is the other half of the same trap.
            sizedFile(File(dir, VoiceModelPack.ASR_DECODER), 540_688L)
            sizedFile(File(dir, VoiceModelPack.ASR_ENCODER), 8_192L)
            assertTrue(!VoiceModelPack.ASR_PRIMARY.isComplete(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `fallback asr pack accepts the 20M sizes on device`() {
        val dir = kotlin.io.path.createTempDirectory("asr-fallback").toFile()
        try {
            sizedFile(File(dir, VoiceModelPack.ASR_FALLBACK.encoder), 42_845_182L)
            sizedFile(File(dir, VoiceModelPack.ASR_FALLBACK.decoder), 539_499L)
            sizedFile(File(dir, VoiceModelPack.ASR_FALLBACK.joiner), 259_572L)
            File(dir, VoiceModelPack.ASR_FALLBACK.tokens).writeText("t".repeat(1200))
            assertTrue(VoiceModelPack.ASR_FALLBACK.isComplete(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun sizedFile(file: File, bytes: Long) {
        RandomAccessFile(file, "rw").use { it.setLength(bytes) }
    }

    @Test
    fun `keywords file is non-empty and includes yomeow`() {
        assertTrue(VoiceModelPack.keywordsText.contains("Y OM E OW"))
        assertTrue(VoiceModelPack.keywordsText.endsWith("\n"))
    }

}
